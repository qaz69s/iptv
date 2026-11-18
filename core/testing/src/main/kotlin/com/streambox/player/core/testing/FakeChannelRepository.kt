package com.streambox.player.core.testing

import androidx.paging.PagingData
import com.streambox.player.core.domain.repository.ChannelRepository
import com.streambox.player.core.model.Channel
import com.streambox.player.core.model.ChannelGroupSummary
import com.streambox.player.core.model.ChannelKey
import com.streambox.player.core.model.PlaybackItem
import com.streambox.player.core.model.ZapDirection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** In-memory [ChannelRepository] with simple list semantics (filter by group name, wrap-around zapping). */
class FakeChannelRepository(channels: List<Pair<String, Channel>> = emptyList()) : ChannelRepository {
    /** (groupName, channel) in playlist order. */
    val channelsFlow = MutableStateFlow(channels)
    val requestedGroups = mutableListOf<String?>()

    override val groups: Flow<List<ChannelGroupSummary>> = channelsFlow.map { list ->
        list.groupBy({ it.first }, { it.second }).map { (name, items) -> ChannelGroupSummary(name, items.size) }
    }

    override val channelCount: Flow<Int> = channelsFlow.map { it.size }

    override fun pagedChannels(groupName: String?): Flow<PagingData<Channel>> {
        requestedGroups += groupName
        return flowOf(PagingData.from(filtered(groupName)))
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

    override suspend fun adjacentChannel(channelId: Long, groupName: String?, direction: ZapDirection): Long? {
        val list = filtered(groupName)
        if (list.isEmpty()) return null
        val index = list.indexOfFirst { it.id == channelId }
        val next = when (direction) {
            ZapDirection.NEXT -> if (index < 0) 0 else (index + 1) % list.size
            ZapDirection.PREVIOUS -> if (index < 0) list.lastIndex else (index - 1 + list.size) % list.size
        }
        return list[next].id
    }

    override suspend fun nextChannel(channelId: Long, groupName: String?): Long? {
        val list = filtered(groupName)
        val index = list.indexOfFirst { it.id == channelId }
        return if (index < 0) null else list.getOrNull(index + 1)?.id
    }

    override suspend fun channelIdByNumber(number: Int, groupName: String?): Long? {
        val inGroup = filtered(groupName).firstOrNull { it.displayNumber == number }
        return (inGroup ?: filtered(null).firstOrNull { it.displayNumber == number })?.id
    }

    override suspend fun channelIdByKey(key: ChannelKey): Long? =
        channelsFlow.value.firstOrNull { it.second.key == key }?.second?.id

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
