package com.streambox.player.core.testing

import com.streambox.player.core.domain.repository.SettingsRepository
import com.streambox.player.core.model.AppSettings
import com.streambox.player.core.model.AspectRatioMode
import com.streambox.player.core.model.BufferProfile
import com.streambox.player.core.model.OnboardingState
import com.streambox.player.core.model.PlaybackSettings
import com.streambox.player.core.model.SubtitleStyle
import com.streambox.player.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** In-memory [SettingsRepository] for ViewModel tests. */
@Suppress("TooManyFunctions")
class FakeSettingsRepository(
    initialSettings: AppSettings = AppSettings(),
    initialOnboarding: OnboardingState = OnboardingState(),
) : SettingsRepository {
    private val settingsFlow = MutableStateFlow(initialSettings)
    private val onboardingFlow = MutableStateFlow(initialOnboarding)

    override val settings: Flow<AppSettings> = settingsFlow
    override val onboardingState: Flow<OnboardingState> = onboardingFlow

    override suspend fun setThemeMode(mode: ThemeMode) = settingsFlow.update { it.copy(themeMode = mode) }

    override suspend fun setDynamicColor(enabled: Boolean) = settingsFlow.update { it.copy(useDynamicColor = enabled) }

    override suspend fun setBufferProfile(profile: BufferProfile) = updatePlayback { it.copy(bufferProfile = profile) }

    override suspend fun setSoftwareDecodingOnly(enabled: Boolean) =
        updatePlayback { it.copy(softwareDecodingOnly = enabled) }

    override suspend fun setMaxReconnectAttempts(attempts: Int) =
        updatePlayback { it.copy(maxReconnectAttempts = attempts.coerceIn(0, PlaybackSettings.MAX_RECONNECT_ATTEMPTS)) }

    override suspend fun setDefaultAspectRatio(mode: AspectRatioMode) =
        updatePlayback { it.copy(defaultAspectRatio = mode) }

    override suspend fun setSubtitleStyle(style: SubtitleStyle) = updatePlayback { it.copy(subtitleStyle = style) }

    override suspend fun setBackgroundPlayback(enabled: Boolean) =
        updatePlayback { it.copy(backgroundPlayback = enabled) }

    override suspend fun setPictureInPicture(enabled: Boolean) = updatePlayback { it.copy(pictureInPicture = enabled) }

    override suspend fun setPlaybackGestures(enabled: Boolean) = updatePlayback { it.copy(playbackGestures = enabled) }

    override suspend fun setResumeLastChannel(enabled: Boolean) =
        updatePlayback { it.copy(resumeLastChannel = enabled) }

    override suspend fun setAutoPlayNext(enabled: Boolean) = updatePlayback { it.copy(autoPlayNext = enabled) }

    private fun updatePlayback(transform: (PlaybackSettings) -> PlaybackSettings) =
        settingsFlow.update { it.copy(playback = transform(it.playback)) }

    override suspend fun markWelcomeSeen() = onboardingFlow.update { it.copy(welcomeSeen = true) }

    override suspend fun acceptDisclaimer() = onboardingFlow.update { it.copy(disclaimerAccepted = true) }
}
