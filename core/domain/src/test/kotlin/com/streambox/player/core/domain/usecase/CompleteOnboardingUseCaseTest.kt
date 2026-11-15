package com.streambox.player.core.domain.usecase

import com.streambox.player.core.domain.repository.SettingsRepository
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

class CompleteOnboardingUseCaseTest {
    private val repository = mockk<SettingsRepository>(relaxed = true)

    @Test
    fun `marks welcome seen then accepts disclaimer`() = runTest {
        CompleteOnboardingUseCase(repository).invoke()

        coVerifyOrder {
            repository.markWelcomeSeen()
            repository.acceptDisclaimer()
        }
    }
}
