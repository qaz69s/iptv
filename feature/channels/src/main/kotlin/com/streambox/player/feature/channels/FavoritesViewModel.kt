package com.streambox.player.feature.channels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streambox.player.core.domain.repository.FavoritesRepository
import com.streambox.player.core.domain.repository.PlaylistRepository
import com.streambox.player.core.domain.repository.WatchHistoryRepository
import com.streambox.player.core.model.Channel
import com.streambox.player.core.model.ChannelKey
import com.streambox.player.core.model.WatchHistoryEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class LibraryTab { Favorites, Recent }

data class FavoritesUiState(
    val tab: LibraryTab = LibraryTab.Favorites,
    /** Favorites in their order: drag (move up / down) order first, else the time added (P3a). */
    val favorites: List<Channel> = emptyList(),
    /** Recently watched, newest first (at most 50). */
    val history: List<WatchHistoryEntry> = emptyList(),
    val cleartextPlaylists: Set<Long> = emptySet(),
    val loading: Boolean = true,
)

/**
 * The "Favorites" tab (requirements §4.7): favorites with a custom order (move up / down, which works
 * with touch and the remote alike) and the watch history, which can be cleared.
 */
@HiltViewModel
class FavoritesViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val favoritesRepository: FavoritesRepository,
    private val watchHistoryRepository: WatchHistoryRepository,
    playlistRepository: PlaylistRepository,
) : ViewModel() {
    val uiState: StateFlow<FavoritesUiState> = combine(
        savedStateHandle.getStateFlow(KEY_TAB, LibraryTab.Favorites.name),
        favoritesRepository.favorites,
        watchHistoryRepository.history,
        playlistRepository.playlists,
    ) { tab, favorites, history, playlists ->
        FavoritesUiState(
            tab = LibraryTab.entries.firstOrNull { it.name == tab } ?: LibraryTab.Favorites,
            favorites = favorites,
            history = history,
            cleartextPlaylists = playlists.filter { it.allowCleartext }.mapTo(mutableSetOf()) { it.id },
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), FavoritesUiState())

    fun selectTab(tab: LibraryTab) {
        savedStateHandle[KEY_TAB] = tab.name
    }

    /**
     * Moves one favorite a place up or down; the shown order becomes the custom order (favorites whose
     * channel is currently missing follow, by time added).
     */
    fun moveFavorite(key: ChannelKey, up: Boolean) {
        val keys = uiState.value.favorites.map { it.key }
        val index = keys.indexOf(key)
        val target = if (up) index - 1 else index + 1
        if (index < 0 || target !in keys.indices) return
        val reordered = keys.toMutableList().apply {
            this[index] = keys[target]
            this[target] = key
        }
        viewModelScope.launch { favoritesRepository.reorder(reordered) }
    }

    fun removeFavorite(key: ChannelKey) {
        viewModelScope.launch { favoritesRepository.remove(key) }
    }

    /** Back to "ordered by time added". */
    fun resetFavoriteOrder() {
        viewModelScope.launch { favoritesRepository.clearCustomOrder() }
    }

    fun removeFromHistory(key: ChannelKey) {
        viewModelScope.launch { watchHistoryRepository.remove(key) }
    }

    fun clearHistory() {
        viewModelScope.launch { watchHistoryRepository.clear() }
    }

    private companion object {
        const val KEY_TAB = "libraryTab"
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
