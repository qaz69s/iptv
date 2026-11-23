package com.streamdeck.player.feature.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.streamdeck.player.core.data.ChannelRepository
import com.streamdeck.player.core.data.GroupFilter
import com.streamdeck.player.core.data.LibraryRepository
import com.streamdeck.player.core.data.PlaylistRepository
import com.streamdeck.player.core.database.ChannelGroupEntity
import com.streamdeck.player.core.database.ChannelListItem
import com.streamdeck.player.core.database.PlaylistEntity
import com.streamdeck.player.core.datastore.AppSettings
import com.streamdeck.player.core.datastore.ChannelSort
import com.streamdeck.player.core.datastore.ChannelViewMode
import com.streamdeck.player.core.datastore.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Chip counts: total visible channels, favourites, and per group. */
data class ChipCounts(val total: Int = 0, val favorites: Int = 0, val groups: Map<Long, Int> = emptyMap())

sealed interface RefreshState {
    data object Idle : RefreshState
    data class Running(val count: Int) : RefreshState
    data class Failed(val reason: String) : RefreshState
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class ChannelsViewModel @Inject constructor(
    private val playlists: PlaylistRepository,
    private val channels: ChannelRepository,
    private val library: LibraryRepository,
    private val settingsRepo: SettingsRepository,
) : ViewModel() {

    val playlistList: StateFlow<List<PlaylistEntity>?> = playlists.observePlaylists().map { l -> l.filter { it.enabled } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val activeId: StateFlow<Long?> = combine(settingsRepo.activePlaylistId, playlistList) { saved, list ->
        list?.firstOrNull { it.id == saved }?.id ?: list?.firstOrNull()?.id
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val settings: StateFlow<AppSettings> = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val activePlaylist: StateFlow<PlaylistEntity?> = combine(activeId, playlistList) { id, list -> list?.firstOrNull { it.id == id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val counts: StateFlow<ChipCounts> = activeId.flatMapLatest { id ->
        if (id == null) flowOf(ChipCounts()) else combine(channels.groupCounts(id), channels.favoriteCount(id)) { g, f ->
            ChipCounts(total = g.sumOf { it.count }, favorites = f, groups = g.associate { it.groupId to it.count })
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChipCounts())

    /** “继续观看” rail of the phone home. */
    val recent: StateFlow<List<ChannelListItem>> = activeId.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else channels.recent(id, RECENT_RAIL) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val filter = MutableStateFlow<GroupFilter>(GroupFilter.All)
    val query = MutableStateFlow("")

    private val _refresh = MutableStateFlow<RefreshState>(RefreshState.Idle)
    val refresh: StateFlow<RefreshState> = _refresh

    val groups: StateFlow<List<ChannelGroupEntity>> = activeId.flatMapLatest { id ->
        if (id == null) flowOf(emptyList()) else channels.groups(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val sort = settingsRepo.settings.map { it.channelSort }.distinctUntilChanged()

    val items: Flow<PagingData<ChannelListItem>> =
        combine(activeId, filter, query.debounce(SEARCH_DEBOUNCE_MS).distinctUntilChanged(), sort) { a, f, q, s -> Query(a, f, q, s) }
            .flatMapLatest { (id, f, q, s) ->
                when {
                    id == null -> emptyFlow()
                    q.isNotBlank() -> channels.search(id, q, s)
                    else -> channels.channels(id, f, s)
                }
            }.cachedIn(viewModelScope)

    fun selectPlaylist(id: Long) { viewModelScope.launch { filter.value = GroupFilter.All; settingsRepo.setActivePlaylist(id) } }

    fun refresh(id: Long) {
        viewModelScope.launch {
            _refresh.value = RefreshState.Running(0)
            _refresh.value = runCatching { playlists.import(id) { _refresh.value = RefreshState.Running(it) } }
                .fold({ RefreshState.Idle }, { RefreshState.Failed(it.message ?: it.javaClass.simpleName) })
        }
    }

    fun dismissRefreshError() { _refresh.value = RefreshState.Idle }
    fun toggleFavorite(item: ChannelListItem) { viewModelScope.launch { library.setFavorite(item.channel, !item.isFavorite) } }
    fun setView(mode: ChannelViewMode) { viewModelScope.launch { settingsRepo.update { it.copy(channelView = mode) } } }
    fun setSort(s: ChannelSort) { viewModelScope.launch { settingsRepo.update { it.copy(channelSort = s) } } }

    /** True when the channel or its group is locked; the caller asks for the PIN first (spec §4.7). */
    fun isLocked(item: ChannelListItem) = item.channel.locked || item.groupLocked

    private data class Query(val id: Long?, val filter: GroupFilter, val q: String, val sort: ChannelSort)

    companion object {
        const val SEARCH_DEBOUNCE_MS = 300L
        private const val RECENT_RAIL = 12
    }
}
