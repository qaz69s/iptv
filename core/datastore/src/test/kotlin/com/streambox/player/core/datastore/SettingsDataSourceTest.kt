package com.streambox.player.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.streambox.player.core.model.AspectRatioMode
import com.streambox.player.core.model.BufferProfile
import com.streambox.player.core.model.LastPlayedChannel
import com.streambox.player.core.model.OnboardingState
import com.streambox.player.core.model.PlaybackSettings
import com.streambox.player.core.model.SubtitleBackground
import com.streambox.player.core.model.SubtitleColor
import com.streambox.player.core.model.SubtitleSize
import com.streambox.player.core.model.SubtitleStyle
import com.streambox.player.core.model.ThemeMode
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class SettingsDataSourceTest {
    @TempDir
    lateinit var tempDir: File

    private fun TestScope.createDataStore(): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = backgroundScope,
        produceFile = { File(tempDir, "test.preferences_pb") },
    )

    private fun TestScope.createDataSource() = SettingsDataSource(createDataStore())

    @Test
    fun `fresh install has defaults and incomplete onboarding`() = runTest(UnconfinedTestDispatcher()) {
        val source = createDataSource()

        assertEquals(ThemeMode.System, source.settings.first().themeMode)
        assertFalse(source.settings.first().useDynamicColor)
        assertEquals(OnboardingState(), source.onboardingState.first())
        assertFalse(source.onboardingState.first().isComplete)
    }

    @Test
    fun `persists theme mode and dynamic color`() = runTest(UnconfinedTestDispatcher()) {
        val source = createDataSource()

        source.setThemeMode(ThemeMode.Amoled)
        source.setDynamicColor(true)

        assertEquals(ThemeMode.Amoled, source.settings.first().themeMode)
        assertTrue(source.settings.first().useDynamicColor)
    }

    @Test
    fun `onboarding completes only after both steps`() = runTest(UnconfinedTestDispatcher()) {
        val source = createDataSource()

        source.markWelcomeSeen()
        assertFalse(source.onboardingState.first().isComplete)

        source.acceptDisclaimer()
        assertTrue(source.onboardingState.first().isComplete)
    }

    @Test
    fun `fresh install has default playback settings`() = runTest(UnconfinedTestDispatcher()) {
        val playback = createDataSource().settings.first().playback

        assertEquals(PlaybackSettings(), playback)
        assertEquals(BufferProfile.Balanced, playback.bufferProfile)
        assertFalse(playback.softwareDecodingOnly)
        assertEquals(PlaybackSettings.DEFAULT_RECONNECT_ATTEMPTS, playback.maxReconnectAttempts)
        assertEquals(AspectRatioMode.Fit, playback.defaultAspectRatio)
        assertEquals(SubtitleStyle(), playback.subtitleStyle)
        assertTrue(playback.backgroundPlayback)
        assertTrue(playback.pictureInPicture)
        assertTrue(playback.playbackGestures)
    }

    @Test
    fun `persists every playback key`() = runTest(UnconfinedTestDispatcher()) {
        val source = createDataSource()
        val style = SubtitleStyle(
            size = SubtitleSize.ExtraLarge,
            color = SubtitleColor.Yellow,
            background = SubtitleBackground.Opaque,
        )

        source.setBufferProfile(BufferProfile.LowLatency)
        source.setSoftwareDecodingOnly(true)
        source.setMaxReconnectAttempts(0)
        source.setDefaultAspectRatio(AspectRatioMode.Stretch)
        source.setSubtitleStyle(style)
        source.setBackgroundPlayback(false)
        source.setPictureInPicture(false)
        source.setPlaybackGestures(false)

        assertEquals(
            PlaybackSettings(
                bufferProfile = BufferProfile.LowLatency,
                softwareDecodingOnly = true,
                maxReconnectAttempts = 0,
                defaultAspectRatio = AspectRatioMode.Stretch,
                subtitleStyle = style,
                backgroundPlayback = false,
                pictureInPicture = false,
                playbackGestures = false,
            ),
            source.settings.first().playback,
        )
    }

    @Test
    fun `each playback key is stored independently`() = runTest(UnconfinedTestDispatcher()) {
        val source = createDataSource()
        val defaults = PlaybackSettings()

        source.setBufferProfile(BufferProfile.Stable)
        assertEquals(defaults.copy(bufferProfile = BufferProfile.Stable), source.settings.first().playback)

        source.setSoftwareDecodingOnly(true)
        assertTrue(source.settings.first().playback.softwareDecodingOnly)
        source.setSoftwareDecodingOnly(false)
        assertFalse(source.settings.first().playback.softwareDecodingOnly)

        source.setMaxReconnectAttempts(10)
        assertEquals(10, source.settings.first().playback.maxReconnectAttempts)

        source.setDefaultAspectRatio(AspectRatioMode.Original)
        assertEquals(AspectRatioMode.Original, source.settings.first().playback.defaultAspectRatio)

        source.setPictureInPicture(false)
        assertFalse(source.settings.first().playback.pictureInPicture)
        assertTrue(source.settings.first().playback.backgroundPlayback)
        source.setPictureInPicture(true)
        assertTrue(source.settings.first().playback.pictureInPicture)

        source.setPlaybackGestures(false)
        assertFalse(source.settings.first().playback.playbackGestures)
        assertTrue(source.settings.first().playback.pictureInPicture)
        source.setPlaybackGestures(true)
        assertTrue(source.settings.first().playback.playbackGestures)

        source.setSubtitleStyle(SubtitleStyle(size = SubtitleSize.Small))
        assertEquals(SubtitleStyle(size = SubtitleSize.Small), source.settings.first().playback.subtitleStyle)
        assertEquals(BufferProfile.Stable, source.settings.first().playback.bufferProfile)

        source.setBackgroundPlayback(false)
        assertFalse(source.settings.first().playback.backgroundPlayback)
        source.setBackgroundPlayback(true)
        assertTrue(source.settings.first().playback.backgroundPlayback)
        assertEquals(AspectRatioMode.Original, source.settings.first().playback.defaultAspectRatio)
    }

    @Test
    fun `unknown enum names fall back to defaults`() = runTest(UnconfinedTestDispatcher()) {
        val store = createDataStore()
        val source = SettingsDataSource(store)
        store.edit {
            it[stringPreferencesKey("playback_buffer_profile")] = "Turbo"
            it[stringPreferencesKey("playback_default_aspect_ratio")] = "21:9"
            it[stringPreferencesKey("playback_subtitle_size")] = "Huge"
            it[stringPreferencesKey("playback_subtitle_color")] = ""
            it[stringPreferencesKey("playback_subtitle_background")] = "blur"
        }

        assertEquals(PlaybackSettings(), source.settings.first().playback)
    }

    @Test
    fun `out of range reconnect attempts are clamped`() = runTest(UnconfinedTestDispatcher()) {
        val store = createDataStore()
        val source = SettingsDataSource(store)
        val key = intPreferencesKey("playback_max_reconnect_attempts")

        store.edit { it[key] = -3 }
        assertEquals(0, source.settings.first().playback.maxReconnectAttempts)
        store.edit { it[key] = 999 }
        assertEquals(PlaybackSettings.MAX_RECONNECT_ATTEMPTS, source.settings.first().playback.maxReconnectAttempts)

        source.setMaxReconnectAttempts(-1)
        assertEquals(0, source.settings.first().playback.maxReconnectAttempts)
        source.setMaxReconnectAttempts(PlaybackSettings.MAX_RECONNECT_ATTEMPTS + 1)
        assertEquals(PlaybackSettings.MAX_RECONNECT_ATTEMPTS, source.settings.first().playback.maxReconnectAttempts)
    }

    @Test
    fun `resume last channel and auto play next default off and persist`() = runTest(UnconfinedTestDispatcher()) {
        val source = createDataSource()
        assertFalse(source.settings.first().playback.resumeLastChannel)
        assertFalse(source.settings.first().playback.autoPlayNext)

        source.setResumeLastChannel(true)
        source.setAutoPlayNext(true)
        assertTrue(source.settings.first().playback.resumeLastChannel)
        assertTrue(source.settings.first().playback.autoPlayNext)

        source.setAutoPlayNext(false)
        assertTrue(source.settings.first().playback.resumeLastChannel)
        assertFalse(source.settings.first().playback.autoPlayNext)
    }

    @Test
    fun `last played channel is saved, overwritten and cleared`() = runTest(UnconfinedTestDispatcher()) {
        val source = createDataSource()
        assertNull(source.lastPlayedChannel.first())

        source.setLastPlayedChannel(LastPlayedChannel(playlistId = 3, channelId = 42))
        assertEquals(LastPlayedChannel(3, 42), source.lastPlayedChannel.first())
        source.setLastPlayedChannel(LastPlayedChannel(playlistId = 4, channelId = 7))
        assertEquals(LastPlayedChannel(4, 7), source.lastPlayedChannel.first())

        source.clearLastPlayedChannel()
        assertNull(source.lastPlayedChannel.first())
        // Saving the last channel does not touch the settings.
        assertEquals(PlaybackSettings(), source.settings.first().playback)
    }

    @Test
    fun `last played channel survives a new data source on the same file`() = runTest(UnconfinedTestDispatcher()) {
        val store = createDataStore()
        SettingsDataSource(store).setLastPlayedChannel(LastPlayedChannel(1, 9))

        assertEquals(LastPlayedChannel(1, 9), SettingsDataSource(store).lastPlayedChannel.first())
    }
}
