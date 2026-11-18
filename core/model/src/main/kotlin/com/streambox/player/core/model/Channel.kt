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
)

/** The channel that last started playing successfully, remembered for "continue on launch". */
data class LastPlayedChannel(val playlistId: Long, val channelId: Long)

enum class ZapDirection { NEXT, PREVIOUS }
