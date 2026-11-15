package com.streambox.player.core.data.repository

import com.streambox.player.core.datastore.SettingsDataSource
import com.streambox.player.core.domain.repository.SettingsRepository
import com.streambox.player.core.model.AppSettings
import com.streambox.player.core.model.OnboardingState
import com.streambox.player.core.model.ThemeMode
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

internal class DefaultSettingsRepository @Inject constructor(private val dataSource: SettingsDataSource) :
    SettingsRepository {
    override val settings: Flow<AppSettings> = dataSource.settings
    override val onboardingState: Flow<OnboardingState> = dataSource.onboardingState

    override suspend fun setThemeMode(mode: ThemeMode) = dataSource.setThemeMode(mode)

    override suspend fun setDynamicColor(enabled: Boolean) = dataSource.setDynamicColor(enabled)

    override suspend fun markWelcomeSeen() = dataSource.markWelcomeSeen()

    override suspend fun acceptDisclaimer() = dataSource.acceptDisclaimer()
}
