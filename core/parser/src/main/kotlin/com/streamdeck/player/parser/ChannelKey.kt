package com.streamdeck.player.parser

import java.security.MessageDigest

/** Stable channel identity across playlist refreshes: tvg-id, falling back to a hash of the URL. */
object ChannelKey {
    fun of(entry: M3uEntry): String =
        entry.tvgId?.let { "id:$it" } ?: "url:${sha1(entry.url)}"

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
