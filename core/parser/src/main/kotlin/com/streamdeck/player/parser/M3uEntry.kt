package com.streamdeck.player.parser

/** One channel parsed from an M3U playlist. All optional attributes are null when absent. */
data class M3uEntry(
    val name: String,
    val url: String,
    val tvgId: String? = null,
    val tvgName: String? = null,
    val logo: String? = null,
    val group: String? = null,
    val number: Int? = null,
    val catchup: String? = null,
    val catchupSource: String? = null,
    val catchupDays: Int? = null,
    val timeshift: Int? = null,
    val userAgent: String? = null,
    val referrer: String? = null,
)

/** Header-level info from the #EXTM3U line. */
data class M3uHeader(val epgUrl: String? = null, val catchup: String? = null, val catchupSource: String? = null)
