package com.streambox.player.core.testing

import com.streambox.player.core.domain.repository.ChannelOverridesRepository
import com.streambox.player.core.model.ChannelKey
import com.streambox.player.core.model.ChannelOverride
import com.streambox.player.core.model.EditorChannel
import com.streambox.player.core.model.EditorGroup
import com.streambox.player.core.model.GroupOverride
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * In-memory [ChannelOverridesRepository] that only stores the overrides (it does not change any
 * channel list). Reordering hands out positions 0..n-1 in the given order. The editor lists are what
 * tests put into [editorGroupsFlow] / [editorChannelsFlow] (keyed by group name), with the stored
 * overrides applied (hidden, name, order), like the database does.
 */
@Suppress("TooManyFunctions")
class FakeChannelOverridesRepository : ChannelOverridesRepository {
    val channels = MutableStateFlow<Map<ChannelKey, ChannelOverride>>(emptyMap())
    val groups = MutableStateFlow<Map<Pair<Long, String>, GroupOverride>>(emptyMap())
    val editorGroupsFlow = MutableStateFlow<List<EditorGroup>>(emptyList())
    val editorChannelsFlow = MutableStateFlow<Map<String, List<EditorChannel>>>(emptyMap())

    override fun editorGroups(playlistId: Long): Flow<List<EditorGroup>> =
        combine(editorGroupsFlow, groups) { list, overrides ->
            list.filter { it.playlistId == playlistId }
                .map { group ->
                    val o = overrides[playlistId to group.name]
                    group.copy(hidden = o?.hidden ?: group.hidden, customName = o?.customName ?: group.customName)
                }
                .withIndex()
                .sortedBy { (index, group) -> overrides[playlistId to group.name]?.customSortOrder ?: index }
                .map { it.value }
        }

    override fun editorChannels(playlistId: Long, groupName: String): Flow<List<EditorChannel>> =
        combine(editorChannelsFlow, channels, groups) { lists, overrides, groupOverrides ->
            val groupHidden = groupOverrides[playlistId to groupName]?.hidden == true
            lists[groupName].orEmpty()
                .filter { it.key.playlistId == playlistId }
                .map { channel ->
                    val o = overrides[channel.key]
                    val self = o?.hidden ?: channel.selfHidden
                    channel.copy(
                        selfHidden = self,
                        hidden = self || groupHidden,
                        customName = o?.customName ?: channel.customName,
                    )
                }
                .withIndex()
                .sortedBy { (index, channel) -> overrides[channel.key]?.customSortOrder ?: index }
                .map { it.value }
        }

    override fun channelOverrides(playlistId: Long): Flow<List<ChannelOverride>> =
        channels.map { map -> map.values.filter { it.key.playlistId == playlistId } }

    override fun groupOverrides(playlistId: Long): Flow<List<GroupOverride>> =
        groups.map { map -> map.values.filter { it.playlistId == playlistId } }

    override suspend fun setChannelHidden(key: ChannelKey, hidden: Boolean) = updateChannel(key) {
        it.copy(hidden = hidden)
    }

    override suspend fun renameChannel(key: ChannelKey, name: String?) = updateChannel(key) {
        it.copy(customName = name?.trim()?.takeIf(String::isNotEmpty))
    }

    override suspend fun reorderChannels(playlistId: Long, orderedKeys: List<String>) =
        orderedKeys.distinct().forEachIndexed { index, key ->
            updateChannel(ChannelKey(playlistId, key)) { it.copy(customSortOrder = index) }
        }

    override suspend fun resetChannelOrder(playlistId: Long) = channels.value.keys
        .filter { it.playlistId == playlistId }
        .forEach { key -> updateChannel(key) { it.copy(customSortOrder = null) } }

    override suspend fun setGroupHidden(playlistId: Long, groupName: String, hidden: Boolean) =
        updateGroup(playlistId, groupName) { it.copy(hidden = hidden) }

    override suspend fun renameGroup(playlistId: Long, groupName: String, name: String?) =
        updateGroup(playlistId, groupName) { it.copy(customName = name?.trim()?.takeIf(String::isNotEmpty)) }

    override suspend fun reorderGroups(playlistId: Long, orderedGroupNames: List<String>) =
        orderedGroupNames.distinct().forEachIndexed { index, name ->
            updateGroup(playlistId, name) { it.copy(customSortOrder = index) }
        }

    private fun updateChannel(key: ChannelKey, transform: (ChannelOverride) -> ChannelOverride) = channels.update {
        val updated = transform(it[key] ?: ChannelOverride(key))
        if (updated == ChannelOverride(key)) it - key else it + (key to updated)
    }

    private fun updateGroup(playlistId: Long, name: String, transform: (GroupOverride) -> GroupOverride) =
        groups.update {
            val id = playlistId to name
            val default = GroupOverride(playlistId, name)
            val updated = transform(it[id] ?: default)
            if (updated == default) it - id else it + (id to updated)
        }
}
