package com.streambox.player.feature.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streambox.player.core.domain.repository.ChannelOverridesRepository
import com.streambox.player.core.domain.repository.PlaylistRepository
import com.streambox.player.core.model.EditorGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface PlaylistEditorUiState {
    data object Loading : PlaylistEditorUiState

    /** The playlist was deleted meanwhile. */
    data object Missing : PlaylistEditorUiState

    data class Loaded(val playlistName: String, val groups: List<EditorGroup>) : PlaylistEditorUiState
}

/**
 * List editor, level 1 (P3c): the groups of one playlist, hidden ones included. Hide / show, rename
 * (a name another group already has merges them in the browser, user decision 6) and move up / down.
 * Group order does not depend on the browse sort, so moving a group does not change it.
 */
@HiltViewModel
class PlaylistEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    playlistRepository: PlaylistRepository,
    private val overrides: ChannelOverridesRepository,
) : ViewModel() {
    private val playlistId: Long = checkNotNull(savedStateHandle.get<Long>("playlistId"))

    val uiState: StateFlow<PlaylistEditorUiState> = combine(
        playlistRepository.observePlaylist(playlistId),
        overrides.editorGroups(playlistId),
    ) { playlist, groups ->
        if (playlist == null) {
            PlaylistEditorUiState.Missing
        } else {
            PlaylistEditorUiState.Loaded(playlist.name, groups)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PlaylistEditorUiState.Loading)

    fun setHidden(group: EditorGroup, hidden: Boolean) {
        viewModelScope.launch { overrides.setGroupHidden(playlistId, group.name, hidden) }
    }

    /** A blank name restores the playlist's name. */
    fun rename(group: EditorGroup, name: String) {
        viewModelScope.launch { overrides.renameGroup(playlistId, group.name, name) }
    }

    fun move(group: EditorGroup, up: Boolean) {
        val groups = (uiState.value as? PlaylistEditorUiState.Loaded)?.groups ?: return
        val moved = groups.movedOrNull(groups.indexOf(group), up) ?: return
        viewModelScope.launch { overrides.reorderGroups(playlistId, moved.map { it.name }) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
