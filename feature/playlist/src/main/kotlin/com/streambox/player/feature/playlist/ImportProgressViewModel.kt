package com.streambox.player.feature.playlist

import androidx.lifecycle.SavedStateHandle
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
import kotlinx.coroutines.flow.stateIn

data class ImportProgressUiState(val playlist: Playlist? = null, val status: ImportStatus = ImportStatus.Idle)

@HiltViewModel
class ImportProgressViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PlaylistRepository,
) : ViewModel() {
    /** Argument of the type-safe route [com.streambox.player.feature.playlist.navigation.ImportProgressRoute]. */
    private val playlistId: Long = checkNotNull(savedStateHandle.get<Long>("playlistId"))

    val uiState: StateFlow<ImportProgressUiState> = combine(
        repository.observePlaylist(playlistId),
        repository.importStatus(playlistId),
    ) { playlist, status -> ImportProgressUiState(playlist, status) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ImportProgressUiState())

    fun retry() = repository.startImport(playlistId)

    fun cancel() = repository.cancelImport(playlistId)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
