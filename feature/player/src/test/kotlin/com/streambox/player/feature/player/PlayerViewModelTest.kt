package com.streambox.player.feature.player

import androidx.lifecycle.SavedStateHandle
import com.streambox.player.core.model.PlaybackError
import com.streambox.player.core.model.ZapDirection
import com.streambox.player.core.testing.FakeChannelRepository
import com.streambox.player.core.testing.FakeChannelRepository.Companion.channel
import com.streambox.player.core.testing.MainDispatcherExtension
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val repository = FakeChannelRepository(
        listOf(
            "News" to channel(1, "News 1"),
            "News" to channel(2, "News 2"),
            "Sports" to channel(3, "Sports 1"),
            "News" to channel(4, "News 3"),
        ),
    )
    private val controller = FakePlaybackController()

    private fun viewModel(channelId: Long = 1, group: String? = null) = PlayerViewModel(
        SavedStateHandle(mapOf("channelId" to channelId, "groupName" to group)),
        repository,
        controller,
    )

    private fun TestScope.collect(viewModel: PlayerViewModel) {
        backgroundScope.launch(mainDispatcher.testDispatcher) { viewModel.uiState.collect {} }
    }

    @Test
    fun `plays the route channel once started`() = runTest {
        val viewModel = viewModel(channelId = 2)
        collect(viewModel)
        assertTrue(controller.played.isEmpty())

        viewModel.onStart()

        assertEquals(listOf(2L), controller.played.map { it.channelId })
        val state = viewModel.uiState.value
        assertEquals("News 2", state.channel?.name)
        assertEquals(2, state.channel?.displayNumber)
        assertTrue(state.isBuffering)
    }

    @Test
    fun `reports playing once the controller plays`() = runTest {
        val viewModel = viewModel()
        collect(viewModel)
        viewModel.onStart()

        controller.startPlaying()

        assertTrue(viewModel.uiState.value.isPlaying)
        assertFalse(viewModel.uiState.value.isBuffering)
    }

    @Test
    fun `zapping stays inside the group and wraps around`() = runTest {
        val viewModel = viewModel(channelId = 4, group = "News")
        collect(viewModel)
        viewModel.onStart()

        viewModel.zap(ZapDirection.NEXT)
        assertEquals(1L, viewModel.uiState.value.channel?.channelId)

        viewModel.zap(ZapDirection.PREVIOUS)
        viewModel.zap(ZapDirection.PREVIOUS)
        assertEquals(2L, viewModel.uiState.value.channel?.channelId)
        assertEquals(listOf(4L, 1L, 4L, 2L), controller.played.map { it.channelId })
    }

    @Test
    fun `zapping without a group walks all channels`() = runTest {
        val viewModel = viewModel(channelId = 2)
        collect(viewModel)
        viewModel.onStart()

        viewModel.zap(ZapDirection.NEXT)

        assertEquals(3L, viewModel.uiState.value.channel?.channelId)
    }

    @Test
    fun `playback error is shown with the overlay and retry asks the controller`() = runTest {
        val viewModel = viewModel()
        collect(viewModel)
        viewModel.onStart()

        controller.fail(PlaybackError.Http(404))
        advanceTimeBy(PlayerViewModel.OVERLAY_TIMEOUT_MS + 1)
        runCurrent()

        val state = viewModel.uiState.value
        assertEquals(PlaybackError.Http(404), state.error)
        assertTrue(state.overlayVisible)
        assertFalse(state.isBuffering)

        viewModel.retry()
        assertEquals(1, controller.retries)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `missing channel reports ChannelNotFound and does not play`() = runTest {
        val viewModel = viewModel(channelId = 99)
        collect(viewModel)
        viewModel.onStart()

        assertEquals(PlaybackError.ChannelNotFound, viewModel.uiState.value.error)
        assertTrue(controller.played.isEmpty())
    }

    @Test
    fun `overlay hides after the timeout and tap toggles it`() = runTest {
        val viewModel = viewModel()
        collect(viewModel)
        viewModel.onStart()
        assertTrue(viewModel.uiState.value.overlayVisible)

        advanceTimeBy(PlayerViewModel.OVERLAY_TIMEOUT_MS + 1)
        runCurrent()
        assertFalse(viewModel.uiState.value.overlayVisible)

        viewModel.toggleOverlay()
        assertTrue(viewModel.uiState.value.overlayVisible)
        viewModel.toggleOverlay()
        assertFalse(viewModel.uiState.value.overlayVisible)
    }

    @Test
    fun `stop releases and start resumes the current channel`() = runTest {
        val viewModel = viewModel(channelId = 3)
        collect(viewModel)
        viewModel.onStart()

        viewModel.onStop()
        assertEquals(1, controller.releases)
        viewModel.onStart()

        assertEquals(listOf(3L, 3L), controller.played.map { it.channelId })
    }

    @Test
    fun `stale controller state from the previous channel is ignored`() = runTest {
        val viewModel = viewModel(channelId = 1)
        collect(viewModel)
        // Not started: the controller still reports another item's error.
        controller.play(requireNotNull(repository.playbackItem(3)))
        controller.fail(PlaybackError.Timeout)

        assertNull(viewModel.uiState.value.error)
    }
}
