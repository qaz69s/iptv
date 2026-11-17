package com.streambox.player.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.streambox.player.core.model.AppSettings
import com.streambox.player.core.model.AspectRatioMode
import com.streambox.player.core.model.BufferProfile
import com.streambox.player.core.model.OnboardingState
import com.streambox.player.core.model.PlaybackSettings
import com.streambox.player.core.model.SubtitleBackground
import com.streambox.player.core.model.SubtitleColor
import com.streambox.player.core.model.SubtitleSize
import com.streambox.player.core.model.SubtitleStyle
import com.streambox.player.core.model.ThemeMode
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@Singleton
class SettingsDataSource @Inject constructor(private val dataStore: DataStore<Preferences>) {
    private val safeData: Flow<Preferences> = dataStore.data.catch { error ->
        // A corrupted or unreadable file must not crash the app: fall back to defaults.
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    val settings: Flow<AppSettings> = safeData.map { prefs ->
        AppSettings(
            themeMode = prefs[Keys.THEME_MODE].toThemeMode(),
            useDynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: false,
            playback = prefs.toPlaybackSettings(),
        )
    }.distinctUntilChanged()

    val onboardingState: Flow<OnboardingState> = safeData.map { prefs ->
        OnboardingState(
            welcomeSeen = prefs[Keys.WELCOME_SEEN] ?: false,
            disclaimerAccepted = prefs[Keys.DISCLAIMER_ACCEPTED] ?: false,
        )
    }.distinctUntilChanged()

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    suspend fun setBufferProfile(profile: BufferProfile) {
        dataStore.edit { it[Keys.BUFFER_PROFILE] = profile.name }
    }

    suspend fun setSoftwareDecodingOnly(enabled: Boolean) {
        dataStore.edit { it[Keys.SOFTWARE_DECODING_ONLY] = enabled }
    }

    suspend fun setMaxReconnectAttempts(attempts: Int) {
        dataStore.edit {
            it[Keys.MAX_RECONNECT_ATTEMPTS] = attempts.coerceIn(0, PlaybackSettings.MAX_RECONNECT_ATTEMPTS)
        }
    }

    suspend fun setDefaultAspectRatio(mode: AspectRatioMode) {
        dataStore.edit { it[Keys.DEFAULT_ASPECT_RATIO] = mode.name }
    }

    suspend fun setSubtitleStyle(style: SubtitleStyle) {
        dataStore.edit {
            it[Keys.SUBTITLE_SIZE] = style.size.name
            it[Keys.SUBTITLE_COLOR] = style.color.name
            it[Keys.SUBTITLE_BACKGROUND] = style.background.name
        }
    }

    suspend fun markWelcomeSeen() {
        dataStore.edit { it[Keys.WELCOME_SEEN] = true }
    }

    suspend fun acceptDisclaimer() {
        dataStore.edit { it[Keys.DISCLAIMER_ACCEPTED] = true }
    }

    private fun String?.toThemeMode(): ThemeMode = ThemeMode.entries.firstOrNull { it.name == this } ?: ThemeMode.System

    private fun Preferences.toPlaybackSettings(): PlaybackSettings {
        val defaults = PlaybackSettings()
        val style = defaults.subtitleStyle
        return PlaybackSettings(
            bufferProfile = this[Keys.BUFFER_PROFILE].toEnum(defaults.bufferProfile),
            softwareDecodingOnly = this[Keys.SOFTWARE_DECODING_ONLY] ?: defaults.softwareDecodingOnly,
            maxReconnectAttempts = (this[Keys.MAX_RECONNECT_ATTEMPTS] ?: defaults.maxReconnectAttempts)
                .coerceIn(0, PlaybackSettings.MAX_RECONNECT_ATTEMPTS),
            defaultAspectRatio = this[Keys.DEFAULT_ASPECT_RATIO].toEnum(defaults.defaultAspectRatio),
            subtitleStyle = SubtitleStyle(
                size = this[Keys.SUBTITLE_SIZE].toEnum(style.size),
                color = this[Keys.SUBTITLE_COLOR].toEnum(style.color),
                background = this[Keys.SUBTITLE_BACKGROUND].toEnum(style.background),
            ),
        )
    }

    /** Unknown or missing names (e.g. from a newer / older app version) fall back to [default]. */
    private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
        enumValues<E>().firstOrNull { it.name == this } ?: default

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val WELCOME_SEEN = booleanPreferencesKey("onboarding_welcome_seen")
        val DISCLAIMER_ACCEPTED = booleanPreferencesKey("onboarding_disclaimer_accepted")
        val BUFFER_PROFILE = stringPreferencesKey("playback_buffer_profile")
        val SOFTWARE_DECODING_ONLY = booleanPreferencesKey("playback_software_decoding_only")
        val MAX_RECONNECT_ATTEMPTS = intPreferencesKey("playback_max_reconnect_attempts")
        val DEFAULT_ASPECT_RATIO = stringPreferencesKey("playback_default_aspect_ratio")
        val SUBTITLE_SIZE = stringPreferencesKey("playback_subtitle_size")
        val SUBTITLE_COLOR = stringPreferencesKey("playback_subtitle_color")
        val SUBTITLE_BACKGROUND = stringPreferencesKey("playback_subtitle_background")
    }
}
