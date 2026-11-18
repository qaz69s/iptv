package com.streambox.player.feature.channels

import androidx.lifecycle.SavedStateHandle
import com.streambox.player.core.domain.usecase.ToggleFavoriteUseCase
import com.streambox.player.core.model.ChannelGroupSummary
import com.streambox.player.core.model.ChannelSortOrder
import com.streambox.player.core.model.ChannelSource
import com.streambox.player.core.model.ChannelViewMode
import com.streambox.player.core.model.Playlist
import com.streambox.player.core.model.PlaylistType
import com.streambox.player.core.testing.FakeChannelRepository
import com.streambox.player.core.testing.FakeChannelRepository.Companion.channel
import com.streambox.player.core.testing.FakeCustomGroupsRepository
import com.streambox.player.core.testing.FakeFavoritesRepository
import com.streambox.player.core.testing.FakePlaylistRepository
import com.streambox.player.core.testing.FakeSettingsRepository
import com.streambox.player.core.testing.FakeWatchHistoryRepository
import com.streambox.player.core.testing.MainDispatcherExtension
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class ChannelsViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val playlist = Playlist(1, "A", PlaylistType.M3U_URL, "https://a/list.m3u")
    private val channels = FakeChannelRepository(
        listOf("News" to channel(1), "News" to channel(2), "Sports" to channel(3)),
    )

    private val library = MutableStateFlow(listOf(channel(1), channel(2), channel(3)))
    private val favorites = FakeFavoritesRepository(library)
    private val history = FakeWatchHistoryRepository(library)
    private val settings = FakeSettingsRepository()
    private val customGroups = FakeCustomGroupsRepository(library.value)

    private fun viewModel(
        playlists: FakePlaylistRepository = FakePlaylistRepository(listOf(playlist)),
        handle: SavedStateHandle = SavedStateHandle(),
    ) = ChannelsViewModel(
        handle,
        playlists,
        channels,
        favorites,
        history,
        customGroups,
        settings,
        ToggleFavoriteUseCase(favorites),
    )

    private fun TestScope.collect(viewModel: ChannelsViewModel) {
        backgroundScope.launch(mainDispatcher.testDispatcher) { viewModel.uiState.collect {} }
        backgroundScope.launch(mainDispatcher.testDispatcher) { viewModel.channels.collect {} }
    }

    @Test
    fun `no playlists`() = runTest {
        val viewModel = viewModel(playlists = FakePlaylistRepository())
        collect(viewModel)

        assertEquals(ChannelsUiState.NoPlaylists, viewModel.uiState.value)
    }

    @Test
    fun `playlists without channels`() = runTest {
        channels.channelsFlow.value = emptyList()
        val viewModel = viewModel()
        collect(viewModel)

        assertEquals(ChannelsUiState.NoChannels, viewModel.uiState.value)
    }

    @Test
    fun `content lists groups and totals`() = runTest {
        val viewModel = viewModel()
        collect(viewModel)

        assertEquals(
            ChannelsUiState.Content(
                groups = listOf(ChannelGroupSummary("News", 2), ChannelGroupSummary("Sports", 1)),
                totalChannels = 3,
                selectedGroup = null,
            ),
            viewModel.uiState.value,
        )
        assertEquals(listOf<String?>(null), channels.requestedGroups)
    }

    @Test
    fun `a custom group is listed, can be selected and falls back to all when deleted`() = runTest {
        val handle = SavedStateHandle()
        val viewModel = viewModel(handle = handle)
        collect(viewModel)
        val id = checkNotNull(customGroups.create("Kids"))
        customGroups.addChannel(id, channel(3).key)

        viewModel.onSourceSelected(ChannelSource.Custom(id))

        val content = viewModel.uiState.value as ChannelsUiState.Content
        assertEquals(ChannelSource.Custom(id), content.source)
        assertEquals(listOf("Kids"), content.customGroups.map { it.name })
        assertEquals(1, content.customGroups.single().channelCount)
        assertEquals("custom:$id", handle.get<String>("selectedVirtual"))

        customGroups.delete(id)
        assertEquals(ChannelSource.All, (viewModel.uiState.value as ChannelsUiState.Content).source)
    }

    @Test
    fun `selecting a group filters the list and is saved`() = runTest {
        val handle = SavedStateHandle()
        val viewModel = viewModel(handle = handle)
        collect(viewModel)

        viewModel.onGroupSelected("Sports")

        assertEquals("Sports", (viewModel.uiState.value as ChannelsUiState.Content).selectedGroup)
        assertEquals(listOf(null, "Sports"), channels.requestedGroups)
        assertEquals("Sports", handle.get<String>("selectedGroup"))
    }

    @Test
    fun `restores the saved group`() = runTest {
        val viewModel = viewModel(handle = SavedStateHandle(mapOf("selectedGroup" to "News")))
        collect(viewModel)

        assertEquals("News", (viewModel.uiState.value as ChannelsUiState.Content).selectedGroup)
        assertEquals(listOf<String?>("News"), channels.requestedGroups)
    }

    @Test
    fun `vanished group falls back to all channels in chips and list`() = runTest {
        val viewModel = viewModel()
        collect(viewModel)
        viewModel.onGroupSelected("Sports")

        // A refresh removes the Sports group.
        channels.channelsFlow.value = listOf("News" to channel(1), "News" to channel(2))

        assertEquals(null, (viewModel.uiState.value as ChannelsUiState.Content).selectedGroup)
        assertEquals(listOf(null, "Sports", null), channels.requestedGroups)
    }

    private fun ChannelsViewModel.content() = uiState.value as ChannelsUiState.Content

    @Test
    fun `virtual groups count favorites and recently watched`() = runTest {
        favorites.add(channel(2).key)
        history.record(channel(3).key)
        history.record(channel(1).key)
        val viewModel = viewModel()
        collect(viewModel)

        assertEquals(1, viewModel.content().favoriteCount)
        assertEquals(2, viewModel.content().recentCount)
        assertEquals(setOf(channel(2).key), viewModel.content().favoriteKeys)
    }

    @Test
    fun `selecting favorites or recently watched is saved and leaves the channel table alone`() = runTest {
        val handle = SavedStateHandle()
        val viewModel = viewModel(handle = handle)
        collect(viewModel)

        viewModel.onSourceSelected(ChannelSource.Favorites)
        assertEquals(ChannelSource.Favorites, viewModel.content().source)
        assertNull(viewModel.content().selectedGroup)
        assertEquals("favorites", handle.get<String>("selectedVirtual"))

        viewModel.onSourceSelected(ChannelSource.Recent)
        assertEquals(ChannelSource.Recent, viewModel.content().source)

        // Only the initial "all" page came from the channel table.
        assertEquals(listOf<String?>(null), channels.requestedGroups)

        viewModel.onSourceSelected(ChannelSource.Group("Sports"))
        assertEquals(ChannelSource.Group("Sports"), viewModel.content().source)
        assertNull(handle.get<String>("selectedVirtual"))
        assertEquals(listOf(null, "Sports"), channels.requestedGroups)
    }

    @Test
    fun `restores a saved virtual group`() = runTest {
        val viewModel = viewModel(handle = SavedStateHandle(mapOf("selectedVirtual" to "recent")))
        collect(viewModel)

        assertEquals(ChannelSource.Recent, viewModel.content().source)
        assertTrue(channels.requestedGroups.isEmpty())
    }

    @Test
    fun `view mode and sort order are persisted and the list reloads in the new order`() = runTest {
        val viewModel = viewModel()
        collect(viewModel)
        assertEquals(ChannelViewMode.List, viewModel.content().viewMode)

        viewModel.onViewModeSelected(ChannelViewMode.Grid)
        viewModel.onSortOrderSelected(ChannelSortOrder.Name)

        assertEquals(ChannelViewMode.Grid, viewModel.content().viewMode)
        assertEquals(ChannelSortOrder.Name, viewModel.content().sortOrder)
        assertEquals(ChannelSortOrder.Name, settings.settings.first().browse.sortOrder)
        assertEquals(listOf(ChannelSortOrder.Default, ChannelSortOrder.Name), channels.requestedSorts)
    }

    @Test
    fun `a channel is added to and removed from the favorites`() = runTest {
        val viewModel = viewModel()
        collect(viewModel)

        viewModel.onToggleFavorite(channel(3))
        assertEquals(setOf(channel(3).key), viewModel.content().favoriteKeys)

        viewModel.onToggleFavorite(channel(3))
        assertFalse(channel(3).key in viewModel.content().favoriteKeys)
    }
}
