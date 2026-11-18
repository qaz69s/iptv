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

/** A group of one playlist as the list editor shows it: hidden groups included, with all their channels. */
data class EditorGroupRow(
    val name: String,
    val customName: String?,
    val hidden: Boolean,
    val channelCount: Int,
)

/**
 * A channel as the list editor shows it. [hidden] is the effective flag (itself or its group),
 * [selfHidden] whether the user hid the channel itself.
 */
data class EditorChannelRow(
    val uniqueKey: String,
    val name: String,
    val customName: String?,
    val logoUrl: String?,
    val hidden: Boolean,
    val selfHidden: Boolean,
    val number: Int?,
    val sortOrder: Int,
)

/** Values of the `sort` argument of [ChannelDao.pagingSource] / [ChannelDao.orderedIds]. */
object SortCode {
    /** Playlist order: playlist by playlist, import order inside each. */
    const val DEFAULT = 0

    /** Display name (user's name if renamed), ASCII case-insensitive, across playlists. */
    const val NAME = 1

    /** Display number: tvg-chno, else the 1-based playlist position, across playlists. */
    const val NUMBER = 2

    /** User order (`customSortOrder`, materialized from the overrides), else import order. */
    const val CUSTOM = 3
}

private const val VISIBLE_IN_GROUP = """
    FROM channels c
    INNER JOIN playlists p ON p.id = c.playlistId AND c.generation = p.activeGeneration
    WHERE p.enabled = 1 AND c.hidden = 0
      AND (:groupName IS NULL OR c.groupId IN
           (SELECT g.id FROM channel_groups g WHERE COALESCE(g.customName, g.name) = :groupName))
"""

/** Ties always fall back to playlist order, then import order, so every order is total and stable. */
private const val ORDER_BY_SORT = """
    ORDER BY
        CASE WHEN :sort = 1 THEN COALESCE(c.customName, c.name) END COLLATE NOCASE,
        CASE WHEN :sort = 2 THEN COALESCE(c.number, c.sortOrder + 1) END,
        p.sortOrder, c.playlistId,
        CASE WHEN :sort = 3 THEN COALESCE(c.customSortOrder, c.sortOrder) END,
        c.sortOrder
"""

/**
 * Every read joins `playlists` on `generation = activeGeneration`, so rows of an import that is still
 * running (or failed) are never visible.
 *
 * Schema v2: user overrides are materialized into the rows (see [OverrideDao]), so reads use
 * `c.hidden` / `g.hidden` and the display name `COALESCE(customName, name)`. Channel order (P3b) is
 * chosen per query ([SortCode]); the user's own order `COALESCE(customSortOrder, sortOrder)` is
 * [SortCode.CUSTOM]. Groups are matched by
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

    /** Visible channels of [groupName] (null = all) in the order chosen by [sort] (a [SortCode]). */
    @Query("SELECT c.* $VISIBLE_IN_GROUP $ORDER_BY_SORT")
    fun pagingSource(groupName: String?, sort: Int): PagingSource<Int, ChannelEntity>

    /** Ids of [pagingSource]'s rows in the same order: the zap order of a group or of all channels. */
    @Query("SELECT c.id $VISIBLE_IN_GROUP $ORDER_BY_SORT")
    suspend fun orderedIds(groupName: String?, sort: Int): List<Long>

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

    /**
     * Search (P3b): channels whose name / tvg-name matches the FTS4 expression [match] (word prefixes),
     * whose user-given name contains the LIKE pattern [like], or, when [substring] is 1, whose name
     * contains it (CJK names have no word breaks for the simple tokenizer). [like] escapes with '\'.
     */
    @Query(
        """
        SELECT c.* FROM channels c
        INNER JOIN playlists p ON p.id = c.playlistId AND c.generation = p.activeGeneration
        WHERE p.enabled = 1 AND c.hidden = 0
          AND (c.id IN (SELECT rowid FROM channels_fts WHERE channels_fts MATCH :match)
               OR c.customName LIKE :like ESCAPE '\'
               OR (:substring = 1 AND c.name LIKE :like ESCAPE '\'))
        ORDER BY p.sortOrder, c.playlistId, c.sortOrder
        LIMIT :limit
        """,
    )
    suspend fun search(match: String, like: String, substring: Int, limit: Int): List<ChannelEntity>

    /** Groups whose display name contains the LIKE pattern [like] (escaped with '\'), as [observeGroups]. */
    @Query(
        """
        SELECT COALESCE(g.customName, g.name) AS name, SUM(g.channelCount) AS channelCount
        FROM channel_groups g INNER JOIN playlists p ON p.id = g.playlistId AND g.generation = p.activeGeneration
        WHERE p.enabled = 1 AND g.hidden = 0 AND g.channelCount > 0
          AND COALESCE(g.customName, g.name) LIKE :like ESCAPE '\'
        GROUP BY COALESCE(g.customName, g.name)
        ORDER BY MIN(p.sortOrder * 1000000 + COALESCE(g.customSortOrder, g.sortOrder))
        LIMIT :limit
        """,
    )
    suspend fun searchGroups(like: String, limit: Int): List<GroupSummaryRow>

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

    /** List editor (P3c): every group of the playlist's current data, hidden ones included, in user order. */
    @Query(
        """
        SELECT g.name AS name, g.customName AS customName, g.hidden AS hidden,
            (SELECT COUNT(*) FROM channels c WHERE c.groupId = g.id) AS channelCount
        FROM channel_groups g
        INNER JOIN playlists p ON p.id = g.playlistId AND g.generation = p.activeGeneration
        WHERE g.playlistId = :playlistId
        ORDER BY COALESCE(g.customSortOrder, g.sortOrder), g.sortOrder
        """,
    )
    fun observeEditorGroups(playlistId: Long): Flow<List<EditorGroupRow>>

    /**
     * List editor (P3c): every channel of one group ([groupName] as the playlist names it) of the
     * playlist's current data, hidden ones included, in user order.
     */
    @Query(
        """
        SELECT c.uniqueKey AS uniqueKey, c.name AS name, c.customName AS customName, c.logoUrl AS logoUrl,
            c.hidden AS hidden,
            COALESCE((SELECT o.hidden FROM channel_overrides o
                      WHERE o.playlistId = c.playlistId AND o.uniqueKey = c.uniqueKey), 0) AS selfHidden,
            c.number AS number, c.sortOrder AS sortOrder
        FROM channels c
        INNER JOIN playlists p ON p.id = c.playlistId AND c.generation = p.activeGeneration
        INNER JOIN channel_groups g ON g.id = c.groupId
        WHERE c.playlistId = :playlistId AND g.name = :groupName
        ORDER BY COALESCE(c.customSortOrder, c.sortOrder), c.sortOrder
        """,
    )
    fun observeEditorChannels(playlistId: Long, groupName: String): Flow<List<EditorChannelRow>>
}

/** A channel key (or group name) with its effective position. */
data class KeyPositionRow(val uniqueKey: String, val position: Int)
