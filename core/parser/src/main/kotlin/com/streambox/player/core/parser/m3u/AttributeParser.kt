package com.streambox.player.core.parser.m3u

/**
 * Tokenizer for `key="value" key2=value2 ...,Display name` as found after `#EXTINF:` and `#EXTM3U`.
 *
 * Rules (tolerant, matching what real-world playlists contain):
 * - keys are case-insensitive and stored lower-cased;
 * - values may be double-quoted, single-quoted or unquoted (unquoted ends at whitespace or comma);
 * - quoted values may contain commas, spaces and the other quote character;
 * - the display name starts after the first comma that is outside quotes;
 * - an unterminated quote makes the value run to the last comma of the line, so the name survives.
 */
internal object AttributeParser {
    class Parsed(val attributes: Map<String, String>, val name: String?, val leading: String)

    /**
     * @param text everything after `#EXTINF:` (or `#EXTM3U`).
     * @param leadingToken when true, the first whitespace/comma delimited token is not an attribute
     *   (the EXTINF duration) and is returned in [Parsed.leading].
     */
    @Suppress("CyclomaticComplexMethod", "LongMethod", "NestedBlockDepth", "LoopWithTooManyJumpStatements")
    fun parse(text: String, leadingToken: Boolean): Parsed {
        val attributes = LinkedHashMap<String, String>()
        var index = 0
        val length = text.length
        var leading = ""

        fun skipSpaces() {
            while (index < length && text[index].isWhitespace()) index++
        }

        skipSpaces()
        if (leadingToken) {
            val start = index
            while (index < length && !text[index].isWhitespace() && text[index] != ',') index++
            leading = text.substring(start, index)
        }

        var name: String? = null
        while (index < length) {
            skipSpaces()
            if (index >= length) break
            val c = text[index]
            if (c == ',') {
                name = text.substring(index + 1)
                break
            }
            // Read a key up to '=' / whitespace / comma.
            val keyStart = index
            while (index < length && text[index] !in "=," && !text[index].isWhitespace()) index++
            val key = text.substring(keyStart, index).lowercase()
            if (index >= length || text[index] != '=') {
                // A bare word without '=' is noise (e.g. "-1 some junk,name"): skip it.
                continue
            }
            index++ // '='
            if (index >= length) {
                if (key.isNotEmpty()) attributes[key] = ""
                break
            }
            val quote = text[index]
            val value: String
            if (quote == '"' || quote == '\'') {
                val close = text.indexOf(quote, index + 1)
                if (close < 0) {
                    // Unterminated quote: value runs to the last comma (if any), the rest is the name.
                    val lastComma = text.lastIndexOf(',')
                    if (lastComma > index) {
                        value = text.substring(index + 1, lastComma)
                        if (key.isNotEmpty()) attributes[key] = value.trim()
                        name = text.substring(lastComma + 1)
                    } else {
                        value = text.substring(index + 1)
                        if (key.isNotEmpty()) attributes[key] = value.trim()
                    }
                    index = length
                    break
                }
                value = text.substring(index + 1, close)
                index = close + 1
            } else {
                val valueStart = index
                while (index < length && !text[index].isWhitespace() && text[index] != ',') index++
                value = text.substring(valueStart, index)
            }
            if (key.isNotEmpty()) attributes[key] = value.trim()
        }
        return Parsed(attributes, name?.trim(), leading)
    }
}
