package com.streambox.player.core.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.streambox.player.core.database.entity.ChannelEntity
import com.streambox.player.core.database.entity.WatchHistoryEntity
import kotlinx.coroutines.flow.Flow

data class WatchHistoryRow(@Embedded val channel: ChannelEntity, val watchedAt: Long)

/**
 * Watch history keyed by (playlistId, uniqueKey): one row per channel, newest first, capped by
 * [record]. Reads resolve the keys to the channels of the active generation like [FavoriteDao].
 */
@Dao
abstract class WatchHistoryDao {
    @Query(
        """
        SELECT c.*, h.watchedAt AS watchedAt FROM watch_history h
        INNER JOIN channels c ON c.playlistId = h.playlistId AND c.uniqueKey = h.uniqueKey
        INNER JOIN playlists p ON p.id = c.playlistId AND c.generation = p.activeGeneration
        WHERE p.enabled = 1 AND c.hidden = 0
        ORDER BY h.watchedAt DESC, h.rowid DESC
        LIMIT :limit
        """,
    )
    abstract fun observe(limit: Int): Flow<List<WatchHistoryRow>>

    @Upsert
    abstract suspend fun upsert(entry: WatchHistoryEntity)

    /** Keeps the [keep] most recent rows. */
    @Query(
        """
        DELETE FROM watch_history WHERE rowid NOT IN
            (SELECT rowid FROM watch_history ORDER BY watchedAt DESC, rowid DESC LIMIT :keep)
        """,
    )
    abstract suspend fun trim(keep: Int)

    @Query("SELECT COUNT(*) FROM watch_history")
    abstract suspend fun count(): Int

    @Query("DELETE FROM watch_history WHERE playlistId = :playlistId AND uniqueKey = :uniqueKey")
    abstract suspend fun delete(playlistId: Long, uniqueKey: String)

    @Query("DELETE FROM watch_history")
    abstract suspend fun clear()

    /** Records (or moves to the top) one channel and drops everything beyond the [cap] most recent. */
    @Transaction
    open suspend fun record(entry: WatchHistoryEntity, cap: Int) {
        upsert(entry)
        trim(cap)
    }
}
