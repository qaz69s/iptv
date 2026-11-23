package com.streamdeck.player.core.database

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

/** Times are UTC epoch millis as published by the source; the global EPG offset is applied at query/display time. */
@Entity(
    tableName = "program",
    indices = [Index("playlistId", "epgChannelId", "start"), Index("playlistId", "pending")],
)
data class ProgramEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val epgChannelId: String,
    val start: Long,
    val end: Long,
    val title: String,
    val description: String?,
    val category: String?,
    val iconUrl: String?,
    val pending: Boolean = false,
)

@Fts4(contentEntity = ProgramEntity::class)
@Entity(tableName = "program_fts")
data class ProgramFts(val title: String)

@Entity(tableName = "epg_channel", primaryKeys = ["playlistId", "id", "pending"])
data class EpgChannelEntity(val playlistId: Long, val id: String, val displayName: String, val icon: String?, val pending: Boolean = false)

/** Channel → EPG channel. [manual] mappings are chosen by the user and never overwritten by auto-matching. */
@Entity(tableName = "epg_mapping", primaryKeys = ["playlistId", "uniqueKey"], indices = [Index("playlistId", "epgChannelId")])
data class EpgMappingEntity(val playlistId: Long, val uniqueKey: String, val epgChannelId: String, val manual: Boolean = false)

@Entity(tableName = "reminder", indices = [Index("startAt")])
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val uniqueKey: String,
    val channelName: String,
    val title: String,
    val startAt: Long,
    val autoSwitch: Boolean,
)
