package com.streamdeck.player.core.data

import com.streamdeck.player.core.database.ChannelDao
import com.streamdeck.player.core.database.ChannelEntity
import com.streamdeck.player.core.database.ChannelGroupEntity
import com.streamdeck.player.core.database.PlaylistDao
import com.streamdeck.player.core.database.PlaylistEntity
import com.streamdeck.player.core.database.PlaylistType
import com.streamdeck.player.core.database.SeriesEntity
import com.streamdeck.player.core.database.VodCategoryEntity
import com.streamdeck.player.core.database.VodDao
import com.streamdeck.player.core.database.VodItemEntity
import com.streamdeck.player.core.datastore.CredentialCipher
import com.streamdeck.player.core.network.XtreamAccount
import com.streamdeck.player.core.network.XtreamClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/** Decrypts stored Xtream credentials on demand; the password never leaves memory in clear text. */
@Singleton
class XtreamAccounts @Inject constructor(private val cipher: CredentialCipher) {
    fun of(p: PlaylistEntity): XtreamAccount? {
        if (p.type != PlaylistType.XTREAM) return null
        val user = p.username ?: return null
        val pass = p.passwordEncrypted?.let(cipher::decrypt) ?: return null
        return XtreamAccount(p.url, user, pass, p.allowCleartext)
    }

    fun encrypt(password: String): String = cipher.encrypt(password)

    companion object {
        /** Sentinel stored as epgUrl so the credential-bearing xmltv.php URL is never persisted. */
        const val XTREAM_EPG = "xtream:xmltv"
        const val LIVE_PREFIX = "xtream:live/"
        const val LIVE_EXT = "ts"
    }
}

/** Imports Xtream live channels (into the channel tables) and the VOD/series catalogue (spec §4.1 / §4.6). */
@Singleton
class XtreamImporter @Inject constructor(
    private val client: XtreamClient,
    private val accounts: XtreamAccounts,
    private val playlistDao: PlaylistDao,
    private val channelDao: ChannelDao,
    private val vodDao: VodDao,
) {
    /** Writes pending live rows; the caller commits. Returns the number of channels. */
    suspend fun importLive(p: PlaylistEntity, onProgress: (Int) -> Unit): Int {
        val a = accounts.of(p) ?: throw ImportException("missing Xtream credentials")
        val auth = client.authenticate(a)
        if (auth.serverTimezone != null && auth.serverTimezone != p.serverTimezone) playlistDao.update(p.copy(serverTimezone = auth.serverTimezone))
        val categories = client.liveCategories(a)
        val groupIds = HashMap<String, Long>()
        val groupNames = HashMap<String, String>()
        categories.forEachIndexed { i, c ->
            groupIds[c.id] = channelDao.insertGroup(ChannelGroupEntity(playlistId = p.id, name = c.name, originalName = c.name, sortOrder = i, pending = true))
            groupNames[c.id] = c.name
        }
        var fallbackGroup: Long? = null
        val job = coroutineContext
        val batch = ArrayList<ChannelEntity>(BATCH)
        var order = 0
        client.liveStreams(a) { s ->
            job.ensureActive()
            val gid = s.categoryId?.let(groupIds::get) ?: fallbackGroup ?: runBlocking {
                channelDao.insertGroup(
                    ChannelGroupEntity(playlistId = p.id, name = PlaylistRepository.UNGROUPED, originalName = PlaylistRepository.UNGROUPED, sortOrder = categories.size, pending = true),
                )
            }.also { fallbackGroup = it }
            batch += ChannelEntity(
                playlistId = p.id, groupId = gid, groupName = s.categoryId?.let(groupNames::get) ?: PlaylistRepository.UNGROUPED,
                uniqueKey = "xc:${s.streamId}", name = s.name, logoUrl = s.icon, streamUrl = XtreamAccounts.LIVE_PREFIX + s.streamId,
                tvgId = s.epgChannelId, tvgName = null, number = s.number,
                catchupType = if (s.tvArchive) "xc" else null, catchupSource = null, catchupDays = if (s.tvArchive) s.archiveDays else null,
                userAgent = null, referrer = null, sortOrder = order, importOrder = order++, pending = true,
            )
            if (batch.size >= BATCH) {
                runBlocking { channelDao.insertChannels(batch.toList()) }
                batch.clear()
                onProgress(order)
            }
        }
        if (batch.isNotEmpty()) channelDao.insertChannels(batch.toList())
        onProgress(order)
        return order
    }

    /** Replaces the VOD catalogue atomically; on failure the previous catalogue stays. */
    suspend fun importVod(p: PlaylistEntity) {
        val a = accounts.of(p) ?: return
        vodDao.abortImport(p.id)
        try {
            val movieCats = client.vodCategories(a).mapIndexed { i, c -> VodCategoryEntity(p.id, KIND_MOVIE, c.id, c.name, i, pending = true) }
            val seriesCats = client.seriesCategories(a).mapIndexed { i, c -> VodCategoryEntity(p.id, KIND_SERIES, c.id, c.name, i, pending = true) }
            vodDao.insertCategories(movieCats + seriesCats)
            val items = ArrayList<VodItemEntity>(BATCH)
            var order = 0
            client.vodStreams(a) { v ->
                items += VodItemEntity(0, p.id, v.streamId, v.name, v.categoryId, v.icon, v.rating, v.extension, v.added, order++, pending = true)
                if (items.size >= BATCH) { runBlocking { vodDao.insertItems(items.toList()) }; items.clear() }
            }
            if (items.isNotEmpty()) vodDao.insertItems(items.toList())
            val series = ArrayList<SeriesEntity>(BATCH)
            order = 0
            client.series(a) { s ->
                series += SeriesEntity(0, p.id, s.seriesId, s.name, s.categoryId, s.cover, s.plot, s.rating, s.genre, s.releaseDate, order++, pending = true)
                if (series.size >= BATCH) { runBlocking { vodDao.insertSeries(series.toList()) }; series.clear() }
            }
            if (series.isNotEmpty()) vodDao.insertSeries(series.toList())
            vodDao.commitImport(p.id)
        } catch (c: CancellationException) {
            withContext(NonCancellable) { vodDao.abortImport(p.id) }
            throw c
        } catch (t: Throwable) {
            withContext(NonCancellable) { vodDao.abortImport(p.id) }
            throw t
        }
    }

    companion object {
        const val KIND_MOVIE = "movie"
        const val KIND_SERIES = "series"
        private const val BATCH = 1000
    }
}
