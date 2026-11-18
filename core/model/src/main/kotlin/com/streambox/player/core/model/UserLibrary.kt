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
