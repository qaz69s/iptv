package com.streambox.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streambox.player.core.domain.repository.PlaylistRepository
import com.streambox.player.core.domain.repository.SettingsRepository
import com.streambox.player.core.domain.usecase.ResolveLastChannelUseCase
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
    private val settingsRepository: SettingsRepository,
    private val playlistRepository: PlaylistRepository,
    private val resolveLastChannel: ResolveLastChannelUseCase,
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

    /** Set once the launch resume has been decided, so a recreated activity never resumes again. */
    private var resumeChecked = false

    /** Used once when onboarding finishes: without a playlist the user goes straight to "Add playlist". */
    suspend fun hasPlaylists(): Boolean = playlistRepository.playlists.first().isNotEmpty()

    /**
     * Channel to open on a cold start: the last played one when "continue on launch" is on. Answers
     * once per ViewModel (later calls return null). The channel is remembered by playlist + uniqueKey,
     * so it is found again after a refresh gave it a new id (P3a, K18 / K20). A channel that no longer
     * exists (deleted playlist, removed from the source) is forgotten and nothing opens.
     */
    suspend fun channelToResume(): Long? {
        val firstCall = !resumeChecked
        resumeChecked = true
        val enabled = firstCall && settingsRepository.settings.first().playback.resumeLastChannel
        return if (enabled) resolveLastChannel() else null
    }
}
