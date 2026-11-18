package com.streambox.player.core.testing

import com.streambox.player.core.domain.repository.FavoritesRepository
import com.streambox.player.core.model.Channel
import com.streambox.player.core.model.ChannelKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [FavoritesRepository] with the real ordering rules (custom order first, then time added).
 * Keys are resolved against [channels] (the "current data"); [clock] supplies the time added.
 */
class FakeFavoritesRepository(
    val channels: MutableStateFlow<List<Channel>> = MutableStateFlow(emptyList()),
    private val clock: () -> Long = { 0L },
) : FavoritesRepository {
    private data class Entry(val key: ChannelKey, val addedAt: Long, val customOrder: Int?)

    private val entries = MutableStateFlow<List<Entry>>(emptyList())

    override val favorites: Flow<List<Channel>> = combine(entries, channels) { stored, current ->
        val byKey = current.associateBy { it.key }
        stored.sortedWith(compareBy<Entry>({ it.customOrder == null }, { it.customOrder }, { it.addedAt }))
            .mapNotNull { byKey[it.key] }
    }

    override val favoriteKeys: Flow<Set<ChannelKey>> = entries.map { list -> list.mapTo(mutableSetOf()) { it.key } }

    override fun isFavorite(key: ChannelKey): Flow<Boolean> = entries.map { list -> list.any { it.key == key } }

    override suspend fun add(key: ChannelKey) = entries.update { list ->
        if (list.any { it.key == key }) list else list + Entry(key, clock(), customOrder = null)
    }

    override suspend fun remove(key: ChannelKey) = entries.update { list -> list.filterNot { it.key == key } }

    override suspend fun reorder(keys: List<ChannelKey>) = entries.update { list ->
        list.map { entry -> entry.copy(customOrder = keys.indexOf(entry.key).takeIf { it >= 0 }) }
    }

    override suspend fun clearCustomOrder() = entries.update { list -> list.map { it.copy(customOrder = null) } }
}
