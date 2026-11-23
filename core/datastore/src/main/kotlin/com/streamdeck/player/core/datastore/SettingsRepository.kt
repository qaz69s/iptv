package com.streamdeck.player.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore("settings")

private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T =
    this?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: default

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext ctx: Context) {
    private val store = ctx.settingsStore

    val settings: Flow<AppSettings> = store.data.map { it.toSettings() }.distinctUntilChanged()
    val isOnboarded: Flow<Boolean> = store.data.map { it[K.onboarded] ?: false }
    val activePlaylistId: Flow<Long?> = store.data.map { it[K.activePlaylist] }
    val lastChannel: Flow<LastChannel?> = store.data.map { p ->
        val pid = p[K.lastPlaylist]
        val key = p[K.lastKey]
        if (pid != null && key != null) LastChannel(pid, key) else null
    }
    val pin: Flow<PinRecord?> = store.data.map { p ->
        val h = p[K.pinHash]
        val s = p[K.pinSalt]
        if (h != null && s != null) PinRecord(h, s, p[K.pinFailed] ?: 0, p[K.pinLockout] ?: 0L) else null
    }

    suspend fun current(): AppSettings = settings.first()
    suspend fun update(transform: (AppSettings) -> AppSettings) { store.edit { it.write(transform(it.toSettings())) } }
    suspend fun setOnboarded() { store.edit { it[K.onboarded] = true } }
    suspend fun setActivePlaylist(id: Long) { store.edit { it[K.activePlaylist] = id } }
    suspend fun setLastChannel(playlistId: Long, uniqueKey: String) {
        store.edit { it[K.lastPlaylist] = playlistId; it[K.lastKey] = uniqueKey }
    }

    suspend fun savePin(hash: String, salt: String) {
        store.edit { it[K.pinHash] = hash; it[K.pinSalt] = salt; it[K.pinFailed] = 0; it[K.pinLockout] = 0L }
    }
    suspend fun clearPin() { store.edit { it.remove(K.pinHash); it.remove(K.pinSalt); it.remove(K.pinFailed); it.remove(K.pinLockout) } }
    suspend fun recordPinAttempt(failedAttempts: Int, lockoutUntil: Long) {
        store.edit { it[K.pinFailed] = failedAttempts; it[K.pinLockout] = lockoutUntil }
    }

    private fun Preferences.toSettings() = AppSettings(
        theme = this[K.theme].toEnum(ThemeMode.SYSTEM),
        accentIndex = this[K.accent] ?: 0,
        fontScale = this[K.fontScale] ?: 1f,
        decoder = this[K.decoder].toEnum(DecoderMode.HARDWARE_PREFERRED),
        buffer = this[K.buffer].toEnum(BufferProfile.BALANCED),
        aspect = this[K.aspect].toEnum(AspectMode.FIT),
        autoResume = this[K.autoResume] ?: false,
        backgroundPlayback = this[K.background] ?: true,
        reconnectMax = this[K.reconnectMax] ?: 5,
        allowMetered = this[K.allowMetered] ?: true,
        autoplayNext = this[K.autoplayNext] ?: false,
        subtitle = SubtitleStyle(
            scale = this[K.subScale] ?: 1f,
            textColor = this[K.subColor] ?: SubtitleStyle().textColor,
            backgroundColor = this[K.subBg] ?: SubtitleStyle().backgroundColor,
        ),
        epgUpdateHours = this[K.epgHours] ?: 24,
        epgKeepDays = this[K.epgDays] ?: 3,
        epgOffsetMinutes = this[K.epgOffset] ?: 0,
        channelView = this[K.view].toEnum(ChannelViewMode.LIST),
        channelSort = this[K.sort].toEnum(ChannelSort.DEFAULT),
        lockSettings = this[K.lockSettings] ?: false,
        lockEditor = this[K.lockEditor] ?: false,
    )

    private fun MutablePreferences.write(s: AppSettings) {
        this[K.theme] = s.theme.name
        this[K.accent] = s.accentIndex
        this[K.fontScale] = s.fontScale.coerceIn(0.8f, 1.6f)
        this[K.decoder] = s.decoder.name
        this[K.buffer] = s.buffer.name
        this[K.aspect] = s.aspect.name
        this[K.autoResume] = s.autoResume
        this[K.background] = s.backgroundPlayback
        this[K.reconnectMax] = s.reconnectMax.coerceIn(0, 50)
        this[K.allowMetered] = s.allowMetered
        this[K.autoplayNext] = s.autoplayNext
        this[K.subScale] = s.subtitle.scale
        this[K.subColor] = s.subtitle.textColor
        this[K.subBg] = s.subtitle.backgroundColor
        this[K.epgHours] = s.epgUpdateHours.coerceIn(1, 168)
        this[K.epgDays] = s.epgKeepDays.coerceIn(1, 14)
        this[K.epgOffset] = s.epgOffsetMinutes.coerceIn(-720, 720)
        this[K.view] = s.channelView.name
        this[K.sort] = s.channelSort.name
        this[K.lockSettings] = s.lockSettings
        this[K.lockEditor] = s.lockEditor
    }

    private object K {
        val onboarded = booleanPreferencesKey("onboarded")
        val activePlaylist = longPreferencesKey("active_playlist")
        val lastPlaylist = longPreferencesKey("last_playlist")
        val lastKey = stringPreferencesKey("last_channel_key")
        val theme = stringPreferencesKey("theme")
        val accent = intPreferencesKey("accent")
        val fontScale = floatPreferencesKey("font_scale")
        val decoder = stringPreferencesKey("decoder")
        val buffer = stringPreferencesKey("buffer")
        val aspect = stringPreferencesKey("aspect")
        val autoResume = booleanPreferencesKey("auto_resume")
        val background = booleanPreferencesKey("background_playback")
        val reconnectMax = intPreferencesKey("reconnect_max")
        val allowMetered = booleanPreferencesKey("allow_metered")
        val autoplayNext = booleanPreferencesKey("autoplay_next")
        val subScale = floatPreferencesKey("sub_scale")
        val subColor = intPreferencesKey("sub_color")
        val subBg = intPreferencesKey("sub_bg")
        val epgHours = intPreferencesKey("epg_hours")
        val epgDays = intPreferencesKey("epg_days")
        val epgOffset = intPreferencesKey("epg_offset_min")
        val view = stringPreferencesKey("channel_view")
        val sort = stringPreferencesKey("channel_sort")
        val lockSettings = booleanPreferencesKey("lock_settings")
        val lockEditor = booleanPreferencesKey("lock_editor")
        val pinHash = stringPreferencesKey("pin_hash")
        val pinSalt = stringPreferencesKey("pin_salt")
        val pinFailed = intPreferencesKey("pin_failed")
        val pinLockout = longPreferencesKey("pin_lockout")
    }
}
