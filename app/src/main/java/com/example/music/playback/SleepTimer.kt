package com.example.music.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Sleep timer choices, in the order a tap cycles through them. */
enum class SleepMode(val label: String, val minutes: Int) {
    OFF("Off", 0),
    MIN_15("15 min", 15),
    MIN_30("30 min", 30),
    MIN_60("1 hour", 60),
    END_OF_TRACK("End of track", 0),
}

data class SleepTimerState(val mode: SleepMode = SleepMode.OFF, val endsAtMs: Long = 0L)

/** Shared between the options sheet (sets it) and [PlaybackService] (acts on it). */
class SleepTimer(private val clock: () -> Long = System::currentTimeMillis) {
    private val _state = MutableStateFlow(SleepTimerState())
    val state: StateFlow<SleepTimerState> = _state.asStateFlow()

    fun cycle() {
        val next = SleepMode.entries[(_state.value.mode.ordinal + 1) % SleepMode.entries.size]
        set(next)
    }

    fun set(mode: SleepMode) {
        _state.value = SleepTimerState(mode, if (mode.minutes > 0) clock() + mode.minutes * 60_000L else 0L)
    }

    fun cancel() = set(SleepMode.OFF)

    /** "24 min left", "End of track" or "Off", for the options row. */
    fun describe(state: SleepTimerState, now: Long = clock()): String = when {
        state.mode == SleepMode.OFF -> "Off"
        state.mode == SleepMode.END_OF_TRACK -> "End of track"
        else -> "${((state.endsAtMs - now + 59_999) / 60_000).coerceAtLeast(1)} min left"
    }
}
