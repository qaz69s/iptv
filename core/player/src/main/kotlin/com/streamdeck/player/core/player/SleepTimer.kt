package com.streamdeck.player.core.player

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/** Sleep timer (spec §4.3): counts down, then fades the volume out over [FADE_MS] and pauses. */
@Singleton
class SleepTimer @Inject constructor(private val controller: PlayerController) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _remaining = MutableStateFlow<Long?>(null)
    val remainingMs: StateFlow<Long?> = _remaining.asStateFlow()
    private var job: Job? = null

    fun start(minutes: Int) {
        require(minutes > 0)
        cancel()
        val end = SystemClock.elapsedRealtime() + minutes * 60_000L
        job = scope.launch {
            while (true) {
                val left = end - SystemClock.elapsedRealtime()
                if (left <= 0) break
                _remaining.value = left
                delay(min(TICK_MS, left))
            }
            fadeOutAndPause()
            _remaining.value = null
        }
    }

    fun cancel() {
        job?.cancel()
        job = null
        _remaining.value = null
    }

    private suspend fun fadeOutAndPause() {
        val p = controller.playerFlow.value ?: return
        val original = p.volume
        for (i in 1..FADE_STEPS) {
            p.volume = original * (1f - i.toFloat() / FADE_STEPS)
            delay(FADE_MS / FADE_STEPS)
        }
        p.pause()
        p.volume = original
    }

    private companion object {
        const val TICK_MS = 1_000L
        const val FADE_MS = 5_000L
        const val FADE_STEPS = 20
    }
}
