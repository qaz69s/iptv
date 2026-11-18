package com.streambox.player.feature.player

import com.streambox.player.core.player.sleep.SleepTimerState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SleepTimerInputTest {
    @Test
    fun `custom minutes accept 1 to 600 only`() {
        assertEquals(1, SleepTimerInput.parseMinutes("1"))
        assertEquals(600, SleepTimerInput.parseMinutes("600"))
        assertEquals(45, SleepTimerInput.parseMinutes(" 45 "))
        assertNull(SleepTimerInput.parseMinutes(""))
        assertNull(SleepTimerInput.parseMinutes("0"))
        assertNull(SleepTimerInput.parseMinutes("601"))
        assertNull(SleepTimerInput.parseMinutes("abc"))
    }

    @Test
    fun `remaining time is shown as m_ss or h_mm_ss, rounded up`() {
        assertEquals("15:00", formatRemaining(15 * 60_000L))
        assertEquals("0:10", formatRemaining(9_001))
        assertEquals("0:01", formatRemaining(1))
        assertEquals("1:30:00", formatRemaining(90 * 60_000L))
        assertEquals("10:00:00", formatRemaining(600 * 60_000L))
    }

    @Test
    fun `selected chip follows the running timer`() {
        assertEquals(SleepChoice.Off, SleepChoice.of(null))
        assertEquals(SleepChoice.Preset(30), SleepChoice.of(SleepTimerState(30, 1_000)))
        assertEquals(SleepChoice.Custom, SleepChoice.of(SleepTimerState(45, 1_000)))
        assertEquals(6, SleepChoice.ALL.size)
    }
}
