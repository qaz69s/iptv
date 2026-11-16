package com.streambox.player.core.parser.m3u

/**
 * Playlist-level attributes from the `#EXTM3U` line.
 * [epgUrls] merges `url-tvg`, `x-tvg-url` and `tvg-url` (each may hold a comma separated list).
 */
data class M3uHeader(val attributes: Map<String, String> = emptyMap(), val epgUrls: List<String> = emptyList())

/** One playable entry. Attribute keys are lower-cased; unknown attributes are kept in [attributes]. */
data class M3uEntry(
    val url: String,
    val name: String,
    val durationSeconds: Double?,
    val attributes: Map<String, String>,
    val tvgId: String?,
    val tvgName: String?,
    val tvgLogo: String?,
    val groupTitle: String?,
    val tvgChno: Int?,
    val catchup: String?,
    val catchupSource: String?,
    val catchupDays: Int?,
    val timeshift: Int?,
    val userAgent: String?,
    val referrer: String?,
    /** Stable identity inside a playlist: `tvg:<tvg-id>` when present, otherwise `url:<hash of url>`. */
    val uniqueKey: String,
    /** 1-based line number of the URL line (useful for diagnostics). */
    val lineNumber: Long,
)

/** Counters collected while parsing; malformed lines are skipped, never fatal. */
data class M3uParseStats(
    val lines: Long = 0,
    val entries: Long = 0,
    val malformedLines: Long = 0,
    val commentLines: Long = 0,
    val hasHeader: Boolean = false,
)

data class M3uParseResult(val header: M3uHeader, val stats: M3uParseStats)
