package com.streambox.player.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.streambox.player.core.database.entity.ChannelOverrideEntity
import com.streambox.player.core.database.entity.GroupOverrideEntity
import kotlinx.coroutines.flow.Flow

/**
 * The user's changes to channels and groups (hide, rename, reorder).
 *
 * Design (P3a): `channel_overrides` / `group_overrides` are the source of truth, keyed by
 * (playlistId, uniqueKey) and (playlistId, group name), so they outlive every refresh. Their values
 * are **materialized** into `channels` (`hidden`, `customName`, `customSortOrder`) and
 * `channel_groups` (same columns):
 * - when an import commits, [applyToGeneration] runs in the commit transaction, before the new
 *   generation becomes visible;
 * - when the user changes an override, the `update*` methods rewrite the affected rows of every
 *   generation in the same transaction.
 * Read queries therefore stay simple joins on the channel tables (paging, zapping, numbers, FTS
 * content), instead of joining two override tables into every one of them.
 *
 * `channels.hidden` is the *effective* flag: the channel itself or its group is hidden.
 */
@Dao
@Suppress("TooManyFunctions") // Small queries composed by the @Transaction methods.
abstract class OverrideDao {
    @Query("SELECT * FROM channel_overrides WHERE playlistId = :playlistId AND uniqueKey = :uniqueKey")
    abstract suspend fun channelOverride(playlistId: Long, uniqueKey: String): ChannelOverrideEntity?

    @Query("SELECT * FROM group_overrides WHERE playlistId = :playlistId AND groupName = :groupName")
    abstract suspend fun groupOverride(playlistId: Long, groupName: String): GroupOverrideEntity?

    @Query("SELECT * FROM channel_overrides WHERE playlistId = :playlistId ORDER BY uniqueKey")
    abstract fun observeChannelOverrides(playlistId: Long): Flow<List<ChannelOverrideEntity>>

    @Query("SELECT * FROM group_overrides WHERE playlistId = :playlistId ORDER BY groupName")
    abstract fun observeGroupOverrides(playlistId: Long): Flow<List<GroupOverrideEntity>>

    @Upsert
    abstract suspend fun upsertChannelOverride(override: ChannelOverrideEntity)

    @Upsert
    abstract suspend fun upsertGroupOverride(override: GroupOverrideEntity)

    @Query("DELETE FROM channel_overrides WHERE playlistId = :playlistId AND uniqueKey = :uniqueKey")
    abstract suspend fun deleteChannelOverride(playlistId: Long, uniqueKey: String)

    @Query("DELETE FROM group_overrides WHERE playlistId = :playlistId AND groupName = :groupName")
    abstract suspend fun deleteGroupOverride(playlistId: Long, groupName: String)

    @Query(
        "UPDATE channels SET $CHANNEL_VALUES WHERE playlistId = :playlistId AND uniqueKey = :uniqueKey",
    )
    abstract suspend fun materializeChannel(playlistId: Long, uniqueKey: String)

    @Query(
        "UPDATE channel_groups SET $GROUP_VALUES WHERE playlistId = :playlistId AND name = :groupName",
    )
    abstract suspend fun materializeGroup(playlistId: Long, groupName: String)

    /** The group's hidden flag changes the effective `hidden` of its channels. */
    @Query(
        """
        UPDATE channels SET hidden = $CHANNEL_HIDDEN
        WHERE groupId IN (SELECT id FROM channel_groups WHERE playlistId = :playlistId AND name = :groupName)
        """,
    )
    abstract suspend fun materializeChannelsOfGroup(playlistId: Long, groupName: String)

    /** Visible channels per group of the active generation (hidden channels do not count). */
    @Query(
        """
        UPDATE channel_groups SET channelCount =
            (SELECT COUNT(*) FROM channels c WHERE c.groupId = channel_groups.id AND c.hidden = 0)
        WHERE playlistId = :playlistId
          AND generation = (SELECT activeGeneration FROM playlists WHERE id = :playlistId)
        """,
    )
    abstract suspend fun refreshGroupCounts(playlistId: Long)

    @Query(
        """
        UPDATE channel_groups SET $GROUP_VALUES
        WHERE playlistId = :playlistId AND generation = :generation
          AND name IN (SELECT groupName FROM group_overrides WHERE playlistId = :playlistId)
        """,
    )
    abstract suspend fun materializeGroupsOfGeneration(playlistId: Long, generation: Int)

    @Query(
        """
        UPDATE channels SET $CHANNEL_VALUES
        WHERE playlistId = :playlistId AND generation = :generation
          AND (uniqueKey IN (SELECT uniqueKey FROM channel_overrides WHERE playlistId = :playlistId)
            OR groupId IN (SELECT id FROM channel_groups
                           WHERE playlistId = :playlistId AND generation = :generation AND hidden = 1))
        """,
    )
    abstract suspend fun materializeChannelsOfGeneration(playlistId: Long, generation: Int)

    /**
     * Applies every stored override to a freshly imported [generation]. Must run inside the import's
     * commit transaction, before group counts are computed and the generation is activated. Rows of a
     * new generation start with no overrides, so only rows with one are touched.
     */
    @Transaction
    open suspend fun applyToGeneration(playlistId: Long, generation: Int) {
        materializeGroupsOfGeneration(playlistId, generation)
        materializeChannelsOfGeneration(playlistId, generation)
    }

    /** Reads, changes and stores one channel override (removed when back to defaults), then re-applies it. */
    @Transaction
    open suspend fun updateChannel(
        playlistId: Long,
        uniqueKey: String,
        transform: (ChannelOverrideEntity) -> ChannelOverrideEntity,
    ) {
        val current = channelOverride(playlistId, uniqueKey) ?: ChannelOverrideEntity(playlistId, uniqueKey)
        val updated = transform(current)
        if (updated.isDefault()) deleteChannelOverride(playlistId, uniqueKey) else upsertChannelOverride(updated)
        materializeChannel(playlistId, uniqueKey)
        refreshGroupCounts(playlistId)
    }

    /** Reads, changes and stores one group override (removed when back to defaults), then re-applies it. */
    @Transaction
    open suspend fun updateGroup(
        playlistId: Long,
        groupName: String,
        transform: (GroupOverrideEntity) -> GroupOverrideEntity,
    ) {
        val current = groupOverride(playlistId, groupName) ?: GroupOverrideEntity(playlistId, groupName)
        val updated = transform(current)
        if (updated.isDefault()) deleteGroupOverride(playlistId, groupName) else upsertGroupOverride(updated)
        materializeGroup(playlistId, groupName)
        materializeChannelsOfGroup(playlistId, groupName)
        refreshGroupCounts(playlistId)
    }

    /** Sets the custom positions of several channels at once ([positions]: uniqueKey -> position). */
    @Transaction
    open suspend fun updateChannelPositions(playlistId: Long, positions: Map<String, Int>) {
        positions.forEach { (key, position) ->
            updateChannel(playlistId, key) { it.copy(customSortOrder = position) }
        }
    }

    /** Sets the custom positions of several groups at once ([positions]: group name -> position). */
    @Transaction
    open suspend fun updateGroupPositions(playlistId: Long, positions: Map<String, Int>) {
        positions.forEach { (name, position) ->
            updateGroup(playlistId, name) { it.copy(customSortOrder = position) }
        }
    }

    private fun ChannelOverrideEntity.isDefault() = !hidden && customName == null && customSortOrder == null

    private fun GroupOverrideEntity.isDefault() = !hidden && customName == null && customSortOrder == null

    internal companion object {
        private const val CHANNEL_OVERRIDE = "FROM channel_overrides o " +
            "WHERE o.playlistId = channels.playlistId AND o.uniqueKey = channels.uniqueKey"
        private const val GROUP_OVERRIDE = "FROM group_overrides o " +
            "WHERE o.playlistId = channel_groups.playlistId AND o.groupName = channel_groups.name"

        /** Effective hidden flag of a `channels` row: hidden itself, or its group is hidden. */
        const val CHANNEL_HIDDEN = "(COALESCE((SELECT o.hidden $CHANNEL_OVERRIDE), 0) OR EXISTS(" +
            "SELECT 1 FROM channel_groups g INNER JOIN group_overrides gro " +
            "ON gro.playlistId = g.playlistId AND gro.groupName = g.name " +
            "WHERE g.id = channels.groupId AND gro.hidden = 1))"

        const val CHANNEL_VALUES = "hidden = $CHANNEL_HIDDEN, " +
            "customName = (SELECT o.customName $CHANNEL_OVERRIDE), " +
            "customSortOrder = (SELECT o.customSortOrder $CHANNEL_OVERRIDE)"

        const val GROUP_VALUES = "hidden = COALESCE((SELECT o.hidden $GROUP_OVERRIDE), 0), " +
            "customName = (SELECT o.customName $GROUP_OVERRIDE), " +
            "customSortOrder = (SELECT o.customSortOrder $GROUP_OVERRIDE)"
    }
}
