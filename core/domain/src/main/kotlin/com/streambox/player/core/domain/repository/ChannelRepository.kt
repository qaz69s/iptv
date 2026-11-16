package com.streambox.player.core.domain.repository

import androidx.paging.PagingData
import com.streambox.player.core.model.Channel
import com.streambox.player.core.model.ChannelGroupSummary
import com.streambox.player.core.model.PlaybackItem
import com.streambox.player.core.model.ZapDirection
import kotlinx.coroutines.flow.Flow

interface ChannelRepository {
    /** Groups of all enabled playlists, merged by name, in playlist order. */
    val groups: Flow<List<ChannelGroupSummary>>

    /** Total visible channels across enabled playlists. */
    val channelCount: Flow<Int>

    /** Paged channels; [groupName] null = all groups. */
    fun pagedChannels(groupName: String?): Flow<PagingData<Channel>>

    suspend fun playbackItem(channelId: Long): PlaybackItem?

    /** Next / previous channel within the same filter, wrapping around at the ends. */
    suspend fun adjacentChannel(channelId: Long, groupName: String?, direction: ZapDirection): Long?
}
