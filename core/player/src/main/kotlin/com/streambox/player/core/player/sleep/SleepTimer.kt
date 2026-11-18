package com.streambox.player.core.player.sleep

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Monotonic milliseconds (SystemClock.elapsedRealtime in the app, virtual time in tests). */
fun interface MonotonicClock {
    fun nowMs(): Long
}

/** A running sleep timer: [durationMinutes] as chosen, [remainingMs] until playback pauses. */
data class SleepTimerState(val durationMinutes: Int, val remainingMs: Long, val fading: Boolean = false)

/** What the timer acts on; implemented by the player screen on top of the playback controller. */
interface SleepTimerOutput {
    /** Player output volume 0..1. */
    fun setVolume(volume: Float)

    /** Time is up: stop playing (pause). Called before the volume is restored to 1. */
    fun onExpired()
}

/**
 * Sleep timer (requirements §4.3 "睡眠定时器"): counts down [start]'s minutes on [scope], fades the
 * volume out linearly over the last [FADE_MS], then pauses and restores the volume. [cancel] or a new
 * [start] restores the volume when a fade was in progress. Remaining time comes from [clock] (not from
 * adding up delays), so a late tick never stretches the timer. Not persisted.
 */
class SleepTimer(
    private val scope: CoroutineScope,
    private val clock: MonotonicClock,
    private val output: SleepTimerOutput,
) {
    private val _state = MutableStateFlow<SleepTimerState?>(null)

    /** Null while off. */
    val state: StateFlow<SleepTimerState?> = _state.asStateFlow()

    private var job: Job? = null
    private var volumeLowered = false

    /** (Re)starts the countdown; [minutes] is clamped to [MIN_MINUTES]..[MAX_MINUTES]. */
    fun start(minutes: Int) {
        stop()
        val duration = minutes.coerceIn(MIN_MINUTES, MAX_MINUTES)
        val deadline = clock.nowMs() + duration * MS_PER_MINUTE
        _state.value = SleepTimerState(duration, duration * MS_PER_MINUTE)
        job = scope.launch { countDown(duration, deadline) }
    }

    /** Turns the timer off; a partly faded volume goes back to full. */
    fun cancel() = stop()

    private fun stop() {
        job?.cancel()
        job = null
        restoreVolume()
        _state.value = null
    }

    private suspend fun countDown(duration: Int, deadline: Long) {
        while (true) {
            val remaining = deadline - clock.nowMs()
            if (remaining <= 0) break
            val fading = remaining <= FADE_MS
            if (fading) {
                volumeLowered = true
                output.setVolume(remaining.toFloat() / FADE_MS)
            }
            _state.value = SleepTimerState(duration, remaining, fading)
            val wait = if (fading) minOf(FADE_STEP_MS, remaining) else minOf(TICK_MS, remaining - FADE_MS)
            delay(wait.coerceAtLeast(1))
        }
        output.setVolume(0f)
        volumeLowered = true
        output.onExpired()
        restoreVolume()
        job = null
        _state.value = null
    }

    private fun restoreVolume() {
        if (!volumeLowered) return
        volumeLowered = false
        output.setVolume(1f)
    }

    companion object {
        const val MIN_MINUTES = 1
        const val MAX_MINUTES = 600

        /** Preset choices; "custom" accepts [MIN_MINUTES]..[MAX_MINUTES]. */
        val PRESET_MINUTES = listOf(15, 30, 60, 90)

        const val FADE_MS = 10_000L
        const val TICK_MS = 1_000L
        const val FADE_STEP_MS = 100L
        private const val MS_PER_MINUTE = 60_000L
    }
}
