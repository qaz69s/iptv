package com.streamdeck.player.feature.vod

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.streamdeck.player.core.data.PlaylistRepository
import com.streamdeck.player.core.data.VodKey
import com.streamdeck.player.core.data.VodRepository
import com.streamdeck.player.core.data.XtreamImporter
import com.streamdeck.player.core.database.PlaylistEntity
import com.streamdeck.player.core.database.PlaylistType
import com.streamdeck.player.core.database.SeriesEntity
import com.streamdeck.player.core.database.VodCategoryEntity
import com.streamdeck.player.core.database.VodItemEntity
import com.streamdeck.player.core.database.VodProgressEntity
import com.streamdeck.player.core.network.XtreamEpisode
import com.streamdeck.player.core.network.XtreamSeriesInfo
import com.streamdeck.player.core.network.XtreamVodInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

object VodArgs {
    const val PLAYLIST_ID = "vodPlaylist"
    const val KIND = "vodKind"
    const val ITEM_ID = "vodItem"
}

enum class VodTab(val kind: String) { MOVIES(XtreamImporter.KIND_MOVIE), SERIES(XtreamImporter.KIND_SERIES) }

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class VodHomeViewModel @Inject constructor(playlists: PlaylistRepository, private val vod: VodRepository) : ViewModel() {
    val xtreamPlaylists: StateFlow<List<PlaylistEntity>?> = playlists.observePlaylists()
        .map { l -> l.filter { it.enabled && it.type == PlaylistType.XTREAM } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val selectedPlaylist = MutableStateFlow<Long?>(null)
    val tab = MutableStateFlow(VodTab.MOVIES)
    val category = MutableStateFlow<String?>(null)
    val query = MutableStateFlow("")

    val activeId: StateFlow<Long?> = combine(selectedPlaylist, xtreamPlaylists) { sel, list ->
        list?.firstOrNull { it.id == sel }?.id ?: list?.firstOrNull()?.id
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val categories: StateFlow<List<VodCategoryEntity>> = combine(activeId, tab) { id, t -> id to t }
        .flatMapLatest { (id, t) -> if (id == null) flowOf(emptyList()) else vod.categories(id, t.kind) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val filters = combine(activeId, category, query.debounce(300)) { id, c, q -> Triple(id, c, q) }

    val movies: Flow<PagingData<VodItemEntity>> = filters.flatMapLatest { (id, c, q) -> if (id == null) emptyFlow() else vod.movies(id, c,
        q) }.cachedIn(viewModelScope)
    val series: Flow<PagingData<SeriesEntity>> = filters.flatMapLatest { (id, c, q) -> if (id == null) emptyFlow() else vod.series(id, c,
        q) }.cachedIn(viewModelScope)

    val progress: StateFlow<Map<String, VodProgressEntity>> = activeId.flatMapLatest { id -> if (id == null) flowOf(emptyMap()) else vod.progress(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** “继续观看” rail: unfinished movies, most recently watched first (episodes are reached through their series). */
    val continueWatching: StateFlow<List<Pair<VodItemEntity, VodProgressEntity>>> = combine(activeId, progress) { id, map -> id to map }
        .mapLatest { (id, map) ->
            if (id == null) {
                emptyList()
            } else {
                map.values.asSequence()
                    .filter { it.key.startsWith(MOVIE_PREFIX) && !it.watched && it.positionMs > 0 }
                    .sortedByDescending { it.updatedAt }
                    .take(CONTINUE_LIMIT)
                    .toList()
                    .mapNotNull { pr -> pr.key.removePrefix(MOVIE_PREFIX).toLongOrNull()?.let { sid -> vod.movie(id, sid) }?.let { it to pr } }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectTab(t: VodTab) { tab.value = t; category.value = null }

    private companion object {
        const val MOVIE_PREFIX = "movie:"
        const val CONTINUE_LIMIT = 12
    }
}

sealed interface DetailState {
    data object Loading : DetailState
    data class Movie(val item: VodItemEntity?, val info: XtreamVodInfo?, val key: String) : DetailState
    data class Series(val entity: SeriesEntity?, val info: XtreamSeriesInfo, val playlistId: Long) : DetailState
    data class Failed(val reason: String) : DetailState
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class VodDetailViewModel @Inject constructor(savedState: SavedStateHandle, private val vod: VodRepository) : ViewModel() {
    private val pid: Long = checkNotNull(savedState[VodArgs.PLAYLIST_ID])
    private val kind: String = checkNotNull(savedState[VodArgs.KIND])
    private val itemId: Long = checkNotNull(savedState[VodArgs.ITEM_ID])

    private val _state = MutableStateFlow<DetailState>(DetailState.Loading)
    val state: StateFlow<DetailState> = _state
    val progress: StateFlow<Map<String, VodProgressEntity>> = vod.progress(pid).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    val season = MutableStateFlow<Int?>(null)

    init { load() }

    fun load() {
        _state.value = DetailState.Loading
        viewModelScope.launch {
            _state.value = runCatching {
                if (kind == XtreamImporter.KIND_MOVIE) {
                    val item = vod.movie(pid, itemId)
                    val info = runCatching { vod.movieInfo(pid, itemId) }.getOrNull()
                    DetailState.Movie(item, info, VodKey.Movie(pid, itemId, item?.extension ?: "mp4").encode())
                } else {
                    val info = vod.seriesInfo(pid, itemId) ?: error("series unavailable")
                    season.value = info.episodes.firstOrNull()?.season
                    DetailState.Series(vod.seriesEntity(pid, itemId), info, pid)
                }
            }.getOrElse { DetailState.Failed(it.message ?: it.javaClass.simpleName) }
        }
    }

    fun episodeKey(e: XtreamEpisode) = VodKey.Episode(pid, itemId, e.id, e.extension).encode()
    fun progressKey(e: XtreamEpisode) = VodKey.Episode(pid, itemId, e.id, e.extension).progressKey
    fun movieProgressKey() = VodKey.Movie(pid, itemId, "").progressKey

    fun toggleWatched(key: String, watched: Boolean) { viewModelScope.launch { vod.markWatched(key, !watched) } }
}
