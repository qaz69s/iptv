package com.streambox.player.core.data.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

/** Wall clock for timestamps stored in the database (UTC epoch milliseconds); replaced in tests. */
@Module
@InstallIn(SingletonComponent::class)
internal object TimeModule {
    @Provides
    fun providesClock(): Clock = Clock.systemUTC()
}
