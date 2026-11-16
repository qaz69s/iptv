package com.streambox.player.core.player.di

import com.streambox.player.core.player.Media3PlaybackController
import com.streambox.player.core.player.PlaybackController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PlayerModule {
    @Binds
    abstract fun bindsPlaybackController(impl: Media3PlaybackController): PlaybackController
}
