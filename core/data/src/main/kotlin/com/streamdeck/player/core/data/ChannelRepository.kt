package com.streamdeck.player.core.data

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import com.streamdeck.player.core.database.ChannelDao
import com.streamdeck.player.core.database.ChannelEntity
import com.streamdeck.player.core.database.ChannelListItem
import com.streamdeck.player.core.datastore.ChannelSort
import com.streamdeck.player.core.database.GroupCount
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Virtual and real groups shown in the browser (spec §4.2). */
sealed interface GroupFilter {
    data object All : GroupFilter
    data object Favorites : GroupFilter
    data object Recent : GroupFilter
    data class Group(val id: Long) : GroupFilter
}

@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ChannelRepository @Inject constructor(private val dao: ChannelDao, private val epgClock: EpgClock) {
    fun groups(pid: Long) = dao.observeGroups(pid)

    fun groupCounts(pid: Long): Flow<List<GroupCount>> = dao.observeGroupCounts(pid)
    fun favoriteCount(pid: Long): Flow<Int> = dao.observeFavoriteCount(pid)
    fun visibleCount(pid: Long): Flow<Int> = dao.observeVisibleCount(pid)

    /** “继续观看” rail: most recent channels with now/next, re-queried every minute so the airing programme stays current. */
    fun recent(pid: Long, limit: Int = RAIL_LIMIT): Flow<List<ChannelListItem>> =
        ticker().flatMapLatest { dao.observeRecent(pid, epgClock.sourceNow(), limit) }.map { l -> l.map { it.toReal() } }

    fun favorites(pid: Long, limit: Int = RAIL_LIMIT): Flow<List<ChannelListItem>> =
        ticker().flatMapLatest { dao.observeFavorites(pid, epgClock.sourceNow(), limit) }.map { l -> l.map { it.toReal() } }

    /** Channels of [groupId] with now/next (TV zapping list, phone “同组频道”). */
    fun groupItems(pid: Long, groupId: Long, limit: Int = GROUP_LIMIT): Flow<List<ChannelListItem>> =
        ticker().flatMapLatest { dao.observeGroupItems(pid, groupId, epgClock.sourceNow(), limit) }.map { l -> l.map { it.toReal() } }

    fun channels(pid: Long, filter: GroupFilter, sort: ChannelSort): Flow<PagingData<ChannelListItem>> = pager {
        when (filter) {
            GroupFilter.All -> dao.pageChannels(pid, null, sort.name, epgClock.sourceNow())
            is GroupFilter.Group -> dao.pageChannels(pid, filter.id, sort.name, epgClock.sourceNow())
            GroupFilter.Favorites -> dao.pageFavorites(pid, epgClock.sourceNow())
            GroupFilter.Recent -> dao.pageRecent(pid, epgClock.sourceNow())
        }
    }

    /** Favorites across every playlist (bottom navigation tab). */
    fun allFavorites(): Flow<PagingData<ChannelListItem>> = pager { dao.pageFavorites(null, epgClock.sourceNow()) }

    fun search(pid: Long, query: String, sort: ChannelSort): Flow<PagingData<ChannelListItem>> =
        pager { dao.search(pid, FtsQuery.of(query), sort.name, epgClock.sourceNow()) }

    suspend fun get(id: Long) = dao.get(id)
    suspend fun group(id: Long) = dao.group(id)
    /** Zapping skips locked channels unless the parental PIN was entered recently (spec §4.7). */
    suspend fun next(c: ChannelEntity, includeLocked: Boolean = false) = dao.next(c.playlistId, c.sortOrder, includeLocked)
    suspend fun previous(c: ChannelEntity, includeLocked: Boolean = false) = dao.previous(c.playlistId, c.sortOrder, includeLocked)
    suspend fun byNumber(pid: Long, number: Int, includeLocked: Boolean = false) = dao.byNumber(pid, number, includeLocked)
    suspend fun isLocked(c: ChannelEntity): Boolean = c.locked || dao.isGroupLocked(c.groupId) == true
    suspend fun byKey(pid: Long, key: String) = dao.byKey(pid, key)

    private fun pager(source: () -> androidx.paging.PagingSource<Int, ChannelListItem>): Flow<PagingData<ChannelListItem>> =
        Pager(PagingConfig(pageSize = PAGE, prefetchDistance = PAGE, enablePlaceholders = true), pagingSourceFactory = source).flow
            .map { data -> data.map { it.toReal() } }

    /** Programme times come out of SQL in EPG source time; the UI works in real time (spec §4.5). */
    private fun ChannelListItem.toReal(): ChannelListItem {
        val off = epgClock.offsetMs
        if (off == 0L) return this
        return copy(nowStart = nowStart?.plus(off), nowEnd = nowEnd?.plus(off), nextStart = nextStart?.plus(off))
    }

    private fun ticker(): Flow<Long> = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(TICK_MS)
        }
    }

    private companion object {
        const val PAGE = 60
        const val RAIL_LIMIT = 20
        const val GROUP_LIMIT = 500
        const val TICK_MS = 60_000L
    }
}

/** Converts free text into a safe FTS4 prefix query: `cc tv` -> `"cc"* "tv"*`. */
object FtsQuery {
    fun of(query: String): String = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        .joinToString(" ") { "\"" + it.replace("\"", "") + "\"*" }
}
