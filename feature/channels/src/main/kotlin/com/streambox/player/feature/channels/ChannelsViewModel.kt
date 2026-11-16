package com.streambox.player.feature.channels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.streambox.player.core.domain.repository.ChannelRepository
import com.streambox.player.core.domain.repository.PlaylistRepository
import com.streambox.player.core.model.Channel
import com.streambox.player.core.model.ChannelGroupSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

sealed interface ChannelsUiState {
    data object Loading : ChannelsUiState

    /** Fresh install: nothing is bundled, the user must add a playlist first. */
    data object NoPlaylists : ChannelsUiState

    /** Playlists exist but none has channels yet (first import running or failed). */
    data object NoChannels : ChannelsUiState

    data class Content(
        val groups: List<ChannelGroupSummary>,
        val totalChannels: Int,
        /** null = all groups. */
        val selectedGroup: String?,
    ) : ChannelsUiState
}

@HiltViewModel
class ChannelsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    playlistRepository: PlaylistRepository,
    channelRepository: ChannelRepository,
) : ViewModel() {
    private val selectedGroup: StateFlow<String?> = savedStateHandle.getStateFlow(KEY_GROUP, null)

    val uiState: StateFlow<ChannelsUiState> = combine(
        playlistRepository.playlists,
        channelRepository.groups,
        channelRepository.channelCount,
        selectedGroup,
    ) { playlists, groups, count, selected ->
        when {
            playlists.isEmpty() -> ChannelsUiState.NoPlaylists

            count == 0 -> ChannelsUiState.NoChannels

            else -> ChannelsUiState.Content(
                groups = groups,
                totalChannels = count,
                // A group can disappear after a refresh; fall back to "all" instead of an empty list.
                selectedGroup = selected?.takeIf { name -> groups.any { it.name == name } },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ChannelsUiState.Loading)

    val channels: Flow<PagingData<Channel>> = selectedGroup
        .flatMapLatest { channelRepository.pagedChannels(it) }
        .cachedIn(viewModelScope)

    fun onGroupSelected(groupName: String?) {
        savedStateHandle[KEY_GROUP] = groupName
    }

    private companion object {
        const val KEY_GROUP = "selectedGroup"
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
