package com.streamdeck.player.core.database

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

enum class PlaylistType { M3U_URL, M3U_FILE, XTREAM }

/** updateMode: MANUAL | ON_START | INTERVAL (every [updateIntervalHours]). */
@Entity(tableName = "playlist")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: PlaylistType,
    val url: String,
    val username: String? = null,
    val passwordEncrypted: String? = null,
    val userAgent: String? = null,
    val headersJson: String? = null,
    val epgUrl: String? = null,
    val updateMode: String = UPDATE_MANUAL,
    val updateIntervalHours: Int = 24,
    val lastUpdated: Long = 0,
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
    /** Off by default: HTTPS only unless the user opts in for this playlist (spec §5). */
    val allowCleartext: Boolean = false,
    val locked: Boolean = false,
    /** Xtream panel time zone (from `server_info.timezone`), used for `xc` catch-up. */
    val serverTimezone: String? = null,
) {
    companion object {
        const val UPDATE_MANUAL = "MANUAL"
        const val UPDATE_ON_START = "ON_START"
        const val UPDATE_INTERVAL = "INTERVAL"
    }
}

/** [pending] rows belong to an import in progress; they are swapped in atomically on success (spec §8.2). */
@Entity(tableName = "channel_group", indices = [Index("playlistId", "pending"), Index("playlistId", "originalName")])
data class ChannelGroupEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val name: String,
    /** Name as it appears in the source; user overrides are keyed by it. */
    val originalName: String = name,
    val hidden: Boolean = false,
    val sortOrder: Int = 0,
    val locked: Boolean = false,
    val pending: Boolean = false,
)

@Entity(
    tableName = "channel",
    indices = [Index("playlistId", "pending"), Index("groupId"), Index("playlistId", "uniqueKey"), Index("playlistId", "number")],
)
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val groupId: Long,
    val groupName: String = "",
    val uniqueKey: String,
    val name: String,
    val logoUrl: String?,
    val streamUrl: String,
    val tvgId: String?,
    val tvgName: String?,
    val number: Int?,
    val catchupType: String?,
    val catchupSource: String?,
    val catchupDays: Int?,
    val userAgent: String?,
    val referrer: String?,
    val hidden: Boolean = false,
    /** Effective (possibly user-customised) order. */
    val sortOrder: Int = 0,
    /** Order in the source playlist — used by the “default” sort. */
    val importOrder: Int = 0,
    val locked: Boolean = false,
    val pending: Boolean = false,
)

@Fts4(contentEntity = ChannelEntity::class)
@Entity(tableName = "channel_fts")
data class ChannelFts(val name: String, val groupName: String)

/** Favorites survive playlist refreshes because they reference playlistId + uniqueKey, not row ids (spec §4.7). */
@Entity(tableName = "favorite", primaryKeys = ["playlistId", "uniqueKey"])
data class FavoriteEntity(val playlistId: Long, val uniqueKey: String, val addedAt: Long)

@Entity(tableName = "history", primaryKeys = ["playlistId", "uniqueKey"], indices = [Index("watchedAt")])
data class HistoryEntity(val playlistId: Long, val uniqueKey: String, val watchedAt: Long, val positionMs: Long = 0)

/** User edits to a channel; null = keep the source value. Re-applied after every refresh. */
@Entity(tableName = "channel_override", primaryKeys = ["playlistId", "uniqueKey"])
data class ChannelOverrideEntity(
    val playlistId: Long,
    val uniqueKey: String,
    val customName: String? = null,
    val hidden: Boolean? = null,
    val sortOrder: Int? = null,
    val locked: Boolean? = null,
    /** originalName of the target group (may be a custom group). */
    val customGroup: String? = null,
)

/** User edits to a group keyed by its original name; [custom] = group created by the user. */
@Entity(tableName = "group_override", primaryKeys = ["playlistId", "name"])
data class GroupOverrideEntity(
    val playlistId: Long,
    val name: String,
    val customName: String? = null,
    val hidden: Boolean? = null,
    val sortOrder: Int? = null,
    val locked: Boolean? = null,
    val custom: Boolean = false,
)
