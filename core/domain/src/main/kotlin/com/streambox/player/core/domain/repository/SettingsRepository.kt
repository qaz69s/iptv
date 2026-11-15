package com.streambox.player.core.domain.repository

import com.streambox.player.core.model.AppSettings
import com.streambox.player.core.model.OnboardingState
import com.streambox.player.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>
    val onboardingState: Flow<OnboardingState>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setDynamicColor(enabled: Boolean)

    suspend fun markWelcomeSeen()

    suspend fun acceptDisclaimer()
}
