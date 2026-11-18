package com.streambox.player.core.data.repository

import com.streambox.player.core.datastore.SettingsDataSource
import com.streambox.player.core.domain.repository.LastChannelRepository
import com.streambox.player.core.model.LastPlayedChannel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

internal class DefaultLastChannelRepository @Inject constructor(private val dataSource: SettingsDataSource) :
    LastChannelRepository {
    override val lastChannel: Flow<LastPlayedChannel?> = dataSource.lastPlayedChannel

    override suspend fun save(channel: LastPlayedChannel) = dataSource.setLastPlayedChannel(channel)

    override suspend fun clear() = dataSource.clearLastPlayedChannel()
}
