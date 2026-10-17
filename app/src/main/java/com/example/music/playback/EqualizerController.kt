package com.example.music.playback

import android.content.Context
import android.media.audiofx.Equalizer
import android.util.Log
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Sound presets, each a gain curve over frequency (dB), sampled at the device's band centers. */
enum class EqPreset(val label: String, private val curve: (hz: Double) -> Double) {
    FLAT("Flat", { 0.0 }),
    BASS("Bass boost", { hz -> when { hz < 120 -> 6.0; hz < 300 -> 4.0; hz < 800 -> 1.0; else -> 0.0 } }),
    HIP_HOP("Hip-hop", { hz -> when { hz < 120 -> 5.0; hz < 300 -> 3.0; hz < 1500 -> -1.0; hz < 5000 -> 1.0; else -> 3.0 } }),
    VOCAL("Vocal", { hz -> when { hz < 150 -> -2.0; hz < 500 -> 0.0; hz < 4000 -> 4.0; else -> 1.0 } }),
    PODCAST("Spoken word", { hz -> when { hz < 120 -> -6.0; hz < 300 -> -2.0; hz < 1000 -> 1.0; hz < 4000 -> 4.0; else -> 0.0 } }),
    TREBLE("Treble", { hz -> when { hz < 2000 -> 0.0; hz < 6000 -> 3.0; else -> 5.0 } }),
    CUSTOM("Custom", { 0.0 }),
    ;

    fun gainDb(hz: Double): Double = curve(hz)
}

data class EqBand(val index: Int, val centerHz: Int, val levelMb: Int)

data class EqState(
    val supported: Boolean = false,
    val enabled: Boolean = false,
    val preset: EqPreset = EqPreset.FLAT,
    val bands: List<EqBand> = emptyList(),
    val minMb: Int = -1500,
    val maxMb: Int = 1500,
)

/**
 * The platform [Equalizer] on the player's audio session. Created by [PlaybackService], driven by
 * the Equalizer screen. Settings persist in shared preferences and are re-applied whenever the
 * player gets a new audio session.
 */
class EqualizerController(context: Context) {
    private val prefs = context.getSharedPreferences("equalizer", Context.MODE_PRIVATE)
    private var eq: Equalizer? = null

    private val _state = MutableStateFlow(EqState(enabled = prefs.getBoolean(KEY_ENABLED, false)))
    val state: StateFlow<EqState> = _state.asStateFlow()

    fun attach(audioSessionId: Int) {
        if (audioSessionId == 0) return
        release()
        eq = runCatching { Equalizer(0, audioSessionId) }
            .onFailure { Log.w(TAG, "Equalizer unavailable", it) }
            .getOrNull()
        val e = eq ?: run { _state.value = _state.value.copy(supported = false); return }
        val range = e.bandLevelRange
        val saved = prefs.getString(KEY_LEVELS, null)?.split(',')?.mapNotNull { it.toIntOrNull() }
        val preset = runCatching { EqPreset.valueOf(prefs.getString(KEY_PRESET, EqPreset.FLAT.name)!!) }
            .getOrDefault(EqPreset.FLAT)
        val bands = (0 until e.numberOfBands).map { i ->
            val center = e.getCenterFreq(i.toShort()) / 1000
            val level = saved?.getOrNull(i) ?: 0
            runCatching { e.setBandLevel(i.toShort(), level.toShort()) }
            EqBand(i, center, level)
        }
        val enabled = prefs.getBoolean(KEY_ENABLED, false)
        runCatching { e.enabled = enabled }
        _state.value = EqState(true, enabled, preset, bands, range[0].toInt(), range[1].toInt())
    }

    fun setEnabled(on: Boolean) {
        runCatching { eq?.enabled = on }
        prefs.edit { putBoolean(KEY_ENABLED, on) }
        _state.value = _state.value.copy(enabled = on)
    }

    fun applyPreset(preset: EqPreset) {
        val s = _state.value
        val levels = s.bands.map { band ->
            (preset.gainDb(band.centerHz.toDouble()) * 100).toInt().coerceIn(s.minMb, s.maxMb)
        }
        setLevels(levels, preset)
        if (!s.enabled && preset != EqPreset.FLAT) setEnabled(true)
    }

    /** Sets one band (millibels) and switches the preset label to Custom. */
    fun setBand(index: Int, levelMb: Int) {
        val levels = _state.value.bands.map { if (it.index == index) levelMb else it.levelMb }
        setLevels(levels, EqPreset.CUSTOM)
    }

    private fun setLevels(levels: List<Int>, preset: EqPreset) {
        val s = _state.value
        val clamped = levels.map { it.coerceIn(s.minMb, s.maxMb) }
        clamped.forEachIndexed { i, mb -> runCatching { eq?.setBandLevel(i.toShort(), mb.toShort()) } }
        prefs.edit {
            putString(KEY_LEVELS, clamped.joinToString(","))
            putString(KEY_PRESET, preset.name)
        }
        _state.value = s.copy(preset = preset, bands = s.bands.mapIndexed { i, b -> b.copy(levelMb = clamped[i]) })
    }

    fun release() {
        runCatching { eq?.release() }
        eq = null
    }

    companion object {
        private const val TAG = "Equalizer"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_LEVELS = "levels"
        private const val KEY_PRESET = "preset"

        fun formatHz(hz: Int): String = if (hz >= 1000) "${"%.1f".format(hz / 1000.0).removeSuffix(".0")} kHz" else "$hz Hz"
    }
}
