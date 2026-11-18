package com.streambox.player.core.domain.usecase

import com.streambox.player.core.domain.repository.ChannelRepository
import com.streambox.player.core.domain.repository.LastChannelRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/**
 * Current channel id of the remembered last channel. The record is a playlist + uniqueKey, so it
 * still resolves after the playlist was refreshed (new channel ids). A channel that is gone (deleted
 * playlist, removed from the source) is forgotten and null is returned.
 */
class ResolveLastChannelUseCase @Inject constructor(
    private val lastChannelRepository: LastChannelRepository,
    private val channelRepository: ChannelRepository,
) {
    suspend operator fun invoke(): Long? {
        val remembered = lastChannelRepository.lastChannel.first() ?: return null
        val id = channelRepository.channelIdByKey(remembered.key)
        if (id == null) lastChannelRepository.clear()
        return id
    }
}
