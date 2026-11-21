package com.streambox.player.core.parser.xmltv

import java.io.Reader

/**
 * Streaming XMLTV parser on top of [XmlTokenizer]. Pull-based ([next]) so callers can suspend
 * between items (database batches, cancellation); [parse] and [items] are conveniences. Memory stays
 * bounded: only the item being read, plus at most one programme per channel that is waiting for its
 * stop time, is held.
 *
 * - `<channel id>`: display names (all, the [preferredLanguage] one first) and the first icon.
 * - `<programme start stop channel>`: title, description and category (the first of each, or the one
 *   in [preferredLanguage]) and the first icon. Times go through [XmltvTime] (UTC epoch millis).
 * - A programme without a (valid) stop ends where the next programme of the same channel starts; it
 *   is emitted when that programme is read, or skipped at the end of the document.
 * - Malformed items are skipped and counted in [stats]; unknown elements are ignored.
 */
class XmltvParser(reader: Reader, private val preferredLanguage: String? = null) {
    private val tokenizer = XmlTokenizer(reader)
    private val output = ArrayDeque<XmltvItem>()
    private val waitingForStop = HashMap<String, XmltvProgramme>()
    private var ended = false
    private var finished = false

    private var channels = 0L
    private var programmes = 0L
    private var skippedChannels = 0L
    private var skippedProgrammes = 0L

    val stats: XmltvParseStats
        get() = XmltvParseStats(channels, programmes, skippedChannels, skippedProgrammes)

    /** Returns the next channel or programme, or null at the end of the document. */
    fun next(): XmltvItem? {
        while (output.isEmpty() && !finished) {
            if (ended) finish() else step()
        }
        return output.removeFirstOrNull()
    }

    /** Consumes the whole document, invoking [onItem] for each item. */
    fun parse(onItem: (XmltvItem) -> Unit): XmltvParseStats {
        while (true) {
            val item = next() ?: break
            onItem(item)
        }
        return stats
    }

    /** Lazy sequence of items; the reader is consumed as the sequence is iterated. */
    fun items(): Sequence<XmltvItem> = generateSequence { next() }

    private fun step() {
        when (tokenizer.next()) {
            XmlTokenizer.Event.END_DOCUMENT -> ended = true

            XmlTokenizer.Event.START_TAG -> when (tokenizer.name) {
                "channel" -> readChannel()
                "programme" -> readProgramme()
                else -> Unit // <tv> and anything else: their children are read as they come
            }

            else -> Unit
        }
    }

    private fun finish() {
        skippedProgrammes += waitingForStop.size
        waitingForStop.clear()
        finished = true
    }

    private fun readChannel() {
        val id = tokenizer.attribute("id")?.trim().orEmpty()
        val names = ArrayList<String>()
        var preferredIndex = -1
        var icon: String? = null
        forEachChild("channel") { child ->
            when (child) {
                "display-name" -> {
                    val matches = languageMatches(tokenizer.attribute("lang"))
                    readText(child)?.let(::collapse)?.takeIf { it.isNotEmpty() }?.let { name ->
                        val index = names.indexOf(name).takeIf { it >= 0 } ?: names.size.also { names += name }
                        if (matches && preferredIndex < 0) preferredIndex = index
                    }
                }

                "icon" -> {
                    if (icon == null) icon = tokenizer.attribute("src")?.trim()?.takeIf { it.isNotEmpty() }
                    skipElement(child)
                }

                else -> skipElement(child)
            }
        }
        if (ended || id.isEmpty()) {
            skippedChannels++
            return
        }
        if (preferredIndex > 0 && preferredIndex < names.size) names.add(0, names.removeAt(preferredIndex))
        output += XmltvChannel(id, names, icon)
        channels++
    }

    private fun readProgramme() {
        val channel = tokenizer.attribute("channel")?.trim().orEmpty()
        val start = tokenizer.attribute("start")?.let(XmltvTime::parse)
        val stop = tokenizer.attribute("stop")?.let(XmltvTime::parse)
        val title = LanguagePick()
        val description = LanguagePick()
        val category = LanguagePick()
        var icon: String? = null
        forEachChild("programme") { child ->
            val lang = tokenizer.attribute("lang")
            when (child) {
                "title" -> title.offer(readText(child)?.let(::collapse), lang)

                "desc" -> description.offer(readText(child)?.let(::tidyParagraphs), lang)

                "category" -> category.offer(readText(child)?.let(::collapse), lang)

                "icon" -> {
                    if (icon == null) icon = tokenizer.attribute("src")?.trim()?.takeIf { it.isNotEmpty() }
                    skipElement(child)
                }

                else -> skipElement(child)
            }
        }
        val titleText = title.value
        val complete = !ended && channel.isNotEmpty()
        if (!complete || start == null || titleText == null) {
            skippedProgrammes++
            return
        }
        accept(XmltvProgramme(channel, start, stop ?: start, titleText, description.value, category.value, icon), stop)
    }

    /** [programme] carries a placeholder stop when [stop] is null. */
    private fun accept(programme: XmltvProgramme, stop: Long?) {
        waitingForStop.remove(programme.channelId)?.let { waiting ->
            if (programme.start > waiting.start) emit(waiting.copy(stop = programme.start)) else skippedProgrammes++
        }
        when {
            stop == null -> waitingForStop[programme.channelId] = programme
            stop > programme.start -> emit(programme)
            else -> skippedProgrammes++
        }
    }

    private fun emit(programme: XmltvProgramme) {
        output += programme
        programmes++
    }

    // ---- element helpers ---------------------------------------------------------------------------

    /**
     * Calls [onChild] for every child start tag of the element [parent] whose start tag was just read;
     * [onChild] must consume the child up to its end tag. Returns after the parent's end tag (or at the
     * end of the document, which sets [ended]).
     */
    private inline fun forEachChild(parent: String, onChild: (String) -> Unit) {
        var open = true
        while (open) {
            when (tokenizer.next()) {
                XmlTokenizer.Event.START_TAG -> onChild(tokenizer.name)

                XmlTokenizer.Event.END_TAG -> if (tokenizer.name == parent) open = false

                XmlTokenizer.Event.END_DOCUMENT -> {
                    ended = true
                    open = false
                }

                XmlTokenizer.Event.TEXT -> Unit
            }
        }
    }

    /**
     * Text content of the element [name] whose start tag was just read (nested elements' text
     * included); null when empty. A stray end tag at the top level also ends it.
     */
    private fun readText(name: String): String? {
        val text = StringBuilder()
        var depth = 0
        var open = true
        while (open) {
            when (tokenizer.next()) {
                XmlTokenizer.Event.TEXT -> if (text.length < XmlTokenizer.MAX_TEXT) text.append(tokenizer.text)

                XmlTokenizer.Event.START_TAG -> depth++

                // The element's own end tag always closes it, even after unclosed children like <br>.
                XmlTokenizer.Event.END_TAG -> if (depth == 0 || tokenizer.name == name) open = false else depth--

                XmlTokenizer.Event.END_DOCUMENT -> {
                    ended = true
                    open = false
                }
            }
        }
        return text.toString().trim().takeIf { it.isNotEmpty() }
    }

    private fun skipElement(name: String) {
        readText(name)
    }

    private fun languageMatches(lang: String?): Boolean {
        val preferred = preferredLanguage?.takeIf { it.isNotBlank() } ?: return false
        return lang != null && lang.substringBefore('-').equals(preferred.substringBefore('-'), ignoreCase = true)
    }

    /** First non-empty value, unless one in the preferred language comes along. */
    private inner class LanguagePick {
        private var first: String? = null
        private var preferred: String? = null

        val value: String? get() = preferred ?: first

        fun offer(value: String?, lang: String?) {
            if (value.isNullOrEmpty()) return
            if (first == null) first = value
            if (preferred == null && languageMatches(lang)) preferred = value
        }
    }

    companion object {
        /** Convenience for tests and small inputs. */
        fun parse(reader: Reader, preferredLanguage: String? = null, onItem: (XmltvItem) -> Unit): XmltvParseStats =
            XmltvParser(reader, preferredLanguage).parse(onItem)

        /** Single spaces between words, no leading / trailing whitespace. */
        internal fun collapse(text: String): String {
            val builder = StringBuilder(text.length)
            var space = false
            for (c in text) {
                if (c.isWhitespace()) {
                    space = builder.isNotEmpty()
                } else {
                    if (space) builder.append(' ')
                    builder.append(c)
                    space = false
                }
            }
            return builder.toString()
        }

        /** Descriptions keep their line breaks (normalized to `\n`) but lose surrounding blanks. */
        internal fun tidyParagraphs(text: String): String = text.replace("\r\n", "\n").replace('\r', '\n').trim()
    }
}
