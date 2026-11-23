package com.streamdeck.player.core.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.random.Random

class ReconnectPolicyTest {
    private val noJitter = ReconnectPolicy(maxAttempts = 8, baseDelayMs = 1_000, maxDelayMs = 30_000, jitterRatio = 0.0)

    @Test fun `delays grow exponentially`() {
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L), (1..5).map { noJitter.delayFor(it) })
    }

    @Test fun `delay is capped at max`() {
        assertEquals(30_000L, noJitter.delayFor(6))
        assertEquals(30_000L, noJitter.delayFor(8))
    }

    @Test fun `exhausted after max attempts`() {
        assertNull(noJitter.delayFor(9))
        assertNull(noJitter.delayFor(0))
    }

    @Test fun `jitter stays within ratio`() {
        val p = ReconnectPolicy(maxAttempts = 3, baseDelayMs = 1_000, maxDelayMs = 10_000, jitterRatio = 0.2, random = Random(42))
        repeat(200) {
            val d = p.delayFor(2)!!
            assertTrue(d in 1_600L..2_400L, "delay $d out of range")
        }
    }

    @Test fun `jitter never exceeds max delay`() {
        val p = ReconnectPolicy(maxAttempts = 10, baseDelayMs = 1_000, maxDelayMs = 5_000, jitterRatio = 1.0, random = Random(1))
        repeat(200) { assertTrue(p.delayFor(10)!! in 0L..5_000L) }
    }

    @Test fun `zero attempts means never retry`() {
        val s = ReconnectSession(ReconnectPolicy(maxAttempts = 0))
        assertTrue(s.exhausted)
        assertNull(s.next())
    }

    @Test fun `session counts and resets`() {
        val s = ReconnectSession(ReconnectPolicy(maxAttempts = 2, jitterRatio = 0.0))
        assertEquals(1_000L, s.next())
        assertEquals(2_000L, s.next())
        assertTrue(s.exhausted)
        assertNull(s.next())
        s.reset()
        assertFalse(s.exhausted)
        assertEquals(1_000L, s.next())
        assertEquals(1, s.attempts)
    }

    @Test fun `invalid parameters rejected`() {
        assertThrows<IllegalArgumentException> { ReconnectPolicy(maxAttempts = -1) }
        assertThrows<IllegalArgumentException> { ReconnectPolicy(baseDelayMs = 0) }
        assertThrows<IllegalArgumentException> { ReconnectPolicy(baseDelayMs = 10, maxDelayMs = 5) }
        assertThrows<IllegalArgumentException> { ReconnectPolicy(jitterRatio = 1.5) }
    }

    @Test fun `huge attempt numbers do not overflow`() {
        val p = ReconnectPolicy(maxAttempts = 1_000, jitterRatio = 0.0)
        assertEquals(30_000L, p.delayFor(1_000))
    }
}
