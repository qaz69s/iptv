package com.streambox.player.feature.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Remote-control channel number entry: digits accumulate, and the number is committed [timeoutMs]
 * after the last key, as soon as [maxDigits] are typed, or on [commitNow] (OK). [backspace] removes
 * the last digit and restarts the timeout. "0" alone or an all-zero entry is dropped.
 */
class ChannelNumberInput(
    private val scope: CoroutineScope,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    private val maxDigits: Int = DEFAULT_MAX_DIGITS,
    private val onCommit: (Int) -> Unit,
) {
    private val _digits = MutableStateFlow<String?>(null)

    /** The number being typed, shown on the overlay; null when not typing. */
    val digits: StateFlow<String?> = _digits.asStateFlow()

    private var timer: Job? = null

    val isActive: Boolean get() = _digits.value != null

    fun onDigit(digit: Int) {
        require(digit in 0..MAX_DIGIT) { "digit must be 0..$MAX_DIGIT" }
        val typed = _digits.value.orEmpty() + digit
        _digits.value = typed
        timer?.cancel()
        if (typed.length >= maxDigits) {
            commitNow()
        } else {
            restartTimer()
        }
    }

    /** Removes the last typed digit; clears the entry when it was the only one. @return true if typing. */
    fun backspace(): Boolean {
        val typed = _digits.value ?: return false
        timer?.cancel()
        val remaining = typed.dropLast(1)
        if (remaining.isEmpty()) {
            _digits.value = null
        } else {
            _digits.value = remaining
            restartTimer()
        }
        return true
    }

    fun commitNow() {
        timer?.cancel()
        val number = _digits.value?.toIntOrNull()
        _digits.value = null
        if (number != null && number > 0) onCommit(number)
    }

    private fun restartTimer() {
        timer = scope.launch {
            delay(timeoutMs)
            commitNow()
        }
    }

    fun cancel() {
        timer?.cancel()
        _digits.value = null
    }

    companion object {
        /** Requirements §4.2: the typed number is confirmed after 2 seconds. */
        const val DEFAULT_TIMEOUT_MS = 2_000L
        const val DEFAULT_MAX_DIGITS = 4
        private const val MAX_DIGIT = 9
    }
}
