package com.streambox.player.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streambox.player.core.domain.usecase.CompleteOnboardingUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DisclaimerUiState(
    val consentChecked: Boolean = false,
    val submitting: Boolean = false,
    val completed: Boolean = false,
) {
    val canAccept: Boolean get() = consentChecked && !submitting && !completed
}

@HiltViewModel
class DisclaimerViewModel @Inject constructor(private val completeOnboarding: CompleteOnboardingUseCase) :
    ViewModel() {
    private val _uiState = MutableStateFlow(DisclaimerUiState())
    val uiState: StateFlow<DisclaimerUiState> = _uiState.asStateFlow()

    fun onConsentChanged(checked: Boolean) {
        _uiState.update { if (it.submitting || it.completed) it else it.copy(consentChecked = checked) }
    }

    fun onAccept() {
        if (!_uiState.value.canAccept) return
        _uiState.update { it.copy(submitting = true) }
        viewModelScope.launch {
            completeOnboarding()
            _uiState.update { it.copy(submitting = false, completed = true) }
        }
    }
}
