package com.streambox.player.core.testing

import androidx.paging.PagingData
import com.streambox.player.core.domain.channel.ChannelSequence
import com.streambox.player.core.domain.channel.ChannelSorting
import com.streambox.player.core.domain.repository.ChannelRepository
import com.streambox.player.core.model.Channel
import com.streambox.player.core.model.ChannelGroupSummary
import com.streambox.player.core.model.ChannelKey
import com.streambox.player.core.model.ChannelSortOrder
import com.streambox.player.core.model.ChannelSource
import com.streambox.player.core.model.PlaybackItem
import com.streambox.player.core.model.SearchResults
import com.streambox.player.core.model.ZapDirection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * In-memory [ChannelRepository] with simple list semantics: filter by group name, sort with
 * [ChannelSorting] (Default and Custom keep the list order), zapping with [ChannelSequence].
 * Favorites / recently watched / custom groups are the channel ids in [favoriteIds] / [recentIds] /
 * [customGroupIds], in their list order.
 */
class FakeChannelRepository(channels: List<Pair<String, Channel>> = emptyList()) : ChannelRepository {
    /** (groupName, channel) in playlist order. */
    val channelsFlow = MutableStateFlow(channels)
    val favoriteIds = MutableStateFlow<List<Long>>(emptyList())
    val recentIds = MutableStateFlow<List<Long>>(emptyList())

    /** Channel ids of each custom group, in the group's order. */
    val customGroupIds = MutableStateFlow<Map<Long, List<Long>>>(emptyMap())
    val requestedGroups = mutableListOf<String?>()
    val requestedSorts = mutableListOf<ChannelSortOrder>()
    val searches = mutableListOf<String>()

    override val groups: Flow<List<ChannelGroupSummary>> = channelsFlow.map { list ->
        list.groupBy({ it.first }, { it.second }).map { (name, items) -> ChannelGroupSummary(name, items.size) }
    }

    override val channelCount: Flow<Int> = channelsFlow.map { it.size }

    override fun pagedChannels(groupName: String?, sort: ChannelSortOrder): Flow<PagingData<Channel>> {
        requestedGroups += groupName
        requestedSorts += sort
        return flowOf(PagingData.from(ChannelSorting.sort(filtered(groupName), sort)))
    }

    override suspend fun playbackItem(channelId: Long): PlaybackItem? =
        channelsFlow.value.firstOrNull { it.second.id == channelId }?.let { (group, it) ->
            PlaybackItem(
                channelId = it.id,
                name = it.name,
                logoUrl = it.logoUrl,
                streamUrl = it.streamUrl,
                userAgent = null,
                referrer = null,
                allowCleartext = true,
                displayNumber = it.displayNumber,
                playlistId = it.playlistId,
                groupName = group,
                uniqueKey = it.uniqueKey,
            )
        }

    override suspend fun adjacentChannel(
        channelId: Long,
        source: ChannelSource,
        sort: ChannelSortOrder,
        direction: ZapDirection,
    ): Long? = ChannelSequence.adjacent(ordered(source, sort).map { it.id }, channelId, direction)

    override suspend fun nextChannel(channelId: Long, source: ChannelSource, sort: ChannelSortOrder): Long? =
        ChannelSequence.next(ordered(source, sort).map { it.id }, channelId)

    override suspend fun channelIdByNumber(number: Int, source: ChannelSource, sort: ChannelSortOrder): Long? =
        when (source) {
            ChannelSource.Favorites, ChannelSource.Recent, is ChannelSource.Custom ->
                ChannelSequence.atNumber(ordered(source, sort), number)?.id

            else -> {
                val group = (source as? ChannelSource.Group)?.name
                val inGroup = filtered(group).firstOrNull { it.displayNumber == number }
                (inGroup ?: filtered(null).firstOrNull { it.displayNumber == number })?.id
            }
        }

    override suspend fun channelIdByKey(key: ChannelKey): Long? =
        channelsFlow.value.firstOrNull { it.second.key == key }?.second?.id

    override suspend fun search(query: String, limit: Int): SearchResults {
        searches += query
        val text = query.trim()
        if (text.isEmpty()) return SearchResults()
        val groups = channelsFlow.value
            .groupBy({ it.first }, { it.second })
            .filterKeys { it.contains(text, ignoreCase = true) }
            .map { (name, items) -> ChannelGroupSummary(name, items.size) }
        val channels = filtered(null).filter { it.name.contains(text, ignoreCase = true) }.take(limit)
        return SearchResults(groups, channels)
    }

    /** The source's channels in browse order (recently watched always newest first). */
    fun ordered(source: ChannelSource, sort: ChannelSortOrder): List<Channel> = when (source) {
        ChannelSource.All -> ChannelSorting.sort(filtered(null), sort)
        is ChannelSource.Group -> ChannelSorting.sort(filtered(source.name), sort)
        ChannelSource.Favorites -> ChannelSorting.sort(byIds(favoriteIds.value), sort)
        ChannelSource.Recent -> byIds(recentIds.value)
        is ChannelSource.Custom -> ChannelSorting.sort(byIds(customGroupIds.value[source.groupId].orEmpty()), sort)
    }

    private fun byIds(ids: List<Long>): List<Channel> {
        val all = filtered(null).associateBy { it.id }
        return ids.mapNotNull { all[it] }
    }

    private fun filtered(groupName: String?) =
        channelsFlow.value.filter { groupName == null || it.first == groupName }.map { it.second }

    companion object {
        fun channel(id: Long, name: String = "Channel $id", url: String = "https://s/$id.m3u8") = Channel(
            id = id,
            playlistId = 1,
            groupId = null,
            uniqueKey = "url:$id",
            name = name,
            logoUrl = null,
            streamUrl = url,
            position = id.toInt(),
        )
    }
}
