package com.streambox.player.core.data.repository

import com.streambox.player.core.database.dao.ChannelDao
import com.streambox.player.core.database.dao.EditorChannelRow
import com.streambox.player.core.database.dao.EditorGroupRow
import com.streambox.player.core.database.dao.KeyPositionRow
import com.streambox.player.core.database.dao.OverrideDao
import com.streambox.player.core.database.entity.ChannelOverrideEntity
import com.streambox.player.core.database.entity.GroupOverrideEntity
import com.streambox.player.core.domain.repository.ChannelOverridesRepository
import com.streambox.player.core.model.ChannelKey
import com.streambox.player.core.model.ChannelOverride
import com.streambox.player.core.model.EditorChannel
import com.streambox.player.core.model.EditorGroup
import com.streambox.player.core.model.GroupOverride
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal class DefaultChannelOverridesRepository @Inject constructor(
    private val overrideDao: OverrideDao,
    private val channelDao: ChannelDao,
) : ChannelOverridesRepository {
    override fun channelOverrides(playlistId: Long): Flow<List<ChannelOverride>> =
        overrideDao.observeChannelOverrides(playlistId).map { rows -> rows.map { it.toModel() } }

    override fun groupOverrides(playlistId: Long): Flow<List<GroupOverride>> =
        overrideDao.observeGroupOverrides(playlistId).map { rows -> rows.map { it.toModel() } }

    override fun editorGroups(playlistId: Long): Flow<List<EditorGroup>> =
        channelDao.observeEditorGroups(playlistId).map { rows -> rows.map { it.toModel(playlistId) } }

    override fun editorChannels(playlistId: Long, groupName: String): Flow<List<EditorChannel>> =
        channelDao.observeEditorChannels(playlistId, groupName).map { rows -> rows.map { it.toModel(playlistId) } }

    override suspend fun setChannelHidden(key: ChannelKey, hidden: Boolean) =
        overrideDao.updateChannel(key.playlistId, key.uniqueKey) { it.copy(hidden = hidden) }

    override suspend fun renameChannel(key: ChannelKey, name: String?) =
        overrideDao.updateChannel(key.playlistId, key.uniqueKey) { it.copy(customName = name.normalizedName()) }

    override suspend fun reorderChannels(playlistId: Long, orderedKeys: List<String>) {
        val keys = orderedKeys.distinct()
        if (keys.isEmpty()) return
        val positions = permutedPositions(keys, channelDao.channelPositions(playlistId, keys))
        overrideDao.updateChannelPositions(playlistId, positions)
    }

    override suspend fun resetChannelOrder(playlistId: Long) {
        val ordered = overrideDao.observeChannelOverrides(playlistId).first().filter { it.customSortOrder != null }
        ordered.forEach { row ->
            overrideDao.updateChannel(playlistId, row.uniqueKey) { it.copy(customSortOrder = null) }
        }
    }

    override suspend fun setGroupHidden(playlistId: Long, groupName: String, hidden: Boolean) =
        overrideDao.updateGroup(playlistId, groupName) { it.copy(hidden = hidden) }

    override suspend fun renameGroup(playlistId: Long, groupName: String, name: String?) =
        overrideDao.updateGroup(playlistId, groupName) { it.copy(customName = name.normalizedName()) }

    override suspend fun reorderGroups(playlistId: Long, orderedGroupNames: List<String>) {
        val names = orderedGroupNames.distinct()
        if (names.isEmpty()) return
        val positions = permutedPositions(names, channelDao.groupPositions(playlistId, names))
        overrideDao.updateGroupPositions(playlistId, positions)
    }

    private fun String?.normalizedName(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}

/**
 * Reordering inside the slots the items occupy now: the current positions of the items present in
 * [current] are sorted and handed out in the order of [ordered], so everything not listed keeps its
 * place. Items missing from [current] (not in the latest refresh) are skipped.
 */
internal fun permutedPositions(ordered: List<String>, current: List<KeyPositionRow>): Map<String, Int> {
    val present = current.associate { it.uniqueKey to it.position }
    val slots = ordered.mapNotNull { present[it] }.sorted()
    return ordered.filter { it in present }.zip(slots).toMap()
}

private fun ChannelOverrideEntity.toModel() = ChannelOverride(
    key = ChannelKey(playlistId, uniqueKey),
    hidden = hidden,
    customName = customName,
    customSortOrder = customSortOrder,
)

private fun GroupOverrideEntity.toModel() = GroupOverride(
    playlistId = playlistId,
    groupName = groupName,
    hidden = hidden,
    customName = customName,
    customSortOrder = customSortOrder,
)

private fun EditorGroupRow.toModel(playlistId: Long) = EditorGroup(
    playlistId = playlistId,
    name = name,
    customName = customName,
    hidden = hidden,
    channelCount = channelCount,
)

private fun EditorChannelRow.toModel(playlistId: Long) = EditorChannel(
    key = ChannelKey(playlistId, uniqueKey),
    name = name,
    customName = customName,
    logoUrl = logoUrl,
    hidden = hidden,
    selfHidden = selfHidden,
    displayNumber = number ?: (sortOrder + 1),
)
