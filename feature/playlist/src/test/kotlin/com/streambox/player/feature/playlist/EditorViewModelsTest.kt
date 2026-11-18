package com.streambox.player.feature.playlist

import androidx.lifecycle.SavedStateHandle
import com.streambox.player.core.model.ChannelKey
import com.streambox.player.core.model.ChannelSortOrder
import com.streambox.player.core.model.EditorChannel
import com.streambox.player.core.model.EditorGroup
import com.streambox.player.core.model.Playlist
import com.streambox.player.core.model.PlaylistType
import com.streambox.player.core.testing.FakeChannelOverridesRepository
import com.streambox.player.core.testing.FakeChannelRepository.Companion.channel
import com.streambox.player.core.testing.FakeCustomGroupsRepository
import com.streambox.player.core.testing.FakePlaylistRepository
import com.streambox.player.core.testing.FakeSettingsRepository
import com.streambox.player.core.testing.MainDispatcherExtension
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/** P3c list editors: hide / rename / move, auto-switch to the custom sort, custom groups. */
class EditorViewModelsTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val playlists = FakePlaylistRepository(listOf(Playlist(1, "Home", PlaylistType.M3U_URL, "https://a")))
    private val overrides = FakeChannelOverridesRepository().apply {
        editorGroupsFlow.value = listOf(EditorGroup(1, "News", channelCount = 3), EditorGroup(1, "Sport"))
        editorChannelsFlow.value = mapOf(
            "News" to listOf("n1", "n2", "n3").map { EditorChannel(ChannelKey(1, it), "Channel $it") },
        )
    }
    private val settings = FakeSettingsRepository()

    private fun TestScope.collect(flow: Flow<*>) {
        backgroundScope.launch(mainDispatcher.testDispatcher) { flow.collect {} }
    }

    private fun groupEditor() = GroupEditorViewModel(
        SavedStateHandle(mapOf("playlistId" to 1L, "groupName" to "News")),
        overrides,
        settings,
    )

    @Test
    fun `the playlist editor lists the groups and hides, renames and moves them`() = runTest {
        val viewModel = PlaylistEditorViewModel(SavedStateHandle(mapOf("playlistId" to 1L)), playlists, overrides)
        collect(viewModel.uiState)
        val news = (viewModel.uiState.value as PlaylistEditorUiState.Loaded).groups.first()

        viewModel.setHidden(news, true)
        viewModel.rename(news, "Nachrichten")
        viewModel.move(news, up = false)

        val loaded = viewModel.uiState.value as PlaylistEditorUiState.Loaded
        assertEquals("Home", loaded.playlistName)
        assertEquals(listOf("Sport", "News"), loaded.groups.map { it.name })
        val moved = loaded.groups.last()
        assertTrue(moved.hidden)
        assertEquals("Nachrichten", moved.displayName)
        assertEquals(ChannelSortOrder.Default, settings.settings.first().browse.sortOrder)
    }

    @Test
    fun `a deleted playlist shows as missing`() = runTest {
        playlists.deletePlaylist(1)
        val viewModel = PlaylistEditorViewModel(SavedStateHandle(mapOf("playlistId" to 1L)), playlists, overrides)
        collect(viewModel.uiState)

        assertEquals(PlaylistEditorUiState.Missing, viewModel.uiState.value)
    }

    @Test
    fun `moving a channel reorders it and switches browsing to the custom sort`() = runTest {
        val viewModel = groupEditor()
        collect(viewModel.uiState)
        val n3 = (viewModel.uiState.value as GroupEditorUiState.Loaded).channels.last()

        viewModel.move(n3, up = true)

        val keys = (viewModel.uiState.value as GroupEditorUiState.Loaded).channels.map { it.key.uniqueKey }
        assertEquals(listOf("n1", "n3", "n2"), keys)
        assertEquals(ChannelSortOrder.Custom, settings.settings.first().browse.sortOrder)
    }

    @Test
    fun `hidden channels stay listed, and the group switch tells them apart`() = runTest {
        val viewModel = groupEditor()
        collect(viewModel.uiState)
        val channels = { (viewModel.uiState.value as GroupEditorUiState.Loaded).channels }

        viewModel.setHidden(channels()[1], true)
        assertEquals(listOf(false, true, false), channels().map { it.hidden })

        viewModel.setGroupHidden(true)
        assertEquals(listOf(true, true, true), channels().map { it.hidden })
        assertEquals(listOf(true, false, true), channels().map { it.hiddenByGroup })
        assertTrue((viewModel.uiState.value as GroupEditorUiState.Loaded).group!!.hidden)

        viewModel.rename(channels()[0], "Mine")
        assertEquals("Mine", channels()[0].displayName)
    }

    @Test
    fun `custom groups are created, renamed, moved and deleted`() = runTest {
        val repository = FakeCustomGroupsRepository()
        val viewModel = CustomGroupsViewModel(repository)
        collect(viewModel.uiState)

        viewModel.create("Kids")
        viewModel.create("  ")
        viewModel.create("Movies")
        val (kids, movies) = viewModel.uiState.value.groups!!
        viewModel.move(movies, up = true)
        viewModel.rename(kids, "Children")
        assertEquals(listOf("Movies", "Children"), viewModel.uiState.value.groups!!.map { it.name })

        viewModel.delete(movies)
        assertEquals(listOf("Children"), viewModel.uiState.value.groups!!.map { it.name })
    }

    @Test
    fun `a custom group's channels move, switch to the custom sort and can be removed`() = runTest {
        val repository = FakeCustomGroupsRepository(listOf(channel(1), channel(2), channel(3)))
        val id = checkNotNull(repository.create("Mix"))
        listOf(1L, 2L, 3L).forEach { repository.addChannel(id, channel(it).key) }
        val viewModel = CustomGroupEditorViewModel(SavedStateHandle(mapOf("groupId" to id)), repository, settings)
        collect(viewModel.uiState)

        viewModel.move(channel(3), up = true)
        assertEquals(listOf(1L, 3L, 2L), viewModel.uiState.value.channels.map { it.id })
        assertEquals(ChannelSortOrder.Custom, settings.settings.first().browse.sortOrder)

        viewModel.remove(channel(1))
        assertEquals(listOf(3L, 2L), viewModel.uiState.value.channels.map { it.id })
        assertEquals("Mix", viewModel.uiState.value.group?.name)
        assertFalse(viewModel.uiState.value.channels.isEmpty())
    }
}
