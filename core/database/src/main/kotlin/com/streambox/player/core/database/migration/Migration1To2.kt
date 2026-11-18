package com.streambox.player.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 -> v2 (P3a): favorites, watch history and user overrides, plus the materialized override
 * columns on channels / channel_groups. Existing rows keep their data; the new columns start empty
 * (no overrides existed before). The CREATE statements match the exported schema `2.json`.
 */
object Migration1To2 : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `channels` ADD COLUMN `customName` TEXT")
        db.execSQL("ALTER TABLE `channels` ADD COLUMN `customSortOrder` INTEGER")
        db.execSQL("ALTER TABLE `channel_groups` ADD COLUMN `customName` TEXT")
        db.execSQL("ALTER TABLE `channel_groups` ADD COLUMN `customSortOrder` INTEGER")
        CREATE_STATEMENTS.forEach(db::execSQL)
    }

    private val CREATE_STATEMENTS = listOf(
        "CREATE TABLE IF NOT EXISTS `favorites` (`playlistId` INTEGER NOT NULL, `uniqueKey` TEXT NOT NULL, " +
            "`addedAt` INTEGER NOT NULL, `customOrder` INTEGER, PRIMARY KEY(`playlistId`, `uniqueKey`), " +
            "FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_favorites_addedAt` ON `favorites` (`addedAt`)",
        "CREATE TABLE IF NOT EXISTS `watch_history` (`playlistId` INTEGER NOT NULL, `uniqueKey` TEXT NOT NULL, " +
            "`watchedAt` INTEGER NOT NULL, PRIMARY KEY(`playlistId`, `uniqueKey`), " +
            "FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_watch_history_watchedAt` ON `watch_history` (`watchedAt`)",
        "CREATE TABLE IF NOT EXISTS `channel_overrides` (`playlistId` INTEGER NOT NULL, `uniqueKey` TEXT NOT NULL, " +
            "`hidden` INTEGER NOT NULL, `customName` TEXT, `customSortOrder` INTEGER, " +
            "PRIMARY KEY(`playlistId`, `uniqueKey`), " +
            "FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE TABLE IF NOT EXISTS `group_overrides` (`playlistId` INTEGER NOT NULL, `groupName` TEXT NOT NULL, " +
            "`hidden` INTEGER NOT NULL, `customName` TEXT, `customSortOrder` INTEGER, " +
            "PRIMARY KEY(`playlistId`, `groupName`), " +
            "FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
    )
}
