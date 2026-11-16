package com.streambox.player.core.parser.m3u

import java.io.BufferedReader
import java.io.Reader
import java.security.MessageDigest

/**
 * Streaming M3U / M3U8 / M3U_plus parser.
 *
 * Reads one line at a time and keeps only the state of the entry being assembled, so memory use
 * is independent of playlist size. Pull-based ([nextEntry]) so callers can suspend between entries
 * (batching into a database, cancellation); [parse] and [entries] are conveniences on top.
 *
 * Tolerated input: UTF-8 BOM, CRLF / CR line endings, blank lines, comments and unknown directives,
 * a missing `#EXTM3U` header, bare URLs without `#EXTINF`, and malformed lines (skipped and counted).
 */
class M3uParser(reader: Reader) {
    private val lines: BufferedReader = reader as? BufferedReader ?: BufferedReader(reader, BUFFER_SIZE)

    private var lineNumber = 0L
    private var entries = 0L
    private var malformed = 0L
    private var comments = 0L
    private var hasHeader = false
    private var finished = false

    private val headerAttributes = LinkedHashMap<String, String>()
    private val epgUrls = LinkedHashSet<String>()

    // State for the entry being assembled. Reset after every URL line.
    private var pendingInfo: AttributeParser.Parsed? = null
    private var pendingDuration: Double? = null
    private var pendingGroup: String? = null
    private var pendingUserAgent: String? = null
    private var pendingReferrer: String? = null

    val header: M3uHeader get() = M3uHeader(LinkedHashMap(headerAttributes), epgUrls.toList())

    val stats: M3uParseStats
        get() = M3uParseStats(
            lines = lineNumber,
            entries = entries,
            malformedLines = malformed,
            commentLines = comments,
            hasHeader = hasHeader,
        )

    /** Returns the next entry, or null at end of input. */
    @Suppress("ReturnCount", "LoopWithTooManyJumpStatements")
    fun nextEntry(): M3uEntry? {
        if (finished) return null
        while (true) {
            val raw = lines.readLine()
            if (raw == null) {
                finish()
                return null
            }
            lineNumber++
            val line = clean(raw)
            if (line.isEmpty()) continue
            if (line[0] == '#') {
                handleDirective(line)
                continue
            }
            if (!looksLikeUrl(line)) {
                malformed++
                continue
            }
            return buildEntry(line).also { entries++ }
        }
    }

    private fun clean(raw: String): String {
        var line = raw
        if (lineNumber == 1L && line.isNotEmpty() && line[0] == BOM) line = line.substring(1)
        // Handles stray CR (files with mixed endings) and surrounding whitespace.
        return line.trim()
    }

    private fun finish() {
        finished = true
        if (pendingInfo != null) malformed++ // #EXTINF without a URL at end of file
        resetPending()
    }

    @Suppress("CyclomaticComplexMethod")
    private fun handleDirective(line: String) {
        val tagEnd = line.indexOfFirst { it == ':' || it.isWhitespace() }.let { if (it < 0) line.length else it }
        val tag = line.substring(0, tagEnd).uppercase()
        val colon = if (tagEnd < line.length && line[tagEnd] == ':') tagEnd else -1
        val value = if (colon < 0) "" else line.substring(colon + 1)
        when {
            tag == "#EXTM3U" -> parseHeader(line.substring(tagEnd).removePrefix(":"))

            tag == "#EXTINF" -> {
                if (colon < 0) {
                    malformed++
                    return
                }
                if (pendingInfo != null) malformed++ // previous #EXTINF had no URL
                val parsed = AttributeParser.parse(value, leadingToken = true)
                pendingInfo = parsed
                pendingDuration = parsed.leading.toDoubleOrNull()
            }

            tag == "#EXTGRP" -> pendingGroup = value.trim().ifEmpty { null }

            tag == "#EXTVLCOPT" -> parseVlcOption(value)

            else -> comments++
        }
    }

    private fun parseHeader(rest: String) {
        hasHeader = true
        val parsed = AttributeParser.parse(rest, leadingToken = false)
        headerAttributes.putAll(parsed.attributes)
        for (key in EPG_HEADER_KEYS) {
            parsed.attributes[key]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.let(epgUrls::addAll)
        }
    }

    private fun parseVlcOption(value: String) {
        val eq = value.indexOf('=')
        if (eq <= 0) {
            malformed++
            return
        }
        val key = value.substring(0, eq).trim().lowercase()
        val optionValue = value.substring(eq + 1).trim().trim('"').ifEmpty { null }
        when (key) {
            "http-user-agent" -> pendingUserAgent = optionValue
            "http-referrer", "http-referer" -> pendingReferrer = optionValue
            else -> comments++
        }
    }

    private fun buildEntry(url: String): M3uEntry {
        val info = pendingInfo
        val attrs = info?.attributes.orEmpty()
        val tvgId = attrs["tvg-id"].nonBlank()
        val tvgName = attrs["tvg-name"].nonBlank()
        val name = info?.name.nonBlank() ?: tvgName ?: nameFromUrl(url)
        val entry = M3uEntry(
            url = url,
            name = name,
            durationSeconds = pendingDuration,
            attributes = attrs,
            tvgId = tvgId,
            tvgName = tvgName,
            tvgLogo = (attrs["tvg-logo"] ?: attrs["logo"]).nonBlank(),
            groupTitle = attrs["group-title"].nonBlank() ?: pendingGroup,
            tvgChno = (attrs["tvg-chno"] ?: attrs["channel-number"])?.trim()?.toIntOrNull(),
            catchup = (attrs["catchup"] ?: attrs["catchup-type"]).nonBlank(),
            catchupSource = attrs["catchup-source"].nonBlank(),
            catchupDays = attrs["catchup-days"]?.trim()?.toIntOrNull(),
            timeshift = (attrs["timeshift"] ?: attrs["tvg-shift"])?.trim()?.toIntOrNull(),
            userAgent = pendingUserAgent ?: attrs["user-agent"].nonBlank() ?: attrs["http-user-agent"].nonBlank(),
            referrer = pendingReferrer ?: attrs["http-referrer"].nonBlank() ?: attrs["referrer"].nonBlank(),
            uniqueKey = uniqueKey(tvgId, url),
            lineNumber = lineNumber,
        )
        resetPending()
        return entry
    }

    private fun resetPending() {
        pendingInfo = null
        pendingDuration = null
        pendingGroup = null
        pendingUserAgent = null
        pendingReferrer = null
    }

    /** Consumes the whole input, invoking [onEntry] for each entry. */
    fun parse(onEntry: (M3uEntry) -> Unit): M3uParseResult {
        while (true) {
            val entry = nextEntry() ?: break
            onEntry(entry)
        }
        return M3uParseResult(header, stats)
    }

    /** Lazy sequence of entries; the reader is consumed as the sequence is iterated. */
    fun entries(): Sequence<M3uEntry> = generateSequence { nextEntry() }

    companion object {
        private const val BUFFER_SIZE = 64 * 1024
        private const val BOM = '\uFEFF'
        private const val HASH_BYTES = 8
        private val EPG_HEADER_KEYS = listOf("url-tvg", "x-tvg-url", "tvg-url")
        private val SCHEME = Regex("^[A-Za-z][A-Za-z0-9+.-]*://\\S")

        /** Convenience for tests and small inputs. */
        fun parse(reader: Reader, onEntry: (M3uEntry) -> Unit): M3uParseResult = M3uParser(reader).parse(onEntry)

        internal fun looksLikeUrl(line: String): Boolean =
            SCHEME.containsMatchIn(line) && line.none { it == ' ' || it == '\t' }

        /** `tvg:<id>` when the entry has a tvg-id, otherwise `url:<16 hex chars of SHA-256(url)>`. */
        fun uniqueKey(tvgId: String?, url: String): String = if (tvgId != null) "tvg:$tvgId" else "url:${hashUrl(url)}"

        @Suppress("MagicNumber") // Byte-to-hex nibble arithmetic.
        fun hashUrl(url: String): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
            val builder = StringBuilder(HASH_BYTES * 2)
            for (i in 0 until HASH_BYTES) {
                val b = digest[i].toInt() and 0xFF
                builder.append(HEX[b ushr 4]).append(HEX[b and 0x0F])
            }
            return builder.toString()
        }

        private const val HEX = "0123456789abcdef"

        internal fun nameFromUrl(url: String): String {
            val path = url.substringBefore('?').substringBefore('#').trimEnd('/')
            val last = path.substringAfterLast('/')
            return if (last.isEmpty() || last.contains("://")) url else last
        }

        private fun String?.nonBlank(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
    }
}
