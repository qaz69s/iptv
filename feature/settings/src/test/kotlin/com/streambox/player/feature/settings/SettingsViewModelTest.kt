package com.streambox.player.feature.settings

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.streambox.player.core.model.AppSettings
import com.streambox.player.core.model.AspectRatioMode
import com.streambox.player.core.model.BufferProfile
import com.streambox.player.core.model.PlaybackSettings
import com.streambox.player.core.model.SubtitleBackground
import com.streambox.player.core.model.SubtitleColor
import com.streambox.player.core.model.SubtitleSize
import com.streambox.player.core.model.SubtitleStyle
import com.streambox.player.core.model.ThemeMode
import com.streambox.player.core.testing.FakeSettingsRepository
import com.streambox.player.core.testing.MainDispatcherExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class SettingsViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val repository = FakeSettingsRepository()
    private val viewModel = SettingsViewModel(repository)

    @Test
    fun `emits stored settings`() = runTest {
        viewModel.uiState.test {
            assertEquals(AppSettings(), awaitSettings { true })
        }
    }

    @Test
    fun `selecting a theme persists it`() = runTest {
        viewModel.uiState.test {
            awaitSettings { true }
            viewModel.onThemeModeSelected(ThemeMode.Amoled)
            assertEquals(ThemeMode.Amoled, awaitSettings { it.themeMode == ThemeMode.Amoled }.themeMode)
        }
    }

    @Test
    fun `toggling dynamic color persists it`() = runTest {
        viewModel.uiState.test {
            awaitSettings { true }
            viewModel.onDynamicColorChanged(true)
            assertEquals(true, awaitSettings { it.useDynamicColor }.useDynamicColor)
        }
    }

    @Test
    fun `emits default playback settings`() = runTest {
        viewModel.uiState.test {
            assertEquals(PlaybackSettings(), awaitSettings { true }.playback)
        }
    }

    @Test
    fun `selecting a buffer profile persists it`() = runTest {
        viewModel.uiState.test {
            awaitSettings { true }
            viewModel.onBufferProfileSelected(BufferProfile.LowLatency)
            val playback = awaitSettings { it.playback.bufferProfile == BufferProfile.LowLatency }.playback
            assertEquals(PlaybackSettings(bufferProfile = BufferProfile.LowLatency), playback)
        }
    }

    @Test
    fun `background playback is on by default and toggling persists it`() = runTest {
        viewModel.uiState.test {
            assertTrue(awaitSettings { true }.playback.backgroundPlayback)
            viewModel.onBackgroundPlaybackChanged(false)
            assertFalse(awaitSettings { !it.playback.backgroundPlayback }.playback.backgroundPlayback)
            viewModel.onBackgroundPlaybackChanged(true)
            assertTrue(awaitSettings { it.playback.backgroundPlayback }.playback.backgroundPlayback)
        }
    }

    @Test
    fun `picture-in-picture is on by default and toggling persists it`() = runTest {
        viewModel.uiState.test {
            assertTrue(awaitSettings { true }.playback.pictureInPicture)
            viewModel.onPictureInPictureChanged(false)
            assertFalse(awaitSettings { !it.playback.pictureInPicture }.playback.pictureInPicture)
            viewModel.onPictureInPictureChanged(true)
            assertTrue(awaitSettings { it.playback.pictureInPicture }.playback.pictureInPicture)
        }
    }

    @Test
    fun `playback gestures are on by default and toggling persists them`() = runTest {
        viewModel.uiState.test {
            assertTrue(awaitSettings { true }.playback.playbackGestures)
            viewModel.onPlaybackGesturesChanged(false)
            assertFalse(awaitSettings { !it.playback.playbackGestures }.playback.playbackGestures)
            viewModel.onPlaybackGesturesChanged(true)
            assertTrue(awaitSettings { it.playback.playbackGestures }.playback.playbackGestures)
        }
    }

    @Test
    fun `toggling software decoding persists it`() = runTest {
        viewModel.uiState.test {
            awaitSettings { true }
            viewModel.onSoftwareDecodingChanged(true)
            assertTrue(awaitSettings { it.playback.softwareDecodingOnly }.playback.softwareDecodingOnly)
            viewModel.onSoftwareDecodingChanged(false)
            assertFalse(awaitSettings { !it.playback.softwareDecodingOnly }.playback.softwareDecodingOnly)
        }
    }

    @Test
    fun `selecting reconnect attempts persists them and zero disables retrying`() = runTest {
        viewModel.uiState.test {
            awaitSettings { true }
            viewModel.onReconnectAttemptsSelected(10)
            assertEquals(10, awaitSettings { it.playback.maxReconnectAttempts == 10 }.playback.maxReconnectAttempts)
            viewModel.onReconnectAttemptsSelected(0)
            assertEquals(0, awaitSettings { it.playback.maxReconnectAttempts == 0 }.playback.maxReconnectAttempts)
        }
    }

    @Test
    fun `selecting a default aspect ratio persists it`() = runTest {
        viewModel.uiState.test {
            awaitSettings { true }
            viewModel.onDefaultAspectRatioSelected(AspectRatioMode.Ratio4x3)
            val playback = awaitSettings { it.playback.defaultAspectRatio == AspectRatioMode.Ratio4x3 }.playback
            assertEquals(AspectRatioMode.Ratio4x3, playback.defaultAspectRatio)
        }
    }

    @Test
    fun `changing the subtitle style persists all of it`() = runTest {
        val style = SubtitleStyle(SubtitleSize.Large, SubtitleColor.Cyan, SubtitleBackground.None)
        viewModel.uiState.test {
            awaitSettings { true }
            viewModel.onSubtitleStyleChanged(style)
            assertEquals(style, awaitSettings { it.playback.subtitleStyle == style }.playback.subtitleStyle)
        }
    }

    @Test
    fun `toggling resume last channel and auto play next persists them`() = runTest {
        viewModel.uiState.test {
            assertFalse(awaitSettings { true }.playback.resumeLastChannel)
            viewModel.onResumeLastChannelChanged(true)
            assertTrue(awaitSettings { it.playback.resumeLastChannel }.playback.resumeLastChannel)
            viewModel.onAutoPlayNextChanged(true)
            val playback = awaitSettings { it.playback.autoPlayNext }.playback
            assertTrue(playback.resumeLastChannel)
            viewModel.onAutoPlayNextChanged(false)
            assertFalse(awaitSettings { !it.playback.autoPlayNext }.playback.autoPlayNext)
        }
    }

    /** Skips Loading (and any stale states) until a Success matching [predicate] arrives. */
    private suspend fun ReceiveTurbine<SettingsUiState>.awaitSettings(
        predicate: (AppSettings) -> Boolean,
    ): AppSettings {
        while (true) {
            val item = awaitItem()
            if (item is SettingsUiState.Success && predicate(item.settings)) return item.settings
        }
    }
}
