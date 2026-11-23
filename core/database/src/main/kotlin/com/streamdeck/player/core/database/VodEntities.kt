package com.streamdeck.player.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** [kind] = movie | series. */
@Entity(tableName = "vod_category", primaryKeys = ["playlistId", "kind", "categoryId", "pending"])
data class VodCategoryEntity(val playlistId: Long, val kind: String, val categoryId: String, val name: String, val sortOrder: Int, val pending: Boolean = false)

@Entity(tableName = "vod_item", indices = [Index("playlistId", "pending", "categoryId"), Index("playlistId", "streamId")])
data class VodItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val streamId: Long,
    val name: String,
    val categoryId: String?,
    val poster: String?,
    val rating: Float?,
    val extension: String,
    val added: Long?,
    val sortOrder: Int,
    val pending: Boolean = false,
)

@Entity(tableName = "series", indices = [Index("playlistId", "pending", "categoryId"), Index("playlistId", "seriesId")])
data class SeriesEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val seriesId: Long,
    val name: String,
    val categoryId: String?,
    val cover: String?,
    val plot: String?,
    val rating: Float?,
    val genre: String?,
    val releaseDate: String?,
    val sortOrder: Int,
    val pending: Boolean = false,
)

/** Resume position / watched flag keyed by `movie:{streamId}` or `episode:{episodeId}` (spec §4.6). */
@Entity(tableName = "vod_progress", primaryKeys = ["playlistId", "key"])
data class VodProgressEntity(val playlistId: Long, val key: String, val positionMs: Long, val durationMs: Long, val watched: Boolean, val updatedAt: Long)
