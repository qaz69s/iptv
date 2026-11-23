package com.streamdeck.player.core.data

import android.content.Context
import android.net.Uri
import com.streamdeck.player.core.database.ChannelDao
import com.streamdeck.player.core.database.ChannelEntity
import com.streamdeck.player.core.database.ChannelGroupEntity
import com.streamdeck.player.core.database.EditorDao
import com.streamdeck.player.core.database.PlaylistDao
import com.streamdeck.player.core.database.PlaylistEntity
import com.streamdeck.player.core.database.PlaylistType
import com.streamdeck.player.core.database.VodDao
import com.streamdeck.player.core.network.XtreamUrls
import com.streamdeck.player.core.network.HttpDefaults
import com.streamdeck.player.parser.ChannelKey
import com.streamdeck.player.parser.M3uEntry
import com.streamdeck.player.parser.M3uHeader
import com.streamdeck.player.parser.M3uParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InputStreamReader
import java.io.Reader
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

class ImportException(message: String) : IOException(message)

@Singleton
class PlaylistRepository @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val playlistDao: PlaylistDao,
    private val channelDao: ChannelDao,
    private val editorDao: EditorDao,
    private val http: OkHttpClient,
    /** Lazy to keep construction cheap; EPG matching runs after each successful import. */
    private val epg: dagger.Lazy<EpgRepository>,
    private val xtream: XtreamImporter,
    private val accounts: XtreamAccounts,
    private val vodDao: VodDao,
    private val work: BackgroundWork,
) {
    fun observePlaylists() = playlistDao.observeAll()

    suspend fun observePlaylistsOnce() = playlistDao.all()

    suspend fun get(id: Long) = playlistDao.get(id)

    suspend fun create(name: String, type: PlaylistType, source: String, userAgent: String?, allowCleartext: Boolean = false): Long =
        playlistDao.insert(
            PlaylistEntity(
                name = name, type = type, url = source, userAgent = userAgent?.ifBlank { null }, sortOrder = playlistDao.all().size,
                allowCleartext = allowCleartext,
            ),
        )

    /** Xtream Codes: the password is encrypted with the Android Keystore before it is stored (spec §4.9). */
    suspend fun createXtream(name: String, server: String, username: String, password: String, allowCleartext: Boolean = false): Long =
        playlistDao.insert(
            PlaylistEntity(
                name = name, type = PlaylistType.XTREAM, url = XtreamUrls.normalizeServer(server), username = username.trim(),
                passwordEncrypted = accounts.encrypt(password), epgUrl = XtreamAccounts.XTREAM_EPG, sortOrder = playlistDao.all().size,
                allowCleartext = allowCleartext,
            ),
        )

    suspend fun updateXtreamPassword(p: PlaylistEntity, password: String) = playlistDao.update(p.copy(passwordEncrypted = accounts.encrypt(password)))

    suspend fun update(p: PlaylistEntity) {
        playlistDao.update(p)
        work.schedulePlaylist(p)
    }

    /** Called once per process start: applies every playlist's update policy (spec §4.1). */
    suspend fun applyUpdatePolicies() {
        playlistDao.all().forEach { p ->
            work.schedulePlaylist(p)
            if (p.enabled && p.updateMode == PlaylistEntity.UPDATE_ON_START) work.refreshOnStart(p)
        }
    }

    suspend fun setEnabled(id: Long, enabled: Boolean) { playlistDao.get(id)?.let { update(it.copy(enabled = enabled)) } }

    /** Moves a playlist one step up (-1) or down (+1) in the user's order. */
    suspend fun move(id: Long, delta: Int) {
        val list = playlistDao.all().toMutableList()
        val i = list.indexOfFirst { it.id == id }
        val j = i + delta
        if (i < 0 || j !in list.indices) return
        java.util.Collections.swap(list, i, j)
        playlistDao.updateAll(list.mapIndexed { idx, p -> p.copy(sortOrder = idx) })
    }

    suspend fun delete(id: Long) {
        channelDao.deletePlaylistData(id)
        editorDao.deleteChannelOverrides(id)
        editorDao.deleteGroupOverrides(id)
        editorDao.deleteFavorites(id)
        editorDao.deleteHistory(id)
        vodDao.deleteAll(id)
        work.cancelPlaylist(id)
        playlistDao.delete(id)
    }

    /**
     * Streams the playlist, writes channels as `pending` in batches, then atomically swaps them in.
     * On any failure the previous data is left untouched (spec §8.2).
     */
    suspend fun import(playlistId: Long, onProgress: (Int) -> Unit): Int = withContext(Dispatchers.IO) {
        val p = playlistDao.get(playlistId) ?: throw ImportException("playlist not found")
        channelDao.abortImport(playlistId)
        try {
            var header: M3uHeader? = null
            val count = if (p.type == PlaylistType.XTREAM) {
                xtream.importLive(p, onProgress)
            } else {
                openReader(p).use { reader -> parseInto(p, reader, onProgress) { header = it } }
            }
            if (count == 0) throw ImportException(EMPTY)
            channelDao.commitImport(playlistId)
            val fresh = playlistDao.get(playlistId) ?: p
            playlistDao.update(fresh.copy(lastUpdated = System.currentTimeMillis(), epgUrl = fresh.epgUrl ?: header?.epgUrl))
            epg.get().rematch(playlistId)
            if (p.type == PlaylistType.XTREAM) runCatching { xtream.importVod(playlistDao.get(playlistId) ?: p) }
            count
        } catch (t: Throwable) {
            withContext(NonCancellable) { channelDao.abortImport(playlistId) }
            throw t
        }
    }

    private fun openReader(p: PlaylistEntity): Reader = when (p.type) {
        PlaylistType.M3U_FILE -> {
            val stream = ctx.contentResolver.openInputStream(Uri.parse(p.url)) ?: throw ImportException("cannot open file")
            InputStreamReader(stream, Charsets.UTF_8)
        }
        PlaylistType.M3U_URL -> {
            val req = Request.Builder().url(p.url).header("User-Agent", p.userAgent ?: HttpDefaults.USER_AGENT).cleartextTag(p.allowCleartext).build()
            val resp = http.newCall(req).execute()
            if (!resp.isSuccessful) { resp.close(); throw ImportException("HTTP ${resp.code}") }
            val body = resp.body ?: throw ImportException("empty body")
            InputStreamReader(body.byteStream(), body.contentType()?.charset() ?: Charsets.UTF_8)
        }
        PlaylistType.XTREAM -> throw ImportException("Xtream playlists are imported through the API")
    }

    private suspend fun parseInto(p: PlaylistEntity, reader: Reader, onProgress: (Int) -> Unit, onHeader: (M3uHeader) -> Unit): Int {
        val job = coroutineContext
        val groupIds = HashMap<String, Long>()
        val batch = ArrayList<ChannelEntity>(BATCH)
        var order = 0
        var groupOrder = 0
        // The parser is synchronous; DAO calls are bridged with runBlocking on this IO thread.
        val count = M3uParser().parse(reader, onHeader) { e: M3uEntry ->
            job.ensureActive()
            val gName = e.group ?: UNGROUPED
            val gid = groupIds.getOrPut(gName) {
                runBlocking {
                    channelDao.insertGroup(ChannelGroupEntity(playlistId = p.id, name = gName, originalName = gName, sortOrder = groupOrder++, pending = true))
                }
            }
            batch += ChannelEntity(
                playlistId = p.id, groupId = gid, groupName = gName, uniqueKey = ChannelKey.of(e), name = e.name, logoUrl = e.logo,
                streamUrl = e.url, tvgId = e.tvgId, tvgName = e.tvgName, number = e.number,
                catchupType = e.catchup, catchupSource = e.catchupSource, catchupDays = e.catchupDays,
                userAgent = e.userAgent, referrer = e.referrer, sortOrder = order, importOrder = order++, pending = true,
            )
            if (batch.size >= BATCH) {
                runBlocking { channelDao.insertChannels(batch.toList()) }
                batch.clear()
                onProgress(order)
            }
        }
        if (batch.isNotEmpty()) channelDao.insertChannels(batch.toList())
        onProgress(count)
        return count
    }

    companion object {
        const val EMPTY = "EMPTY"
        const val UNGROUPED = "—"
        private const val BATCH = 1000
    }
}
