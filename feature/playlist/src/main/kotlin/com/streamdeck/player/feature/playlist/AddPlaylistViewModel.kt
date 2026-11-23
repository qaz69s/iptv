package com.streamdeck.player.feature.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamdeck.player.core.database.PlaylistEntity
import com.streamdeck.player.core.database.PlaylistType
import com.streamdeck.player.core.data.PlaylistRepository
import com.streamdeck.player.core.datastore.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ImportUiState {
    data object Editing : ImportUiState
    data class Importing(val count: Int) : ImportUiState
    data class Failed(val reason: String) : ImportUiState
    data object Done : ImportUiState
}

/** Optional settings of the add form (“高级选项”); applied right after the playlist row is created. */
data class PlaylistExtras(
    val userAgent: String? = null,
    val headersJson: String? = null,
    val epgUrl: String? = null,
    val updateMode: String = PlaylistEntity.UPDATE_MANUAL,
    val updateIntervalHours: Int = 24,
)

@HiltViewModel
class AddPlaylistViewModel @Inject constructor(
    private val repo: PlaylistRepository,
    private val prefs: SettingsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<ImportUiState>(ImportUiState.Editing)
    val state: StateFlow<ImportUiState> = _state.asStateFlow()
    private var job: Job? = null
    private var lastId: Long? = null

    fun submit(name: String, type: PlaylistType, source: String, userAgent: String, allowCleartext: Boolean, extras: PlaylistExtras = PlaylistExtras()) {
        job = viewModelScope.launch {
            val id = lastId ?: repo.create(name.trim(), type, source.trim(), userAgent, allowCleartext).also { lastId = it; applyExtras(it, extras) }
            runImport(id)
        }
    }

    fun submitXtream(name: String, server: String, user: String, password: String, allowCleartext: Boolean, extras: PlaylistExtras = PlaylistExtras()) {
        job = viewModelScope.launch {
            val id = lastId ?: repo.createXtream(name.trim(), server, user, password, allowCleartext).also { lastId = it; applyExtras(it, extras) }
            runImport(id)
        }
    }

    private suspend fun applyExtras(id: Long, e: PlaylistExtras) {
        if (e == PlaylistExtras()) return
        val p = repo.get(id) ?: return
        repo.update(
            p.copy(
                userAgent = e.userAgent?.ifBlank { null } ?: p.userAgent,
                headersJson = e.headersJson ?: p.headersJson,
                epgUrl = e.epgUrl?.ifBlank { null } ?: p.epgUrl,
                updateMode = e.updateMode,
                updateIntervalHours = e.updateIntervalHours.coerceIn(1, MAX_INTERVAL_HOURS),
            ),
        )
    }

    private companion object { const val MAX_INTERVAL_HOURS = 168 }

    fun retry() { lastId?.let { id -> job = viewModelScope.launch { runImport(id) } } }

    fun cancel() {
        job?.cancel()
        viewModelScope.launch { lastId?.let { repo.delete(it) }; lastId = null; _state.value = ImportUiState.Editing }
    }

    fun dismissError() { _state.value = ImportUiState.Editing }

    private suspend fun runImport(id: Long) {
        _state.value = ImportUiState.Importing(0)
        try {
            repo.import(id) { _state.value = ImportUiState.Importing(it) }
            prefs.setActivePlaylist(id)
            _state.value = ImportUiState.Done
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            _state.value = ImportUiState.Failed(t.message ?: t.javaClass.simpleName)
        }
    }
}
