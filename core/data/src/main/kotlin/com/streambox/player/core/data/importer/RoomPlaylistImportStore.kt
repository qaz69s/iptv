package com.streambox.player.core.data.importer

import androidx.room.withTransaction
import com.streambox.player.core.database.StreamBoxDatabase
import com.streambox.player.core.database.entity.ChannelEntity
import com.streambox.player.core.database.entity.ChannelGroupEntity
import com.streambox.player.core.model.PlaylistType
import javax.inject.Inject

internal class RoomPlaylistImportStore @Inject constructor(private val database: StreamBoxDatabase) :
    PlaylistImportStore {
    private val playlistDao = database.playlistDao()
    private val channelDao = database.channelDao()
    private val overrideDao = database.overrideDao()

    override suspend fun source(playlistId: Long): PlaylistSource? = playlistDao.get(playlistId)?.let {
        PlaylistSource(
            playlistId = it.id,
            type = PlaylistType.valueOf(it.type),
            location = it.url,
            userAgent = it.userAgent,
            allowCleartext = it.allowCleartext,
        )
    }

    override suspend fun beginImport(playlistId: Long): Int = database.withTransaction {
        val active = playlistDao.get(playlistId)?.activeGeneration ?: 0
        channelDao.deleteChannelsExcept(playlistId, active)
        channelDao.deleteGroupsExcept(playlistId, active)
        active + 1
    }

    override suspend fun writeBatch(
        playlistId: Long,
        generation: Int,
        newGroups: List<NewGroup>,
        channels: List<NewChannel>,
        knownGroupIds: Map<String, Long>,
    ): Map<String, Long> = database.withTransaction {
        val ids = if (newGroups.isEmpty()) {
            emptyList()
        } else {
            channelDao.insertGroups(
                newGroups.map {
                    ChannelGroupEntity(
                        playlistId = playlistId,
                        name = it.name,
                        sortOrder = it.sortOrder,
                        generation = generation,
                    )
                },
            )
        }
        val inserted = newGroups.zip(ids) { group, id -> group.name to id }.toMap()
        if (channels.isNotEmpty()) {
            channelDao.insertChannels(
                channels.map { channel ->
                    channel.toEntity(
                        playlistId,
                        generation,
                        inserted[channel.groupName] ?: knownGroupIds[channel.groupName],
                    )
                },
            )
        }
        inserted
    }

    override suspend fun verifyAndCommit(
        playlistId: Long,
        generation: Int,
        expectedChannels: Int,
        epgUrl: String?,
        updatedAt: Long,
    ): Int = database.withTransaction {
        val written = channelDao.countChannels(playlistId, generation)
        if (written != expectedChannels) {
            throw ImportValidationException("expected $expectedChannels channels, found $written")
        }
        // The user's hide / rename / order edits outlive the refresh: apply them before the swap.
        overrideDao.applyToGeneration(playlistId, generation)
        channelDao.updateGroupCounts(playlistId, generation)
        val groups = channelDao.countGroups(playlistId, generation)
        val updated = playlistDao.activateGeneration(playlistId, generation, written, groups, epgUrl, updatedAt)
        if (updated != 1) throw ImportValidationException("playlist $playlistId was removed during import")
        groups
    }

    override suspend fun deleteInactive(playlistId: Long) {
        database.withTransaction {
            val active = playlistDao.get(playlistId)?.activeGeneration ?: return@withTransaction
            channelDao.deleteChannelsExcept(playlistId, active)
            channelDao.deleteGroupsExcept(playlistId, active)
        }
    }

    override suspend fun discard(playlistId: Long, generation: Int) {
        database.withTransaction {
            channelDao.deleteChannelsOf(playlistId, generation)
            channelDao.deleteGroupsOf(playlistId, generation)
        }
    }

    override suspend fun recordFailure(playlistId: Long, message: String) =
        playlistDao.setLastError(playlistId, message)

    private fun NewChannel.toEntity(playlistId: Long, generation: Int, groupId: Long?) = ChannelEntity(
        playlistId = playlistId,
        groupId = groupId,
        uniqueKey = uniqueKey,
        name = name,
        logoUrl = logoUrl,
        streamUrl = streamUrl,
        tvgId = tvgId,
        tvgName = tvgName,
        number = number,
        catchupType = catchupType,
        catchupSource = catchupSource,
        catchupDays = catchupDays,
        timeshift = timeshift,
        userAgent = userAgent,
        referrer = referrer,
        sortOrder = sortOrder,
        generation = generation,
    )
}
