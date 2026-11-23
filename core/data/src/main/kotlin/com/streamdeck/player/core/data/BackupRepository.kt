package com.streamdeck.player.core.data

import android.content.Context
import android.net.Uri
import com.streamdeck.player.core.database.ChannelDao
import com.streamdeck.player.core.database.ChannelOverrideEntity
import com.streamdeck.player.core.database.EditorDao
import com.streamdeck.player.core.database.FavoriteEntity
import com.streamdeck.player.core.database.GroupOverrideEntity
import com.streamdeck.player.core.database.LibraryDao
import com.streamdeck.player.core.database.PlaylistDao
import com.streamdeck.player.core.database.PlaylistEntity
import com.streamdeck.player.core.database.PlaylistType
import com.streamdeck.player.core.datastore.AppSettings
import com.streamdeck.player.core.datastore.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class PlaylistBackup(
    val ref: Long,
    val name: String,
    val type: String,
    val url: String,
    val username: String? = null,
    val userAgent: String? = null,
    val headersJson: String? = null,
    val epgUrl: String? = null,
    val updateMode: String = PlaylistEntity.UPDATE_MANUAL,
    val updateIntervalHours: Int = 24,
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
    val allowCleartext: Boolean = false,
    /** True for Xtream: the password is never exported and must be re-entered (spec §4.9). */
    val needsPassword: Boolean = false,
)

@Serializable data class FavoriteBackup(val playlistRef: Long, val uniqueKey: String, val addedAt: Long)

@Serializable
data class ChannelOverrideBackup(
    val playlistRef: Long, val uniqueKey: String, val customName: String? = null, val hidden: Boolean? = null,
    val sortOrder: Int? = null, val locked: Boolean? = null, val customGroup: String? = null,
)

@Serializable
data class GroupOverrideBackup(
    val playlistRef: Long, val name: String, val customName: String? = null, val hidden: Boolean? = null,
    val sortOrder: Int? = null, val locked: Boolean? = null, val custom: Boolean = false,
)

@Serializable
data class Backup(
    val format: String = FORMAT,
    val version: Int = 1,
    val createdAt: Long,
    val settings: AppSettings,
    val playlists: List<PlaylistBackup>,
    val favorites: List<FavoriteBackup>,
    val channelOverrides: List<ChannelOverrideBackup>,
    val groupOverrides: List<GroupOverrideBackup>,
) {
    companion object { const val FORMAT = "streamdeck-backup" }
}

data class RestoreResult(val playlists: Int, val favorites: Int, val needPassword: List<String>)

/** JSON backup of playlists (without secrets), favorites, edits and settings (spec §4.9). */
@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val playlistDao: PlaylistDao,
    private val libraryDao: LibraryDao,
    private val editorDao: EditorDao,
    private val channelDao: ChannelDao,
    private val settings: SettingsRepository,
    private val work: BackgroundWork,
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun export(target: Uri) = withContext(Dispatchers.IO) {
        val backup = Backup(
            createdAt = System.currentTimeMillis(),
            settings = settings.current(),
            playlists = playlistDao.all().map {
                PlaylistBackup(
                    it.id, it.name, it.type.name, it.url, it.username, it.userAgent, it.headersJson,
                    it.epgUrl, it.updateMode, it.updateIntervalHours, it.enabled, it.sortOrder, it.allowCleartext,
                    needsPassword = it.type == PlaylistType.XTREAM,
                )
            },
            favorites = libraryDao.allFavorites().map { FavoriteBackup(it.playlistId, it.uniqueKey, it.addedAt) },
            channelOverrides = editorDao.allChannelOverrides().map {
                ChannelOverrideBackup(it.playlistId, it.uniqueKey, it.customName, it.hidden, it.sortOrder, it.locked, it.customGroup)
            },
            groupOverrides = editorDao.allGroupOverrides().map {
                GroupOverrideBackup(it.playlistId, it.name, it.customName, it.hidden, it.sortOrder, it.locked, it.custom)
            },
        )
        val out = ctx.contentResolver.openOutputStream(target, "wt") ?: throw ImportException("cannot write file")
        out.use { it.write(json.encodeToString(Backup.serializer(), backup).toByteArray(Charsets.UTF_8)) }
    }

    /** Adds the backed-up playlists as new entries (existing ones are kept) and restores settings. */
    suspend fun restore(source: Uri): RestoreResult = withContext(Dispatchers.IO) {
        val text = ctx.contentResolver.openInputStream(source)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: throw ImportException("cannot read file")
        val b = runCatching { json.decodeFromString(Backup.serializer(), text) }.getOrElse { throw ImportException(INVALID) }
        if (b.format != Backup.FORMAT) throw ImportException(INVALID)
        val base = playlistDao.all().size
        val ids = HashMap<Long, Long>()
        val needPassword = mutableListOf<String>()
        b.playlists.sortedBy { it.sortOrder }.forEachIndexed { i, p ->
            val type = runCatching { PlaylistType.valueOf(p.type) }.getOrNull() ?: return@forEachIndexed
            ids[p.ref] = playlistDao.insert(
                PlaylistEntity(
                    name = p.name, type = type, url = p.url, username = p.username, userAgent = p.userAgent, headersJson = p.headersJson,
                    epgUrl = p.epgUrl, updateMode = p.updateMode, updateIntervalHours = p.updateIntervalHours, enabled = p.enabled,
                    sortOrder = base + i, allowCleartext = p.allowCleartext,
                ),
            )
            if (p.needsPassword) needPassword += p.name
        }
        libraryDao.insertFavorites(b.favorites.mapNotNull { f -> ids[f.playlistRef]?.let { FavoriteEntity(it, f.uniqueKey, f.addedAt) } })
        editorDao.insertChannelOverrides(
            b.channelOverrides.mapNotNull { o -> ids[o.playlistRef]?.let { ChannelOverrideEntity(it, o.uniqueKey, o.customName, o.hidden, o.sortOrder, o.locked, o.customGroup) } },
        )
        editorDao.insertGroupOverrides(
            b.groupOverrides.mapNotNull { o -> ids[o.playlistRef]?.let { GroupOverrideEntity(it, o.name, o.customName, o.hidden, o.sortOrder, o.locked, o.custom) } },
        )
        settings.update { b.settings }
        ids.values.forEach { id ->
            channelDao.applyOverrides(id)
            playlistDao.get(id)?.let { work.schedulePlaylist(it) }
        }
        RestoreResult(ids.size, b.favorites.count { it.playlistRef in ids }, needPassword)
    }

    companion object { const val INVALID = "INVALID_BACKUP" }
}
