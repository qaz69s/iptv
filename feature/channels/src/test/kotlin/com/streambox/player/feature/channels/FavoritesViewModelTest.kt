package com.streambox.player.feature.channels

import androidx.lifecycle.SavedStateHandle
import com.streambox.player.core.model.Playlist
import com.streambox.player.core.model.PlaylistType
import com.streambox.player.core.testing.FakeChannelRepository.Companion.channel
import com.streambox.player.core.testing.FakeFavoritesRepository
import com.streambox.player.core.testing.FakePlaylistRepository
import com.streambox.player.core.testing.FakeWatchHistoryRepository
import com.streambox.player.core.testing.MainDispatcherExtension
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class FavoritesViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val channels = MutableStateFlow((1L..4L).map { channel(it) })
    private var time = 0L
    private val favorites = FakeFavoritesRepository(channels) { time++ }
    private val history = FakeWatchHistoryRepository(channels) { time++ }
    private val playlists = FakePlaylistRepository(
        listOf(Playlist(1, "A", PlaylistType.M3U_URL, "http://a/list.m3u", allowCleartext = true)),
    )

    private fun TestScope.viewModel(handle: SavedStateHandle = SavedStateHandle()): FavoritesViewModel {
        val viewModel = FavoritesViewModel(handle, favorites, history, playlists)
        backgroundScope.launch(mainDispatcher.testDispatcher) { viewModel.uiState.collect {} }
        return viewModel
    }

    private fun FavoritesViewModel.favoriteIds() = uiState.value.favorites.map { it.id }

    @Test
    fun `lists favorites by time added and the history newest first`() = runTest {
        favorites.add(channel(3).key)
        favorites.add(channel(1).key)
        history.record(channel(2).key)
        history.record(channel(4).key)
        val viewModel = viewModel()

        assertEquals(listOf(3L, 1L), viewModel.favoriteIds())
        assertEquals(listOf(4L, 2L), viewModel.uiState.value.history.map { it.channel.id })
        assertEquals(setOf(1L), viewModel.uiState.value.cleartextPlaylists)
    }

    @Test
    fun `moving a favorite up or down makes the shown order the custom order`() = runTest {
        listOf(1L, 2L, 3L).forEach { favorites.add(channel(it).key) }
        val viewModel = viewModel()

        viewModel.moveFavorite(channel(3).key, up = true)
        assertEquals(listOf(1L, 3L, 2L), viewModel.favoriteIds())

        viewModel.moveFavorite(channel(1).key, up = false)
        assertEquals(listOf(3L, 1L, 2L), viewModel.favoriteIds())

        // Already first / last: nothing changes.
        viewModel.moveFavorite(channel(3).key, up = true)
        viewModel.moveFavorite(channel(2).key, up = false)
        assertEquals(listOf(3L, 1L, 2L), viewModel.favoriteIds())

        // A favorite added later goes to the end; resetting returns to the time order.
        favorites.add(channel(4).key)
        assertEquals(listOf(3L, 1L, 2L, 4L), viewModel.favoriteIds())
        viewModel.resetFavoriteOrder()
        assertEquals(listOf(1L, 2L, 3L, 4L), viewModel.favoriteIds())
    }

    @Test
    fun `removing a favorite`() = runTest {
        listOf(1L, 2L).forEach { favorites.add(channel(it).key) }
        val viewModel = viewModel()

        viewModel.removeFavorite(channel(1).key)

        assertEquals(listOf(2L), viewModel.favoriteIds())
    }

    @Test
    fun `history entries can be removed one by one or all at once`() = runTest {
        listOf(1L, 2L, 3L).forEach { history.record(channel(it).key) }
        val viewModel = viewModel()

        viewModel.removeFromHistory(channel(2).key)
        assertEquals(listOf(3L, 1L), viewModel.uiState.value.history.map { it.channel.id })

        viewModel.clearHistory()
        assertTrue(viewModel.uiState.value.history.isEmpty())
    }

    @Test
    fun `the selected tab is saved`() = runTest {
        val handle = SavedStateHandle()
        val viewModel = viewModel(handle)
        assertEquals(LibraryTab.Favorites, viewModel.uiState.value.tab)

        viewModel.selectTab(LibraryTab.Recent)

        assertEquals(LibraryTab.Recent, viewModel.uiState.value.tab)
        assertEquals(LibraryTab.Recent, viewModel(SavedStateHandle(mapOf("libraryTab" to "Recent"))).uiState.value.tab)
    }
}
