package com.streambox.player.core.player.reconnect

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * All timing runs on the test scheduler's virtual clock; timers are children of the TestScope so
 * advanceUntilIdle drives them.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReconnectorTest {
    private val config = ReconnectConfig(
        maxAttempts = 3,
        initialDelayMs = 1_000,
        maxDelayMs = 10_000,
        jitterRatio = 0.0,
        stableResetMs = 30_000,
    )

    /** Records when (virtual ms) each reconnect fired. */
    private class Harness(scope: TestScope, config: ReconnectConfig) {
        val firedAt = mutableListOf<Long>()
        val reconnector = Reconnector(scope, ReconnectPolicy(config)) {
            firedAt += scope.testScheduler.currentTime
        }
    }

    private fun TestScope.harness(config: ReconnectConfig = this@ReconnectorTest.config) = Harness(this, config)

    @Test
    fun `first failure schedules a retry after the initial delay`() = runTest {
        val h = harness()

        assertTrue(h.reconnector.onFailure(FailureKind.NETWORK))
        assertEquals(ReconnectStatus(1, 3), h.reconnector.status.value)

        advanceTimeBy(999)
        runCurrent()
        assertTrue(h.firedAt.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf(1_000L), h.firedAt)
    }

    @Test
    fun `consecutive failures back off exponentially`() = runTest {
        val h = harness()

        h.reconnector.onFailure(FailureKind.TIMEOUT)
        advanceTimeBy(1_000)
        runCurrent()
        h.reconnector.onFailure(FailureKind.TIMEOUT)
        advanceTimeBy(2_000)
        runCurrent()
        h.reconnector.onFailure(FailureKind.TIMEOUT)
        advanceUntilIdle()

        assertEquals(listOf(1_000L, 3_000L, 7_000L), h.firedAt)
        assertEquals(ReconnectStatus(3, 3), h.reconnector.status.value)
    }

    @Test
    fun `gives up after max attempts and clears the status`() = runTest {
        val h = harness()
        repeat(3) {
            assertTrue(h.reconnector.onFailure(FailureKind.NETWORK))
            advanceUntilIdle()
        }

        assertFalse(h.reconnector.onFailure(FailureKind.NETWORK))
        assertNull(h.reconnector.status.value)
        assertEquals(FailureKind.NETWORK, h.reconnector.gaveUpOn)
        assertEquals(3, h.firedAt.size)
    }

    @Test
    fun `non retryable failure is surfaced immediately`() = runTest {
        val h = harness()

        assertFalse(h.reconnector.onFailure(FailureKind.HTTP_CLIENT_ERROR))
        advanceUntilIdle()

        assertTrue(h.firedAt.isEmpty())
        assertNull(h.reconnector.status.value)
        assertNull(h.reconnector.gaveUpOn)
    }

    @Test
    fun `max attempts zero never reconnects, even when the network returns`() = runTest {
        val h = harness(config.copy(maxAttempts = 0))

        assertFalse(h.reconnector.onFailure(FailureKind.NETWORK))
        assertFalse(h.reconnector.onNetworkAvailable())
        advanceUntilIdle()

        assertTrue(h.firedAt.isEmpty())
    }

    @Test
    fun `playing hides the status but keeps the counter until playback is stable`() = runTest {
        val h = harness()
        h.reconnector.onFailure(FailureKind.NETWORK)
        advanceUntilIdle()

        h.reconnector.onPlaying()
        assertNull(h.reconnector.status.value)
        assertEquals(1, h.reconnector.attempts)

        advanceTimeBy(29_999)
        runCurrent()
        assertEquals(1, h.reconnector.attempts)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(0, h.reconnector.attempts)
    }

    @Test
    fun `failure before playback is stable keeps counting from where it was`() = runTest {
        val h = harness()
        h.reconnector.onFailure(FailureKind.NETWORK)
        advanceUntilIdle()
        h.reconnector.onPlaying()
        advanceTimeBy(10_000)

        h.reconnector.onFailure(FailureKind.NETWORK)

        assertEquals(ReconnectStatus(2, 3), h.reconnector.status.value)
        val before = currentTime
        advanceUntilIdle()
        assertEquals(before + 2_000, h.firedAt.last())
    }

    @Test
    fun `after stable playback a new failure starts again at attempt one`() = runTest {
        val h = harness()
        repeat(2) {
            h.reconnector.onFailure(FailureKind.NETWORK)
            advanceUntilIdle()
        }
        h.reconnector.onPlaying()
        advanceTimeBy(config.stableResetMs + 1)

        h.reconnector.onFailure(FailureKind.SOURCE_ENDED)

        assertEquals(ReconnectStatus(1, 3), h.reconnector.status.value)
    }

    @Test
    fun `network coming back fires a waiting retry immediately`() = runTest {
        val h = harness()
        h.reconnector.onFailure(FailureKind.NETWORK) // attempt 1, waits 1 s
        advanceTimeBy(300)
        assertTrue(h.reconnector.isWaiting)

        assertTrue(h.reconnector.onNetworkAvailable())
        runCurrent()

        assertEquals(listOf(300L), h.firedAt)
        assertFalse(h.reconnector.isWaiting)
        assertEquals(ReconnectStatus(1, 3), h.reconnector.status.value, "the attempt keeps its number")
        advanceUntilIdle()
        assertEquals(listOf(300L), h.firedAt, "the cancelled timer must not fire again")
    }

    @Test
    fun `network coming back after giving up starts a fresh round`() = runTest {
        val h = harness()
        repeat(3) {
            h.reconnector.onFailure(FailureKind.TIMEOUT)
            advanceUntilIdle()
        }
        assertFalse(h.reconnector.onFailure(FailureKind.TIMEOUT))

        assertTrue(h.reconnector.onNetworkAvailable())
        runCurrent()

        assertEquals(4, h.firedAt.size)
        assertEquals(ReconnectStatus(1, 3), h.reconnector.status.value)
        assertNull(h.reconnector.gaveUpOn)
    }

    @Test
    fun `network coming back while idle does nothing`() = runTest {
        val h = harness()

        assertFalse(h.reconnector.onNetworkAvailable())
        advanceUntilIdle()

        assertTrue(h.firedAt.isEmpty())
    }

    @Test
    fun `network coming back after a permanent failure does nothing`() = runTest {
        val h = harness()
        h.reconnector.onFailure(FailureKind.HTTP_CLIENT_ERROR)

        assertFalse(h.reconnector.onNetworkAvailable())
    }

    @Test
    fun `reset cancels the pending retry and clears everything`() = runTest {
        val h = harness()
        h.reconnector.onFailure(FailureKind.NETWORK)

        h.reconnector.reset()
        advanceUntilIdle()

        assertTrue(h.firedAt.isEmpty())
        assertNull(h.reconnector.status.value)
        assertEquals(0, h.reconnector.attempts)
    }

    @Test
    fun `a new failure while waiting replaces the pending timer`() = runTest {
        val h = harness()
        h.reconnector.onFailure(FailureKind.NETWORK) // fires at 1000
        advanceTimeBy(500)
        h.reconnector.onFailure(FailureKind.NETWORK) // attempt 2: fires at 500 + 2000

        advanceUntilIdle()

        assertEquals(listOf(2_500L), h.firedAt)
    }

    @Test
    fun `playing cancels a pending retry`() = runTest {
        val h = harness()
        h.reconnector.onFailure(FailureKind.BEHIND_LIVE_WINDOW)

        h.reconnector.onPlaying()
        advanceUntilIdle()

        assertTrue(h.firedAt.isEmpty())
    }

    @Test
    fun `lowering max attempts applies to the next decision`() = runTest {
        val h = harness()
        h.reconnector.onFailure(FailureKind.NETWORK)
        advanceUntilIdle()

        h.reconnector.updatePolicy(ReconnectPolicy(config.copy(maxAttempts = 1)))

        assertFalse(h.reconnector.onFailure(FailureKind.NETWORK))
    }

    @Test
    fun `status reports the configured maximum`() = runTest {
        val h = harness(config.copy(maxAttempts = 10))

        h.reconnector.onFailure(FailureKind.HTTP_RETRYABLE)

        assertEquals(ReconnectStatus(1, 10), h.reconnector.status.value)
    }

    @Test
    fun `delays are capped at the maximum delay`() = runTest {
        val h = harness(config.copy(maxAttempts = 8, maxDelayMs = 5_000))
        repeat(6) {
            h.reconnector.onFailure(FailureKind.NETWORK)
            advanceUntilIdle()
        }

        val gaps = h.firedAt.zipWithNext { a, b -> b - a }
        assertEquals(listOf(2_000L, 4_000L, 5_000L, 5_000L, 5_000L), gaps)
    }
}
