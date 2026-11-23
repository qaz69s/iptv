package com.streamdeck.player.feature.epg

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamdeck.player.core.data.ChannelRepository
import com.streamdeck.player.core.data.EpgRepository
import com.streamdeck.player.core.data.PlaylistRepository
import com.streamdeck.player.core.data.Program
import com.streamdeck.player.core.data.ReminderRepository
import com.streamdeck.player.core.database.ChannelEntity
import com.streamdeck.player.core.database.ChannelGroupEntity
import com.streamdeck.player.core.database.EpgChannelEntity
import com.streamdeck.player.core.database.GridChannel
import com.streamdeck.player.core.database.ReminderEntity
import com.streamdeck.player.core.datastore.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

object EpgViewModelArgs { const val CHANNEL_ID = "epgChannelRowId" }

sealed interface EpgRefreshState {
    data object Idle : EpgRefreshState
    data class Running(val count: Int) : EpgRefreshState
    data class Failed(val reason: String) : EpgRefreshState
}

/** Shared reminder actions for the grid and the per-channel list. */
abstract class ReminderHost(private val reminders: ReminderRepository) : ViewModel() {
    val upcoming: StateFlow<List<ReminderEntity>> = reminders.upcoming().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun toggleReminder(playlistId: Long, uniqueKey: String, channelName: String, p: Program, autoSwitch: Boolean) {
        viewModelScope.launch {
            val existing = reminders.find(playlistId, uniqueKey, p.start)
            if (existing != null) reminders.remove(existing.id) else reminders.add(playlistId, uniqueKey, channelName, p, autoSwitch)
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EpgGridViewModel @Inject constructor(
    private val epg: EpgRepository,
    playlists: PlaylistRepository,
    channels: ChannelRepository,
    settings: SettingsRepository,
    reminders: ReminderRepository,
) : ReminderHost(reminders) {
    val activeId: StateFlow<Long?> = combine(settings.activePlaylistId, playlists.observePlaylists()) { saved, list ->
        val enabled = list.filter { it.enabled }
        enabled.firstOrNull { it.id == saved }?.id ?: enabled.firstOrNull()?.id
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val hasEpgSource: StateFlow<Boolean> = combine(activeId, playlists.observePlaylists()) { id, list ->
        list.firstOrNull { it.id == id }?.epgUrl?.isNotBlank() == true
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val groups: StateFlow<List<ChannelGroupEntity>> = activeId.flatMapLatest { if (it == null) flowOf(emptyList()) else channels.groups(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedGroup = MutableStateFlow<Long?>(null)

    /** Day shown by the grid relative to today (−1 … +keepDays). */
    val dayOffset = MutableStateFlow(0)
    val keepDays: StateFlow<Int> = settings.settings.map { it.epgKeepDays }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 3)

    val rows: StateFlow<List<GridChannel>?> = combine(activeId, selectedGroup) { a, g -> a to g }
        .flatMapLatest { (a, g) -> if (a == null) flowOf(emptyList()) else epg.gridChannels(a, g) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _refresh = MutableStateFlow<EpgRefreshState>(EpgRefreshState.Idle)
    val refresh: StateFlow<EpgRefreshState> = _refresh

    fun programs(row: GridChannel, from: Long, to: Long): Flow<List<Program>> = epg.programs(row.playlistId, row.uniqueKey, from, to)

    fun refreshEpg() {
        val id = activeId.value ?: return
        viewModelScope.launch {
            _refresh.value = EpgRefreshState.Running(0)
            _refresh.value = runCatching { epg.refresh(id) { _refresh.value = EpgRefreshState.Running(it) } }
                .fold({ EpgRefreshState.Idle }, { EpgRefreshState.Failed(it.message ?: it.javaClass.simpleName) })
        }
    }

    fun dismissError() { _refresh.value = EpgRefreshState.Idle }
}

enum class DayRange { TODAY, TOMORROW, WEEK }

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChannelProgramsViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val channels: ChannelRepository,
    private val epg: EpgRepository,
    reminders: ReminderRepository,
) : ReminderHost(reminders) {
    private val channelId: Long = checkNotNull(savedState[EpgViewModelArgs.CHANNEL_ID])
    private val _channel = MutableStateFlow<ChannelEntity?>(null)
    val channel: StateFlow<ChannelEntity?> = _channel
    val range = MutableStateFlow(DayRange.TODAY)

    init { viewModelScope.launch { _channel.value = channels.get(channelId) } }

    val programs: StateFlow<List<Program>?> = combine(_channel, range) { c, r -> c to r }.flatMapLatest { (c, r) ->
        if (c == null) {
            flowOf(null)
        } else {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val (from, to) = when (r) {
                DayRange.TODAY -> today to today.plusDays(1)
                DayRange.TOMORROW -> today.plusDays(1) to today.plusDays(2)
                DayRange.WEEK -> today.minusDays(7) to today.plusDays(8)
            }
            epg.programs(c.playlistId, c.uniqueKey, from.atStartOfDay(zone).toInstant().toEpochMilli(), to.atStartOfDay(zone).toInstant().toEpochMilli())
                .map { it }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _epgChoices = MutableStateFlow<List<EpgChannelEntity>>(emptyList())
    val epgChoices: StateFlow<List<EpgChannelEntity>> = _epgChoices

    fun searchEpg(q: String) {
        val c = _channel.value ?: return
        viewModelScope.launch { _epgChoices.value = epg.searchEpgChannels(c.playlistId, q) }
    }

    fun assign(e: EpgChannelEntity) {
        val c = _channel.value ?: return
        viewModelScope.launch { epg.setManualMapping(c.playlistId, c.uniqueKey, e.id) }
    }
}
