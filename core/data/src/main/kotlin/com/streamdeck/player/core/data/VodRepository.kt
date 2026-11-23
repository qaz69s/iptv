package com.streamdeck.player.core.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.streamdeck.player.core.database.PlaylistDao
import com.streamdeck.player.core.database.SeriesEntity
import com.streamdeck.player.core.database.VodCategoryEntity
import com.streamdeck.player.core.database.VodDao
import com.streamdeck.player.core.database.VodItemEntity
import com.streamdeck.player.core.database.VodProgressEntity
import com.streamdeck.player.core.network.XtreamClient
import com.streamdeck.player.core.network.XtreamSeriesInfo
import com.streamdeck.player.core.network.XtreamUrls
import com.streamdeck.player.core.network.XtreamVodInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** Opaque key the player route carries for movies and episodes. */
sealed interface VodKey {
    val playlistId: Long
    val progressKey: String

    data class Movie(override val playlistId: Long, val streamId: Long, val extension: String) : VodKey {
        override val progressKey get() = "movie:$streamId"
    }

    data class Episode(override val playlistId: Long, val seriesId: Long, val episodeId: Long, val extension: String) : VodKey {
        override val progressKey get() = "episode:$episodeId"
    }

    fun encode(): String = when (this) {
        is Movie -> "$playlistId|movie|$streamId|$extension"
        is Episode -> "$playlistId|episode|$seriesId|$episodeId|$extension"
    }

    companion object {
        fun decode(raw: String): VodKey? {
            val p = raw.split('|')
            return runCatching {
                when (p.getOrNull(1)) {
                    "movie" -> Movie(p[0].toLong(), p[2].toLong(), p[3])
                    "episode" -> Episode(p[0].toLong(), p[2].toLong(), p[3].toLong(), p[4])
                    else -> null
                }
            }.getOrNull()
        }
    }
}

data class VodPlayable(val title: String, val poster: String?, val stream: ResolvedStream)

/** Xtream movies and series (spec §4.6): browsing, details, resume, watched marks, next episode. */
@Singleton
class VodRepository @Inject constructor(
    private val dao: VodDao,
    private val playlistDao: PlaylistDao,
    private val accounts: XtreamAccounts,
    private val client: XtreamClient,
) {
    private val seriesCache = LinkedHashMap<Pair<Long, Long>, XtreamSeriesInfo>()
    private val cacheLock = Mutex()

    fun categories(pid: Long, kind: String): Flow<List<VodCategoryEntity>> = dao.categories(pid, kind)

    fun movies(pid: Long, category: String?, query: String): Flow<PagingData<VodItemEntity>> =
        Pager(PagingConfig(pageSize = 48, enablePlaceholders = true)) { dao.pageItems(pid, category, query.trim()) }.flow

    fun series(pid: Long, category: String?, query: String): Flow<PagingData<SeriesEntity>> =
        Pager(PagingConfig(pageSize = 48, enablePlaceholders = true)) { dao.pageSeries(pid, category, query.trim()) }.flow

    fun progress(pid: Long): Flow<Map<String, VodProgressEntity>> = dao.observeProgress(pid).map { l -> l.associateBy { it.key } }

    suspend fun movie(pid: Long, streamId: Long) = dao.item(pid, streamId)
    suspend fun seriesEntity(pid: Long, seriesId: Long) = dao.seriesById(pid, seriesId)

    suspend fun movieInfo(pid: Long, streamId: Long): XtreamVodInfo? {
        val a = playlistDao.get(pid)?.let(accounts::of) ?: return null
        return client.vodInfo(a, streamId)
    }

    suspend fun seriesInfo(pid: Long, seriesId: Long, refresh: Boolean = false): XtreamSeriesInfo? {
        val k = pid to seriesId
        if (!refresh) cacheLock.withLock { seriesCache[k] }?.let { return it }
        val a = playlistDao.get(pid)?.let(accounts::of) ?: return null
        val info = client.seriesInfo(a, seriesId)
        cacheLock.withLock {
            seriesCache[k] = info
            while (seriesCache.size > CACHE_SIZE) seriesCache.remove(seriesCache.keys.first())
        }
        return info
    }

    suspend fun resolve(raw: String): VodPlayable? {
        val key = VodKey.decode(raw) ?: return null
        val p = playlistDao.get(key.playlistId) ?: return null
        val a = accounts.of(p) ?: return null
        fun stream(url: String) = ResolvedStream(url, p.userAgent, null, emptyMap(), p.allowCleartext)
        return when (key) {
            is VodKey.Movie -> {
                val m = dao.item(key.playlistId, key.streamId)
                VodPlayable(m?.name ?: key.streamId.toString(), m?.poster, stream(XtreamUrls.movie(a, key.streamId, key.extension)))
            }
            is VodKey.Episode -> {
                val info = seriesInfo(key.playlistId, key.seriesId)
                val ep = info?.episodes?.firstOrNull { it.id == key.episodeId }
                val title = listOfNotNull(info?.name, ep?.let { "S%02dE%02d %s".format(it.season, it.episode, it.title).trim() }).joinToString(" · ")
                VodPlayable(title.ifBlank { key.episodeId.toString() }, ep?.image ?: info?.cover, stream(XtreamUrls.episode(a, key.episodeId, key.extension)))
            }
        }
    }

    /** Resume point unless the item was (almost) finished. */
    suspend fun resumePosition(raw: String): Long {
        val key = VodKey.decode(raw) ?: return 0
        val p = dao.progress(key.playlistId, key.progressKey) ?: return 0
        return if (p.watched || p.durationMs <= 0 || p.positionMs > p.durationMs * WATCHED_RATIO) 0 else p.positionMs
    }

    suspend fun saveProgress(raw: String, positionMs: Long, durationMs: Long) {
        val key = VodKey.decode(raw) ?: return
        val watched = durationMs > 0 && positionMs >= durationMs * WATCHED_RATIO
        val prev = dao.progress(key.playlistId, key.progressKey)
        dao.upsertProgress(VodProgressEntity(key.playlistId, key.progressKey, positionMs, durationMs, watched || prev?.watched == true, System.currentTimeMillis()))
    }

    suspend fun markWatched(raw: String, watched: Boolean = true) {
        val key = VodKey.decode(raw) ?: return
        val prev = dao.progress(key.playlistId, key.progressKey)
        dao.upsertProgress(VodProgressEntity(key.playlistId, key.progressKey, if (watched) prev?.positionMs ?: 0 else 0, prev?.durationMs ?: 0, watched, System.currentTimeMillis()))
    }

    suspend fun nextEpisodeKey(raw: String): String? {
        val key = VodKey.decode(raw) as? VodKey.Episode ?: return null
        val eps = seriesInfo(key.playlistId, key.seriesId)?.episodes ?: return null
        val i = eps.indexOfFirst { it.id == key.episodeId }
        val next = eps.getOrNull(i + 1) ?: return null
        return VodKey.Episode(key.playlistId, key.seriesId, next.id, next.extension).encode()
    }

    companion object {
        const val WATCHED_RATIO = 0.95
        private const val CACHE_SIZE = 20
    }
}
