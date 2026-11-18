package com.streambox.player.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v2 -> v3 (P3c): custom groups and their channels. Nothing existing changes. The CREATE statements
 * match the exported schema `3.json`.
 */
@Suppress("MagicNumber") // Schema version numbers.
object Migration2To3 : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        CREATE_STATEMENTS.forEach(db::execSQL)
    }

    private val CREATE_STATEMENTS = listOf(
        "CREATE TABLE IF NOT EXISTS `custom_groups` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`name` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)",
        "CREATE TABLE IF NOT EXISTS `custom_group_channels` (`groupId` INTEGER NOT NULL, " +
            "`playlistId` INTEGER NOT NULL, `uniqueKey` TEXT NOT NULL, `position` INTEGER NOT NULL, " +
            "`addedAt` INTEGER NOT NULL, PRIMARY KEY(`groupId`, `playlistId`, `uniqueKey`), " +
            "FOREIGN KEY(`groupId`) REFERENCES `custom_groups`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , " +
            "FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        "CREATE INDEX IF NOT EXISTS `index_custom_group_channels_playlistId` " +
            "ON `custom_group_channels` (`playlistId`)",
    )
}
