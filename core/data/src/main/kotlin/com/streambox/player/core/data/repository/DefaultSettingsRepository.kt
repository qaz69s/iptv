package com.streambox.player.core.data.repository

import com.streambox.player.core.datastore.SettingsDataSource
import com.streambox.player.core.domain.repository.SettingsRepository
import com.streambox.player.core.model.AppSettings
import com.streambox.player.core.model.AspectRatioMode
import com.streambox.player.core.model.BufferProfile
import com.streambox.player.core.model.OnboardingState
import com.streambox.player.core.model.SubtitleStyle
import com.streambox.player.core.model.ThemeMode
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

@Suppress("TooManyFunctions")
internal class DefaultSettingsRepository @Inject constructor(private val dataSource: SettingsDataSource) :
    SettingsRepository {
    override val settings: Flow<AppSettings> = dataSource.settings
    override val onboardingState: Flow<OnboardingState> = dataSource.onboardingState

    override suspend fun setThemeMode(mode: ThemeMode) = dataSource.setThemeMode(mode)

    override suspend fun setDynamicColor(enabled: Boolean) = dataSource.setDynamicColor(enabled)

    override suspend fun setBufferProfile(profile: BufferProfile) = dataSource.setBufferProfile(profile)

    override suspend fun setSoftwareDecodingOnly(enabled: Boolean) = dataSource.setSoftwareDecodingOnly(enabled)

    override suspend fun setMaxReconnectAttempts(attempts: Int) = dataSource.setMaxReconnectAttempts(attempts)

    override suspend fun setDefaultAspectRatio(mode: AspectRatioMode) = dataSource.setDefaultAspectRatio(mode)

    override suspend fun setSubtitleStyle(style: SubtitleStyle) = dataSource.setSubtitleStyle(style)

    override suspend fun setBackgroundPlayback(enabled: Boolean) = dataSource.setBackgroundPlayback(enabled)

    override suspend fun setPictureInPicture(enabled: Boolean) = dataSource.setPictureInPicture(enabled)

    override suspend fun setPlaybackGestures(enabled: Boolean) = dataSource.setPlaybackGestures(enabled)

    override suspend fun setResumeLastChannel(enabled: Boolean) = dataSource.setResumeLastChannel(enabled)

    override suspend fun setAutoPlayNext(enabled: Boolean) = dataSource.setAutoPlayNext(enabled)

    override suspend fun markWelcomeSeen() = dataSource.markWelcomeSeen()

    override suspend fun acceptDisclaimer() = dataSource.acceptDisclaimer()
}
