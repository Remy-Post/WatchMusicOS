package com.example.music.presentation.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Slider
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import com.example.music.appGraph
import com.example.music.playback.EqPreset
import com.example.music.playback.EqualizerController
import com.example.music.presentation.components.Icons
import com.example.music.presentation.components.ListScaffold
import com.example.music.presentation.components.NavChip
import com.example.music.presentation.components.header
import com.example.music.presentation.components.hint
import com.example.music.presentation.components.sectionLabel
import com.example.music.presentation.theme.WatchColors

/** Sound settings: on/off, presets, and a slider per band (1 dB steps). */
@Composable
fun EqualizerScreen() {
    val controller = LocalContext.current.appGraph.equalizer
    val eq by controller.state.collectAsStateWithLifecycle()
    val primary = MaterialTheme.colorScheme.primary

    ListScaffold { t ->
        header(t, "Equalizer", Icons.Equalizer)
        if (!eq.supported) {
            hint(t, "unsupported", "Start playing a song to adjust the sound.")
            return@ListScaffold
        }
        item(key = "enabled") {
            with(t) {
                SwitchButton(
                    checked = eq.enabled,
                    onCheckedChange = controller::setEnabled,
                    modifier = Modifier.fillMaxWidth().morph(this@item),
                    transformation = surface(),
                    label = { Text(if (eq.enabled) "On" else "Off") },
                    secondaryLabel = { Text(eq.preset.label, color = WatchColors.OnSurfaceVariant) },
                )
            }
        }

        sectionLabel(t, "presets", "Presets")
        items(EqPreset.entries.filter { it != EqPreset.CUSTOM }, key = { "preset:" + it.name }) { preset ->
            val selected = eq.enabled && eq.preset == preset
            with(t) {
                NavChip(
                    icon = if (selected) Icons.Check else Icons.Equalizer,
                    title = preset.label,
                    subtitle = null,
                    iconTint = if (selected) primary else WatchColors.OnSurfaceVariant,
                    onClick = { controller.applyPreset(preset) },
                    modifier = Modifier.fillMaxWidth().morph(this@items),
                    transformation = surface(),
                )
            }
        }

        sectionLabel(t, "bands", "Bands")
        items(eq.bands, key = { "band:" + it.index }) { band ->
            val db = band.levelMb / 100
            with(t) {
                Column(Modifier.fillMaxWidth().morph(this@items), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.padding(bottom = 2.dp)) {
                        Text(EqualizerController.formatHz(band.centerHz), style = MaterialTheme.typography.labelMedium)
                        Text(
                            "  " + (if (db > 0) "+$db" else "$db") + " dB",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (db != 0) primary else WatchColors.OnSurfaceVariant,
                        )
                    }
                    Slider(
                        value = db,
                        onValueChange = { controller.setBand(band.index, it * 100) },
                        valueProgression = (eq.minMb / 100)..(eq.maxMb / 100),
                        enabled = eq.enabled,
                        segmented = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
