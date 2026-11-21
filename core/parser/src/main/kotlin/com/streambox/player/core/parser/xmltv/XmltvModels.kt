package com.streambox.player.core.parser.xmltv

/** One item of an XMLTV document, emitted in document order (see [XmltvParser] for missing stops). */
sealed interface XmltvItem

/** `<channel id>`: its display names in document order (at least one unless the feed has none) and icon. */
data class XmltvChannel(val id: String, val displayNames: List<String>, val iconUrl: String?) : XmltvItem

/**
 * `<programme start stop channel>`. [start] / [stop] are UTC epoch milliseconds, [stop] > [start].
 * Text fields hold the first value, or the one in the parser's preferred language when present.
 */
data class XmltvProgramme(
    val channelId: String,
    val start: Long,
    val stop: Long,
    val title: String,
    val description: String?,
    val category: String?,
    val iconUrl: String?,
) : XmltvItem

/**
 * Counters of one parse. Skipped items were malformed: a channel without id; a programme without
 * channel, title or a valid start, with a stop that is not after its start, or without a stop and no
 * later programme of the same channel to take it from.
 */
data class XmltvParseStats(
    val channels: Long,
    val programmes: Long,
    val skippedChannels: Long,
    val skippedProgrammes: Long,
) {
    val skipped: Long get() = skippedChannels + skippedProgrammes
}
