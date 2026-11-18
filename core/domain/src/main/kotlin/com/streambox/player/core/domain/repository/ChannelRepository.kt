package com.streambox.player.core.domain.repository

import androidx.paging.PagingData
import com.streambox.player.core.model.Channel
import com.streambox.player.core.model.ChannelGroupSummary
import com.streambox.player.core.model.ChannelKey
import com.streambox.player.core.model.ChannelSortOrder
import com.streambox.player.core.model.ChannelSource
import com.streambox.player.core.model.PlaybackItem
import com.streambox.player.core.model.SearchResults
import com.streambox.player.core.model.ZapDirection
import kotlinx.coroutines.flow.Flow

/**
 * Channels of all enabled playlists. Zapping follows the list the user picked the channel from
 * ([ChannelSource]) in the order the browser shows it ([ChannelSortOrder]); P3b user decision: inside the
 * virtual groups "favorites" / "recently watched" too (wrapping), and a channel number typed there is
 * the 1-based position in that list.
 */
interface ChannelRepository {
    /** Groups of all enabled playlists, merged by name, in playlist order. */
    val groups: Flow<List<ChannelGroupSummary>>

    /** Total visible channels across enabled playlists. */
    val channelCount: Flow<Int>

    /** Paged channels; [groupName] null = all groups. */
    fun pagedChannels(groupName: String?, sort: ChannelSortOrder = ChannelSortOrder.Default): Flow<PagingData<Channel>>

    suspend fun playbackItem(channelId: Long): PlaybackItem?

    /** Next / previous channel of [source] in [sort] order, wrapping around at the ends. */
    suspend fun adjacentChannel(
        channelId: Long,
        source: ChannelSource,
        sort: ChannelSortOrder,
        direction: ZapDirection,
    ): Long?

    /** Next channel of [source] in [sort] order without wrapping; null after the last one. */
    suspend fun nextChannel(channelId: Long, source: ChannelSource, sort: ChannelSortOrder): Long?

    /**
     * All channels / a group: the channel whose display number (tvg-chno, else playlist position) is
     * [number]; a match inside the group wins, otherwise the first match across all enabled playlists.
     * Favorites / recently watched: the [number]-th channel of that list in [sort] order. Null when none.
     */
    suspend fun channelIdByNumber(number: Int, source: ChannelSource, sort: ChannelSortOrder): Long?

    /**
     * Current id of the channel with this stable key (playlist + uniqueKey) in an enabled playlist;
     * null when the latest refresh no longer has it. Channel ids change on every refresh, keys do not.
     */
    suspend fun channelIdByKey(key: ChannelKey): Long?

    /**
     * Global search (requirements §4.2): groups whose name contains [query]; channels whose name or
     * tvg-name matches through the full-text index (word prefixes), whose user-given name contains it,
     * and, for queries with non-Latin characters (no word breaks in CJK names), whose name contains it.
     * Blank queries return nothing. At most [limit] channels.
     */
    suspend fun search(query: String, limit: Int = SEARCH_LIMIT): SearchResults

    companion object {
        const val SEARCH_LIMIT = 200
    }
}
