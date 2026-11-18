package com.streambox.player.core.data.repository

import com.streambox.player.core.database.dao.WatchHistoryDao
import com.streambox.player.core.database.entity.WatchHistoryEntity
import com.streambox.player.core.domain.repository.WatchHistoryRepository
import com.streambox.player.core.model.ChannelKey
import com.streambox.player.core.model.WatchHistoryEntry
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class DefaultWatchHistoryRepository @Inject constructor(
    private val watchHistoryDao: WatchHistoryDao,
    private val clock: Clock,
) : WatchHistoryRepository {
    override val history: Flow<List<WatchHistoryEntry>> =
        watchHistoryDao.observe(WatchHistoryRepository.MAX_ENTRIES).map { rows ->
            rows.map { WatchHistoryEntry(channel = it.channel.toModel(), watchedAtUtcMs = it.watchedAt) }
        }

    override suspend fun record(key: ChannelKey) = watchHistoryDao.record(
        WatchHistoryEntity(key.playlistId, key.uniqueKey, watchedAt = clock.millis()),
        cap = WatchHistoryRepository.MAX_ENTRIES,
    )

    override suspend fun remove(key: ChannelKey) = watchHistoryDao.delete(key.playlistId, key.uniqueKey)

    override suspend fun clear() = watchHistoryDao.clear()
}
