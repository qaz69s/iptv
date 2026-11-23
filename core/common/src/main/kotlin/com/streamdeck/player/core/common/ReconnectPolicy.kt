package com.streamdeck.player.core.common

import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * Exponential back-off with symmetric jitter and a hard attempt cap (spec §8.5).
 * Pure Kotlin so it can be unit-tested without Android.
 *
 * delay(n) = min(base * 2^(n-1), maxDelay) ± jitterRatio, clamped to [0, maxDelay].
 */
class ReconnectPolicy(
    val maxAttempts: Int = 5,
    private val baseDelayMs: Long = 1_000,
    private val maxDelayMs: Long = 30_000,
    private val jitterRatio: Double = 0.2,
    private val random: Random = Random.Default,
) {
    init {
        require(maxAttempts >= 0) { "maxAttempts must be >= 0" }
        require(baseDelayMs > 0) { "baseDelayMs must be > 0" }
        require(maxDelayMs >= baseDelayMs) { "maxDelayMs must be >= baseDelayMs" }
        require(jitterRatio in 0.0..1.0) { "jitterRatio must be in [0, 1]" }
    }

    /** @param attempt 1-based attempt number. @return delay in ms, or null when attempts are exhausted. */
    fun delayFor(attempt: Int): Long? {
        if (attempt < 1 || attempt > maxAttempts) return null
        val exponential = baseDelayMs.toDouble() * 2.0.pow((attempt - 1).coerceAtMost(MAX_SHIFT))
        val capped = min(exponential, maxDelayMs.toDouble())
        val jitter = if (jitterRatio == 0.0) 0.0 else capped * jitterRatio * (random.nextDouble() * 2 - 1)
        return (capped + jitter).toLong().coerceIn(0L, maxDelayMs)
    }

    private companion object { const val MAX_SHIFT = 30 }
}

/** Mutable attempt counter bound to a policy. Reset it whenever playback becomes healthy again. */
class ReconnectSession(val policy: ReconnectPolicy) {
    var attempts: Int = 0
        private set

    val exhausted: Boolean get() = attempts >= policy.maxAttempts

    /** Advances the counter and returns the delay before the next attempt, or null if exhausted. */
    fun next(): Long? {
        if (exhausted) return null
        attempts++
        return policy.delayFor(attempts)
    }

    fun reset() { attempts = 0 }
}
