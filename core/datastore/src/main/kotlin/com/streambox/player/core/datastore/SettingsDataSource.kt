package com.streambox.player.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.streambox.player.core.model.AppSettings
import com.streambox.player.core.model.OnboardingState
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

    suspend fun markWelcomeSeen() {
        dataStore.edit { it[Keys.WELCOME_SEEN] = true }
    }

    suspend fun acceptDisclaimer() {
        dataStore.edit { it[Keys.DISCLAIMER_ACCEPTED] = true }
    }

    private fun String?.toThemeMode(): ThemeMode = ThemeMode.entries.firstOrNull { it.name == this } ?: ThemeMode.System

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val WELCOME_SEEN = booleanPreferencesKey("onboarding_welcome_seen")
        val DISCLAIMER_ACCEPTED = booleanPreferencesKey("onboarding_disclaimer_accepted")
    }
}
