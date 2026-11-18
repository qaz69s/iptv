package com.streambox.player.core.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.streambox.player.core.database.dao.ChannelDao
import com.streambox.player.core.database.entity.ChannelEntity
import com.streambox.player.core.domain.repository.ChannelRepository
import com.streambox.player.core.model.Channel
import com.streambox.player.core.model.ChannelGroupSummary
import com.streambox.player.core.model.PlaybackItem
import com.streambox.player.core.model.ZapDirection
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class DefaultChannelRepository @Inject constructor(private val channelDao: ChannelDao) : ChannelRepository {
    override val groups: Flow<List<ChannelGroupSummary>> = channelDao.observeGroups().map { rows ->
        rows.map { ChannelGroupSummary(it.name, it.channelCount) }
    }

    override val channelCount: Flow<Int> = channelDao.observeChannelCount()

    override fun pagedChannels(groupName: String?): Flow<PagingData<Channel>> = Pager(
        config = PagingConfig(pageSize = PAGE_SIZE, prefetchDistance = PAGE_SIZE, enablePlaceholders = false),
        pagingSourceFactory = { channelDao.pagingSource(groupName) },
    ).flow.map { data -> data.map { it.toModel() } }

    override suspend fun playbackItem(channelId: Long): PlaybackItem? = channelDao.playbackRow(channelId)?.let {
        PlaybackItem(
            channelId = it.id,
            name = it.name,
            logoUrl = it.logoUrl,
            streamUrl = it.streamUrl,
            userAgent = it.userAgent,
            referrer = it.referrer,
            allowCleartext = it.allowCleartext,
            displayNumber = it.number ?: (it.sortOrder + 1),
            playlistId = it.playlistId,
            groupName = it.groupName,
        )
    }

    override suspend fun adjacentChannel(channelId: Long, groupName: String?, direction: ZapDirection): Long? {
        val position = channelDao.position(channelId) ?: return channelDao.first(groupName)
        return when (direction) {
            ZapDirection.NEXT ->
                channelDao.nextAfter(groupName, position.playlistSort, position.playlistId, position.channelSort)
                    ?: channelDao.first(groupName)

            ZapDirection.PREVIOUS ->
                channelDao.previousBefore(groupName, position.playlistSort, position.playlistId, position.channelSort)
                    ?: channelDao.last(groupName)
        }
    }

    override suspend fun nextChannel(channelId: Long, groupName: String?): Long? {
        val position = channelDao.position(channelId) ?: return null
        return channelDao.nextAfter(groupName, position.playlistSort, position.playlistId, position.channelSort)
    }

    override suspend fun channelIdByNumber(number: Int, groupName: String?): Long? =
        groupName?.let { channelDao.idByNumber(number, it) } ?: channelDao.idByNumber(number, null)

    private companion object {
        const val PAGE_SIZE = 60
    }
}

internal fun ChannelEntity.toModel() = Channel(
    id = id,
    playlistId = playlistId,
    groupId = groupId,
    uniqueKey = uniqueKey,
    name = name,
    logoUrl = logoUrl,
    streamUrl = streamUrl,
    tvgId = tvgId,
    tvgName = tvgName,
    number = number,
    position = sortOrder + 1,
    catchupType = catchupType,
    catchupSource = catchupSource,
    catchupDays = catchupDays,
)
