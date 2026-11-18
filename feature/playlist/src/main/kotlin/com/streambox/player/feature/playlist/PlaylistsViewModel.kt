package com.streambox.player.feature.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streambox.player.core.domain.repository.PlaylistRepository
import com.streambox.player.core.model.ImportStatus
import com.streambox.player.core.model.Playlist
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlaylistRow(val playlist: Playlist, val status: ImportStatus) {
    val isImporting: Boolean
        get() = status is ImportStatus.Connecting || status is ImportStatus.Running || status is ImportStatus.Finalizing
}

sealed interface PlaylistsUiState {
    data object Loading : PlaylistsUiState

    data class Loaded(val rows: List<PlaylistRow>) : PlaylistsUiState
}

@HiltViewModel
class PlaylistsViewModel @Inject constructor(private val repository: PlaylistRepository) : ViewModel() {
    val uiState: StateFlow<PlaylistsUiState> = repository.playlists
        .flatMapLatest { playlists ->
            if (playlists.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(
                    playlists.map { playlist ->
                        repository.importStatus(playlist.id).map { PlaylistRow(playlist, it) }
                    },
                ) {
                    it.toList()
                }
            }
        }
        .map<List<PlaylistRow>, PlaylistsUiState> { PlaylistsUiState.Loaded(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PlaylistsUiState.Loading)

    fun refresh(id: Long) = repository.startImport(id)

    fun delete(id: Long) {
        viewModelScope.launch { repository.deletePlaylist(id) }
    }

    fun setAllowCleartext(id: Long, allowed: Boolean) {
        viewModelScope.launch { repository.setAllowCleartext(id, allowed) }
    }

    /** P3c (user decision 5): a disabled playlist keeps its data; its channels are just not shown. */
    fun setEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch { repository.setEnabled(id, enabled) }
    }

    /** P3c: up / down instead of drag and drop (TV friendly). */
    fun move(id: Long, up: Boolean) {
        val rows = (uiState.value as? PlaylistsUiState.Loaded)?.rows ?: return
        val moved = rows.movedOrNull(rows.indexOfFirst { it.playlist.id == id }, up) ?: return
        viewModelScope.launch { repository.reorderPlaylists(moved.map { it.playlist.id }) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
