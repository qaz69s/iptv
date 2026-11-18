package com.streambox.player.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import com.streambox.player.core.database.dao.ChannelDao
import com.streambox.player.core.database.dao.FavoriteDao
import com.streambox.player.core.database.dao.OverrideDao
import com.streambox.player.core.database.dao.PlaylistDao
import com.streambox.player.core.database.dao.WatchHistoryDao
import com.streambox.player.core.database.entity.ChannelEntity
import com.streambox.player.core.database.entity.ChannelFtsEntity
import com.streambox.player.core.database.entity.ChannelGroupEntity
import com.streambox.player.core.database.entity.ChannelOverrideEntity
import com.streambox.player.core.database.entity.FavoriteEntity
import com.streambox.player.core.database.entity.GroupOverrideEntity
import com.streambox.player.core.database.entity.PlaylistEntity
import com.streambox.player.core.database.entity.WatchHistoryEntity
import com.streambox.player.core.database.migration.Migration1To2

/**
 * Schema v2 (P3a): v1 (P1: playlists, groups, channels + FTS) plus favorites, watch history and the
 * user's channel / group overrides ([Migration1To2]). Later phases add Program, EpgMapping, Reminder
 * and VOD tables through further migrations; exported schemas live in `core/database/schemas`.
 */
@Database(
    entities = [
        PlaylistEntity::class,
        ChannelGroupEntity::class,
        ChannelEntity::class,
        ChannelFtsEntity::class,
        FavoriteEntity::class,
        WatchHistoryEntity::class,
        ChannelOverrideEntity::class,
        GroupOverrideEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class StreamBoxDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao

    abstract fun channelDao(): ChannelDao

    abstract fun favoriteDao(): FavoriteDao

    abstract fun watchHistoryDao(): WatchHistoryDao

    abstract fun overrideDao(): OverrideDao

    companion object {
        const val NAME = "streambox.db"

        /** Every migration, in order; used by the app's builder and by the migration tests. */
        val MIGRATIONS: List<Migration> = listOf(Migration1To2)
    }
}
