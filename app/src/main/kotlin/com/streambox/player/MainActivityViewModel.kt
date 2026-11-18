package com.streambox.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streambox.player.core.domain.repository.ChannelRepository
import com.streambox.player.core.domain.repository.LastChannelRepository
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
    private val settingsRepository: SettingsRepository,
    private val playlistRepository: PlaylistRepository,
    private val channelRepository: ChannelRepository,
    private val lastChannelRepository: LastChannelRepository,
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
     * once per ViewModel (later calls return null). A remembered channel that no longer exists (deleted
     * or re-imported playlist) is forgotten and nothing opens.
     */
    suspend fun channelToResume(): Long? {
        val firstCall = !resumeChecked
        resumeChecked = true
        val enabled = firstCall && settingsRepository.settings.first().playback.resumeLastChannel
        val last = if (enabled) lastChannelRepository.lastChannel.first() else null
        return last?.let { remembered ->
            val item = channelRepository.playbackItem(remembered.channelId)
            if (item == null || item.playlistId != remembered.playlistId) {
                lastChannelRepository.clear()
                null
            } else {
                item.channelId
            }
        }
    }
}
