package com.streambox.player.feature.channels

import androidx.lifecycle.SavedStateHandle
import com.streambox.player.core.model.ChannelGroupSummary
import com.streambox.player.core.model.Playlist
import com.streambox.player.core.model.PlaylistType
import com.streambox.player.core.testing.FakeChannelRepository
import com.streambox.player.core.testing.FakeChannelRepository.Companion.channel
import com.streambox.player.core.testing.FakePlaylistRepository
import com.streambox.player.core.testing.MainDispatcherExtension
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
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

    private fun viewModel(
        playlists: FakePlaylistRepository = FakePlaylistRepository(listOf(playlist)),
        handle: SavedStateHandle = SavedStateHandle(),
    ) = ChannelsViewModel(handle, playlists, channels)

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
}
