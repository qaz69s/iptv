package com.streambox.player.core.player.reconnect

import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * Reconnect tuning. [maxAttempts] 0 turns reconnecting off. Delays grow as
 * `initialDelayMs * multiplier^(attempt - 1)`, capped at [maxDelayMs], then spread by ±[jitterRatio]
 * so many clients do not hammer a recovering server in lockstep.
 */
data class ReconnectConfig(
    val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    val initialDelayMs: Long = 1_000,
    val maxDelayMs: Long = 30_000,
    val multiplier: Double = 2.0,
    val jitterRatio: Double = 0.2,
    /** Uninterrupted playback after which the attempt counter starts from zero again. */
    val stableResetMs: Long = 30_000,
) {
    init {
        require(maxAttempts >= 0) { "maxAttempts must be >= 0" }
        require(initialDelayMs >= 0 && maxDelayMs >= initialDelayMs) { "0 <= initialDelayMs <= maxDelayMs" }
        require(multiplier >= 1.0) { "multiplier must be >= 1" }
        require(jitterRatio in 0.0..1.0) { "jitterRatio must be in 0..1" }
        require(stableResetMs >= 0) { "stableResetMs must be >= 0" }
    }

    companion object {
        const val DEFAULT_MAX_ATTEMPTS = 5
    }
}

/** Stateless decisions: whether to retry and how long to wait. [random] returns values in [0, 1). */
class ReconnectPolicy(
    val config: ReconnectConfig = ReconnectConfig(),
    private val random: () -> Double = Random::nextDouble,
) {
    /** [attemptsSoFar] reconnects already made since the last stable playback. */
    fun shouldRetry(kind: FailureKind, attemptsSoFar: Int): Boolean =
        kind.retryable && attemptsSoFar < config.maxAttempts

    /** Delay before the 1-based [attempt]: exponential, capped, with symmetric jitter. Never negative. */
    fun delayBefore(attempt: Int): Long {
        require(attempt >= 1) { "attempt is 1-based" }
        val exponential = config.initialDelayMs * config.multiplier.pow(attempt - 1)
        val base = exponential.coerceAtMost(config.maxDelayMs.toDouble())
        val spread = base * config.jitterRatio * (2 * random() - 1)
        return (base + spread).roundToLong().coerceIn(0, config.maxDelayMs)
    }
}
