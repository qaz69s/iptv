package com.streamdeck.player.parser

import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * XMLTV timestamps: `YYYYMMDDhhmmss ±hhmm`, seconds/minutes optional, offset optional (= UTC per the DTD).
 * Returns UTC epoch milliseconds or null when malformed.
 */
object XmltvTime {
    private val pattern = Regex("""^(\d{4})(\d{2})(\d{2})(\d{2})?(\d{2})?(\d{2})?\s*(?:([+-])(\d{2}):?(\d{2}))?\s*$""")

    fun parse(raw: String): Long? {
        val m = pattern.matchEntire(raw.trim()) ?: return null
        val g = m.groupValues
        return runCatching {
            val local = LocalDateTime.of(
                g[1].toInt(), g[2].toInt(), g[3].toInt(),
                g[4].ifEmpty { "0" }.toInt(), g[5].ifEmpty { "0" }.toInt(), g[6].ifEmpty { "0" }.toInt(),
            )
            val offset = if (g[7].isEmpty()) ZoneOffset.UTC else {
                val sign = if (g[7] == "-") -1 else 1
                ZoneOffset.ofHoursMinutes(sign * g[8].toInt(), sign * g[9].toInt())
            }
            local.toInstant(offset).toEpochMilli()
        }.getOrNull()
    }
}
