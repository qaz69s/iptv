package com.streambox.player.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "channels",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("playlistId", "generation", "sortOrder"),
        Index("groupId", "sortOrder"),
        Index("playlistId", "uniqueKey"),
        Index("tvgId"),
    ],
)
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val groupId: Long?,
    /** tvg-id based key, URL hash fallback; keeps favorites/history stable across refreshes (P3). */
    val uniqueKey: String,
    val name: String,
    val logoUrl: String?,
    val streamUrl: String,
    val tvgId: String?,
    val tvgName: String?,
    /** tvg-chno */
    val number: Int?,
    val catchupType: String?,
    val catchupSource: String?,
    val catchupDays: Int?,
    val timeshift: Int?,
    /** Per-channel #EXTVLCOPT overrides; fall back to the playlist's values when null. */
    val userAgent: String?,
    val referrer: String?,
    val hidden: Boolean = false,
    /** Position inside the playlist (0-based, import order). */
    val sortOrder: Int,
    val locked: Boolean = false,
    val generation: Int,
)

/** External-content FTS4 index on channel names (search UI arrives in P3). Room keeps it in sync via triggers. */
@Fts4(contentEntity = ChannelEntity::class)
@Entity(tableName = "channels_fts")
data class ChannelFtsEntity(val name: String, val tvgName: String?)
