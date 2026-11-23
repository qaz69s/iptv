package com.streamdeck.player.feature.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamdeck.player.core.data.ChannelRepository
import com.streamdeck.player.core.data.LibraryRepository
import com.streamdeck.player.core.data.PlaylistRepository
import com.streamdeck.player.core.database.ChannelGroupEntity
import com.streamdeck.player.core.database.ChannelListItem
import com.streamdeck.player.core.database.PlaylistEntity
import com.streamdeck.player.core.datastore.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One horizontal row of the TV home. */
data class TvRow(val key: String, val title: String?, val titleRes: Int?, val count: Int?, val items: List<ChannelListItem>)

/** TV home (design 4): hero + rows (favourites, recently watched, then the first groups). */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TvHomeViewModel @Inject constructor(
    playlists: PlaylistRepository,
    private val channels: ChannelRepository,
    private val library: LibraryRepository,
    settings: SettingsRepository,
) : ViewModel() {
    val playlistList: StateFlow<List<PlaylistEntity>?> = playlists.observePlaylists().map { l -> l.filter { it.enabled } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val active: StateFlow<PlaylistEntity?> = combine(settings.activePlaylistId, playlistList) { saved, list ->
        list?.firstOrNull { it.id == saved } ?: list?.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val total: StateFlow<Int> = active.flatMapLatest { p -> if (p == null) flowOf(0) else channels.visibleCount(p.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val rows: StateFlow<List<TvRow>?> = active.flatMapLatest { p ->
        if (p == null) flowOf(emptyList()) else channels.groups(p.id).flatMapLatest { groups -> rowsFor(p.id, groups.take(GROUP_ROWS)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun rowsFor(pid: Long, groups: List<ChannelGroupEntity>) = combine(
        listOf(
            channels.favorites(pid, ROW_LIMIT).map { TvRow("fav", null, R.string.favorite_channels, it.size, it) },
            channels.recent(pid, ROW_LIMIT).map { TvRow("recent", null, R.string.recent, null, it) },
        ) + groups.map { g -> channels.groupItems(pid, g.id, ROW_LIMIT).map { TvRow("g${g.id}", g.name, null, null, it) } },
    ) { arr -> arr.filter { it.items.isNotEmpty() } }

    fun toggleFavorite(item: ChannelListItem) { viewModelScope.launch { library.setFavorite(item.channel, !item.isFavorite) } }

    private companion object {
        const val ROW_LIMIT = 30
        const val GROUP_ROWS = 6
    }
}
