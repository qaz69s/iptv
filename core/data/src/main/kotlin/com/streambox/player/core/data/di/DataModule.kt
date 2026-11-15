package com.streambox.player.core.data.di

import com.streambox.player.core.data.repository.DefaultSettingsRepository
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
}
