package com.streambox.player.core.testing

import com.streambox.player.core.domain.repository.WatchHistoryRepository
import com.streambox.player.core.model.Channel
import com.streambox.player.core.model.ChannelKey
import com.streambox.player.core.model.WatchHistoryEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

/**
 * In-memory [WatchHistoryRepository]: newest first, one entry per key, capped at
 * [WatchHistoryRepository.MAX_ENTRIES]. [recorded] lists every [record] call in order.
 */
class FakeWatchHistoryRepository(
    val channels: MutableStateFlow<List<Channel>> = MutableStateFlow(emptyList()),
    private val clock: () -> Long = { 0L },
) : WatchHistoryRepository {
    private val entries = MutableStateFlow<List<Pair<ChannelKey, Long>>>(emptyList())
    val recorded = mutableListOf<ChannelKey>()

    override val history: Flow<List<WatchHistoryEntry>> = combine(entries, channels) { stored, current ->
        val byKey = current.associateBy { it.key }
        stored.mapNotNull { (key, at) -> byKey[key]?.let { WatchHistoryEntry(it, at) } }
    }

    override suspend fun record(key: ChannelKey) {
        recorded += key
        entries.update { list ->
            (listOf(key to clock()) + list.filterNot { it.first == key }).take(WatchHistoryRepository.MAX_ENTRIES)
        }
    }

    override suspend fun remove(key: ChannelKey) = entries.update { list -> list.filterNot { it.first == key } }

    override suspend fun clear() = entries.update { emptyList() }
}
