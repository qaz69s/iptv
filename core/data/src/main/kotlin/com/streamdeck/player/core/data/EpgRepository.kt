package com.streamdeck.player.core.data

import android.content.Context
import android.net.Uri
import android.util.Xml
import com.streamdeck.player.core.database.ChannelDao
import com.streamdeck.player.core.database.EpgChannelEntity
import com.streamdeck.player.core.database.EpgDao
import com.streamdeck.player.core.database.EpgMappingEntity
import com.streamdeck.player.core.database.GridChannel
import com.streamdeck.player.core.database.PlaylistDao
import com.streamdeck.player.core.database.PlaylistEntity
import com.streamdeck.player.core.database.ProgramEntity
import com.streamdeck.player.core.datastore.SettingsRepository
import com.streamdeck.player.core.network.HttpDefaults
import com.streamdeck.player.core.network.XtreamUrls
import com.streamdeck.player.parser.EpgMatcher
import com.streamdeck.player.parser.MatchChannel
import com.streamdeck.player.parser.XmltvChannel
import com.streamdeck.player.parser.XmltvParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

/** Programme in **real** time (EPG offset already applied). */
data class Program(
    val id: Long,
    val epgChannelId: String,
    val start: Long,
    val end: Long,
    val title: String,
    val description: String?,
    val category: String?,
    val iconUrl: String?,
) {
    fun isLive(now: Long) = now in start until end
    fun isPast(now: Long) = end <= now
}

data class NowNext(val now: Program?, val next: Program?)

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class EpgRepository @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val playlistDao: PlaylistDao,
    private val channelDao: ChannelDao,
    private val epgDao: EpgDao,
    private val http: OkHttpClient,
    private val clock: EpgClock,
    private val settings: SettingsRepository,
    private val accounts: XtreamAccounts,
) {
    /**
     * Downloads (xml / xml.gz / content://) and stream-parses the playlist's XMLTV source into pending rows,
     * keeps only [now − 1 day, now + keepDays], swaps atomically, then re-runs channel matching.
     */
    suspend fun refresh(playlistId: Long, onProgress: (Int) -> Unit = {}): Int = withContext(Dispatchers.IO) {
        val p = playlistDao.get(playlistId) ?: throw ImportException("playlist not found")
        val url = p.epgUrl ?: throw ImportException(NO_EPG)
        val keepDays = settings.current().epgKeepDays
        val now = System.currentTimeMillis()
        val slack = abs(clock.offsetMs)
        val from = now - DAY_MS - slack
        val to = now + keepDays * DAY_MS + slack
        epgDao.abortImport(playlistId)
        try {
            val job = coroutineContext
            val epgChannels = ArrayList<XmltvChannel>()
            val batch = ArrayList<ProgramEntity>(BATCH)
            var count = 0
            open(p, url).use { input ->
                val parser = Xml.newPullParser()
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                parser.setInput(input, null)
                XmltvParser(from, to).parse(parser, { epgChannels += it }) { pr ->
                    job.ensureActive()
                    batch += ProgramEntity(
                        playlistId = playlistId, epgChannelId = pr.channelId, start = pr.startUtcMs, end = pr.endUtcMs,
                        title = pr.title, description = pr.description, category = pr.category, iconUrl = pr.icon, pending = true,
                    )
                    if (batch.size >= BATCH) {
                        runBlocking { epgDao.insertPrograms(batch.toList()) }
                        count += batch.size
                        batch.clear()
                        onProgress(count)
                    }
                }
            }
            if (batch.isNotEmpty()) { epgDao.insertPrograms(batch.toList()); count += batch.size }
            epgChannels.map { EpgChannelEntity(playlistId, it.id, it.displayNames.joinToString(NAME_SEP).ifEmpty { it.id }, it.icon, pending = true) }
                .chunked(BATCH).forEach { epgDao.insertChannels(it) }
            if (count == 0 && epgChannels.isEmpty()) throw ImportException(PlaylistRepository.EMPTY)
            epgDao.commitImport(playlistId)
            rematch(playlistId, epgChannels)
            onProgress(count)
            count
        } catch (t: Throwable) {
            withContext(NonCancellable) { epgDao.abortImport(playlistId) }
            throw t
        }
    }

    /** Auto-matches every live channel of the playlist; manual mappings are preserved. */
    suspend fun rematch(playlistId: Long, known: List<XmltvChannel>? = null) = withContext(Dispatchers.IO) {
        val epg = known ?: epgDao.channels(playlistId).map { XmltvChannel(it.id, it.displayName.split(NAME_SEP), it.icon) }
        if (epg.isEmpty()) return@withContext
        val matcher = EpgMatcher(epg)
        val mappings = channelDao.allLive(playlistId).mapNotNull { c ->
            matcher.match(MatchChannel(c.uniqueKey, c.tvgId, c.tvgName, c.name))?.let { EpgMappingEntity(playlistId, c.uniqueKey, it) }
        }.distinctBy { it.uniqueKey }
        epgDao.replaceAutoMappings(playlistId, mappings)
    }

    suspend fun setManualMapping(playlistId: Long, uniqueKey: String, epgChannelId: String) =
        epgDao.upsertMapping(EpgMappingEntity(playlistId, uniqueKey, epgChannelId, manual = true))

    suspend fun searchEpgChannels(playlistId: Long, q: String): List<EpgChannelEntity> =
        epgDao.searchChannels(playlistId, q.trim()).map { it.copy(displayName = it.displayName.substringBefore(NAME_SEP)) }

    suspend fun currentMapping(playlistId: Long, uniqueKey: String) = epgDao.mapping(playlistId, uniqueKey)

    fun gridChannels(playlistId: Long, groupId: Long?): Flow<List<GridChannel>> = epgDao.gridChannels(playlistId, groupId)

    /** Programmes overlapping [fromReal, toReal) in real time. */
    fun programs(playlistId: Long, uniqueKey: String, fromReal: Long, toReal: Long): Flow<List<Program>> =
        epgDao.programsFor(playlistId, uniqueKey, clock.toSource(fromReal), clock.toSource(toReal)).map { l -> l.map { it.toProgram() } }

    suspend fun programsOnce(playlistId: Long, uniqueKey: String, fromReal: Long, toReal: Long): List<Program> =
        epgDao.programsOnce(playlistId, uniqueKey, clock.toSource(fromReal), clock.toSource(toReal)).map { it.toProgram() }

    suspend fun program(id: Long): Program? = epgDao.program(id)?.toProgram()

    /** Current and next programme, re-evaluated every minute. */
    fun nowNext(playlistId: Long, uniqueKey: String): Flow<NowNext> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(TICK_MS)
        }
    }.flatMapLatest { now ->
        programs(playlistId, uniqueKey, now - HOUR_MS * 6, now + HOUR_MS * 12).map { list ->
            val i = list.indexOfFirst { it.isLive(now) }
            if (i >= 0) NowNext(list[i], list.getOrNull(i + 1)) else NowNext(null, list.firstOrNull { it.start > now })
        }
    }

    suspend fun cleanup() {
        epgDao.deleteOlderThan(clock.sourceNow() - DAY_MS * 2)
        epgDao.deletePastReminders(System.currentTimeMillis() - HOUR_MS)
    }

    private fun ProgramEntity.toProgram() = Program(id, epgChannelId, clock.toReal(start), clock.toReal(end), title, description, category, iconUrl)

    private fun open(p: PlaylistEntity, configured: String): InputStream {
        val url = if (configured == XtreamAccounts.XTREAM_EPG) {
            accounts.of(p)?.let(XtreamUrls::xmltv) ?: throw ImportException("missing Xtream credentials")
        } else {
            configured
        }
        val raw: InputStream = if (url.startsWith("content://") || url.startsWith("file://")) {
            ctx.contentResolver.openInputStream(Uri.parse(url)) ?: throw ImportException("cannot open file")
        } else {
            val req = Request.Builder().url(url).header("User-Agent", p.userAgent ?: HttpDefaults.USER_AGENT)
                .cleartextTag(p.allowCleartext).build()
            val resp = http.newCall(req).execute()
            if (!resp.isSuccessful) { resp.close(); throw ImportException("HTTP ${resp.code}") }
            resp.body?.byteStream() ?: throw ImportException("empty body")
        }
        // .xml.gz is usually served without Content-Encoding, so sniff the gzip magic bytes.
        val buffered = BufferedInputStream(raw, BUFFER)
        buffered.mark(2)
        val b1 = buffered.read()
        val b2 = buffered.read()
        buffered.reset()
        return if (b1 == 0x1f && b2 == 0x8b) BufferedInputStream(GZIPInputStream(buffered, BUFFER), BUFFER) else buffered
    }

    companion object {
        const val NO_EPG = "NO_EPG"
        private const val NAME_SEP = "\u001F"
        private const val BATCH = 1000
        private const val BUFFER = 64 * 1024
        private const val TICK_MS = 60_000L
        const val HOUR_MS = 3_600_000L
        const val DAY_MS = 24 * HOUR_MS
    }
}
