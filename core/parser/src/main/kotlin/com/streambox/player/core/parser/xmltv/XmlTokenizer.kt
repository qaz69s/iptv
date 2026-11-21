package com.streambox.player.core.parser.xmltv

import java.io.Reader

/**
 * Minimal pull-style XML tokenizer for XMLTV, written without dependencies so the JVM tests and the
 * app behave identically.
 *
 * Emits start tags (with attributes), end tags and text. Self-closing tags produce a start tag
 * followed by an end tag. Handled: the XML declaration and processing instructions (skipped),
 * comments (skipped), DOCTYPE with an internal subset (skipped), CDATA (text), the five XML entities,
 * `&nbsp;` and numeric character references. Unknown entities stay as written. The tokenizer never
 * throws on malformed markup: a stray `<` becomes text and a truncated document simply ends.
 * Reads through its own buffer, so memory does not depend on the input size; text of a single node is
 * capped at [MAX_TEXT] characters.
 */
internal class XmlTokenizer(private val reader: Reader) {
    enum class Event { START_TAG, END_TAG, TEXT, END_DOCUMENT }

    private val buffer = CharArray(BUFFER_SIZE)
    private var position = 0
    private var limit = 0
    private var eof = false

    private val scratch = StringBuilder()
    private val entity = StringBuilder()
    private var pendingEndTag: String? = null

    /** Tag name of the current START_TAG / END_TAG event. */
    var name: String = ""
        private set

    /** Text of the current TEXT event. */
    var text: String = ""
        private set

    private val attributeNames = ArrayList<String>()
    private val attributeValues = ArrayList<String>()

    /** Value of attribute [attribute] of the current START_TAG, or null. */
    fun attribute(attribute: String): String? {
        val index = attributeNames.indexOf(attribute)
        return if (index < 0) null else attributeValues[index]
    }

    fun next(): Event {
        pendingEndTag?.let {
            pendingEndTag = null
            name = it
            return Event.END_TAG
        }
        while (true) {
            val c = read()
            val event = when {
                c < 0 -> Event.END_DOCUMENT
                c == '<'.code -> markup()
                else -> readText(c)
            }
            if (event != null) return event
        }
    }

    // ---- markup ------------------------------------------------------------------------------------

    /** After `<`. Returns null for skipped markup (comments, declarations, processing instructions). */
    private fun markup(): Event? {
        val c = peek()
        return when {
            c < 0 -> Event.END_DOCUMENT

            c == '!'.code -> declaration()

            c == '?'.code -> {
                skipPast("?>")
                null
            }

            c == '/'.code -> {
                read()
                endTag()
            }

            isNameStart(c) -> startTag()

            else -> {
                // A lone '<' (e.g. "a < b" in a sloppy feed) is kept as text.
                text = "<"
                Event.TEXT
            }
        }
    }

    private fun declaration(): Event? {
        read() // '!'
        return when {
            startsWith("--") -> {
                skip(2)
                skipPast("-->")
                null
            }

            startsWith("[CDATA[") -> {
                skip(CDATA_OPEN_LENGTH)
                cdata()
            }

            else -> {
                skipDeclaration()
                null
            }
        }
    }

    private fun cdata(): Event {
        scratch.setLength(0)
        var c = read()
        while (c >= 0 && !(c == ']'.code && startsWith("]>"))) {
            appendCapped(c.toChar())
            c = read()
        }
        if (c >= 0) skip(2)
        text = scratch.toString()
        return Event.TEXT
    }

    /** `<!DOCTYPE ... [ internal subset ] >` and other `<!...>`: skipped, honouring quotes and brackets. */
    private fun skipDeclaration() {
        var depth = 0
        var quote = 0
        while (true) {
            val c = read()
            if (c < 0) return
            when {
                quote != 0 -> if (c == quote) quote = 0
                c == '"'.code || c == '\''.code -> quote = c
                c == '['.code -> depth++
                c == ']'.code -> depth = maxOf(0, depth - 1)
                c == '>'.code && depth == 0 -> return
            }
        }
    }

    private fun endTag(): Event {
        name = readName()
        skipPast(">")
        return Event.END_TAG
    }

    private fun startTag(): Event {
        name = readName()
        attributeNames.clear()
        attributeValues.clear()
        var open = true
        while (open) {
            skipWhitespace()
            val c = read()
            when {
                c < 0 || c == '>'.code -> open = false

                c == '/'.code -> {
                    skipPast(">")
                    pendingEndTag = name
                    open = false
                }

                else -> readAttribute(c)
            }
        }
        return Event.START_TAG
    }

    private fun readAttribute(first: Int) {
        scratch.setLength(0)
        scratch.append(first.toChar())
        while (!isNameEnd(peek()) && peek() != '='.code) {
            scratch.append(read().toChar())
        }
        val attributeName = scratch.toString()
        skipWhitespace()
        val value = if (peek() == '='.code) {
            read()
            skipWhitespace()
            readAttributeValue()
        } else {
            ""
        }
        attributeNames += attributeName
        attributeValues += value
    }

    private fun readAttributeValue(): String {
        scratch.setLength(0)
        val quote = peek()
        if (quote == '"'.code || quote == '\''.code) {
            read()
            while (true) {
                val c = read()
                if (c < 0 || c == quote) break
                if (c == '&'.code) appendEntity() else appendCapped(c.toChar())
            }
        } else {
            while (!isNameEnd(peek()) || peek() == '/'.code) {
                val c = read()
                if (c == '&'.code) appendEntity() else appendCapped(c.toChar())
            }
        }
        return scratch.toString()
    }

    private fun readName(): String {
        scratch.setLength(0)
        while (!isNameEnd(peek())) {
            scratch.append(read().toChar())
        }
        return scratch.toString()
    }

    // ---- text and entities -------------------------------------------------------------------------

    private fun readText(first: Int): Event {
        scratch.setLength(0)
        var c = first
        while (true) {
            if (c == '&'.code) {
                appendEntity()
            } else if (c != BOM) {
                appendCapped(c.toChar())
            }
            val following = peek()
            if (following < 0 || following == '<'.code) break
            c = read()
        }
        text = scratch.toString()
        return Event.TEXT
    }

    /** After `&`: appends the decoded entity to [scratch], or the raw text when it is not one. */
    private fun appendEntity() {
        entity.setLength(0)
        var terminated = false
        while (!terminated && entity.length < MAX_ENTITY_LENGTH && isEntityChar(peek())) {
            val c = read()
            if (c == ';'.code) terminated = true else entity.append(c.toChar())
        }
        val decoded = if (terminated) decodeEntity(entity) else null
        when {
            decoded != null -> decoded.forEach(::appendCapped)
            terminated -> "&$entity;".forEach(::appendCapped)
            else -> "&$entity".forEach(::appendCapped)
        }
    }

    // ---- low-level reading -------------------------------------------------------------------------

    private fun appendCapped(c: Char) {
        if (scratch.length < MAX_TEXT) scratch.append(c)
    }

    private fun fill(): Boolean {
        val n = if (eof) -1 else reader.read(buffer, 0, buffer.size)
        if (n > 0) {
            position = 0
            limit = n
        } else {
            eof = true
        }
        return n > 0
    }

    private fun read(): Int {
        if (position >= limit && !fill()) return -1
        return buffer[position++].code
    }

    private fun peek(): Int {
        if (position >= limit && !fill()) return -1
        return buffer[position].code
    }

    /** Whether the upcoming characters are [prefix]; may refill the buffer, never consumes. */
    private fun startsWith(prefix: String): Boolean {
        if (limit - position < prefix.length) compact()
        return limit - position >= prefix.length && prefix.indices.all { buffer[position + it] == prefix[it] }
    }

    /** Moves the unread tail to the front of the buffer and reads more after it. */
    private fun compact() {
        val remaining = limit - position
        System.arraycopy(buffer, position, buffer, 0, remaining)
        position = 0
        limit = remaining
        while (!eof && limit < buffer.size) {
            val n = reader.read(buffer, limit, buffer.size - limit)
            if (n <= 0) {
                eof = true
            } else {
                limit += n
                if (limit - position >= MIN_LOOKAHEAD) break
            }
        }
    }

    private fun skip(count: Int) {
        repeat(count) { read() }
    }

    private fun skipPast(terminator: String) {
        while (true) {
            val c = read()
            if (c < 0) return
            if (c == terminator[0].code && startsWith(terminator.substring(1))) {
                skip(terminator.length - 1)
                return
            }
        }
    }

    private fun skipWhitespace() {
        while (true) {
            val c = peek()
            if (c < 0 || !isWhitespace(c)) return
            read()
        }
    }

    companion object {
        /** Longest text kept for one node (a description); the rest is dropped. */
        const val MAX_TEXT = 16 * 1024
        private const val BUFFER_SIZE = 64 * 1024
        private const val MAX_ENTITY_LENGTH = 10
        private const val CDATA_OPEN_LENGTH = 7
        private const val MIN_LOOKAHEAD = 16
        private const val BOM = 0xFEFF
        private const val HEX_RADIX = 16
        private const val DECIMAL_RADIX = 10
        private const val MAX_CODE_POINT = 0x10FFFF

        private fun isWhitespace(c: Int) = c == ' '.code || c == '\n'.code || c == '\r'.code || c == '\t'.code

        private fun isNameStart(c: Int) = Character.isLetter(c) || c == '_'.code || c == ':'.code

        /** End of a tag or attribute name (also end of input). */
        private fun isNameEnd(c: Int) = c < 0 || c == '>'.code || c == '/'.code || isWhitespace(c)

        private fun isEntityChar(c: Int) = c >= 0 && c != '<'.code && c != '&'.code && !isWhitespace(c)

        private fun isSurrogate(codePoint: Int) = codePoint in Char.MIN_SURROGATE.code..Char.MAX_SURROGATE.code

        internal fun decodeEntity(entity: CharSequence): String? = when {
            entity.isEmpty() -> null
            entity[0] == '#' -> decodeNumeric(entity)
            else -> NAMED_ENTITIES[entity.toString()]
        }

        private fun decodeNumeric(entity: CharSequence): String? {
            val hex = entity.length > 1 && (entity[1] == 'x' || entity[1] == 'X')
            val digits = entity.substring(if (hex) 2 else 1)
            val codePoint = digits.toIntOrNull(if (hex) HEX_RADIX else DECIMAL_RADIX) ?: return null
            val valid = codePoint in 1..MAX_CODE_POINT && !isSurrogate(codePoint)
            return if (valid) String(Character.toChars(codePoint)) else null
        }

        private val NAMED_ENTITIES = mapOf(
            "amp" to "&",
            "lt" to "<",
            "gt" to ">",
            "quot" to "\"",
            "apos" to "'",
            "nbsp" to "\u00A0",
        )
    }
}
