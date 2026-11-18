package com.streambox.player.core.domain.repository

import com.streambox.player.core.model.ChannelKey
import com.streambox.player.core.model.WatchHistoryEntry
import kotlinx.coroutines.flow.Flow

/** Recently watched channels (requirements §4.7: the last 50, can be cleared), stored by [ChannelKey]. */
interface WatchHistoryRepository {
    /** Newest first, resolved to the current channel data; at most [MAX_ENTRIES]. */
    val history: Flow<List<WatchHistoryEntry>>

    /** Records that [key] started playing now; a channel watched again moves to the top. */
    suspend fun record(key: ChannelKey)

    suspend fun remove(key: ChannelKey)

    suspend fun clear()

    companion object {
        const val MAX_ENTRIES = 50
    }
}
