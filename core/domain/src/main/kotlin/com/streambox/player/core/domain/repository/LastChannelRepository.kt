package com.streambox.player.core.domain.repository

import com.streambox.player.core.model.LastPlayedChannel
import kotlinx.coroutines.flow.Flow

/** Remembers the channel that last started playing (requirements §4.3 "记住上次观看频道"). */
interface LastChannelRepository {
    val lastChannel: Flow<LastPlayedChannel?>

    suspend fun save(channel: LastPlayedChannel)

    suspend fun clear()
}
