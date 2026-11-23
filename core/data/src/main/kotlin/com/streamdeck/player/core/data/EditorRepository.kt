package com.streamdeck.player.core.data

import com.streamdeck.player.core.database.ChannelDao
import com.streamdeck.player.core.database.ChannelEntity
import com.streamdeck.player.core.database.ChannelGroupEntity
import com.streamdeck.player.core.database.ChannelOverrideEntity
import com.streamdeck.player.core.database.EditorDao
import com.streamdeck.player.core.database.GroupOverrideEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Playlist editor (spec §4.1): every edit is stored as an override keyed by stable identifiers and then re-applied
 * to the live rows, so the same edits survive the next refresh.
 */
@Singleton
class EditorRepository @Inject constructor(private val channels: ChannelDao, private val editor: EditorDao) {
    fun groups(pid: Long): Flow<List<ChannelGroupEntity>> = channels.observeAllGroups(pid)
    fun groupChannels(pid: Long, groupId: Long): Flow<List<ChannelEntity>> = channels.observeGroupChannels(pid, groupId)

    suspend fun setGroupHidden(g: ChannelGroupEntity, hidden: Boolean) = editGroup(g) { it.copy(hidden = hidden) }
    suspend fun setGroupLocked(g: ChannelGroupEntity, locked: Boolean) = editGroup(g) { it.copy(locked = locked) }
    suspend fun renameGroup(g: ChannelGroupEntity, name: String) = editGroup(g) { it.copy(customName = name.trim().ifBlank { null }) }

    suspend fun createCustomGroup(pid: Long, name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        editor.upsertGroupOverride(GroupOverrideEntity(playlistId = pid, name = clean, custom = true, sortOrder = Int.MAX_VALUE / 2))
        channels.applyOverrides(pid)
    }

    /** Swaps the group with its neighbour; persists the whole resulting order. */
    suspend fun moveGroup(all: List<ChannelGroupEntity>, g: ChannelGroupEntity, delta: Int) {
        val list = all.toMutableList()
        val i = list.indexOfFirst { it.id == g.id }
        val j = i + delta
        if (i < 0 || j !in list.indices) return
        java.util.Collections.swap(list, i, j)
        list.forEachIndexed { idx, item ->
            val o = editor.groupOverride(item.playlistId, item.originalName) ?: GroupOverrideEntity(item.playlistId, item.originalName)
            editor.upsertGroupOverride(o.copy(sortOrder = idx))
        }
        channels.applyOverrides(g.playlistId)
    }

    suspend fun setChannelHidden(c: ChannelEntity, hidden: Boolean) = editChannel(c) { it.copy(hidden = hidden) }
    suspend fun setChannelLocked(c: ChannelEntity, locked: Boolean) = editChannel(c) { it.copy(locked = locked) }
    suspend fun renameChannel(c: ChannelEntity, name: String) = editChannel(c) { it.copy(customName = name.trim().ifBlank { null }) }
    suspend fun moveChannelToGroup(c: ChannelEntity, target: ChannelGroupEntity) = editChannel(c) { it.copy(customGroup = target.originalName) }

    /** Swaps sortOrder with the neighbour inside the same group. */
    suspend fun moveChannel(groupChannels: List<ChannelEntity>, c: ChannelEntity, delta: Int) {
        val i = groupChannels.indexOfFirst { it.id == c.id }
        val j = i + delta
        if (i < 0 || j !in groupChannels.indices) return
        val other = groupChannels[j]
        write(c) { it.copy(sortOrder = other.sortOrder) }
        write(other) { it.copy(sortOrder = c.sortOrder) }
        channels.applyOverrides(c.playlistId)
    }

    private suspend fun editGroup(g: ChannelGroupEntity, change: (GroupOverrideEntity) -> GroupOverrideEntity) {
        val o = editor.groupOverride(g.playlistId, g.originalName) ?: GroupOverrideEntity(g.playlistId, g.originalName)
        editor.upsertGroupOverride(change(o))
        channels.applyOverrides(g.playlistId)
    }

    private suspend fun editChannel(c: ChannelEntity, change: (ChannelOverrideEntity) -> ChannelOverrideEntity) {
        write(c, change)
        channels.applyOverrides(c.playlistId)
    }

    private suspend fun write(c: ChannelEntity, change: (ChannelOverrideEntity) -> ChannelOverrideEntity) {
        val o = editor.channelOverride(c.playlistId, c.uniqueKey) ?: ChannelOverrideEntity(c.playlistId, c.uniqueKey)
        editor.upsertChannelOverride(change(o))
    }
}
