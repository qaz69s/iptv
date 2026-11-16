package com.streambox.player.core.data.di

import com.streambox.player.core.data.importer.DefaultPlaylistSourceOpener
import com.streambox.player.core.data.importer.M3uImporter
import com.streambox.player.core.data.importer.PlaylistImportStore
import com.streambox.player.core.data.importer.PlaylistImporter
import com.streambox.player.core.data.importer.PlaylistSourceOpener
import com.streambox.player.core.data.importer.RoomPlaylistImportStore
import com.streambox.player.core.data.repository.AndroidUriPermissions
import com.streambox.player.core.data.repository.DefaultChannelRepository
import com.streambox.player.core.data.repository.DefaultPlaylistRepository
import com.streambox.player.core.data.repository.DefaultSettingsRepository
import com.streambox.player.core.data.repository.UriPermissions
import com.streambox.player.core.domain.repository.ChannelRepository
import com.streambox.player.core.domain.repository.PlaylistRepository
import com.streambox.player.core.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {
    @Binds
    abstract fun bindsSettingsRepository(impl: DefaultSettingsRepository): SettingsRepository

    @Binds
    abstract fun bindsPlaylistRepository(impl: DefaultPlaylistRepository): PlaylistRepository

    @Binds
    abstract fun bindsChannelRepository(impl: DefaultChannelRepository): ChannelRepository

    @Binds
    abstract fun bindsPlaylistImporter(impl: M3uImporter): PlaylistImporter

    @Binds
    abstract fun bindsPlaylistImportStore(impl: RoomPlaylistImportStore): PlaylistImportStore

    @Binds
    abstract fun bindsPlaylistSourceOpener(impl: DefaultPlaylistSourceOpener): PlaylistSourceOpener

    @Binds
    abstract fun bindsUriPermissions(impl: AndroidUriPermissions): UriPermissions
}
