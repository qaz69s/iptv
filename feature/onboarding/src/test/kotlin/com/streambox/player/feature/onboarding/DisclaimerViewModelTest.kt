package com.streambox.player.feature.onboarding

import com.streambox.player.core.domain.usecase.CompleteOnboardingUseCase
import com.streambox.player.core.testing.FakeSettingsRepository
import com.streambox.player.core.testing.MainDispatcherExtension
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class DisclaimerViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val repository = FakeSettingsRepository()
    private val viewModel = DisclaimerViewModel(CompleteOnboardingUseCase(repository))

    @Test
    fun `accept is ignored until consent is checked`() = runTest {
        viewModel.onAccept()

        assertFalse(viewModel.uiState.value.completed)
        assertFalse(repository.onboardingState.first().isComplete)
    }

    @Test
    fun `accept after consent completes onboarding`() = runTest {
        viewModel.onConsentChanged(true)
        assertTrue(viewModel.uiState.value.canAccept)

        viewModel.onAccept()

        assertTrue(viewModel.uiState.value.completed)
        assertTrue(repository.onboardingState.first().isComplete)
    }

    @Test
    fun `consent cannot be withdrawn after completion`() = runTest {
        viewModel.onConsentChanged(true)
        viewModel.onAccept()

        viewModel.onConsentChanged(false)

        assertTrue(viewModel.uiState.value.consentChecked)
        assertFalse(viewModel.uiState.value.canAccept)
    }
}
