package com.streambox.player.core.database.di

import android.content.Context
import androidx.room.Room
import com.streambox.player.core.database.StreamBoxDatabase
import com.streambox.player.core.database.dao.ChannelDao
import com.streambox.player.core.database.dao.CustomGroupDao
import com.streambox.player.core.database.dao.FavoriteDao
import com.streambox.player.core.database.dao.OverrideDao
import com.streambox.player.core.database.dao.PlaylistDao
import com.streambox.player.core.database.dao.WatchHistoryDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun providesDatabase(@ApplicationContext context: Context): StreamBoxDatabase =
        Room.databaseBuilder(context, StreamBoxDatabase::class.java, StreamBoxDatabase.NAME)
            .apply { StreamBoxDatabase.MIGRATIONS.forEach { addMigrations(it) } }
            .build()

    @Provides
    fun providesPlaylistDao(database: StreamBoxDatabase): PlaylistDao = database.playlistDao()

    @Provides
    fun providesChannelDao(database: StreamBoxDatabase): ChannelDao = database.channelDao()

    @Provides
    fun providesFavoriteDao(database: StreamBoxDatabase): FavoriteDao = database.favoriteDao()

    @Provides
    fun providesWatchHistoryDao(database: StreamBoxDatabase): WatchHistoryDao = database.watchHistoryDao()

    @Provides
    fun providesOverrideDao(database: StreamBoxDatabase): OverrideDao = database.overrideDao()

    @Provides
    fun providesCustomGroupDao(database: StreamBoxDatabase): CustomGroupDao = database.customGroupDao()
}
