package com.streambox.player.feature.playlist

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.streambox.player.core.model.ImportStatus
import com.streambox.player.core.model.Playlist
import com.streambox.player.core.model.PlaylistType
import com.streambox.player.core.testing.FakePlaylistRepository
import com.streambox.player.core.testing.MainDispatcherExtension
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class PlaylistsViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val a = Playlist(1, "A", PlaylistType.M3U_URL, "https://a/list.m3u")
    private val b = Playlist(2, "B", PlaylistType.M3U_URL, "http://b/list.m3u")

    @Test
    fun `empty repository loads an empty list`() = runTest {
        val viewModel = PlaylistsViewModel(FakePlaylistRepository())

        viewModel.uiState.test {
            assertEquals(PlaylistsUiState.Loaded(emptyList()), awaitLoaded())
        }
    }

    @Test
    fun `rows carry each playlist's import status`() = runTest {
        val repository = FakePlaylistRepository(listOf(a, b))
        val viewModel = PlaylistsViewModel(repository)

        viewModel.uiState.test {
            assertEquals(listOf(ImportStatus.Idle, ImportStatus.Idle), awaitLoaded().rows.map { it.status })

            repository.emitStatus(2, ImportStatus.Running(1, null, 0))
            val rows = awaitLoaded().rows
            assertFalse(rows[0].isImporting)
            assertTrue(rows[1].isImporting)
        }
    }

    @Test
    fun `refresh, delete and cleartext toggle reach the repository`() = runTest {
        val repository = FakePlaylistRepository(listOf(a, b))
        val viewModel = PlaylistsViewModel(repository)

        viewModel.refresh(1)
        viewModel.setAllowCleartext(2, true)
        viewModel.delete(1)

        assertEquals(listOf(1L), repository.startedImports)
        assertEquals(listOf(b.copy(allowCleartext = true)), repository.playlists.first())
    }

    @Test
    fun `move up and down reorder the playlists, enable switch keeps them listed`() = runTest {
        val c = Playlist(3, "C", PlaylistType.M3U_URL, "https://c/list.m3u")
        val repository = FakePlaylistRepository(listOf(a, b, c))
        val viewModel = PlaylistsViewModel(repository)

        viewModel.uiState.test {
            awaitLoaded()
            viewModel.move(3, up = true)
            assertEquals(listOf(1L, 3L, 2L), awaitLoaded().rows.map { it.playlist.id })
            viewModel.move(1, up = false)
            assertEquals(listOf(3L, 1L, 2L), awaitLoaded().rows.map { it.playlist.id })
            viewModel.move(3, up = true) // already first: nothing happens
            viewModel.setEnabled(2, false)
            val rows = awaitLoaded().rows
            assertEquals(listOf(3L, 1L, 2L), rows.map { it.playlist.id })
            assertFalse(rows.last().playlist.enabled)
        }
    }

    private suspend fun ReceiveTurbine<PlaylistsUiState>.awaitLoaded(): PlaylistsUiState.Loaded {
        while (true) {
            val item = awaitItem()
            if (item is PlaylistsUiState.Loaded) return item
        }
    }
}
