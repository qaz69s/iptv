package com.streambox.player.core.testing

import com.streambox.player.core.domain.repository.CustomGroupsRepository
import com.streambox.player.core.model.Channel
import com.streambox.player.core.model.ChannelKey
import com.streambox.player.core.model.CustomGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [CustomGroupsRepository]. Members are channel keys resolved against [channels] (the
 * channels that exist and are visible); a member without a channel is kept but not listed.
 */
@Suppress("TooManyFunctions")
class FakeCustomGroupsRepository(channels: List<Channel> = emptyList()) : CustomGroupsRepository {
    val channelsFlow = MutableStateFlow(channels)

    /** Groups in order with their member keys in order. */
    val state = MutableStateFlow<List<Pair<CustomGroup, List<ChannelKey>>>>(emptyList())
    private var nextId = 1L

    override val groups: Flow<List<CustomGroup>> = combine(state, channelsFlow) { groups, all ->
        val keys = all.map { it.key }.toSet()
        groups.map { (group, members) -> group.copy(channelCount = members.count { it in keys }) }
    }

    override fun channels(groupId: Long): Flow<List<Channel>> = combine(state, channelsFlow) { groups, all ->
        val byKey = all.associateBy { it.key }
        groups.firstOrNull { it.first.id == groupId }?.second.orEmpty().mapNotNull { byKey[it] }
    }

    override fun groupsOf(key: ChannelKey): Flow<Set<Long>> = state.map { groups ->
        groups.filter { key in it.second }.mapTo(mutableSetOf()) { it.first.id }
    }

    override suspend fun create(name: String): Long? {
        val trimmed = name.trim().takeIf { it.isNotEmpty() } ?: return null
        state.value.firstOrNull { it.first.name == trimmed }?.let { return it.first.id }
        val id = nextId++
        state.update { it + (CustomGroup(id, trimmed) to emptyList()) }
        return id
    }

    override suspend fun rename(groupId: Long, name: String): Boolean {
        val trimmed = name.trim().takeIf { it.isNotEmpty() } ?: return false
        update(groupId) { (group, members) -> group.copy(name = trimmed) to members }
        return true
    }

    override suspend fun delete(groupId: Long) = state.update { list -> list.filterNot { it.first.id == groupId } }

    override suspend fun reorderGroups(orderedIds: List<Long>) = state.update { list ->
        val listed = orderedIds.distinct().mapNotNull { id -> list.firstOrNull { it.first.id == id } }
        listed + list.filterNot { it in listed }
    }

    override suspend fun addChannel(groupId: Long, key: ChannelKey) = update(groupId) { (group, members) ->
        group to if (key in members) members else members + key
    }

    override suspend fun removeChannel(groupId: Long, key: ChannelKey) = update(groupId) { (group, members) ->
        group to members - key
    }

    override suspend fun reorderChannels(groupId: Long, orderedKeys: List<ChannelKey>) =
        update(groupId) { (group, members) ->
            val listed = orderedKeys.distinct().filter { it in members }
            group to listed + members.filterNot { it in listed }
        }

    private fun update(
        groupId: Long,
        change: (Pair<CustomGroup, List<ChannelKey>>) -> Pair<CustomGroup, List<ChannelKey>>,
    ) = state.update { list -> list.map { if (it.first.id == groupId) change(it) else it } }
}
