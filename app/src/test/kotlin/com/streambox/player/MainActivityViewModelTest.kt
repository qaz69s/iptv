package com.streambox.player

import com.streambox.player.core.model.AppSettings
import com.streambox.player.core.model.OnboardingState
import com.streambox.player.core.model.Playlist
import com.streambox.player.core.model.PlaylistType
import com.streambox.player.core.model.ThemeMode
import com.streambox.player.core.testing.FakePlaylistRepository
import com.streambox.player.core.testing.FakeSettingsRepository
import com.streambox.player.core.testing.MainDispatcherExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class MainActivityViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    @Test
    fun `fresh install starts onboarding`() = runTest {
        val viewModel = MainActivityViewModel(FakeSettingsRepository(), FakePlaylistRepository())

        assertEquals(MainUiState.Ready(AppSettings(), onboardingComplete = false), viewModel.uiState.value)
    }

    @Test
    fun `returning user skips onboarding with saved theme`() = runTest {
        val repository = FakeSettingsRepository(
            initialSettings = AppSettings(themeMode = ThemeMode.Dark),
            initialOnboarding = OnboardingState(welcomeSeen = true, disclaimerAccepted = true),
        )
        val viewModel = MainActivityViewModel(repository, FakePlaylistRepository())

        assertEquals(
            MainUiState.Ready(AppSettings(themeMode = ThemeMode.Dark), onboardingComplete = true),
            viewModel.uiState.value,
        )
    }

    @Test
    fun `hasPlaylists reflects the playlist repository`() = runTest {
        val empty = MainActivityViewModel(FakeSettingsRepository(), FakePlaylistRepository())
        val withOne = MainActivityViewModel(
            FakeSettingsRepository(),
            FakePlaylistRepository(listOf(Playlist(1, "A", PlaylistType.M3U_URL, "https://a/list.m3u"))),
        )

        assertFalse(empty.hasPlaylists())
        assertTrue(withOne.hasPlaylists())
    }
}
