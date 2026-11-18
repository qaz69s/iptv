package com.streambox.player.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.streambox.player.core.database.entity.ChannelEntity
import com.streambox.player.core.database.entity.ChannelGroupEntity
import kotlinx.coroutines.flow.Flow

data class GroupSummaryRow(val name: String, val channelCount: Int)

data class PlaybackRow(
    val id: Long,
    val name: String,
    val logoUrl: String?,
    val streamUrl: String,
    val number: Int?,
    val sortOrder: Int,
    val userAgent: String?,
    val referrer: String?,
    val allowCleartext: Boolean,
    val playlistId: Long,
    val groupName: String?,
    val uniqueKey: String,
)

/**
 * Where a channel sits in the browse / zap order: playlist order, playlist id, the effective position
 * ([channelSort] = user order, else import order) and the import position as the final tie-breaker.
 */
data class ChannelPositionRow(val playlistSort: Int, val playlistId: Long, val channelSort: Int, val importSort: Int)

/**
 * Every read joins `playlists` on `generation = activeGeneration`, so rows of an import that is still
 * running (or failed) are never visible.
 *
 * Schema v2: user overrides are materialized into the rows (see [OverrideDao]), so reads use
 * `c.hidden` / `g.hidden`, the display name `COALESCE(customName, name)` and the effective order
 * `COALESCE(customSortOrder, sortOrder)` with the import order as tie-breaker. Groups are matched by
 * their display name, which is also what [observeGroups] returns.
 */
@Dao
@Suppress("TooManyFunctions") // One DAO per table; queries share the generation join.
interface ChannelDao {
    @Insert
    suspend fun insertGroups(groups: List<ChannelGroupEntity>): List<Long>

    @Insert
    suspend fun insertChannels(channels: List<ChannelEntity>)

    @Query("SELECT COUNT(*) FROM channels WHERE playlistId = :playlistId AND generation = :generation")
    suspend fun countChannels(playlistId: Long, generation: Int): Int

    @Query("SELECT COUNT(*) FROM channel_groups WHERE playlistId = :playlistId AND generation = :generation")
    suspend fun countGroups(playlistId: Long, generation: Int): Int

    @Query(
        """
        UPDATE channel_groups SET channelCount =
            (SELECT COUNT(*) FROM channels c WHERE c.groupId = channel_groups.id AND c.hidden = 0)
        WHERE playlistId = :playlistId AND generation = :generation
        """,
    )
    suspend fun updateGroupCounts(playlistId: Long, generation: Int)

    @Query("DELETE FROM channels WHERE playlistId = :playlistId AND generation != :keep")
    suspend fun deleteChannelsExcept(playlistId: Long, keep: Int)

    @Query("DELETE FROM channel_groups WHERE playlistId = :playlistId AND generation != :keep")
    suspend fun deleteGroupsExcept(playlistId: Long, keep: Int)

    @Query("DELETE FROM channels WHERE playlistId = :playlistId AND generation = :generation")
    suspend fun deleteChannelsOf(playlistId: Long, generation: Int)

    @Query("DELETE FROM channel_groups WHERE playlistId = :playlistId AND generation = :generation")
    suspend fun deleteGroupsOf(playlistId: Long, generation: Int)

    @Query(
        """
        SELECT COALESCE(g.customName, g.name) AS name, SUM(g.channelCount) AS channelCount
        FROM channel_groups g INNER JOIN playlists p ON p.id = g.playlistId AND g.generation = p.activeGeneration
        WHERE p.enabled = 1 AND g.hidden = 0 AND g.channelCount > 0
        GROUP BY COALESCE(g.customName, g.name)
        ORDER BY MIN(p.sortOrder * 1000000 + COALESCE(g.customSortOrder, g.sortOrder))
        """,
    )
    fun observeGroups(): Flow<List<GroupSummaryRow>>

    @Query(
        """
        SELECT COUNT(*) FROM channels c
        INNER JOIN playlists p ON p.id = c.playlistId AND c.generation = p.activeGeneration
        WHERE p.enabled = 1 AND c.hidden = 0
        """,
    )
    fun observeChannelCount(): Flow<Int>

    @Query(
        """
        SELECT c.* FROM channels c
        INNER JOIN playlists p ON p.id = c.playlistId AND c.generation = p.activeGeneration
        WHERE p.enabled = 1 AND c.hidden = 0
          AND (:groupName IS NULL OR c.groupId IN
               (SELECT g.id FROM channel_groups g WHERE COALESCE(g.customName, g.name) = :groupName))
        ORDER BY p.sortOrder, c.playlistId, COALESCE(c.customSortOrder, c.sortOrder), c.sortOrder
        """,
    )
    fun pagingSource(groupName: String?): PagingSource<Int, ChannelEntity>

    @Query(
        """
        SELECT c.id, COALESCE(c.customName, c.name) AS name, c.logoUrl, c.streamUrl, c.number, c.sortOrder,
            COALESCE(c.userAgent, p.userAgent) AS userAgent,
            COALESCE(c.referrer, p.referrer) AS referrer,
            p.allowCleartext AS allowCleartext, c.playlistId AS playlistId,
            (SELECT COALESCE(g.customName, g.name) FROM channel_groups g WHERE g.id = c.groupId)
                AS groupName,
            c.uniqueKey AS uniqueKey
        FROM channels c INNER JOIN playlists p ON p.id = c.playlistId
        WHERE c.id = :channelId
        """,
    )
    suspend fun playbackRow(channelId: Long): PlaybackRow?

    @Query(
        """
        SELECT p.sortOrder AS playlistSort, c.playlistId AS playlistId,
            COALESCE(c.customSortOrder, c.sortOrder) AS channelSort, c.sortOrder AS importSort
        FROM channels c INNER JOIN playlists p ON p.id = c.playlistId WHERE c.id = :channelId
        """,
    )
    suspend fun position(channelId: Long): ChannelPositionRow?

    @Query(
        """
        SELECT c.id FROM channels c
        INNER JOIN playlists p ON p.id = c.playlistId AND c.generation = p.activeGeneration
        WHERE p.enabled = 1 AND c.hidden = 0
          AND (:groupName IS NULL OR c.groupId IN
               (SELECT g.id FROM channel_groups g WHERE COALESCE(g.customName, g.name) = :groupName))
          AND (p.sortOrder > :playlistSort OR (p.sortOrder = :playlistSort AND
               (c.playlistId > :playlistId OR (c.playlistId = :playlistId AND
                 (COALESCE(c.customSortOrder, c.sortOrder) > :channelSort OR
                  (COALESCE(c.customSortOrder, c.sortOrder) = :channelSort AND c.sortOrder > :importSort))))))
        ORDER BY p.sortOrder, c.playlistId, COALESCE(c.customSortOrder, c.sortOrder), c.sortOrder LIMIT 1
        """,
    )
    suspend fun nextAfter(
        groupName: String?,
        playlistSort: Int,
        playlistId: Long,
        channelSort: Int,
        importSort: Int,
    ): Long?

    @Query(
        """
        SELECT c.id FROM channels c
        INNER JOIN playlists p ON p.id = c.playlistId AND c.generation = p.activeGeneration
        WHERE p.enabled = 1 AND c.hidden = 0
          AND (:groupName IS NULL OR c.groupId IN
               (SELECT g.id FROM channel_groups g WHERE COALESCE(g.customName, g.name) = :groupName))
          AND (p.sortOrder < :playlistSort OR (p.sortOrder = :playlistSort AND
               (c.playlistId < :playlistId OR (c.playlistId = :playlistId AND
                 (COALESCE(c.customSortOrder, c.sortOrder) < :channelSort OR
                  (COALESCE(c.customSortOrder, c.sortOrder) = :channelSort AND c.sortOrder < :importSort))))))
        ORDER BY p.sortOrder DESC, c.playlistId DESC, COALESCE(c.customSortOrder, c.sortOrder) DESC, c.sortOrder DESC
        LIMIT 1
        """,
    )
    suspend fun previousBefore(
        groupName: String?,
        playlistSort: Int,
        playlistId: Long,
        channelSort: Int,
        importSort: Int,
    ): Long?

    @Query(
        """
        SELECT c.id FROM channels c
        INNER JOIN playlists p ON p.id = c.playlistId AND c.generation = p.activeGeneration
        WHERE p.enabled = 1 AND c.hidden = 0
          AND (:groupName IS NULL OR c.groupId IN
               (SELECT g.id FROM channel_groups g WHERE COALESCE(g.customName, g.name) = :groupName))
        ORDER BY p.sortOrder, c.playlistId, COALESCE(c.customSortOrder, c.sortOrder), c.sortOrder LIMIT 1
        """,
    )
    suspend fun first(groupName: String?): Long?

    @Query(
        """
        SELECT c.id FROM channels c
        INNER JOIN playlists p ON p.id = c.playlistId AND c.generation = p.activeGeneration
        WHERE p.enabled = 1 AND c.hidden = 0
          AND (:groupName IS NULL OR c.groupId IN
               (SELECT g.id FROM channel_groups g WHERE COALESCE(g.customName, g.name) = :groupName))
        ORDER BY p.sortOrder DESC, c.playlistId DESC, COALESCE(c.customSortOrder, c.sortOrder) DESC, c.sortOrder DESC
        LIMIT 1
        """,
    )
    suspend fun last(groupName: String?): Long?

    /** Display number = tvg-chno, else 1-based playlist position; explicit numbers win over positions. */
    @Query(
        """
        SELECT c.id FROM channels c
        INNER JOIN playlists p ON p.id = c.playlistId AND c.generation = p.activeGeneration
        WHERE p.enabled = 1 AND c.hidden = 0
          AND (:groupName IS NULL OR c.groupId IN
               (SELECT g.id FROM channel_groups g WHERE COALESCE(g.customName, g.name) = :groupName))
          AND (c.number = :number OR (c.number IS NULL AND c.sortOrder + 1 = :number))
        ORDER BY CASE WHEN c.number IS NULL THEN 1 ELSE 0 END, p.sortOrder, c.playlistId,
            COALESCE(c.customSortOrder, c.sortOrder), c.sortOrder
        LIMIT 1
        """,
    )
    suspend fun idByNumber(number: Int, groupName: String?): Long?

    /** Current-generation id of the channel with this stable key, in an enabled playlist; null when gone. */
    @Query(
        """
        SELECT c.id FROM channels c
        INNER JOIN playlists p ON p.id = c.playlistId AND c.generation = p.activeGeneration
        WHERE p.enabled = 1 AND c.playlistId = :playlistId AND c.uniqueKey = :uniqueKey
        LIMIT 1
        """,
    )
    suspend fun idByKey(playlistId: Long, uniqueKey: String): Long?

    /** Effective positions of the given channels in the current generation (for reordering). */
    @Query(
        """
        SELECT c.uniqueKey AS uniqueKey, COALESCE(c.customSortOrder, c.sortOrder) AS position
        FROM channels c
        INNER JOIN playlists p ON p.id = c.playlistId AND c.generation = p.activeGeneration
        WHERE c.playlistId = :playlistId AND c.uniqueKey IN (:uniqueKeys)
        """,
    )
    suspend fun channelPositions(playlistId: Long, uniqueKeys: List<String>): List<KeyPositionRow>

    /** Effective positions of the given groups in the current generation (for reordering). */
    @Query(
        """
        SELECT g.name AS uniqueKey, COALESCE(g.customSortOrder, g.sortOrder) AS position
        FROM channel_groups g
        INNER JOIN playlists p ON p.id = g.playlistId AND g.generation = p.activeGeneration
        WHERE g.playlistId = :playlistId AND g.name IN (:groupNames)
        """,
    )
    suspend fun groupPositions(playlistId: Long, groupNames: List<String>): List<KeyPositionRow>
}

/** A channel key (or group name) with its effective position. */
data class KeyPositionRow(val uniqueKey: String, val position: Int)
