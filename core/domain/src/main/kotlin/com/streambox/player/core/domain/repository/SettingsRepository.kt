package com.streambox.player.core.domain.repository

import com.streambox.player.core.model.AppSettings
import com.streambox.player.core.model.AspectRatioMode
import com.streambox.player.core.model.BufferProfile
import com.streambox.player.core.model.OnboardingState
import com.streambox.player.core.model.SubtitleStyle
import com.streambox.player.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow

@Suppress("TooManyFunctions") // One setter per persisted preference.
interface SettingsRepository {
    val settings: Flow<AppSettings>
    val onboardingState: Flow<OnboardingState>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setDynamicColor(enabled: Boolean)

    suspend fun setBufferProfile(profile: BufferProfile)

    suspend fun setSoftwareDecodingOnly(enabled: Boolean)

    /** Clamped to 0..[com.streambox.player.core.model.PlaybackSettings.MAX_RECONNECT_ATTEMPTS]. */
    suspend fun setMaxReconnectAttempts(attempts: Int)

    suspend fun setDefaultAspectRatio(mode: AspectRatioMode)

    suspend fun setSubtitleStyle(style: SubtitleStyle)

    suspend fun markWelcomeSeen()

    suspend fun acceptDisclaimer()
}
