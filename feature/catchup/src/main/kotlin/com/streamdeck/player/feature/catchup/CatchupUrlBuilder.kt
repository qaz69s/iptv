package com.streamdeck.player.feature.catchup

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Catch-up flavours from M3U `catchup=` (spec §4.5). Unknown values fall back to [DEFAULT]. */
enum class CatchupType {
    DEFAULT,
    APPEND,
    SHIFT,
    FLUSSONIC,
    XC,
    VOD;

    companion object {
        fun from(raw: String?): CatchupType? = when (raw?.trim()?.lowercase()) {
            null, "" -> null
            "default" -> DEFAULT
            "append" -> APPEND
            "shift", "timeshift" -> SHIFT
            "flussonic", "flussonic-hls", "flussonic-ts", "fs" -> FLUSSONIC
            "xc", "xtream" -> XC
            "vod" -> VOD
            else -> DEFAULT
        }
    }
}

/** Xtream Codes credentials and stream id for `xc` catch-up. */
data class XtreamCatchup(val server: String, val username: String, val password: String, val streamId: String)

/**
 * All times are UTC instants. [zone] is used for human-readable placeholders (`{Y}`, `${start:yyyyMMdd}` …),
 * [xcZone] for the Xtream `YYYY-MM-DD:HH-MM` start (servers expect their own local time; default UTC).
 */
data class CatchupRequest(
    val streamUrl: String,
    val type: CatchupType?,
    val source: String?,
    val start: Instant,
    val end: Instant,
    val now: Instant,
    val zone: ZoneId = ZoneId.systemDefault(),
    val xtream: XtreamCatchup? = null,
    val xcZone: ZoneId = ZoneOffset.UTC,
)

class CatchupException(message: String) : IllegalArgumentException(message)

/**
 * Builds catch-up / time-shift URLs. Supported placeholders (case-sensitive):
 * - `{utc}` `${start}` start (unix s) · `{utcend}` `${end}` end · `{lutc}` `${now}` `${timestamp}` now
 * - `{duration}` `${duration}` seconds; `{duration:60}` divided (minutes) · `{offset}` `${offset}` now − start; `{offset:60}`
 * - `{Y} {m} {d} {H} {M} {S}` start in [CatchupRequest.zone]
 * - `{utc:Y-m-d H:M:S}` / `{utcend:…}` / `{lutc:…}` strftime-like letters (Y m d H M S)
 * - `${start:pattern}` / `${end:pattern}` / `${timestamp:pattern}` java.time patterns, e.g. `${start:yyyyMMddHHmmss}`
 */
object CatchupUrlBuilder {
    fun build(r: CatchupRequest): String {
        require(!r.end.isBefore(r.start)) { "end before start" }
        val type = r.type ?: if (r.source.isNullOrBlank()) CatchupType.SHIFT else CatchupType.DEFAULT
        return when (type) {
            CatchupType.DEFAULT, CatchupType.VOD -> {
                val src = r.source?.takeIf { it.isNotBlank() } ?: return build(r.copy(type = CatchupType.SHIFT))
                expand(if (src.startsWith("?") || src.startsWith("&")) appendQuery(r.streamUrl, src) else src, r)
            }
            CatchupType.APPEND -> {
                val src = r.source?.takeIf { it.isNotBlank() } ?: DEFAULT_SHIFT
                expand(appendQuery(r.streamUrl, src), r)
            }
            CatchupType.SHIFT -> expand(appendQuery(r.streamUrl, DEFAULT_SHIFT), r)
            CatchupType.FLUSSONIC -> flussonic(r)
            CatchupType.XC -> xtream(r)
        }
    }

    /** Joins a query fragment (`?a=b` or `&a=b` or `a=b`) to a URL with the right separator. */
    internal fun appendQuery(url: String, fragment: String): String {
        val f = fragment.trimStart('?', '&')
        if (f.isEmpty()) return url
        return url + (if (url.contains('?')) "&" else "?") + f
    }

    private fun flussonic(r: CatchupRequest): String {
        val m = FLUSSONIC_RE.matchEntire(r.streamUrl) ?: throw CatchupException("not a Flussonic URL")
        val (base, file, query) = Triple(m.groupValues[1], m.groupValues[2], m.groupValues[3])
        val start = r.start.epochSecond
        val duration = r.end.epochSecond - r.start.epochSecond
        return when {
            file == "mpegts" -> "$base/timeshift_abs-$start.ts$query"
            file.endsWith(".m3u8") -> "$base/${file.removeSuffix(".m3u8")}-$start-$duration.m3u8$query"
            else -> throw CatchupException("unsupported Flussonic stream: $file")
        }
    }

    private fun xtream(r: CatchupRequest): String {
        val x = r.xtream ?: parseXtream(r.streamUrl) ?: throw CatchupException("missing Xtream credentials")
        val minutes = ((r.end.epochSecond - r.start.epochSecond + 59) / 60).coerceAtLeast(1)
        val startText = XC_FORMAT.format(r.start.atZone(r.xcZone))
        return "${x.server.trimEnd('/')}/timeshift/${x.username}/${x.password}/$minutes/$startText/${x.streamId}.ts"
    }

    /** Derives credentials from `http://host[:port]/[live/]user/pass/id[.ext]`. */
    internal fun parseXtream(url: String): XtreamCatchup? {
        val m = XTREAM_RE.matchEntire(url) ?: return null
        return XtreamCatchup(m.groupValues[1], m.groupValues[2], m.groupValues[3], m.groupValues[4])
    }

    internal fun expand(template: String, r: CatchupRequest): String {
        val start = r.start.epochSecond
        val end = r.end.epochSecond
        val now = r.now.epochSecond
        val local = r.start.atZone(r.zone)
        var out = JAVA_PATTERN.replace(template) { m ->
            val instant = when (m.groupValues[1]) {
                "start" -> r.start
                "end" -> r.end
                else -> r.now
            }
            runCatching { DateTimeFormatter.ofPattern(m.groupValues[2]).format(instant.atZone(r.zone)) }
                .getOrElse { throw CatchupException("bad date pattern: ${m.groupValues[2]}") }
        }
        out = STRFTIME.replace(out) { m ->
            val instant = when (m.groupValues[1]) {
                "utc" -> r.start
                "utcend" -> r.end
                else -> r.now
            }
            strftime(m.groupValues[2], instant, r.zone)
        }
        out = DIVIDED.replace(out) { m ->
            val divisor = m.groupValues[3].toLong().coerceAtLeast(1)
            val value = if (m.groupValues[2] == "duration") end - start else now - start
            (value / divisor).toString()
        }
        val simple = linkedMapOf(
            "\${start}" to start, "\${end}" to end, "\${timestamp}" to now, "\${now}" to now,
            "\${duration}" to end - start, "\${offset}" to now - start,
            "{utcend}" to end, "{utc}" to start, "{lutc}" to now, "{duration}" to end - start, "{offset}" to now - start,
            "{timestamp}" to now,
        )
        simple.forEach { (k, v) -> out = out.replace(k, v.toString()) }
        out = out.replace("{Y}", "%04d".format(local.year)).replace("{m}", "%02d".format(local.monthValue))
            .replace("{d}", "%02d".format(local.dayOfMonth)).replace("{H}", "%02d".format(local.hour))
            .replace("{M}", "%02d".format(local.minute)).replace("{S}", "%02d".format(local.second))
        return out
    }

    private fun strftime(fmt: String, instant: Instant, zone: ZoneId): String {
        val t = instant.atZone(zone)
        val sb = StringBuilder()
        fmt.forEach { c ->
            sb.append(
                when (c) {
                    'Y' -> "%04d".format(t.year)
                    'm' -> "%02d".format(t.monthValue)
                    'd' -> "%02d".format(t.dayOfMonth)
                    'H' -> "%02d".format(t.hour)
                    'M' -> "%02d".format(t.minute)
                    'S' -> "%02d".format(t.second)
                    else -> c.toString()
                },
            )
        }
        return sb.toString()
    }

    private const val DEFAULT_SHIFT = "utc={utc}&lutc={lutc}"
    private val JAVA_PATTERN = Regex("""\$\{(start|end|timestamp|now):([^}]+)\}""")
    private val STRFTIME = Regex("""\{(utc|utcend|lutc):([^}]+)\}""")
    private val DIVIDED = Regex("""(\$)?\{(duration|offset):(\d+)\}""")
    private val FLUSSONIC_RE = Regex("""^(https?://.+?)/([^/?]+)(\?.*)?$""")
    private val XTREAM_RE = Regex("""^(https?://[^/]+)/(?:live/)?([^/]+)/([^/]+)/(\d+)(?:\.\w+)?$""")
    private val XC_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd:HH-mm")
}

/** EPG offset helper (spec §4.5): real = source + offset. Kept here so catch-up tests can cover negative offsets. */
object EpgOffset {
    fun toReal(sourceMs: Long, offsetMinutes: Int): Long = sourceMs + offsetMinutes * 60_000L
    fun toSource(realMs: Long, offsetMinutes: Int): Long = realMs - offsetMinutes * 60_000L
}
