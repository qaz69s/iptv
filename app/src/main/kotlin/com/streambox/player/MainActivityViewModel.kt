package com.streambox.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streambox.player.core.domain.repository.PlaylistRepository
import com.streambox.player.core.domain.repository.SettingsRepository
import com.streambox.player.core.model.AppSettings
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn

sealed interface MainUiState {
    data object Loading : MainUiState

    data class Ready(val settings: AppSettings, val onboardingComplete: Boolean) : MainUiState
}

@HiltViewModel
class MainActivityViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    private val playlistRepository: PlaylistRepository,
) : ViewModel() {
    val uiState: StateFlow<MainUiState> = combine(
        settingsRepository.settings,
        settingsRepository.onboardingState,
    ) { settings, onboarding ->
        MainUiState.Ready(settings = settings, onboardingComplete = onboarding.isComplete)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = MainUiState.Loading,
    )

    /** Used once when onboarding finishes: without a playlist the user goes straight to "Add playlist". */
    suspend fun hasPlaylists(): Boolean = playlistRepository.playlists.first().isNotEmpty()
}
