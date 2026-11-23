package com.streamdeck.player.core.data

import android.os.SystemClock
import com.streamdeck.player.core.common.PinHasher
import com.streamdeck.player.core.common.PinPolicy
import com.streamdeck.player.core.common.PinResult
import com.streamdeck.player.core.common.PinState
import com.streamdeck.player.core.datastore.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parental control (spec §4.7). A correct PIN unlocks locked content for [UNLOCK_WINDOW_MS] so zapping
 * through a locked group does not prompt on every channel; the window ends when the process dies.
 */
@Singleton
class ParentalRepository @Inject constructor(private val settings: SettingsRepository) {
    private val policy = PinPolicy()
    @Volatile private var unlockedUntil = 0L

    val hasPin: Flow<Boolean> = settings.pin.map { it != null }

    fun isUnlocked(): Boolean = SystemClock.elapsedRealtime() < unlockedUntil
    fun lockNow() { unlockedUntil = 0 }

    fun isValidFormat(pin: String) = policy.isValidFormat(pin)

    suspend fun verify(pin: String): PinResult = withContext(Dispatchers.Default) {
        val rec = settings.pin.first() ?: return@withContext PinResult.Ok
        val state = PinState(rec.failedAttempts, rec.lockoutUntil)
        val (result, next) = policy.attempt(state, PinHasher.verify(pin, rec.salt, rec.hash))
        if (next != state) settings.recordPinAttempt(next.failedAttempts, next.lockoutUntil)
        if (result == PinResult.Ok) unlockedUntil = SystemClock.elapsedRealtime() + UNLOCK_WINDOW_MS
        result
    }

    /** Sets or replaces the PIN; the caller must have verified the old one. */
    suspend fun setPin(pin: String) = withContext(Dispatchers.Default) {
        require(policy.isValidFormat(pin))
        val salt = PinHasher.newSalt()
        settings.savePin(PinHasher.hash(pin, salt), salt)
    }

    suspend fun clearPin() {
        settings.clearPin()
        settings.update { it.copy(lockSettings = false, lockEditor = false) }
    }

    companion object { const val UNLOCK_WINDOW_MS = 5 * 60_000L }
}
