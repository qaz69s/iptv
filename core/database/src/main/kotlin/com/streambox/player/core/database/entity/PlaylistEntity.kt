package com.streambox.player.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Columns follow requirements §7. [activeGeneration] implements "write new data -> validate -> atomic swap":
 * channels/groups of an import are written under a new generation and only become visible when the
 * playlist row is switched to it in one transaction.
 */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** M3U_URL | M3U_FILE | XTREAM */
    val type: String,
    /** URL, SAF content URI or Xtream server address. */
    val url: String,
    val username: String? = null,
    val passwordEncrypted: String? = null,
    val userAgent: String? = null,
    val referrer: String? = null,
    val headersJson: String? = null,
    val epgUrl: String? = null,
    /** MANUAL | ON_START | INTERVAL */
    val updateMode: String = "MANUAL",
    val updateIntervalHours: Int = 0,
    val lastUpdated: Long? = null,
    val lastError: String? = null,
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
    val allowCleartext: Boolean = false,
    @ColumnInfo(defaultValue = "0") val activeGeneration: Int = 0,
    val channelCount: Int = 0,
    val groupCount: Int = 0,
    val createdAt: Long = 0,
)
