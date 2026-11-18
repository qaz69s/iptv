package com.streambox.player.feature.player

import androidx.lifecycle.SavedStateHandle
import com.streambox.player.core.model.AppSettings
import com.streambox.player.core.model.LastPlayedChannel
import com.streambox.player.core.model.PlaybackSettings
import com.streambox.player.core.model.ZapDirection
import com.streambox.player.core.player.sleep.MonotonicClock
import com.streambox.player.core.player.sleep.SleepTimer
import com.streambox.player.core.testing.FakeChannelRepository
import com.streambox.player.core.testing.FakeChannelRepository.Companion.channel
import com.streambox.player.core.testing.FakeLastChannelRepository
import com.streambox.player.core.testing.FakeSettingsRepository
import com.streambox.player.core.testing.MainDispatcherExtension
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/** P2c: sleep timer, remembering the last channel, auto play next. */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelSleepAndNextTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val repository = FakeChannelRepository(
        listOf(
            "Movies" to channel(1, "Movie 1").copy(playlistId = 7),
            "Movies" to channel(2, "Movie 2").copy(playlistId = 7),
            "News" to channel(3, "News 1").copy(playlistId = 7),
            "Movies" to channel(4, "Movie 3").copy(playlistId = 8),
        ),
    )
    private val controller = FakePlaybackController()
    private val lastChannel = FakeLastChannelRepository()
    private var settings = FakeSettingsRepository()

    private fun autoNext(enabled: Boolean) {
        settings = FakeSettingsRepository(AppSettings(playback = PlaybackSettings(autoPlayNext = enabled)))
    }

    private fun viewModel(channelId: Long = 1, group: String? = null) = PlayerViewModel(
        SavedStateHandle(mapOf("channelId" to channelId, "groupName" to group)),
        repository,
        controller,
        settings,
        lastChannel,
        MonotonicClock { mainDispatcher.testDispatcher.scheduler.currentTime },
    )

    private fun TestScope.started(channelId: Long = 1, group: String? = null): PlayerViewModel {
        val viewModel = viewModel(channelId, group)
        backgroundScope.launch(mainDispatcher.testDispatcher) { viewModel.uiState.collect {} }
        viewModel.onStart()
        return viewModel
    }

    // --- Sleep timer ----------------------------------------------------------------------------

    @Test
    fun `sleep timer shows the remaining time and pauses at the end with a fade`() = runTest {
        val viewModel = started()
        controller.startPlaying()

        viewModel.setSleepTimer(15)
        assertEquals(15 * 60_000L, viewModel.uiState.value.sleepTimer?.remainingMs)

        advanceTimeBy(5 * 60_000L)
        runCurrent()
        assertEquals(10 * 60_000L, viewModel.uiState.value.sleepTimer?.remainingMs)
        assertTrue(controller.volumes.isEmpty())

        advanceTimeBy(10 * 60_000L - 5_000)
        runCurrent()
        assertEquals(0.5f, controller.volume, 0.001f)
        assertEquals(0, controller.pauses)

        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(1, controller.pauses)
        assertEquals(1f, controller.volume)
        assertNull(viewModel.uiState.value.sleepTimer)
        assertTrue(!viewModel.uiState.value.isPlaying)
    }

    @Test
    fun `turning the sleep timer off cancels it and restores the volume`() = runTest {
        val viewModel = started()
        viewModel.setSleepTimer(15)
        advanceTimeBy(15 * 60_000L - 3_000)
        runCurrent()
        assertTrue(controller.volume < 0.5f)

        viewModel.setSleepTimer(null)

        assertNull(viewModel.uiState.value.sleepTimer)
        assertEquals(1f, controller.volume)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(0, controller.pauses)
    }

    @Test
    fun `choosing another duration restarts the timer`() = runTest {
        val viewModel = started()
        viewModel.setSleepTimer(30)
        advanceTimeBy(20 * 60_000L)
        runCurrent()

        viewModel.setSleepTimer(SleepTimer.MAX_MINUTES)

        assertEquals(SleepTimer.MAX_MINUTES, viewModel.uiState.value.sleepTimer?.durationMinutes)
        advanceTimeBy(11 * 60_000L)
        runCurrent()
        assertEquals(0, controller.pauses)
        assertNotNull(viewModel.uiState.value.sleepTimer)
    }

    @Test
    fun `sleep timer keeps running in the background and in picture-in-picture`() = runTest {
        val viewModel = started()
        controller.startPlaying()
        viewModel.setSleepTimer(15)

        viewModel.onPictureInPictureModeChanged(inPip = true, stopped = false)
        viewModel.onStop(appInBackground = true, inPictureInPicture = true)
        advanceTimeBy(5 * 60_000L)
        runCurrent()
        assertEquals(10 * 60_000L, viewModel.uiState.value.sleepTimer?.remainingMs)

        viewModel.onPictureInPictureModeChanged(inPip = false, stopped = true)
        assertEquals(listOf(true), controller.backgroundModes)
        advanceTimeBy(10 * 60_000L)
        runCurrent()
        assertEquals(1, controller.pauses)
        assertEquals(0, controller.releases)
    }

    @Test
    fun `zero minutes means off`() = runTest {
        val viewModel = started()
        viewModel.setSleepTimer(15)

        viewModel.setSleepTimer(0)

        assertNull(viewModel.uiState.value.sleepTimer)
    }

    // --- Last channel -----------------------------------------------------------------------------

    @Test
    fun `a channel is remembered once it starts playing, not while buffering`() = runTest {
        started(channelId = 4)
        assertTrue(lastChannel.saves.isEmpty())

        controller.startPlaying()
        assertEquals(listOf(LastPlayedChannel(playlistId = 8, channelId = 4)), lastChannel.saves)

        // Pause / resume of the same channel does not write again.
        controller.state.update { it.copy(isPlaying = false) }
        controller.startPlaying()
        assertEquals(1, lastChannel.saves.size)
    }

    @Test
    fun `zapping remembers each channel that starts playing`() = runTest {
        val viewModel = started(channelId = 1, group = "Movies")
        controller.startPlaying()

        viewModel.zap(ZapDirection.NEXT)
        controller.startPlaying()

        assertEquals(
            listOf(LastPlayedChannel(7, 1), LastPlayedChannel(7, 2)),
            lastChannel.saves,
        )
    }

    @Test
    fun `a failed channel is not remembered`() = runTest {
        started(channelId = 2)
        controller.fail(com.streambox.player.core.model.PlaybackError.Http(404))

        assertTrue(lastChannel.saves.isEmpty())
    }

    // --- Auto play next ---------------------------------------------------------------------------

    @Test
    fun `finished on-demand content plays the next channel of the group when enabled`() = runTest {
        autoNext(true)
        val viewModel = started(channelId = 2, group = "Movies")
        controller.startPlaying()

        controller.end()

        assertEquals(4L, viewModel.uiState.value.channel?.channelId)
        assertEquals(listOf(2L, 4L), controller.played.map { it.channelId })
    }

    @Test
    fun `without a route group the channel's own group is used`() = runTest {
        autoNext(true)
        val viewModel = started(channelId = 2, group = null)

        controller.end()

        // Channel 3 (News) is next overall, but auto play stays in "Movies".
        assertEquals(4L, viewModel.uiState.value.channel?.channelId)
    }

    @Test
    fun `nothing happens when auto play next is off`() = runTest {
        autoNext(false)
        val viewModel = started(channelId = 1, group = "Movies")

        controller.end()

        assertEquals(1L, viewModel.uiState.value.channel?.channelId)
        assertEquals(listOf(1L), controller.played.map { it.channelId })
    }

    @Test
    fun `the last channel of the group does not wrap around`() = runTest {
        autoNext(true)
        val viewModel = started(channelId = 4, group = "Movies")

        controller.end()

        assertEquals(4L, viewModel.uiState.value.channel?.channelId)
        assertEquals(listOf(4L), controller.played.map { it.channelId })
    }

    @Test
    fun `a live stream ending is left to the reconnect logic`() = runTest {
        autoNext(true)
        val viewModel = started(channelId = 1, group = "Movies")
        controller.startPlaying()

        // The controller reconnects a live stream that ended; it never reports ended = true.
        controller.state.update { it.copy(isPlaying = false, isBuffering = true) }

        assertEquals(1L, viewModel.uiState.value.channel?.channelId)
        assertEquals(listOf(1L), controller.played.map { it.channelId })
    }

    @Test
    fun `an ended flag from the previous channel does not skip again`() = runTest {
        autoNext(true)
        val viewModel = started(channelId = 1, group = "Movies")

        controller.end()
        assertEquals(2L, viewModel.uiState.value.channel?.channelId)

        // The new channel buffers (ended reset by play); only its own end moves on.
        assertEquals(listOf(1L, 2L), controller.played.map { it.channelId })
        controller.end()
        assertEquals(4L, viewModel.uiState.value.channel?.channelId)
    }

    @Test
    fun `auto play next also works during background playback`() = runTest {
        autoNext(true)
        val viewModel = started(channelId = 1, group = "Movies")
        controller.startPlaying()
        viewModel.onStop(appInBackground = true)

        controller.end()

        assertEquals(listOf(1L, 2L), controller.played.map { it.channelId })
    }
}
