package com.streamdeck.player.parser

import org.xmlpull.v1.XmlPullParser

data class XmltvChannel(val id: String, val displayNames: List<String>, val icon: String?)

data class XmltvProgramme(
    val channelId: String,
    val startUtcMs: Long,
    val endUtcMs: Long,
    val title: String,
    val description: String?,
    val category: String?,
    val icon: String?,
)

/**
 * Streaming XMLTV parser on top of the XmlPull API (Android's built-in parser on device, kXML2 in unit tests).
 * Never builds a DOM; programmes outside [windowStartMs, windowEndMs) are skipped (spec §4.4).
 * The caller owns the [XmlPullParser] and its input.
 */
class XmltvParser(
    private val windowStartMs: Long = Long.MIN_VALUE,
    private val windowEndMs: Long = Long.MAX_VALUE,
) {
    data class Stats(val channels: Int, val programmes: Int, val skipped: Int)

    fun parse(parser: XmlPullParser, onChannel: (XmltvChannel) -> Unit, onProgramme: (XmltvProgramme) -> Unit): Stats {
        var channels = 0
        var programmes = 0
        var skipped = 0
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "channel" -> readChannel(parser)?.let { onChannel(it); channels++ }
                    "programme" -> {
                        val p = readProgramme(parser)
                        if (p != null && p.endUtcMs > windowStartMs && p.startUtcMs < windowEndMs) { onProgramme(p); programmes++ } else skipped++
                    }
                }
            }
            event = parser.next()
        }
        return Stats(channels, programmes, skipped)
    }

    private fun readChannel(p: XmlPullParser): XmltvChannel? {
        val id = p.getAttributeValue(null, "id")?.trim()
        val names = mutableListOf<String>()
        var icon: String? = null
        val depth = p.depth
        while (true) {
            val e = p.next()
            if (e == XmlPullParser.END_DOCUMENT || (e == XmlPullParser.END_TAG && p.depth == depth)) break
            if (e == XmlPullParser.START_TAG) when (p.name) {
                "display-name" -> readText(p).takeIf { it.isNotBlank() }?.let(names::add)
                "icon" -> icon = p.getAttributeValue(null, "src")
            }
        }
        return if (id.isNullOrEmpty()) null else XmltvChannel(id, names, icon)
    }

    private fun readProgramme(p: XmlPullParser): XmltvProgramme? {
        val channel = p.getAttributeValue(null, "channel")?.trim()
        val start = p.getAttributeValue(null, "start")?.let(XmltvTime::parse)
        val stop = p.getAttributeValue(null, "stop")?.let(XmltvTime::parse)
        var title: String? = null
        var desc: String? = null
        var category: String? = null
        var icon: String? = null
        val depth = p.depth
        while (true) {
            val e = p.next()
            if (e == XmlPullParser.END_DOCUMENT || (e == XmlPullParser.END_TAG && p.depth == depth)) break
            if (e == XmlPullParser.START_TAG) when (p.name) {
                "title" -> readText(p).let { if (title == null && it.isNotBlank()) title = it }
                "desc" -> readText(p).let { if (desc == null && it.isNotBlank()) desc = it }
                "category" -> readText(p).let { if (category == null && it.isNotBlank()) category = it }
                "icon" -> if (icon == null) icon = p.getAttributeValue(null, "src")
            }
        }
        if (channel.isNullOrEmpty() || start == null) return null
        val end = stop?.takeIf { it > start } ?: (start + DEFAULT_DURATION_MS)
        return XmltvProgramme(channel, start, end, title ?: "", desc, category, icon)
    }

    /** Reads the text of the current element and leaves the parser on its END_TAG; tolerates nested markup. */
    private fun readText(p: XmlPullParser): String {
        val sb = StringBuilder()
        val depth = p.depth
        while (true) {
            val e = p.next()
            when {
                e == XmlPullParser.TEXT || e == XmlPullParser.CDSECT -> sb.append(p.text)
                e == XmlPullParser.END_DOCUMENT -> break
                e == XmlPullParser.END_TAG && p.depth == depth -> break
            }
        }
        return sb.toString().trim()
    }

    private companion object { const val DEFAULT_DURATION_MS = 60 * 60 * 1000L }
}
