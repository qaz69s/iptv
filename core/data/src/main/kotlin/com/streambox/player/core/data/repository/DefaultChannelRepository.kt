package com.streambox.player.core.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.streambox.player.core.database.dao.ChannelDao
import com.streambox.player.core.database.dao.CustomGroupDao
import com.streambox.player.core.database.dao.FavoriteDao
import com.streambox.player.core.database.dao.SortCode
import com.streambox.player.core.database.dao.WatchHistoryDao
import com.streambox.player.core.database.entity.ChannelEntity
import com.streambox.player.core.domain.channel.ChannelSequence
import com.streambox.player.core.domain.channel.ChannelSorting
import com.streambox.player.core.domain.repository.ChannelRepository
import com.streambox.player.core.domain.repository.WatchHistoryRepository
import com.streambox.player.core.model.Channel
import com.streambox.player.core.model.ChannelGroupSummary
import com.streambox.player.core.model.ChannelKey
import com.streambox.player.core.model.ChannelSortOrder
import com.streambox.player.core.model.ChannelSource
import com.streambox.player.core.model.PlaybackItem
import com.streambox.player.core.model.SearchResults
import com.streambox.player.core.model.ZapDirection
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Zapping works on the ordered id list of the source (the same order the browser pages through), so
 * every sort order and the virtual groups share one rule set ([ChannelSequence]). For all channels / a
 * group the list comes from one indexed-join query of ids; favorites and history are small lists.
 */
internal class DefaultChannelRepository @Inject constructor(
    private val channelDao: ChannelDao,
    private val favoriteDao: FavoriteDao,
    private val watchHistoryDao: WatchHistoryDao,
    private val customGroupDao: CustomGroupDao,
) : ChannelRepository {
    override val groups: Flow<List<ChannelGroupSummary>> = channelDao.observeGroups().map { rows ->
        rows.map { ChannelGroupSummary(it.name, it.channelCount) }
    }

    override val channelCount: Flow<Int> = channelDao.observeChannelCount()

    override fun pagedChannels(groupName: String?, sort: ChannelSortOrder): Flow<PagingData<Channel>> = Pager(
        config = PagingConfig(pageSize = PAGE_SIZE, prefetchDistance = PAGE_SIZE, enablePlaceholders = false),
        pagingSourceFactory = { channelDao.pagingSource(groupName, sort.code) },
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
            uniqueKey = it.uniqueKey,
        )
    }

    override suspend fun adjacentChannel(
        channelId: Long,
        source: ChannelSource,
        sort: ChannelSortOrder,
        direction: ZapDirection,
    ): Long? = ChannelSequence.adjacent(orderedIds(source, sort), channelId, direction)

    override suspend fun nextChannel(channelId: Long, source: ChannelSource, sort: ChannelSortOrder): Long? =
        ChannelSequence.next(orderedIds(source, sort), channelId)

    override suspend fun channelIdByNumber(number: Int, source: ChannelSource, sort: ChannelSortOrder): Long? =
        when (source) {
            ChannelSource.All -> channelDao.idByNumber(number, null)

            is ChannelSource.Group ->
                channelDao.idByNumber(number, source.name) ?: channelDao.idByNumber(number, null)

            ChannelSource.Favorites, ChannelSource.Recent, is ChannelSource.Custom ->
                ChannelSequence.atNumber(orderedIds(source, sort), number)
        }

    override suspend fun channelIdByKey(key: ChannelKey): Long? = channelDao.idByKey(key.playlistId, key.uniqueKey)

    override suspend fun search(query: String, limit: Int): SearchResults {
        val parsed = SearchQuery.parse(query) ?: return SearchResults()
        val channels = channelDao.search(
            // A match that cannot hit anything keeps the statement valid when there is no word to look for.
            match = parsed.ftsMatch ?: NO_MATCH,
            like = parsed.likePattern,
            substring = if (parsed.substring) 1 else 0,
            limit = limit,
        )
        val groups = channelDao.searchGroups(parsed.likePattern, GROUP_SEARCH_LIMIT)
        return SearchResults(
            groups = groups.map { ChannelGroupSummary(it.name, it.channelCount) },
            channels = channels.map { it.toModel() },
        )
    }

    /** The source's channels as the browser lists them. Recently watched is always newest first. */
    private suspend fun orderedIds(source: ChannelSource, sort: ChannelSortOrder): List<Long> = when (source) {
        ChannelSource.All -> channelDao.orderedIds(null, sort.code)

        is ChannelSource.Group -> channelDao.orderedIds(source.name, sort.code)

        ChannelSource.Favorites -> ChannelSorting.sort(
            favoriteDao.observeFavoriteChannels().first().map { it.toModel() },
            sort,
        ).map { it.id }

        ChannelSource.Recent -> watchHistoryDao.observe(WatchHistoryRepository.MAX_ENTRIES).first().map {
            it.channel.id
        }

        is ChannelSource.Custom -> ChannelSorting.sort(
            customGroupDao.observeChannels(source.groupId).first().map { it.toModel() },
            sort,
        ).map { it.id }
    }

    private companion object {
        const val PAGE_SIZE = 60
        const val GROUP_SEARCH_LIMIT = 50

        /** Valid FTS expression for text without words (only punctuation): one exact term no name contains. */
        const val NO_MATCH = "\"sbnomatch7f3e9a1c0b5d4e2f\""
    }
}

internal val ChannelSortOrder.code: Int
    get() = when (this) {
        ChannelSortOrder.Default -> SortCode.DEFAULT
        ChannelSortOrder.Name -> SortCode.NAME
        ChannelSortOrder.Number -> SortCode.NUMBER
        ChannelSortOrder.Custom -> SortCode.CUSTOM
    }

internal fun ChannelEntity.toModel() = Channel(
    id = id,
    playlistId = playlistId,
    groupId = groupId,
    uniqueKey = uniqueKey,
    name = customName ?: name,
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
