package com.streambox.player.core.model

/** One entry of the watch history: a channel of the current playlist data and when it last started playing. */
data class WatchHistoryEntry(val channel: Channel, val watchedAtUtcMs: Long)

/**
 * The user's own changes to one channel, kept apart from the imported rows so they survive refreshes.
 * [customName] null = the playlist's name; [customSortOrder] null = the playlist's order.
 */
data class ChannelOverride(
    val key: ChannelKey,
    val hidden: Boolean = false,
    val customName: String? = null,
    val customSortOrder: Int? = null,
)

/**
 * The user's own changes to one group of one playlist, keyed by the group name as the playlist
 * defines it ([groupName]; empty = channels without a group).
 */
data class GroupOverride(
    val playlistId: Long,
    val groupName: String,
    val hidden: Boolean = false,
    val customName: String? = null,
    val customSortOrder: Int? = null,
)

/** A group the user made (P3c). Its channels may come from several playlists. */
data class CustomGroup(val id: Long, val name: String, val channelCount: Int = 0)

/** A group of one playlist in the list editor; hidden groups are listed too. */
data class EditorGroup(
    val playlistId: Long,
    /** The name the playlist gives the group (empty = channels without a group); identifies it. */
    val name: String,
    val customName: String? = null,
    val hidden: Boolean = false,
    /** All channels of the group, hidden ones included. */
    val channelCount: Int = 0,
) {
    val displayName: String get() = customName ?: name
}

/** A channel of one playlist in the list editor; hidden channels are listed too. */
data class EditorChannel(
    val key: ChannelKey,
    val name: String,
    val customName: String? = null,
    val logoUrl: String? = null,
    /** Hidden itself or through its group. */
    val hidden: Boolean = false,
    /** Hidden by the user itself (as opposed to only through its hidden group). */
    val selfHidden: Boolean = false,
    val displayNumber: Int = 0,
) {
    val displayName: String get() = customName ?: name

    /** Hidden only because its group is hidden. */
    val hiddenByGroup: Boolean get() = hidden && !selfHidden
}
