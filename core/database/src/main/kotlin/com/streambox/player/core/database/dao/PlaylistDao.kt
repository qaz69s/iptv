package com.streambox.player.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.streambox.player.core.database.entity.PlaylistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observe(id: Long): Flow<PlaylistEntity?>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun get(id: Long): PlaylistEntity?

    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM playlists")
    suspend fun maxSortOrder(): Int

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE playlists SET allowCleartext = :allowed WHERE id = :id")
    suspend fun setAllowCleartext(id: Long, allowed: Boolean)

    /** A disabled playlist keeps all its data; every browse / zap / search query skips it. */
    @Query("UPDATE playlists SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE playlists SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setSortOrder(id: Long, sortOrder: Int)

    @Query("UPDATE playlists SET lastError = :error WHERE id = :id")
    suspend fun setLastError(id: Long, error: String?)

    /** The atomic swap: one UPDATE makes the new generation visible. */
    @Query(
        """
        UPDATE playlists SET activeGeneration = :generation, channelCount = :channelCount, groupCount = :groupCount,
            epgUrl = COALESCE(:epgUrl, epgUrl), lastUpdated = :updatedAt, lastError = NULL
        WHERE id = :id
        """,
    )
    suspend fun activateGeneration(
        id: Long,
        generation: Int,
        channelCount: Int,
        groupCount: Int,
        epgUrl: String?,
        updatedAt: Long,
    ): Int
}
