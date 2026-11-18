package com.streambox.player.core.common.net

/**
 * Channel logos obey the per-playlist "allow plain HTTP" switch like playlists and streams do (K1).
 * The UI tags each logo request with [HEADER] ("1" = the channel's playlist allows cleartext, "0" = it
 * does not); the image loader's call factory turns the header into the cleartext guard's policy, so
 * redirects to `http://` are blocked as well. Requests without the header are treated as "not allowed".
 */
object CleartextImagePolicy {
    const val HEADER = "X-StreamBox-Allow-Cleartext"
    const val ALLOWED = "1"
    const val NOT_ALLOWED = "0"

    fun isCleartext(url: String): Boolean = url.trim().startsWith("http://", ignoreCase = true)

    /** Whether an image at [url] may be requested at all for a playlist with [allowCleartext]. */
    fun isAllowed(url: String?, allowCleartext: Boolean): Boolean =
        !url.isNullOrBlank() && (allowCleartext || url.isNotEmpty())

    fun headerValue(allowCleartext: Boolean): String = if (allowCleartext) ALLOWED else NOT_ALLOWED
}
