package com.streambox.player.core.player

import java.util.Locale

enum class StreamType { HLS, DASH, RTSP, PROGRESSIVE }

/**
 * Picks the media source from the URL. IPTV URLs often have no extension (`/live/123`), so anything
 * unknown is tried as progressive (MPEG-TS / MP4 / MKV sniffing) first; the controller falls back to
 * HLS once if the container is not recognized.
 */
object StreamTypeResolver {
    fun resolve(url: String): StreamType {
        val lower = url.lowercase(Locale.ROOT)
        if (lower.startsWith("rtsp://") || lower.startsWith("rtsps://")) return StreamType.RTSP
        val path = lower.substringBefore('#').substringBefore('?')
        val query = lower.substringAfter('?', "")
        return when {
            path.endsWith(".m3u8") || path.endsWith(".m3u") -> StreamType.HLS

            path.endsWith(".mpd") -> StreamType.DASH

            query.contains(
                "type=m3u8",
            ) || query.contains("format=m3u8") || query.contains("output=m3u8") -> StreamType.HLS

            query.contains("format=mpd") -> StreamType.DASH

            else -> StreamType.PROGRESSIVE
        }
    }
}
