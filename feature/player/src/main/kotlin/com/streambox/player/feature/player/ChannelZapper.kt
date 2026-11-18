package com.streambox.player.feature.player

import com.streambox.player.core.domain.channel.ChannelSequence
import com.streambox.player.core.domain.repository.ChannelRepository
import com.streambox.player.core.model.ChannelKey
import com.streambox.player.core.model.ChannelSortOrder
import com.streambox.player.core.model.ChannelSource
import com.streambox.player.core.model.ZapDirection

/**
 * Zapping, auto-play next and channel numbers inside the list the player was opened from (P3b user
 * decision: the list's display order, wrapping; auto-play next does not wrap; a number inside a virtual
 * group is the 1-based position).
 *
 * "Recently watched" reorders itself as soon as a channel plays (it moves to the top), which would make
 * next / previous bounce between two channels. So for that source the order is frozen ([freeze]) when
 * the player opens and zapping walks that snapshot by stable key; every other source is read live.
 */
internal class ChannelZapper(private val repository: ChannelRepository, val source: ChannelSource) {
    private var snapshot: List<ChannelKey>? = null

    val needsSnapshot: Boolean get() = source == ChannelSource.Recent

    fun freeze(keys: List<ChannelKey>) {
        snapshot = keys
    }

    suspend fun adjacent(
        currentId: Long,
        currentKey: ChannelKey?,
        sort: ChannelSortOrder,
        direction: ZapDirection,
    ): Long? {
        val keys = snapshot ?: return repository.adjacentChannel(currentId, source, sort, direction)
        return walk(keys, currentKey) { ChannelSequence.adjacent(keys, it, direction) }
    }

    suspend fun next(currentId: Long, currentKey: ChannelKey?, sort: ChannelSortOrder): Long? {
        val keys = snapshot ?: return repository.nextChannel(currentId, source, sort)
        return walk(keys, currentKey) { ChannelSequence.next(keys, it) }
    }

    /** Steps through the snapshot from [start], skipping channels the latest refresh removed. */
    private suspend fun walk(keys: List<ChannelKey>, start: ChannelKey?, step: (ChannelKey) -> ChannelKey?): Long? {
        var key = start
        var found: Long? = null
        var steps = 0
        while (found == null && key != null && steps < keys.size) {
            key = step(key)
            found = key?.let { repository.channelIdByKey(it) }
            steps++
        }
        return found
    }

    suspend fun byNumber(number: Int, sort: ChannelSortOrder): Long? {
        val keys = snapshot ?: return repository.channelIdByNumber(number, source, sort)
        return ChannelSequence.atNumber(keys, number)?.let { repository.channelIdByKey(it) }
    }
}
