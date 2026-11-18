package com.streambox.player.feature.player

import androidx.lifecycle.SavedStateHandle
import com.streambox.player.core.domain.usecase.RecordPlaybackStartUseCase
import com.streambox.player.core.domain.usecase.ToggleFavoriteUseCase
import com.streambox.player.core.model.ZapDirection
import com.streambox.player.core.player.StreamInfo
import com.streambox.player.core.player.audio.AudioStatus
import com.streambox.player.core.player.audio.MediaFormatInfo
import com.streambox.player.core.player.sleep.MonotonicClock
import com.streambox.player.core.testing.FakeChannelRepository
import com.streambox.player.core.testing.FakeChannelRepository.Companion.channel
import com.streambox.player.core.testing.FakeFavoritesRepository
import com.streambox.player.core.testing.FakeLastChannelRepository
import com.streambox.player.core.testing.FakeSettingsRepository
import com.streambox.player.core.testing.FakeWatchHistoryRepository
import com.streambox.player.core.testing.MainDispatcherExtension
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/** K26: "some channels show video but have no audio" is surfaced instead of staying silent. */
class PlayerViewModelAudioTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val repository = FakeChannelRepository(
        listOf("News" to channel(1, "News 1"), "News" to channel(2, "News 2")),
    )
    private val controller = FakePlaybackController()
    private val favorites = FakeFavoritesRepository()
    private val ac3 = MediaFormatInfo("audio/ac3", channelCount = 6, sampleRate = 48_000)

    private fun viewModel() = PlayerViewModel(
        SavedStateHandle(mapOf("channelId" to 1L, "groupName" to "News")),
        repository,
        controller,
        FakeSettingsRepository(),
        RecordPlaybackStartUseCase(FakeLastChannelRepository(), FakeWatchHistoryRepository()),
        favorites,
        ToggleFavoriteUseCase(favorites),
        FakeWatchHistoryRepository(),
        MonotonicClock { mainDispatcher.testDispatcher.scheduler.currentTime },
    )

    private fun TestScope.collect(viewModel: PlayerViewModel) {
        backgroundScope.launch(mainDispatcher.testDispatcher) { viewModel.uiState.collect {} }
    }

    @Test
    fun `audio no renderer can decode is a non-fatal notice while the video keeps playing`() = runTest {
        val viewModel = viewModel()
        collect(viewModel)
        viewModel.onStart()
        controller.startPlaying()

        controller.state.update {
            it.copy(streamInfo = StreamInfo(audioStatus = AudioStatus.Unsupported(listOf(ac3))))
        }

        val state = viewModel.uiState.value
        assertEquals(AudioStatus.Unsupported(listOf(ac3)), state.unsupportedAudio)
        assertNull(state.error)
        assertTrue(state.isPlaying)
    }

    @Test
    fun `the stream info shows the codecs and decoders of the playing channel`() = runTest {
        val viewModel = viewModel()
        collect(viewModel)
        viewModel.onStart()
        val info = StreamInfo(audio = ac3, audioDecoder = "ffmpegLib", audioStatus = AudioStatus.Playable)

        controller.state.update { it.copy(streamInfo = info) }

        assertEquals(info, viewModel.uiState.value.streamInfo)
        assertNull(viewModel.uiState.value.unsupportedAudio)
    }

    @Test
    fun `the notice of the previous channel is not shown after zapping`() = runTest {
        val viewModel = viewModel()
        collect(viewModel)
        viewModel.onStart()
        controller.state.update {
            it.copy(streamInfo = StreamInfo(audioStatus = AudioStatus.Unsupported(listOf(ac3))))
        }

        viewModel.zap(ZapDirection.NEXT)

        assertEquals(2L, viewModel.uiState.value.channel?.channelId)
        assertNull(viewModel.uiState.value.unsupportedAudio)
        assertEquals(StreamInfo(), viewModel.uiState.value.streamInfo)
    }
}
