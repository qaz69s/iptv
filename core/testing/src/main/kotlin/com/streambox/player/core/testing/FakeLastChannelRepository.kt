package com.streambox.player.core.testing

import com.streambox.player.core.domain.repository.LastChannelRepository
import com.streambox.player.core.model.LastPlayedChannel
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [LastChannelRepository]; [saves] records every save in order. */
class FakeLastChannelRepository(initial: LastPlayedChannel? = null) : LastChannelRepository {
    override val lastChannel = MutableStateFlow(initial)
    val saves = mutableListOf<LastPlayedChannel>()
    var clears = 0

    override suspend fun save(channel: LastPlayedChannel) {
        saves += channel
        lastChannel.value = channel
    }

    override suspend fun clear() {
        clears++
        lastChannel.value = null
    }
}
