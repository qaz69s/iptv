package com.streambox.player.core.testing

import com.streambox.player.core.domain.repository.SettingsRepository
import com.streambox.player.core.model.AppSettings
import com.streambox.player.core.model.OnboardingState
import com.streambox.player.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** In-memory [SettingsRepository] for ViewModel tests. */
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

    override suspend fun markWelcomeSeen() = onboardingFlow.update { it.copy(welcomeSeen = true) }

    override suspend fun acceptDisclaimer() = onboardingFlow.update { it.copy(disclaimerAccepted = true) }
}
