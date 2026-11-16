package com.streambox.player.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.streambox.player.core.database.dao.ChannelDao
import com.streambox.player.core.database.dao.PlaylistDao
import com.streambox.player.core.database.entity.ChannelEntity
import com.streambox.player.core.database.entity.ChannelFtsEntity
import com.streambox.player.core.database.entity.ChannelGroupEntity
import com.streambox.player.core.database.entity.PlaylistEntity

/**
 * Schema v1 (P1). Later phases add Program, EpgMapping, Favorite, History, Reminder and VOD tables
 * through migrations; exported schemas live in `core/database/schemas`.
 */
@Database(
    entities = [PlaylistEntity::class, ChannelGroupEntity::class, ChannelEntity::class, ChannelFtsEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class StreamBoxDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao

    abstract fun channelDao(): ChannelDao

    companion object {
        const val NAME = "streambox.db"
    }
}
