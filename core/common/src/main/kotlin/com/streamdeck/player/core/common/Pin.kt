package com.streamdeck.player.core.common

import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class PinState(val failedAttempts: Int = 0, val lockoutUntil: Long = 0)

sealed interface PinResult {
    data object Ok : PinResult
    data class Wrong(val attemptsLeft: Int) : PinResult
    data class LockedOut(val remainingMs: Long) : PinResult
}

/**
 * Parental-control PIN rules (spec §4.7): 4–6 digits; after [maxAttempts] consecutive failures the PIN is locked
 * for [baseLockoutMs], doubling on every further failure up to [maxLockoutMs]. Pure logic with an injected clock.
 */
class PinPolicy(
    private val maxAttempts: Int = 5,
    private val baseLockoutMs: Long = 30_000,
    private val maxLockoutMs: Long = 30 * 60_000L,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun isValidFormat(pin: String): Boolean = pin.length in 4..6 && pin.all { it in '0'..'9' }

    fun remainingLockout(state: PinState): Long = (state.lockoutUntil - clock()).coerceAtLeast(0)

    /** Evaluates an attempt whose correctness is [correct]; returns the result and the state to persist. */
    fun attempt(state: PinState, correct: Boolean): Pair<PinResult, PinState> {
        val remaining = remainingLockout(state)
        if (remaining > 0) return PinResult.LockedOut(remaining) to state
        if (correct) return PinResult.Ok to PinState()
        val failed = state.failedAttempts + 1
        if (failed < maxAttempts) return PinResult.Wrong(maxAttempts - failed) to PinState(failed, 0)
        val exponent = (failed - maxAttempts).coerceAtMost(MAX_SHIFT)
        val lockout = (baseLockoutMs shl exponent).coerceAtMost(maxLockoutMs)
        return PinResult.LockedOut(lockout) to PinState(failed, clock() + lockout)
    }

    private companion object { const val MAX_SHIFT = 20 }
}

/** PBKDF2 PIN hashing. SHA-256 where available (API 26+), SHA-1 otherwise; the algorithm is recorded in the hash. */
object PinHasher {
    private const val ITERATIONS = 20_000
    private const val KEY_BITS = 256
    private val random = SecureRandom()

    fun newSalt(): String = ByteArray(16).also(random::nextBytes).toHex()

    fun hash(pin: String, salt: String): String {
        val (tag, factory) = try {
            "s256" to SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        } catch (e: NoSuchAlgorithmException) {
            "s1" to SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        }
        return "$tag$" + derive(factory, pin, salt)
    }

    fun verify(pin: String, salt: String, stored: String): Boolean {
        val tag = stored.substringBefore('$')
        val algo = when (tag) {
            "s256" -> "PBKDF2WithHmacSHA256"
            "s1" -> "PBKDF2WithHmacSHA1"
            else -> return false
        }
        val actual = runCatching { derive(SecretKeyFactory.getInstance(algo), pin, salt) }.getOrNull() ?: return false
        return MessageDigest.isEqual(actual.toByteArray(), stored.substringAfter('$').toByteArray())
    }

    private fun derive(f: SecretKeyFactory, pin: String, salt: String): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt.toByteArray(), ITERATIONS, KEY_BITS)
        return try { f.generateSecret(spec).encoded.toHex() } finally { spec.clearPassword() }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
