package com.streambox.player.core.model

data class Channel(
    val id: Long,
    val playlistId: Long,
    val groupId: Long?,
    val uniqueKey: String,
    val name: String,
    val logoUrl: String?,
    val streamUrl: String,
    val tvgId: String? = null,
    val tvgName: String? = null,
    /** tvg-chno when the playlist provides it. */
    val number: Int? = null,
    /** 1-based position in the playlist; used as display number when [number] is absent. */
    val position: Int = 0,
    val catchupType: String? = null,
    val catchupSource: String? = null,
    val catchupDays: Int? = null,
) {
    val key: ChannelKey get() = ChannelKey(playlistId, uniqueKey)

    val displayNumber: Int get() = number ?: position
}

/** A group as shown in the channel browser; groups with the same name across playlists are merged. */
data class ChannelGroupSummary(val name: String, val channelCount: Int)

/** Everything the player needs for one channel, including per-playlist request headers. */
data class PlaybackItem(
    val channelId: Long,
    val name: String,
    val logoUrl: String?,
    val streamUrl: String,
    val userAgent: String?,
    val referrer: String?,
    val allowCleartext: Boolean,
    val displayNumber: Int,
    /** Playlist the channel belongs to (remembered as "last channel"). */
    val playlistId: Long = 0,
    /** Name of the channel's own group; null when it has none. */
    val groupName: String? = null,
    /** Stable key inside the playlist (tvg-id, else URL hash); survives refreshes, unlike [channelId]. */
    val uniqueKey: String = "",
) {
    val key: ChannelKey get() = ChannelKey(playlistId, uniqueKey)
}

/**
 * The channel that last started playing successfully, remembered for "continue on launch". Keyed by
 * [ChannelKey] rather than the row id, because a refresh re-imports every channel under a new id.
 */
data class LastPlayedChannel(val playlistId: Long, val uniqueKey: String) {
    val key: ChannelKey get() = ChannelKey(playlistId, uniqueKey)
}

/**
 * Identity of a channel that survives playlist refreshes: the playlist plus the channel's
 * `uniqueKey` (tvg-id first, else a hash of the stream URL; duplicates get a suffix). Favorites,
 * watch history, user overrides and the last channel are all stored by this key, never by channel id.
 */
data class ChannelKey(val playlistId: Long, val uniqueKey: String)

enum class ZapDirection { NEXT, PREVIOUS }
