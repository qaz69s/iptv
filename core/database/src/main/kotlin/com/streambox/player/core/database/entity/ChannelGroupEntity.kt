package com.streambox.player.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "channel_groups",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("playlistId", "generation", "sortOrder"), Index("name")],
)
data class ChannelGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    /** Empty string = entries without a group ("Uncategorized" in the UI). */
    val name: String,
    /** Hidden by the user (v2, materialized from [GroupOverrideEntity]). */
    val hidden: Boolean = false,
    val sortOrder: Int,
    val locked: Boolean = false,
    val generation: Int,
    val channelCount: Int = 0,
    /** User's name for the group (v2, materialized from [GroupOverrideEntity]); null = [name]. */
    val customName: String? = null,
    /** User's position (v2, materialized from [GroupOverrideEntity]); null = [sortOrder]. */
    val customSortOrder: Int? = null,
)
