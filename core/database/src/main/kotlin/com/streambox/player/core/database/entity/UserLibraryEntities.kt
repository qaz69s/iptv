package com.streambox.player.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/*
 * Schema v2 (P3a): the user's own data. Every table is keyed by (playlistId, uniqueKey) or
 * (playlistId, groupName), never by channel / group row id, because each refresh re-imports the
 * playlist under a new generation with new ids. Rows are removed together with their playlist.
 */

/** A favorite channel. Ordered by [customOrder] when set (drag order), else by [addedAt]. */
@Entity(
    tableName = "favorites",
    primaryKeys = ["playlistId", "uniqueKey"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("addedAt")],
)
data class FavoriteEntity(
    val playlistId: Long,
    val uniqueKey: String,
    /** UTC epoch milliseconds. */
    val addedAt: Long,
    val customOrder: Int? = null,
)

/** One watched channel; a channel watched again moves to the top. Capped at the 50 most recent. */
@Entity(
    tableName = "watch_history",
    primaryKeys = ["playlistId", "uniqueKey"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("watchedAt")],
)
data class WatchHistoryEntity(
    val playlistId: Long,
    val uniqueKey: String,
    /** UTC epoch milliseconds. */
    val watchedAt: Long,
)

/**
 * The user's changes to one channel. Source of truth for `channels.hidden / customName /
 * customSortOrder`, which are re-applied to every new generation before it becomes visible.
 */
@Entity(
    tableName = "channel_overrides",
    primaryKeys = ["playlistId", "uniqueKey"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ChannelOverrideEntity(
    val playlistId: Long,
    val uniqueKey: String,
    val hidden: Boolean = false,
    val customName: String? = null,
    val customSortOrder: Int? = null,
)

/** The user's changes to one group of a playlist, keyed by the group's name in the playlist. */
@Entity(
    tableName = "group_overrides",
    primaryKeys = ["playlistId", "groupName"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class GroupOverrideEntity(
    val playlistId: Long,
    val groupName: String,
    val hidden: Boolean = false,
    val customName: String? = null,
    val customSortOrder: Int? = null,
)
