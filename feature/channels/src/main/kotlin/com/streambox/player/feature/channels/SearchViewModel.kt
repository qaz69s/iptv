package com.streambox.player.feature.channels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streambox.player.core.domain.repository.ChannelRepository
import com.streambox.player.core.domain.repository.FavoritesRepository
import com.streambox.player.core.domain.repository.PlaylistRepository
import com.streambox.player.core.domain.usecase.ToggleFavoriteUseCase
import com.streambox.player.core.model.Channel
import com.streambox.player.core.model.ChannelKey
import com.streambox.player.core.model.SearchResults
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SearchUiState(
    /** What the user typed (shown at once; the search runs [SearchViewModel.DEBOUNCE_MS] later). */
    val query: String = "",
    /** The text [results] belong to; differs from [query] while a search is pending. */
    val searchedQuery: String = "",
    val results: SearchResults = SearchResults(),
    val favoriteKeys: Set<ChannelKey> = emptySet(),
    val cleartextPlaylists: Set<Long> = emptySet(),
) {
    val isSearching: Boolean get() = query.isNotBlank() && query.trim() != searchedQuery
}

/**
 * Global search (requirements §4.2): search as you type, 300 ms after the last keystroke, through the
 * channel full-text index (plus the user's own channel names). Search by current programme follows
 * with the EPG (P4).
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val channelRepository: ChannelRepository,
    favoritesRepository: FavoritesRepository,
    playlistRepository: PlaylistRepository,
    private val toggleFavorite: ToggleFavoriteUseCase,
) : ViewModel() {
    private val query: StateFlow<String> = savedStateHandle.getStateFlow(KEY_QUERY, "")

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private val results: Flow<Pair<String, SearchResults>> = query
        .map { it.trim() }
        .debounce(DEBOUNCE_MS)
        .distinctUntilChanged()
        .mapLatest { text ->
            text to if (text.isEmpty()) SearchResults() else channelRepository.search(text)
        }

    val uiState: StateFlow<SearchUiState> = combine(
        query,
        flow {
            emit("" to SearchResults())
            emitAll(results)
        },
        favoritesRepository.favoriteKeys,
        playlistRepository.playlists,
    ) { typed, (searched, found), favorites, playlists ->
        SearchUiState(
            query = typed,
            searchedQuery = searched,
            results = found,
            favoriteKeys = favorites,
            cleartextPlaylists = playlists.filter { it.allowCleartext }.mapTo(mutableSetOf()) { it.id },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), SearchUiState())

    fun onQueryChange(text: String) {
        savedStateHandle[KEY_QUERY] = text
    }

    fun onToggleFavorite(channel: Channel) {
        viewModelScope.launch { toggleFavorite(channel.key) }
    }

    companion object {
        const val DEBOUNCE_MS = 300L
        private const val KEY_QUERY = "query"
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
