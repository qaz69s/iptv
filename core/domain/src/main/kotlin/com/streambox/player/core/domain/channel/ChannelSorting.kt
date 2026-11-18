package com.streambox.player.core.domain.channel

import com.streambox.player.core.model.Channel
import com.streambox.player.core.model.ChannelSortOrder

/**
 * In-memory version of the database's channel order, for lists that are not paged from the channel
 * table (favorites). [Name] and [Number] are stable sorts, so equal values keep the list's own order;
 * [ChannelSortOrder.Default] and [ChannelSortOrder.Custom] keep the given order (for favorites that is
 * the drag order, else the time added).
 */
object ChannelSorting {
    fun sort(channels: List<Channel>, order: ChannelSortOrder): List<Channel> = when (order) {
        ChannelSortOrder.Name -> channels.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        ChannelSortOrder.Number -> channels.sortedBy { it.displayNumber }
        ChannelSortOrder.Default, ChannelSortOrder.Custom -> channels
    }
}
