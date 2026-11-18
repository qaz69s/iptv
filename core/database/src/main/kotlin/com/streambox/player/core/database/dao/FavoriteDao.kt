package com.streambox.player.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.streambox.player.core.database.entity.ChannelEntity
import com.streambox.player.core.database.entity.FavoriteEntity
import kotlinx.coroutines.flow.Flow

data class ChannelKeyRow(val playlistId: Long, val uniqueKey: String)

/**
 * Favorites are stored by (playlistId, uniqueKey) and resolved to the channels of each playlist's
 * active generation on read, so they survive refreshes. A favorite whose channel is missing from the
 * current data (or hidden, or in a disabled playlist) is kept but not listed; it comes back with it.
 *
 * Order: favorites with a custom (drag) order first, by that order; then the rest by the time they
 * were added (oldest first), so a new favorite is appended at the end.
 */
@Dao
abstract class FavoriteDao {
    @Query(
        """
        SELECT c.* FROM favorites f
        INNER JOIN channels c ON c.playlistId = f.playlistId AND c.uniqueKey = f.uniqueKey
        INNER JOIN playlists p ON p.id = c.playlistId AND c.generation = p.activeGeneration
        WHERE p.enabled = 1 AND c.hidden = 0
        ORDER BY CASE WHEN f.customOrder IS NULL THEN 1 ELSE 0 END, f.customOrder, f.addedAt,
            f.playlistId, f.uniqueKey
        """,
    )
    abstract fun observeFavoriteChannels(): Flow<List<ChannelEntity>>

    /** Every stored favorite key, including those whose channel is currently missing. */
    @Query("SELECT playlistId, uniqueKey FROM favorites")
    abstract fun observeKeys(): Flow<List<ChannelKeyRow>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE playlistId = :playlistId AND uniqueKey = :uniqueKey)")
    abstract fun observeIsFavorite(playlistId: Long, uniqueKey: String): Flow<Boolean>

    /** Adding an existing favorite keeps its original time and order. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE playlistId = :playlistId AND uniqueKey = :uniqueKey")
    abstract suspend fun delete(playlistId: Long, uniqueKey: String): Int

    @Query("UPDATE favorites SET customOrder = :order WHERE playlistId = :playlistId AND uniqueKey = :uniqueKey")
    abstract suspend fun setCustomOrder(playlistId: Long, uniqueKey: String, order: Int?)

    @Query("UPDATE favorites SET customOrder = NULL")
    abstract suspend fun clearCustomOrder()

    /**
     * Drag order: [keys] get positions 0..n-1 in that order; favorites not listed lose their custom
     * position and follow, by time added.
     */
    @Transaction
    open suspend fun reorder(keys: List<ChannelKeyRow>) {
        clearCustomOrder()
        keys.forEachIndexed { index, key -> setCustomOrder(key.playlistId, key.uniqueKey, index) }
    }
}
