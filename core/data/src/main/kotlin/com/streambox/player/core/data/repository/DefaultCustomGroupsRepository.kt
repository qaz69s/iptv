package com.streambox.player.core.data.repository

import com.streambox.player.core.database.dao.ChannelKeyRow
import com.streambox.player.core.database.dao.CustomGroupDao
import com.streambox.player.core.domain.repository.CustomGroupsRepository
import com.streambox.player.core.model.Channel
import com.streambox.player.core.model.ChannelKey
import com.streambox.player.core.model.CustomGroup
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal class DefaultCustomGroupsRepository @Inject constructor(
    private val dao: CustomGroupDao,
) : CustomGroupsRepository {
    override val groups: Flow<List<CustomGroup>> = dao.observeGroups().map { rows ->
        rows.map { CustomGroup(it.id, it.name, it.channelCount) }
    }

    override fun channels(groupId: Long): Flow<List<Channel>> =
        dao.observeChannels(groupId).map { rows -> rows.map { it.toModel() } }

    override fun groupsOf(key: ChannelKey): Flow<Set<Long>> =
        dao.observeGroupsOf(key.playlistId, key.uniqueKey).map { it.toSet() }

    override suspend fun create(name: String): Long? {
        val normalized = name.normalizedGroupName() ?: return null
        val existing = dao.observeGroups().first().firstOrNull { it.name == normalized }
        return existing?.id ?: dao.createGroup(normalized, System.currentTimeMillis())
    }

    override suspend fun rename(groupId: Long, name: String): Boolean {
        val normalized = name.normalizedGroupName() ?: return false
        dao.renameGroup(groupId, normalized)
        return true
    }

    override suspend fun delete(groupId: Long) = dao.deleteGroup(groupId)

    override suspend fun reorderGroups(orderedIds: List<Long>) = dao.reorderGroups(orderedIds)

    override suspend fun addChannel(groupId: Long, key: ChannelKey) =
        dao.addMember(groupId, key.playlistId, key.uniqueKey, System.currentTimeMillis())

    override suspend fun removeChannel(groupId: Long, key: ChannelKey) =
        dao.deleteMember(groupId, key.playlistId, key.uniqueKey)

    override suspend fun reorderChannels(groupId: Long, orderedKeys: List<ChannelKey>) =
        dao.reorderMembers(groupId, orderedKeys.map { ChannelKeyRow(it.playlistId, it.uniqueKey) })
}

/** Trimmed name; null when blank. */
internal fun String.normalizedGroupName(): String? = trim().takeIf { it.isNotEmpty() }
