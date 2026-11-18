package com.streambox.player.core.domain.repository

import com.streambox.player.core.model.Channel
import com.streambox.player.core.model.ChannelKey
import kotlinx.coroutines.flow.Flow

/**
 * Favorite channels across all playlists (requirements §4.7), stored by [ChannelKey] so they survive
 * refreshes. Order: custom (drag) order where set, otherwise the time added, oldest first.
 */
interface FavoritesRepository {
    /** Favorites resolved to the current channel data (missing / hidden channels are left out). */
    val favorites: Flow<List<Channel>>

    /** Every stored favorite key, also those whose channel is currently missing. */
    val favoriteKeys: Flow<Set<ChannelKey>>

    fun isFavorite(key: ChannelKey): Flow<Boolean>

    /** Adds [key]; adding an existing favorite changes nothing. */
    suspend fun add(key: ChannelKey)

    suspend fun remove(key: ChannelKey)

    /** Custom order: [keys] first in this order, the rest after them by time added. */
    suspend fun reorder(keys: List<ChannelKey>)

    /** Back to "ordered by time added". */
    suspend fun clearCustomOrder()
}
