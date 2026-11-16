package com.streambox.player.feature.playlist

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.streambox.player.core.model.ImportFailure
import com.streambox.player.core.model.ImportStatus
import com.streambox.player.core.model.Playlist
import com.streambox.player.core.model.PlaylistType
import com.streambox.player.core.testing.FakePlaylistRepository
import com.streambox.player.core.testing.MainDispatcherExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.RegisterExtension

class ImportProgressViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val playlist = Playlist(7, "Home", PlaylistType.M3U_URL, "https://a/list.m3u")
    private val repository = FakePlaylistRepository(listOf(playlist))
    private val viewModel = ImportProgressViewModel(SavedStateHandle(mapOf("playlistId" to 7L)), repository)

    @Test
    fun `combines playlist and import status`() = runTest {
        viewModel.uiState.test {
            var first = awaitItem()
            // Skip the stateIn placeholder emitted before the repository answers.
            while (first.playlist == null) first = awaitItem()
            assertEquals(ImportProgressUiState(playlist, ImportStatus.Idle), first)

            repository.emitStatus(7, ImportStatus.Running(bytesRead = 10, totalBytes = 100, channels = 3))
            assertEquals(ImportStatus.Running(10, 100, 3), awaitItem().status)

            repository.emitStatus(7, ImportStatus.Failed(ImportFailure.Http(500)))
            assertEquals(ImportStatus.Failed(ImportFailure.Http(500)), awaitItem().status)
        }
    }

    @Test
    fun `retry restarts and cancel cancels this playlist's import`() = runTest {
        viewModel.retry()
        viewModel.cancel()

        assertEquals(listOf(7L), repository.startedImports)
        assertEquals(listOf(7L), repository.cancelledImports)
    }

    @Test
    fun `missing route argument fails fast`() {
        assertThrows<IllegalStateException> { ImportProgressViewModel(SavedStateHandle(), repository) }
    }
}
