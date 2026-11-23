package com.streamdeck.player.feature.playlist

/**
 * Converts the “one header per line” text of the add/edit form (`Referer: https://a.b`) to the JSON object stored in
 * `PlaylistEntity.headersJson` (read by `StreamResolver`), and back. Pure Kotlin so it is unit-tested.
 */
object HeaderLines {
    /** Result of parsing: the JSON (null when there are no headers) or the 1-based line numbers that are malformed. */
    data class Parsed(val json: String?, val badLines: List<Int>)

    fun parse(text: String): Parsed {
        val pairs = ArrayList<Pair<String, String>>()
        val bad = ArrayList<Int>()
        text.lines().forEachIndexed { i, raw ->
            val line = raw.trim()
            if (line.isEmpty()) return@forEachIndexed
            val idx = line.indexOf(':')
            val name = if (idx > 0) line.substring(0, idx).trim() else ""
            if (idx <= 0 || name.isEmpty() || name.any { it.isWhitespace() }) {
                bad += i + 1
            } else {
                pairs += name to line.substring(idx + 1).trim()
            }
        }
        if (pairs.isEmpty()) return Parsed(null, bad)
        val json = pairs.joinToString(",", "{", "}") { (k, v) -> "\"${escape(k)}\":\"${escape(v)}\"" }
        return Parsed(json, bad)
    }

    /** Best-effort inverse for the edit form; tolerates the simple flat objects this app writes. */
    fun format(json: String?): String {
        if (json.isNullOrBlank()) return ""
        val body = json.trim().removePrefix("{").removeSuffix("}")
        val out = ArrayList<String>()
        val re = Regex("\"((?:\\\\.|[^\"\\\\])*)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        re.findAll(body).forEach { m -> out += unescape(m.groupValues[1]) + ": " + unescape(m.groupValues[2]) }
        return out.joinToString("\n")
    }

    private fun escape(s: String) = buildString {
        s.forEach { c ->
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\t' -> append("\\t")
                else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
            }
        }
    }

    private fun unescape(s: String) = buildString {
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (val n = s[i + 1]) {
                    'n' -> append('\n')
                    't' -> append('\t')
                    'u' -> if (i + 5 < s.length) {
                        s.substring(i + 2, i + 6).toIntOrNull(16)?.let { append(it.toChar()) }
                        i += 4
                    }
                    else -> append(n)
                }
                i += 2
            } else {
                append(c)
                i++
            }
        }
    }
}
