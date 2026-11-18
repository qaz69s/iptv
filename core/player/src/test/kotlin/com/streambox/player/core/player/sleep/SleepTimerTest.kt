package com.streambox.player.core.player.sleep

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/** Runs on the test scheduler: the clock is its virtual time, the timer a child of the TestScope. */
@OptIn(ExperimentalCoroutinesApi::class)
class SleepTimerTest {
    private class Harness(scope: TestScope) : SleepTimerOutput {
        val volumes = mutableListOf<Float>()
        val expiredAt = mutableListOf<Long>()

        /** Volume at the moment the timer expired. */
        var volumeAtExpiry: Float? = null
        private val time = scope.testScheduler
        val timer = SleepTimer(scope, MonotonicClock { time.currentTime }, this)

        override fun setVolume(volume: Float) {
            volumes += volume
        }

        override fun onExpired() {
            expiredAt += time.currentTime
            volumeAtExpiry = volumes.lastOrNull()
        }

        val volume: Float get() = volumes.lastOrNull() ?: 1f
    }

    private fun TestScope.harness() = Harness(this)

    @ParameterizedTest
    @ValueSource(ints = [15, 30, 60, 90])
    fun `each preset pauses exactly when its time is up`(minutes: Int) = runTest {
        val h = harness()
        val durationMs = minutes * 60_000L

        h.timer.start(minutes)
        runCurrent()
        assertEquals(SleepTimerState(minutes, durationMs), h.timer.state.value)

        advanceTimeBy(durationMs - 1)
        runCurrent()
        assertTrue(h.expiredAt.isEmpty())
        assertTrue(h.timer.state.value!!.remainingMs <= SleepTimer.FADE_STEP_MS)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(durationMs), h.expiredAt)
        assertNull(h.timer.state.value)
        assertEquals(1f, h.volume)
    }

    @Test
    fun `custom minutes are clamped to 1 to 600`() = runTest {
        val h = harness()

        h.timer.start(0)
        assertEquals(1, h.timer.state.value?.durationMinutes)
        h.timer.start(601)
        assertEquals(600, h.timer.state.value?.durationMinutes)
        h.timer.start(45)
        assertEquals(45, h.timer.state.value?.durationMinutes)

        advanceUntilIdle()
        assertEquals(listOf(45 * 60_000L), h.expiredAt)
    }

    @Test
    fun `remaining time ticks every second`() = runTest {
        val h = harness()
        h.timer.start(1)
        runCurrent()

        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(59_000L, h.timer.state.value?.remainingMs)
        advanceTimeBy(30_000)
        runCurrent()
        assertEquals(29_000L, h.timer.state.value?.remainingMs)
        assertFalse(h.timer.state.value!!.fading)
        assertTrue(h.volumes.isEmpty())
    }

    @Test
    fun `volume fades out linearly over the last 10 seconds then is restored after pausing`() = runTest {
        val h = harness()
        h.timer.start(1)
        runCurrent()

        advanceTimeBy(49_999)
        runCurrent()
        assertTrue(h.volumes.isEmpty(), "no fade before the last 10 s")

        advanceTimeBy(1)
        runCurrent()
        assertEquals(1f, h.volume, 0.001f)
        assertTrue(h.timer.state.value!!.fading)

        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(0.5f, h.volume, 0.001f)

        advanceTimeBy(2_500)
        runCurrent()
        assertEquals(0.25f, h.volume, 0.001f)

        advanceTimeBy(2_500)
        runCurrent()
        assertEquals(listOf(60_000L), h.expiredAt)
        assertEquals(0f, h.volumeAtExpiry)
        assertEquals(1f, h.volume)
        // Volume only goes down during the fade.
        val fade = h.volumes.dropLast(1)
        assertEquals(fade.sortedDescending(), fade)
    }

    @Test
    fun `cancel stops the countdown and restores a faded volume`() = runTest {
        val h = harness()
        h.timer.start(1)
        advanceTimeBy(55_000)
        runCurrent()
        assertEquals(0.5f, h.volume, 0.001f)

        h.timer.cancel()

        assertNull(h.timer.state.value)
        assertEquals(1f, h.volume)
        advanceUntilIdle()
        assertTrue(h.expiredAt.isEmpty())
    }

    @Test
    fun `cancel before the fade leaves the volume alone`() = runTest {
        val h = harness()
        h.timer.start(15)
        advanceTimeBy(60_000)

        h.timer.cancel()
        advanceUntilIdle()

        assertTrue(h.volumes.isEmpty())
        assertTrue(h.expiredAt.isEmpty())
    }

    @Test
    fun `setting a new duration restarts from now`() = runTest {
        val h = harness()
        h.timer.start(15)
        advanceTimeBy(10 * 60_000L)
        runCurrent()

        h.timer.start(30)
        runCurrent()
        assertEquals(SleepTimerState(30, 30 * 60_000L), h.timer.state.value)

        advanceTimeBy(30 * 60_000L - 1)
        runCurrent()
        assertTrue(h.expiredAt.isEmpty(), "the old 15 min deadline no longer fires")
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(40 * 60_000L), h.expiredAt)
    }

    @Test
    fun `restarting during the fade restores full volume`() = runTest {
        val h = harness()
        h.timer.start(1)
        advanceTimeBy(58_000)
        runCurrent()
        assertTrue(h.volume < 0.5f)

        h.timer.start(15)
        runCurrent()

        assertEquals(1f, h.volume)
        assertFalse(h.timer.state.value!!.fading)
    }

    @Test
    fun `a late tick never stretches the deadline`() = runTest {
        val h = harness()
        var skew = 0L
        val timer = SleepTimer(this, MonotonicClock { testScheduler.currentTime + skew }, h)
        timer.start(1)
        runCurrent()

        // The device slept: the monotonic clock jumped 30 s ahead of the scheduled delays.
        skew = 30_000
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(29_000L, timer.state.value?.remainingMs)

        advanceTimeBy(29_000)
        runCurrent()
        assertEquals(1, h.expiredAt.size)
    }
}
