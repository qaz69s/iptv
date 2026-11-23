package com.streamdeck.player.core.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PinTest {
    private var now = 1_000_000L
    private val policy = PinPolicy(maxAttempts = 3, baseLockoutMs = 10_000, maxLockoutMs = 60_000, clock = { now })

    @Test fun `format rules`() {
        assertTrue(policy.isValidFormat("1234"))
        assertTrue(policy.isValidFormat("123456"))
        assertFalse(policy.isValidFormat("123"))
        assertFalse(policy.isValidFormat("1234567"))
        assertFalse(policy.isValidFormat("12a4"))
        assertFalse(policy.isValidFormat("１２３４"))
    }

    @Test fun `correct pin resets counter`() {
        val (r, s) = policy.attempt(PinState(2, 0), correct = true)
        assertEquals(PinResult.Ok, r)
        assertEquals(PinState(), s)
    }

    @Test fun `wrong pin counts down`() {
        val (r1, s1) = policy.attempt(PinState(), false)
        assertEquals(PinResult.Wrong(2), r1)
        val (r2, _) = policy.attempt(s1, false)
        assertEquals(PinResult.Wrong(1), r2)
    }

    @Test fun `lockout after max attempts and expiry`() {
        val (r, s) = policy.attempt(PinState(2, 0), false)
        assertEquals(PinResult.LockedOut(10_000), r)
        assertEquals(now + 10_000, s.lockoutUntil)
        val (blocked, same) = policy.attempt(s, correct = true)
        assertTrue(blocked is PinResult.LockedOut)
        assertEquals(s, same)
        now += 10_001
        assertEquals(PinResult.Ok, policy.attempt(s, true).first)
    }

    @Test fun `lockout doubles and is capped`() {
        var s = PinState(3, 0)
        val durations = (0 until 5).map {
            val (r, next) = policy.attempt(s, false)
            s = next
            now += 120_000
            (r as PinResult.LockedOut).remainingMs
        }
        assertEquals(listOf(20_000L, 40_000L, 60_000L, 60_000L, 60_000L), durations)
    }

    @Test fun `hash verifies and salts differ`() {
        val salt = PinHasher.newSalt()
        val h = PinHasher.hash("4321", salt)
        assertTrue(PinHasher.verify("4321", salt, h))
        assertFalse(PinHasher.verify("4322", salt, h))
        assertNotEquals(salt, PinHasher.newSalt())
        assertNotEquals(h, PinHasher.hash("4321", PinHasher.newSalt()))
    }

    @Test fun `unknown hash format rejected`() { assertFalse(PinHasher.verify("1234", "aa", "md5\$abc")) }
}
