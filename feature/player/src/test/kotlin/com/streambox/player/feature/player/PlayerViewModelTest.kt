package com.streambox.player.feature.player

import androidx.lifecycle.SavedStateHandle
import com.streambox.player.core.domain.usecase.RecordPlaybackStartUseCase
import com.streambox.player.core.model.AppSettings
import com.streambox.player.core.model.AspectRatioMode
import com.streambox.player.core.model.PlaybackError
import com.streambox.player.core.model.PlaybackSettings
import com.streambox.player.core.model.ZapDirection
import com.streambox.player.core.player.sleep.MonotonicClock
import com.streambox.player.core.testing.FakeChannelRepository
import com.streambox.player.core.testing.FakeChannelRepository.Companion.channel
import com.streambox.player.core.testing.FakeLastChannelRepository
import com.streambox.player.core.testing.FakeSettingsRepository
import com.streambox.player.core.testing.FakeWatchHistoryRepository
import com.streambox.player.core.testing.MainDispatcherExtension
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.update
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
    private var settings = FakeSettingsRepository()

    private fun viewModel(channelId: Long = 1, group: String? = null) = PlayerViewModel(
        SavedStateHandle(mapOf("channelId" to channelId, "groupName" to group)),
        repository,
        controller,
        settings,
        RecordPlaybackStartUseCase(FakeLastChannelRepository(), FakeWatchHistoryRepository()),
        MonotonicClock { mainDispatcher.testDispatcher.scheduler.currentTime },
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

    @Test
    fun `typed channel number jumps to that channel after the timeout`() = runTest {
        val viewModel = viewModel(channelId = 1)
        collect(viewModel)
        viewModel.onStart()

        viewModel.onDigit(3)
        assertEquals("3", viewModel.uiState.value.numberInput)
        advanceTimeBy(ChannelNumberInput.DEFAULT_TIMEOUT_MS + 1)
        runCurrent()

        assertNull(viewModel.uiState.value.numberInput)
        assertEquals(3L, viewModel.uiState.value.channel?.channelId)
    }

    @Test
    fun `unknown channel number is reported and then cleared`() = runTest {
        val viewModel = viewModel(channelId = 1)
        collect(viewModel)
        viewModel.onStart()

        viewModel.onDigit(9)
        assertTrue(viewModel.commitNumberInput())
        runCurrent()
        assertEquals(9, viewModel.uiState.value.numberNotFound)
        assertEquals(1L, viewModel.uiState.value.channel?.channelId)

        advanceTimeBy(PlayerViewModel.NOT_FOUND_VISIBLE_MS + 1)
        runCurrent()
        assertNull(viewModel.uiState.value.numberNotFound)
        assertFalse(viewModel.commitNumberInput())
    }

    @Test
    fun `back while typing removes digits and is not consumed once the entry is empty`() = runTest {
        val viewModel = viewModel(channelId = 1)
        collect(viewModel)
        viewModel.onStart()
        assertFalse(viewModel.onNumberBackspace())

        viewModel.onDigit(1)
        viewModel.onDigit(2)
        assertTrue(viewModel.onNumberBackspace())
        assertEquals("1", viewModel.uiState.value.numberInput)
        assertTrue(viewModel.onNumberBackspace())
        assertNull(viewModel.uiState.value.numberInput)

        // Nothing left to delete: Back falls through to the normal behaviour, and no jump happens.
        assertFalse(viewModel.onNumberBackspace())
        advanceTimeBy(ChannelNumberInput.DEFAULT_TIMEOUT_MS + 1)
        runCurrent()
        assertEquals(1L, viewModel.uiState.value.channel?.channelId)
    }

    @Test
    fun `left and right change volume on live streams`() = runTest {
        val viewModel = viewModel()
        collect(viewModel)
        viewModel.onStart()

        viewModel.onHorizontalKey(forward = true)
        viewModel.onHorizontalKey(forward = false)

        assertEquals(listOf(true, false), controller.volumeChanges)
        assertTrue(controller.seeks.isEmpty())
    }

    @Test
    fun `cycling the aspect ratio persists the next mode`() = runTest {
        val viewModel = viewModel()
        collect(viewModel)

        viewModel.cycleAspectRatio()
        runCurrent()

        assertEquals(AspectRatioMode.Ratio16x9, viewModel.uiState.value.aspectRatio)
        assertEquals(AspectRatioMode.Ratio16x9, controller.appliedSettings.last().defaultAspectRatio)
    }

    @Test
    fun `menu toggles the panel`() = runTest {
        val viewModel = viewModel()
        collect(viewModel)

        viewModel.togglePanel()
        assertTrue(viewModel.uiState.value.panelOpen)
        viewModel.togglePanel()
        assertFalse(viewModel.uiState.value.panelOpen)
    }

    // --- Background playback ---------------------------------------------------------------------

    @Test
    fun `app going to the background keeps playing audio-only when background playback is on`() = runTest {
        val viewModel = viewModel(channelId = 2)
        collect(viewModel)
        viewModel.onStart()
        controller.startPlaying()

        viewModel.onStop(appInBackground = true)

        assertEquals(0, controller.releases)
        assertEquals(listOf(true), controller.backgroundModes)
        assertTrue(viewModel.uiState.value.backgroundPlayback)

        // Back to the foreground: picture again, no restart of the stream.
        viewModel.onStart()
        assertEquals(listOf(true, false), controller.backgroundModes)
        assertEquals(listOf(2L), controller.played.map { it.channelId })
    }

    @Test
    fun `app going to the background releases when background playback is off`() = runTest {
        settings = FakeSettingsRepository(AppSettings(playback = PlaybackSettings(backgroundPlayback = false)))
        val viewModel = viewModel(channelId = 2)
        collect(viewModel)
        viewModel.onStart()
        controller.startPlaying()
        assertFalse(viewModel.uiState.value.backgroundPlayback)

        viewModel.onStop(appInBackground = true)

        assertEquals(1, controller.releases)
        assertTrue(controller.backgroundModes.isEmpty())
        viewModel.onStart()
        assertEquals(listOf(2L, 2L), controller.played.map { it.channelId })
    }

    @Test
    fun `turning background playback off in settings applies to the next stop`() = runTest {
        val viewModel = viewModel()
        collect(viewModel)
        viewModel.onStart()

        settings.setBackgroundPlayback(false)
        runCurrent()
        viewModel.onStop(appInBackground = true)

        assertEquals(1, controller.releases)
        assertTrue(controller.backgroundModes.isEmpty())
    }

    @Test
    fun `leaving the screen inside the app releases even with background playback on`() = runTest {
        val viewModel = viewModel()
        collect(viewModel)
        viewModel.onStart()
        controller.startPlaying()

        viewModel.onStop(appInBackground = false)

        assertEquals(1, controller.releases)
        assertTrue(controller.backgroundModes.isEmpty())
    }

    @Test
    fun `a failed stream is released instead of kept in the background`() = runTest {
        val viewModel = viewModel()
        collect(viewModel)
        viewModel.onStart()
        controller.fail(PlaybackError.Http(404))

        viewModel.onStop(appInBackground = true)

        assertEquals(1, controller.releases)
        assertTrue(controller.backgroundModes.isEmpty())
    }

    @Test
    fun `notification next and previous zap while in the background`() = runTest {
        val viewModel = viewModel(channelId = 1, group = "News")
        collect(viewModel)
        viewModel.onStart()
        controller.startPlaying()
        viewModel.onStop(appInBackground = true)

        val navigator = requireNotNull(controller.channelNavigator)
        navigator.zap(ZapDirection.NEXT)
        assertEquals(2L, viewModel.uiState.value.channel?.channelId)
        navigator.zap(ZapDirection.PREVIOUS)
        navigator.zap(ZapDirection.PREVIOUS)

        assertEquals(listOf(1L, 2L, 1L, 4L), controller.played.map { it.channelId })
        assertEquals(0, controller.releases)

        // Returning shows the channel picked in the background without restarting it.
        viewModel.onStart()
        assertEquals(4L, viewModel.uiState.value.channel?.channelId)
        assertEquals(4, controller.played.size)
    }

    // --- Picture-in-picture ----------------------------------------------------------------------

    private fun TestScope.playingInPip(channelId: Long = 1, group: String? = "News"): PlayerViewModel {
        val viewModel = viewModel(channelId = channelId, group = group)
        collect(viewModel)
        viewModel.onStart()
        controller.startPlaying()
        viewModel.onPictureInPictureModeChanged(inPip = true, stopped = false)
        return viewModel
    }

    @Test
    fun `entering picture-in-picture closes the panel, number entry and overlay`() = runTest {
        val viewModel = viewModel()
        collect(viewModel)
        viewModel.onStart()
        viewModel.openPanel()
        viewModel.onDigit(3)
        assertEquals("3", viewModel.uiState.value.numberInput)

        viewModel.onPictureInPictureModeChanged(inPip = true, stopped = false)

        val state = viewModel.uiState.value
        assertTrue(state.pictureInPicture)
        assertFalse(state.panelOpen)
        assertFalse(state.overlayVisible)
        assertNull(state.numberInput)
        // The cancelled number never jumps.
        advanceTimeBy(PlayerViewModel.OVERLAY_TIMEOUT_MS + 1)
        runCurrent()
        assertEquals(1L, viewModel.uiState.value.channel?.channelId)
        assertFalse(viewModel.uiState.value.overlayVisible)
    }

    @Test
    fun `stopping while in picture-in-picture neither releases nor goes to the background`() = runTest {
        val viewModel = playingInPip()

        viewModel.onStop(appInBackground = true, inPictureInPicture = true)

        assertEquals(0, controller.releases)
        assertTrue(controller.backgroundModes.isEmpty())
        viewModel.onStart()
        assertEquals(listOf(1L), controller.played.map { it.channelId })
        assertEquals(0, controller.releases)
    }

    @Test
    fun `closing the window keeps playing in the background when background playback is on`() = runTest {
        val viewModel = playingInPip()
        viewModel.onStop(appInBackground = true, inPictureInPicture = true)

        viewModel.onPictureInPictureModeChanged(inPip = false, stopped = true)

        assertEquals(listOf(true), controller.backgroundModes)
        assertEquals(0, controller.releases)
        assertFalse(viewModel.uiState.value.pictureInPicture)
        viewModel.onStart()
        assertEquals(listOf(true, false), controller.backgroundModes)
        assertEquals(listOf(1L), controller.played.map { it.channelId })
    }

    @Test
    fun `closing the window releases once when background playback is off`() = runTest {
        settings = FakeSettingsRepository(AppSettings(playback = PlaybackSettings(backgroundPlayback = false)))
        val viewModel = playingInPip()
        viewModel.onStop(appInBackground = true, inPictureInPicture = true)

        viewModel.onPictureInPictureModeChanged(inPip = false, stopped = true)

        assertEquals(1, controller.releases)
        assertTrue(controller.backgroundModes.isEmpty())
        // Zapping afterwards does not start playback until the screen is shown again.
        viewModel.zap(ZapDirection.NEXT)
        assertEquals(listOf(1L), controller.played.map { it.channelId })
    }

    @Test
    fun `expanding back to full screen only shows the overlay`() = runTest {
        val viewModel = playingInPip()

        viewModel.onPictureInPictureModeChanged(inPip = false, stopped = false)

        val state = viewModel.uiState.value
        assertFalse(state.pictureInPicture)
        assertTrue(state.overlayVisible)
        assertEquals(0, controller.releases)
        assertTrue(controller.backgroundModes.isEmpty())
        assertEquals(0, controller.playPauseToggles)
        assertEquals(listOf(1L), controller.played.map { it.channelId })
    }

    @Test
    fun `window buttons zap and toggle play pause`() = runTest {
        val viewModel = playingInPip(channelId = 1, group = "News")

        viewModel.onPipAction(PipAction.NEXT)
        assertEquals(2L, viewModel.uiState.value.channel?.channelId)
        viewModel.onPipAction(PipAction.PREVIOUS)
        viewModel.onPipAction(PipAction.PREVIOUS)
        assertEquals(4L, viewModel.uiState.value.channel?.channelId)
        viewModel.onPipAction(PipAction.PLAY_PAUSE)

        assertEquals(1, controller.playPauseToggles)
        assertEquals(listOf(1L, 2L, 1L, 4L), controller.played.map { it.channelId })
        assertFalse(viewModel.uiState.value.overlayVisible)
    }

    @Test
    fun `zapping after the screen went off in picture-in-picture still plays`() = runTest {
        val viewModel = playingInPip(channelId = 1, group = "News")
        viewModel.onStop(appInBackground = true, inPictureInPicture = true)

        viewModel.onPipAction(PipAction.NEXT)

        assertEquals(listOf(1L, 2L), controller.played.map { it.channelId })
        assertEquals(0, controller.releases)
    }

    @Test
    fun `picture-in-picture setting follows the settings`() = runTest {
        val viewModel = viewModel()
        collect(viewModel)
        assertTrue(viewModel.uiState.value.pictureInPictureEnabled)

        settings.setPictureInPicture(false)
        runCurrent()

        assertFalse(viewModel.uiState.value.pictureInPictureEnabled)
    }

    private fun TestScope.collectPipRequests(viewModel: PlayerViewModel): List<Unit> {
        val requests = mutableListOf<Unit>()
        backgroundScope.launch(mainDispatcher.testDispatcher) {
            viewModel.pictureInPictureRequests.collect { requests += it }
        }
        return requests
    }

    @Test
    fun `panel picture-in-picture action closes the panel and requests PiP once`() = runTest {
        val viewModel = viewModel()
        collect(viewModel)
        val requests = collectPipRequests(viewModel)
        viewModel.onStart()
        viewModel.openPanel()

        viewModel.requestPictureInPicture()
        runCurrent()

        assertFalse(viewModel.uiState.value.panelOpen)
        assertEquals(1, requests.size)
    }

    @Test
    fun `picture-in-picture request is ignored when disabled or already in PiP`() = runTest {
        val viewModel = viewModel()
        collect(viewModel)
        val requests = collectPipRequests(viewModel)
        viewModel.onStart()

        settings.setPictureInPicture(false)
        runCurrent()
        viewModel.openPanel()
        viewModel.requestPictureInPicture()
        runCurrent()
        assertTrue(viewModel.uiState.value.panelOpen)
        assertTrue(requests.isEmpty())

        settings.setPictureInPicture(true)
        runCurrent()
        viewModel.onPictureInPictureModeChanged(inPip = true, stopped = false)
        viewModel.requestPictureInPicture()
        runCurrent()
        assertTrue(requests.isEmpty())
    }

    // --- Touch gestures ---------------------------------------------------------------------------

    private fun FakePlaybackController.seekable(seekable: Boolean) = state.update { it.copy(isSeekable = seekable) }

    @Test
    fun `gesture setting follows the settings`() = runTest {
        val viewModel = viewModel()
        collect(viewModel)
        assertTrue(viewModel.uiState.value.gesturesEnabled)

        settings.setPlaybackGestures(false)
        runCurrent()

        assertFalse(viewModel.uiState.value.gesturesEnabled)
    }

    @Test
    fun `seek swipe seeks by the offset on seekable content`() = runTest {
        val viewModel = viewModel()
        collect(viewModel)
        viewModel.onStart()
        controller.seekable(true)

        assertTrue(viewModel.seekByGesture(25_000L))
        assertTrue(viewModel.seekByGesture(-5_000L))
        assertFalse(viewModel.seekByGesture(0L))

        assertEquals(listOf(25_000L, -5_000L), controller.seeks)
    }

    @Test
    fun `seek swipe is ignored on live content, in PiP and with gestures off`() = runTest {
        val viewModel = viewModel()
        collect(viewModel)
        viewModel.onStart()

        controller.seekable(false)
        assertFalse(viewModel.seekByGesture(10_000L))

        controller.seekable(true)
        settings.setPlaybackGestures(false)
        runCurrent()
        assertFalse(viewModel.seekByGesture(10_000L))

        settings.setPlaybackGestures(true)
        runCurrent()
        viewModel.onPictureInPictureModeChanged(inPip = true, stopped = false)
        assertFalse(viewModel.seekByGesture(10_000L))

        assertTrue(controller.seeks.isEmpty())
    }

    @Test
    fun `double-tap sides skip 10 s and the middle toggles play pause`() = runTest {
        val viewModel = viewModel()
        collect(viewModel)
        viewModel.onStart()
        controller.seekable(true)

        assertEquals(DoubleTapAction.REWIND, viewModel.onDoubleTap(TouchZone.LEFT))
        assertEquals(DoubleTapAction.FORWARD, viewModel.onDoubleTap(TouchZone.RIGHT))
        assertEquals(DoubleTapAction.PLAY_PAUSE, viewModel.onDoubleTap(TouchZone.CENTER))

        assertEquals(listOf(-PlayerViewModel.SEEK_STEP_MS, PlayerViewModel.SEEK_STEP_MS), controller.seeks)
        assertEquals(1, controller.playPauseToggles)
    }

    @Test
    fun `double-tap sides are ignored on live content but the middle still pauses`() = runTest {
        val viewModel = viewModel()
        collect(viewModel)
        viewModel.onStart()
        controller.seekable(false)

        assertNull(viewModel.onDoubleTap(TouchZone.LEFT))
        assertNull(viewModel.onDoubleTap(TouchZone.RIGHT))
        assertEquals(DoubleTapAction.PLAY_PAUSE, viewModel.onDoubleTap(TouchZone.CENTER))

        assertTrue(controller.seeks.isEmpty())
        assertEquals(1, controller.playPauseToggles)
    }

    @Test
    fun `double-tap does nothing in PiP or with gestures off`() = runTest {
        val viewModel = viewModel()
        collect(viewModel)
        viewModel.onStart()
        controller.seekable(true)

        settings.setPlaybackGestures(false)
        runCurrent()
        assertNull(viewModel.onDoubleTap(TouchZone.CENTER))

        settings.setPlaybackGestures(true)
        runCurrent()
        viewModel.onPictureInPictureModeChanged(inPip = true, stopped = false)
        assertNull(viewModel.onDoubleTap(TouchZone.RIGHT))

        assertTrue(controller.seeks.isEmpty())
        assertEquals(0, controller.playPauseToggles)
    }
}
