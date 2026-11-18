package com.streambox.player.core.domain.usecase

import com.streambox.player.core.domain.repository.LastChannelRepository
import com.streambox.player.core.domain.repository.WatchHistoryRepository
import com.streambox.player.core.model.LastPlayedChannel
import com.streambox.player.core.model.PlaybackItem
import javax.inject.Inject

/**
 * A channel really started playing: remember it as the last channel (continue on launch) and add it
 * to the watch history. Both are stored by playlist + uniqueKey so they survive refreshes.
 */
class RecordPlaybackStartUseCase @Inject constructor(
    private val lastChannelRepository: LastChannelRepository,
    private val watchHistoryRepository: WatchHistoryRepository,
) {
    suspend operator fun invoke(item: PlaybackItem) {
        lastChannelRepository.save(LastPlayedChannel(playlistId = item.playlistId, uniqueKey = item.uniqueKey))
        watchHistoryRepository.record(item.key)
    }
}
