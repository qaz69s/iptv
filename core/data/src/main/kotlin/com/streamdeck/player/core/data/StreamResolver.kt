package com.streamdeck.player.core.data

import com.streamdeck.player.core.database.ChannelEntity
import com.streamdeck.player.core.database.PlaylistDao
import com.streamdeck.player.core.database.PlaylistType
import com.streamdeck.player.core.network.XtreamUrls
import com.streamdeck.player.feature.catchup.CatchupRequest
import com.streamdeck.player.feature.catchup.CatchupType
import com.streamdeck.player.feature.catchup.CatchupUrlBuilder
import com.streamdeck.player.feature.catchup.XtreamCatchup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

/** Everything the player needs to open a stream; independent of Media3 so the data layer stays player-agnostic. */
data class ResolvedStream(
    val url: String,
    val userAgent: String?,
    val referrer: String?,
    val headers: Map<String, String>,
    val allowCleartext: Boolean,
)

class StreamUnavailableException(message: String) : IllegalStateException(message)

@Singleton
class StreamResolver @Inject constructor(private val playlistDao: PlaylistDao, private val accounts: XtreamAccounts) {
    /** True when the channel declares catch-up via `catchup`/`catchup-days` (M3U) or `tv_archive` (Xtream). */
    fun supportsCatchup(c: ChannelEntity): Boolean = !c.catchupType.isNullOrBlank() || (c.catchupDays ?: 0) > 0

    /** Oldest start time (real UTC ms) still inside the archive window. */
    fun archiveStart(c: ChannelEntity, now: Long = System.currentTimeMillis()): Long =
        now - (c.catchupDays ?: DEFAULT_ARCHIVE_DAYS).coerceAtLeast(1) * 86_400_000L

    /** Live URL. Xtream URLs are assembled here with the decrypted credentials so they are never stored. */
    suspend fun resolve(c: ChannelEntity): ResolvedStream {
        val p = playlistDao.get(c.playlistId)
        val url = if (c.streamUrl.startsWith(XtreamAccounts.LIVE_PREFIX)) {
            val a = p?.let(accounts::of) ?: throw StreamUnavailableException("missing Xtream credentials")
            XtreamUrls.live(a, c.streamUrl.removePrefix(XtreamAccounts.LIVE_PREFIX).toLong(), XtreamAccounts.LIVE_EXT)
        } else {
            c.streamUrl
        }
        return ResolvedStream(
            url = url,
            userAgent = c.userAgent ?: p?.userAgent,
            referrer = c.referrer,
            headers = parseHeaders(p?.headersJson),
            allowCleartext = p?.allowCleartext ?: false,
        )
    }

    /**
     * Catch-up / time-shift URL for [startMs, endMs] in **real** UTC time (EPG offset already applied).
     * @throws com.streamdeck.player.feature.catchup.CatchupException when the configuration cannot produce a URL.
     */
    suspend fun resolveCatchup(c: ChannelEntity, startMs: Long, endMs: Long): ResolvedStream {
        val base = resolve(c)
        val p = playlistDao.get(c.playlistId)
        val xc = if (p?.type == PlaylistType.XTREAM) {
            accounts.of(p)?.let { a -> XtreamCatchup(a.base, a.username, a.password, c.streamUrl.removePrefix(XtreamAccounts.LIVE_PREFIX)) }
        } else {
            null
        }
        val serverZone = p?.serverTimezone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneOffset.UTC
        val req = CatchupRequest(
            streamUrl = base.url,
            type = CatchupType.from(c.catchupType),
            source = c.catchupSource,
            start = Instant.ofEpochMilli(startMs),
            end = Instant.ofEpochMilli(maxOf(endMs, startMs)),
            now = Instant.now(),
            xtream = xc,
            xcZone = serverZone,
        )
        return base.copy(url = CatchupUrlBuilder.build(req))
    }

    private fun parseHeaders(json: String?): Map<String, String> = if (json.isNullOrBlank()) {
        emptyMap()
    } else {
        runCatching {
            Json.parseToJsonElement(json).jsonObject.mapValues { it.value.jsonPrimitive.content }
        }.getOrDefault(emptyMap())
    }

    private companion object { const val DEFAULT_ARCHIVE_DAYS = 1 }
}
