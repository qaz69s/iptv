package com.streambox.player.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import com.streambox.player.core.database.dao.ChannelDao
import com.streambox.player.core.database.dao.CustomGroupDao
import com.streambox.player.core.database.dao.FavoriteDao
import com.streambox.player.core.database.dao.OverrideDao
import com.streambox.player.core.database.dao.PlaylistDao
import com.streambox.player.core.database.dao.WatchHistoryDao
import com.streambox.player.core.database.entity.ChannelEntity
import com.streambox.player.core.database.entity.ChannelFtsEntity
import com.streambox.player.core.database.entity.ChannelGroupEntity
import com.streambox.player.core.database.entity.ChannelOverrideEntity
import com.streambox.player.core.database.entity.CustomGroupChannelEntity
import com.streambox.player.core.database.entity.CustomGroupEntity
import com.streambox.player.core.database.entity.FavoriteEntity
import com.streambox.player.core.database.entity.GroupOverrideEntity
import com.streambox.player.core.database.entity.PlaylistEntity
import com.streambox.player.core.database.entity.WatchHistoryEntity
import com.streambox.player.core.database.migration.Migration1To2
import com.streambox.player.core.database.migration.Migration2To3

/**
 * Schema v3 (P3c): v1 (P1: playlists, groups, channels + FTS); v2 (P3a, [Migration1To2]) adds favorites,
 * watch history and the user's channel / group overrides; v3 ([Migration2To3]) adds custom groups. Later phases add Program, EpgMapping, Reminder
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
        CustomGroupEntity::class,
        CustomGroupChannelEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class StreamBoxDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao

    abstract fun channelDao(): ChannelDao

    abstract fun favoriteDao(): FavoriteDao

    abstract fun watchHistoryDao(): WatchHistoryDao

    abstract fun overrideDao(): OverrideDao

    abstract fun customGroupDao(): CustomGroupDao

    companion object {
        const val NAME = "streambox.db"

        /** Every migration, in order; used by the app's builder and by the migration tests. */
        val MIGRATIONS: List<Migration> = listOf(Migration1To2, Migration2To3)
    }
}
