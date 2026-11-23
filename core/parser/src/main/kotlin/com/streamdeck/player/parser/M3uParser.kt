package com.streamdeck.player.parser

import java.io.BufferedReader
import java.io.Reader

/**
 * Streaming M3U / M3U_plus parser. Reads line-by-line, never holds the whole file in memory.
 * Malformed lines are skipped instead of failing the whole import.
 */
class M3uParser {

    private val attrRegex = Regex("""([A-Za-z0-9_-]+)\s*=\s*"([^"]*)"""")

    /**
     * @param onHeader invoked once if an #EXTM3U header is present.
     * @param onEntry invoked for every channel, in file order.
     * @return number of entries emitted.
     */
    fun parse(reader: Reader, onHeader: (M3uHeader) -> Unit = {}, onEntry: (M3uEntry) -> Unit): Int {
        val br = if (reader is BufferedReader) reader else BufferedReader(reader, BUFFER)
        var header: M3uHeader? = null
        var pending: Pending? = null
        var count = 0
        while (true) {
            val raw = br.readLine() ?: break
            val line = raw.trim().removePrefix("\uFEFF")
            when {
                line.isEmpty() -> Unit
                line.startsWith("#EXTM3U", ignoreCase = true) -> if (header == null) {
                    val a = attrs(line)
                    header = M3uHeader(
                        epgUrl = a["url-tvg"] ?: a["x-tvg-url"],
                        catchup = a["catchup"],
                        catchupSource = a["catchup-source"],
                    ).also(onHeader)
                }
                line.startsWith("#EXTINF", ignoreCase = true) -> pending = parseExtInf(line)
                line.startsWith("#EXTGRP:", ignoreCase = true) ->
                    pending = (pending ?: Pending()).copy(extGrp = line.substringAfter(':').trim())
                line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> pending = applyVlcOpt(pending ?: Pending(), line)
                line.startsWith("#") -> Unit
                else -> {
                    val p = pending
                    if (p != null) {
                        onEntry(p.toEntry(line, header))
                        count++
                    }
                    pending = null
                }
            }
        }
        return count
    }

    private fun attrs(line: String): Map<String, String> =
        attrRegex.findAll(line).associate { it.groupValues[1].lowercase() to it.groupValues[2] }

    private fun parseExtInf(line: String): Pending {
        val a = attrs(line)
        // Title starts after the first comma outside quotes, so titles may themselves contain commas.
        var inQuote = false
        var commaIdx = -1
        for (i in line.indices) {
            val ch = line[i]
            if (ch == '"') inQuote = !inQuote
            if (ch == ',' && !inQuote) { commaIdx = i; break }
        }
        val title = if (commaIdx >= 0) line.substring(commaIdx + 1).trim() else ""
        return Pending(title = title, attrs = a)
    }

    private fun applyVlcOpt(p: Pending, line: String): Pending {
        val opt = line.substringAfter(':').trim()
        val key = opt.substringBefore('=').trim().lowercase()
        val value = opt.substringAfter('=', "").trim()
        return when (key) {
            "http-user-agent" -> p.copy(userAgent = value)
            "http-referrer", "http-referer" -> p.copy(referrer = value)
            else -> p
        }
    }

    private data class Pending(
        val title: String = "",
        val attrs: Map<String, String> = emptyMap(),
        val extGrp: String? = null,
        val userAgent: String? = null,
        val referrer: String? = null,
    ) {
        fun toEntry(url: String, header: M3uHeader?): M3uEntry {
            val a = attrs
            val name = title.ifBlank { a["tvg-name"].orEmpty() }.ifBlank { url }
            return M3uEntry(
                name = name,
                url = url,
                tvgId = a["tvg-id"]?.ifBlank { null },
                tvgName = a["tvg-name"]?.ifBlank { null },
                logo = a["tvg-logo"]?.ifBlank { null },
                group = a["group-title"]?.ifBlank { null } ?: extGrp?.ifBlank { null },
                number = a["tvg-chno"]?.toIntOrNull(),
                catchup = a["catchup"] ?: header?.catchup,
                catchupSource = a["catchup-source"] ?: header?.catchupSource,
                catchupDays = a["catchup-days"]?.toIntOrNull(),
                timeshift = a["timeshift"]?.toIntOrNull(),
                userAgent = userAgent ?: a["user-agent"],
                referrer = referrer,
            )
        }
    }

    private companion object { const val BUFFER = 64 * 1024 }
}
