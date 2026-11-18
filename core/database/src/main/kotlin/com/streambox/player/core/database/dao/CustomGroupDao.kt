package com.streambox.player.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.streambox.player.core.database.entity.ChannelEntity
import com.streambox.player.core.database.entity.CustomGroupChannelEntity
import com.streambox.player.core.database.entity.CustomGroupEntity
import kotlinx.coroutines.flow.Flow

/** A custom group with the number of its channels that are currently visible. */
data class CustomGroupRow(val id: Long, val name: String, val channelCount: Int)

/** Visible = in the active generation of an enabled playlist and not hidden (same rule as favorites). */
private const val VISIBLE_MEMBERS = """
    FROM custom_group_channels m
    INNER JOIN channels c ON c.playlistId = m.playlistId AND c.uniqueKey = m.uniqueKey
    INNER JOIN playlists p ON p.id = c.playlistId AND c.generation = p.activeGeneration
    WHERE p.enabled = 1 AND c.hidden = 0
"""

/**
 * Custom groups (schema v3, P3c). Members are resolved to the channels of each playlist's active
 * generation on read, so they survive refreshes. Order: groups by [CustomGroupEntity.sortOrder];
 * members by their position, then by the time they were added.
 */
@Dao
@Suppress("TooManyFunctions") // Small queries composed by the @Transaction methods.
abstract class CustomGroupDao {
    @Query(
        """
        SELECT g.id AS id, g.name AS name,
            (SELECT COUNT(*) $VISIBLE_MEMBERS AND m.groupId = g.id) AS channelCount
        FROM custom_groups g
        ORDER BY g.sortOrder, g.id
        """,
    )
    abstract fun observeGroups(): Flow<List<CustomGroupRow>>

    @Query("SELECT * FROM custom_groups ORDER BY sortOrder, id")
    abstract suspend fun groups(): List<CustomGroupEntity>

    @Query(
        "SELECT c.* $VISIBLE_MEMBERS AND m.groupId = :groupId " +
            "ORDER BY m.position, m.addedAt, m.playlistId, m.uniqueKey",
    )
    abstract fun observeChannels(groupId: Long): Flow<List<ChannelEntity>>

    /** Ids of the custom groups that contain this channel. */
    @Query("SELECT groupId FROM custom_group_channels WHERE playlistId = :playlistId AND uniqueKey = :uniqueKey")
    abstract fun observeGroupsOf(playlistId: Long, uniqueKey: String): Flow<List<Long>>

    /** Every member of the group in order, including those whose channel is currently missing. */
    @Query(
        "SELECT playlistId, uniqueKey FROM custom_group_channels WHERE groupId = :groupId " +
            "ORDER BY position, addedAt, playlistId, uniqueKey",
    )
    abstract suspend fun memberKeys(groupId: Long): List<ChannelKeyRow>

    @Insert
    abstract suspend fun insertGroup(group: CustomGroupEntity): Long

    @Query("UPDATE custom_groups SET name = :name WHERE id = :id")
    abstract suspend fun renameGroup(id: Long, name: String)

    @Query("DELETE FROM custom_groups WHERE id = :id")
    abstract suspend fun deleteGroup(id: Long)

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM custom_groups")
    abstract suspend fun maxSortOrder(): Int

    @Query("UPDATE custom_groups SET sortOrder = :sortOrder WHERE id = :id")
    abstract suspend fun setGroupOrder(id: Long, sortOrder: Int)

    /** Adding a channel that is already a member keeps its position. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertMember(member: CustomGroupChannelEntity)

    @Query(
        "DELETE FROM custom_group_channels WHERE groupId = :groupId AND playlistId = :playlistId " +
            "AND uniqueKey = :uniqueKey",
    )
    abstract suspend fun deleteMember(groupId: Long, playlistId: Long, uniqueKey: String)

    @Query("SELECT COALESCE(MAX(position), -1) FROM custom_group_channels WHERE groupId = :groupId")
    abstract suspend fun maxPosition(groupId: Long): Int

    @Query(
        "UPDATE custom_group_channels SET position = :position WHERE groupId = :groupId " +
            "AND playlistId = :playlistId AND uniqueKey = :uniqueKey",
    )
    abstract suspend fun setPosition(groupId: Long, playlistId: Long, uniqueKey: String, position: Int)

    /** Creates a group at the end of the list and returns its id. */
    @Transaction
    open suspend fun createGroup(name: String, createdAt: Long): Long =
        insertGroup(CustomGroupEntity(name = name, sortOrder = maxSortOrder() + 1, createdAt = createdAt))

    /** Appends a channel to the group (no-op when it is already a member). */
    @Transaction
    open suspend fun addMember(groupId: Long, playlistId: Long, uniqueKey: String, addedAt: Long) =
        insertMember(CustomGroupChannelEntity(groupId, playlistId, uniqueKey, maxPosition(groupId) + 1, addedAt))

    /** [ids] get positions 0..n-1 in that order; groups not listed follow in their current order. */
    @Transaction
    open suspend fun reorderGroups(ids: List<Long>) {
        val rest = groups().map { it.id }.filterNot { it in ids }
        (ids.distinct() + rest).forEachIndexed { index, id -> setGroupOrder(id, index) }
    }

    /**
     * [keys] get positions 0..n-1 in that order; members not listed (e.g. channels missing from the
     * current data) follow in their current order.
     */
    @Transaction
    open suspend fun reorderMembers(groupId: Long, keys: List<ChannelKeyRow>) {
        val listed = keys.distinct()
        val rest = memberKeys(groupId).filterNot { it in listed }
        (listed + rest).forEachIndexed { index, key -> setPosition(groupId, key.playlistId, key.uniqueKey, index) }
    }
}
