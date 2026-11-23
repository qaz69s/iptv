package com.streamdeck.player.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(
    entities = [
        PlaylistEntity::class, ChannelGroupEntity::class, ChannelEntity::class, ChannelFts::class,
        FavoriteEntity::class, HistoryEntity::class, ChannelOverrideEntity::class, GroupOverrideEntity::class,
        ProgramEntity::class, ProgramFts::class, EpgChannelEntity::class, EpgMappingEntity::class, ReminderEntity::class,
        VodCategoryEntity::class, VodItemEntity::class, SeriesEntity::class, VodProgressEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun channelDao(): ChannelDao
    abstract fun libraryDao(): LibraryDao
    abstract fun editorDao(): EditorDao
    abstract fun epgDao(): EpgDao
    abstract fun vodDao(): VodDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun db(@ApplicationContext ctx: Context): AppDatabase =
        Room.databaseBuilder(ctx, AppDatabase::class.java, "streamdeck.db").build()

    @Provides fun playlistDao(db: AppDatabase) = db.playlistDao()

    @Provides fun channelDao(db: AppDatabase) = db.channelDao()

    @Provides fun libraryDao(db: AppDatabase) = db.libraryDao()

    @Provides fun editorDao(db: AppDatabase) = db.editorDao()

    @Provides fun epgDao(db: AppDatabase) = db.epgDao()

    @Provides fun vodDao(db: AppDatabase) = db.vodDao()
}
