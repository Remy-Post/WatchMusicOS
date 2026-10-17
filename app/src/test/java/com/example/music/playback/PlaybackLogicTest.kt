package com.example.music.playback

import com.example.music.presentation.player.OutputKind
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackLogicTest {

    @Test
    fun `paired devices map to the right output icon`() {
        assertEquals(OutputKind.EARBUDS, OutputKind.of(false, "AirPods Pro"))
        assertEquals(OutputKind.EARBUDS, OutputKind.of(false, "Buds3 Pro"))
        assertEquals(OutputKind.HEADPHONES, OutputKind.of(false, "WH-1000XM6"))
        assertEquals(OutputKind.HEADPHONES, OutputKind.of(false, "Beats Studio³"))
        assertEquals(OutputKind.SPEAKER, OutputKind.of(false, "JBL Go 4"))
        assertEquals(OutputKind.SPEAKER, OutputKind.of(false, "Remy's JBL Flip 6"))
        assertEquals(OutputKind.WATCH, OutputKind.of(false, "Phone"))
        assertEquals(OutputKind.WATCH, OutputKind.of(true, "anything"))
    }

    @Test
    fun `sleep timer cycles and counts down`() {
        var now = 0L
        val timer = SleepTimer { now }
        timer.cycle()
        assertEquals(SleepMode.MIN_15, timer.state.value.mode)
        assertEquals(15 * 60_000L, timer.state.value.endsAtMs)
        now = 6 * 60_000L
        assertEquals("9 min left", timer.describe(timer.state.value, now))
        repeat(3) { timer.cycle() }
        assertEquals(SleepMode.END_OF_TRACK, timer.state.value.mode)
        timer.cycle()
        assertEquals(SleepMode.OFF, timer.state.value.mode)
    }
}
