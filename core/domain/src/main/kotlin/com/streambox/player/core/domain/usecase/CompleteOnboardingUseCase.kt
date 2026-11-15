package com.streambox.player.core.domain.usecase

import com.streambox.player.core.domain.repository.SettingsRepository
import javax.inject.Inject

/**
 * Records that the user read and accepted the "player only, no content provided" disclaimer.
 * The welcome step is marked as well so that an interrupted flow never re-shows half of it.
 */
class CompleteOnboardingUseCase @Inject constructor(private val settingsRepository: SettingsRepository) {
    suspend operator fun invoke() {
        settingsRepository.markWelcomeSeen()
        settingsRepository.acceptDisclaimer()
    }
}
