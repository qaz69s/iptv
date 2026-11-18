package com.streambox.player

import com.streambox.player.core.domain.usecase.ResolveLastChannelUseCase
import com.streambox.player.core.model.AppSettings
import com.streambox.player.core.model.LastPlayedChannel
import com.streambox.player.core.model.OnboardingState
import com.streambox.player.core.model.PlaybackSettings
import com.streambox.player.core.model.Playlist
import com.streambox.player.core.model.PlaylistType
import com.streambox.player.core.model.ThemeMode
import com.streambox.player.core.testing.FakeChannelRepository
import com.streambox.player.core.testing.FakeChannelRepository.Companion.channel
import com.streambox.player.core.testing.FakeLastChannelRepository
import com.streambox.player.core.testing.FakePlaylistRepository
import com.streambox.player.core.testing.FakeSettingsRepository
import com.streambox.player.core.testing.MainDispatcherExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class MainActivityViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val channels = FakeChannelRepository(
        listOf("News" to channel(5).copy(playlistId = 2), "News" to channel(6).copy(playlistId = 2)),
    )
    private val lastChannel = FakeLastChannelRepository()

    private fun viewModel(
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        playlists: FakePlaylistRepository = FakePlaylistRepository(),
    ) = MainActivityViewModel(settings, playlists, ResolveLastChannelUseCase(lastChannel, channels))

    private fun resumeOn(enabled: Boolean) =
        FakeSettingsRepository(AppSettings(playback = PlaybackSettings(resumeLastChannel = enabled)))

    @Test
    fun `fresh install starts onboarding`() = runTest {
        val viewModel = viewModel()

        assertEquals(MainUiState.Ready(AppSettings(), onboardingComplete = false), viewModel.uiState.value)
    }

    @Test
    fun `returning user skips onboarding with saved theme`() = runTest {
        val repository = FakeSettingsRepository(
            initialSettings = AppSettings(themeMode = ThemeMode.Dark),
            initialOnboarding = OnboardingState(welcomeSeen = true, disclaimerAccepted = true),
        )
        val viewModel = viewModel(settings = repository)

        assertEquals(
            MainUiState.Ready(AppSettings(themeMode = ThemeMode.Dark), onboardingComplete = true),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `hasPlaylists reflects the playlist repository`() = runTest {
        val empty = viewModel()
        val withOne = viewModel(
            playlists = FakePlaylistRepository(listOf(Playlist(1, "A", PlaylistType.M3U_URL, "https://a/list.m3u"))),
        )

        assertFalse(empty.hasPlaylists())
        assertTrue(withOne.hasPlaylists())
    }

    @Test
    fun `resume opens the last channel when enabled`() = runTest {
        lastChannel.save(LastPlayedChannel(playlistId = 2, uniqueKey = "url:6"))

        assertEquals(6L, viewModel(settings = resumeOn(true)).channelToResume())
    }

    @Test
    fun `resume is off by default and keeps the remembered channel`() = runTest {
        lastChannel.save(LastPlayedChannel(playlistId = 2, uniqueKey = "url:6"))

        assertNull(viewModel().channelToResume())
        assertEquals(LastPlayedChannel(2, "url:6"), lastChannel.lastChannel.value)
    }

    @Test
    fun `resume answers only once per cold start`() = runTest {
        lastChannel.save(LastPlayedChannel(playlistId = 2, uniqueKey = "url:5"))
        val viewModel = viewModel(settings = resumeOn(true))

        assertEquals(5L, viewModel.channelToResume())
        assertNull(viewModel.channelToResume())
    }

    @Test
    fun `nothing remembered means nothing to resume`() = runTest {
        assertNull(viewModel(settings = resumeOn(true)).channelToResume())
        assertEquals(0, lastChannel.clears)
    }

    @Test
    fun `a channel that no longer exists is ignored and forgotten`() = runTest {
        lastChannel.save(LastPlayedChannel(playlistId = 2, uniqueKey = "url:99"))

        assertNull(viewModel(settings = resumeOn(true)).channelToResume())
        assertNull(lastChannel.lastChannel.value)
        assertEquals(1, lastChannel.clears)
    }

    @Test
    fun `a key of another playlist does not match and is forgotten`() = runTest {
        lastChannel.save(LastPlayedChannel(playlistId = 3, uniqueKey = "url:5"))

        assertNull(viewModel(settings = resumeOn(true)).channelToResume())
        assertNull(lastChannel.lastChannel.value)
    }

    @Test
    fun `resume survives a refresh that re-imported the channel under a new id (K18)`() = runTest {
        lastChannel.save(LastPlayedChannel(playlistId = 2, uniqueKey = "url:6"))
        // Refresh: same channel (same uniqueKey), new row id.
        channels.channelsFlow.value = listOf("News" to channel(16).copy(playlistId = 2, uniqueKey = "url:6"))

        assertEquals(16L, viewModel(settings = resumeOn(true)).channelToResume())
        assertEquals(0, lastChannel.clears)
    }
}
