package com.streambox.player.core.player.reconnect

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Shown on the overlay as "Reconnecting (attempt [attempt]/[maxAttempts])". */
data class ReconnectStatus(val attempt: Int, val maxAttempts: Int)

/**
 * Runs the reconnect loop for one stream. Timers run in [scope] (virtual time in tests); [reconnect]
 * is invoked on [scope]'s dispatcher when a retry is due.
 *
 * - [onFailure]: schedules the next attempt with backoff, or returns false when the error must be
 *   shown (not retryable, reconnect disabled, or attempts used up).
 * - [onPlaying]: hides the status; after [ReconnectConfig.stableResetMs] of uninterrupted playback the
 *   attempt counter is reset.
 * - [onNetworkAvailable]: a waiting retry fires immediately; after giving up on a retryable failure a
 *   fresh round starts.
 * - [reset]: new stream, manual retry or release.
 */
class Reconnector(
    private val scope: CoroutineScope,
    policy: ReconnectPolicy = ReconnectPolicy(),
    private val reconnect: () -> Unit,
) {
    var policy: ReconnectPolicy = policy
        private set

    private val _status = MutableStateFlow<ReconnectStatus?>(null)

    /** Non-null from the moment a retry is scheduled until playback resumes or the loop gives up. */
    val status: StateFlow<ReconnectStatus?> = _status.asStateFlow()

    /** Attempts made since the last stable playback. */
    var attempts: Int = 0
        private set

    /** The retryable failure the loop gave up on; a network comeback restarts from it. */
    var gaveUpOn: FailureKind? = null
        private set

    private var pending: Job? = null
    private var stableTimer: Job? = null

    val isWaiting: Boolean get() = pending?.isActive == true

    /** Applies new settings (e.g. max attempts) from the next decision on. */
    fun updatePolicy(newPolicy: ReconnectPolicy) {
        policy = newPolicy
    }

    /** @return true when a reconnect was scheduled and the error should stay hidden. */
    fun onFailure(kind: FailureKind): Boolean {
        stableTimer?.cancel()
        pending?.cancel()
        if (!policy.shouldRetry(kind, attempts)) {
            _status.value = null
            gaveUpOn = kind.takeIf { it.retryable && policy.config.maxAttempts > 0 }
            return false
        }
        gaveUpOn = null
        schedule(policy.delayBefore(attempts + 1))
        return true
    }

    fun onPlaying() {
        pending?.cancel()
        pending = null
        _status.value = null
        gaveUpOn = null
        stableTimer?.cancel()
        if (attempts > 0) {
            stableTimer = scope.launch {
                delay(policy.config.stableResetMs)
                attempts = 0
            }
        }
    }

    /** @return true when a reconnect was triggered by the network coming back. */
    fun onNetworkAvailable(): Boolean = when {
        isWaiting -> {
            pending?.cancel()
            fire()
            true
        }

        gaveUpOn != null -> {
            gaveUpOn = null
            attempts = 0
            schedule(delayMs = 0)
            true
        }

        else -> false
    }

    fun reset() {
        pending?.cancel()
        stableTimer?.cancel()
        pending = null
        attempts = 0
        gaveUpOn = null
        _status.value = null
    }

    private fun schedule(delayMs: Long) {
        attempts++
        _status.value = ReconnectStatus(attempts, policy.config.maxAttempts)
        pending = scope.launch {
            delay(delayMs)
            pending = null
            reconnect()
        }
    }

    private fun fire() {
        pending = null
        reconnect()
    }
}
