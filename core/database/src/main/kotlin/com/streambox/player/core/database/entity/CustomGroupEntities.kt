package com.streambox.player.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Schema v3 (P3c): the user's own groups. A custom group is not tied to a playlist: its channels can
 * come from several playlists. Members are stored by (playlistId, uniqueKey), like favorites, so they
 * survive refreshes; a member whose channel is missing from the current data is kept but not listed.
 */

/** A user-made group, shown next to the playlists' groups as a virtual group. */
@Entity(tableName = "custom_groups")
data class CustomGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Position among the custom groups (0 first). */
    val sortOrder: Int,
    /** UTC epoch milliseconds. */
    val createdAt: Long,
)

/** One channel of a custom group; removed with its group or with its playlist. */
@Entity(
    tableName = "custom_group_channels",
    primaryKeys = ["groupId", "playlistId", "uniqueKey"],
    foreignKeys = [
        ForeignKey(
            entity = CustomGroupEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("playlistId")],
)
data class CustomGroupChannelEntity(
    val groupId: Long,
    val playlistId: Long,
    val uniqueKey: String,
    /** Position inside the group (0 first); new members are appended. */
    val position: Int,
    /** UTC epoch milliseconds. */
    val addedAt: Long,
)
