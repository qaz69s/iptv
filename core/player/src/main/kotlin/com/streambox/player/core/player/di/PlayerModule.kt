package com.streambox.player.core.player.di

import android.os.SystemClock
import com.streambox.player.core.player.Media3PlaybackController
import com.streambox.player.core.player.PlaybackController
import com.streambox.player.core.player.sleep.MonotonicClock
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PlayerModule {
    @Binds
    abstract fun bindsPlaybackController(impl: Media3PlaybackController): PlaybackController

    companion object {
        /** Keeps counting while the device sleeps; never jumps with wall-clock changes. */
        @Provides
        fun providesMonotonicClock(): MonotonicClock = MonotonicClock { SystemClock.elapsedRealtime() }
    }
}
