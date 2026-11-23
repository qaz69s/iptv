package com.streamdeck.player.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamdeck.player.core.data.BackupRepository
import com.streamdeck.player.core.data.ChannelRepository
import com.streamdeck.player.core.data.EpgRepository
import com.streamdeck.player.core.data.LibraryRepository
import com.streamdeck.player.core.data.ParentalRepository
import com.streamdeck.player.core.data.PlaylistRepository
import com.streamdeck.player.core.data.RestoreResult
import com.streamdeck.player.core.datastore.AppSettings
import com.streamdeck.player.core.datastore.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface SettingsMessage {
    data object Exported : SettingsMessage
    data class Restored(val result: RestoreResult) : SettingsMessage
    data class Failed(val reason: String) : SettingsMessage
    data object HistoryCleared : SettingsMessage
    data object EpgUpdated : SettingsMessage
}

/** Header card of “我的”: active playlist, channel count and last update. */
data class PlaylistSummary(val name: String, val channels: Int, val lastUpdated: Long, val playlists: Int)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val backup: BackupRepository,
    private val library: LibraryRepository,
    private val playlists: PlaylistRepository,
    private val epg: EpgRepository,
    channels: ChannelRepository,
    val parental: ParentalRepository,
) : ViewModel() {
    val playlistSummary: StateFlow<PlaylistSummary?> = combine(repo.activePlaylistId, playlists.observePlaylists()) { saved, all ->
        val enabled = all.filter { it.enabled }
        (enabled.firstOrNull { it.id == saved } ?: enabled.firstOrNull()) to all.size
    }.flatMapLatest { (p, n) ->
        if (p == null) flowOf(null) else channels.visibleCount(p.id).map { PlaylistSummary(p.name, it, p.lastUpdated, n) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val settings: StateFlow<AppSettings> = repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val hasPin: StateFlow<Boolean> = parental.hasPin.stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val historyCount: StateFlow<Int> = library.historyCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val message = MutableStateFlow<SettingsMessage?>(null)
    val busy = MutableStateFlow(false)

    fun update(transform: (AppSettings) -> AppSettings) { viewModelScope.launch { repo.update(transform) } }

    fun setPin(pin: String) { viewModelScope.launch { parental.setPin(pin) } }
    fun clearPin() { viewModelScope.launch { parental.clearPin() } }

    fun export(uri: Uri) = launchTask { backup.export(uri); SettingsMessage.Exported }
    fun restore(uri: Uri) = launchTask { SettingsMessage.Restored(backup.restore(uri)) }
    fun clearHistory() = launchTask { library.clearHistory(); SettingsMessage.HistoryCleared }

    fun updateAllEpg() = launchTask {
        playlists.observePlaylistsOnce().filter { it.enabled && !it.epgUrl.isNullOrBlank() }.forEach { epg.refresh(it.id) }
        SettingsMessage.EpgUpdated
    }

    private fun launchTask(block: suspend () -> SettingsMessage) {
        viewModelScope.launch {
            busy.value = true
            message.value = runCatching { block() }.getOrElse { SettingsMessage.Failed(it.message ?: it.javaClass.simpleName) }
            busy.value = false
        }
    }
}
