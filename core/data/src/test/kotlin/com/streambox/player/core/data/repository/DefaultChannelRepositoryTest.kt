package com.streambox.player.core.data.repository

import com.streambox.player.core.database.dao.ChannelDao
import com.streambox.player.core.database.dao.CustomGroupDao
import com.streambox.player.core.database.dao.FavoriteDao
import com.streambox.player.core.database.dao.GroupSummaryRow
import com.streambox.player.core.database.dao.SortCode
import com.streambox.player.core.database.dao.WatchHistoryDao
import com.streambox.player.core.database.dao.WatchHistoryRow
import com.streambox.player.core.database.entity.ChannelEntity
import com.streambox.player.core.model.ChannelGroupSummary
import com.streambox.player.core.model.ChannelSortOrder
import com.streambox.player.core.model.ChannelSource
import com.streambox.player.core.model.SearchResults
import com.streambox.player.core.model.ZapDirection
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Zapping / channel numbers for each source (P3b) and how search reaches the DAO. */
class DefaultChannelRepositoryTest {
    private val channelDao = mockk<ChannelDao>(relaxed = true)
    private val favoriteDao = mockk<FavoriteDao>(relaxed = true)
    private val historyDao = mockk<WatchHistoryDao>(relaxed = true)
    private val customGroupDao = mockk<CustomGroupDao>(relaxed = true)
    private val repository = DefaultChannelRepository(channelDao, favoriteDao, historyDao, customGroupDao)

    private fun entity(id: Long, name: String, number: Int? = null, sortOrder: Int = id.toInt()) = ChannelEntity(
        id = id,
        playlistId = 1,
        groupId = null,
        uniqueKey = "k$id",
        name = name,
        logoUrl = null,
        streamUrl = "https://s/$id",
        tvgId = null,
        tvgName = null,
        number = number,
        catchupType = null,
        catchupSource = null,
        catchupDays = null,
        timeshift = null,
        userAgent = null,
        referrer = null,
        sortOrder = sortOrder,
        generation = 1,
    )

    @Test
    fun `a group zaps through the dao's ordered ids for the chosen sort, wrapping`() = runTest {
        coEvery { channelDao.orderedIds("News", SortCode.NAME) } returns listOf(3L, 1L, 2L)

        val source = ChannelSource.Group("News")
        assertEquals(1L, repository.adjacentChannel(3, source, ChannelSortOrder.Name, ZapDirection.NEXT))
        assertEquals(3L, repository.adjacentChannel(2, source, ChannelSortOrder.Name, ZapDirection.NEXT))
        assertEquals(2L, repository.adjacentChannel(3, source, ChannelSortOrder.Name, ZapDirection.PREVIOUS))
        assertNull(repository.nextChannel(2, source, ChannelSortOrder.Name))
    }

    @Test
    fun `all channels use the ids of every group`() = runTest {
        coEvery { channelDao.orderedIds(null, SortCode.NUMBER) } returns listOf(5L, 6L)

        assertEquals(6L, repository.adjacentChannel(5, ChannelSource.All, ChannelSortOrder.Number, ZapDirection.NEXT))
        assertEquals(6L, repository.nextChannel(5, ChannelSource.All, ChannelSortOrder.Number))
    }

    @Test
    fun `favorites zap in the favorites order, or sorted like the browser`() = runTest {
        // Drag / time-added order: b(1), c(2), a(3).
        every { favoriteDao.observeFavoriteChannels() } returns
            flowOf(listOf(entity(1, "b"), entity(2, "c"), entity(3, "a")))

        val favorites = ChannelSource.Favorites
        assertEquals(2L, repository.adjacentChannel(1, favorites, ChannelSortOrder.Default, ZapDirection.NEXT))
        assertEquals(1L, repository.adjacentChannel(3, favorites, ChannelSortOrder.Default, ZapDirection.NEXT))
        // By name: a(3), b(1), c(2).
        assertEquals(1L, repository.adjacentChannel(3, favorites, ChannelSortOrder.Name, ZapDirection.NEXT))
        assertEquals(2L, repository.adjacentChannel(3, favorites, ChannelSortOrder.Name, ZapDirection.PREVIOUS))
        assertNull(repository.nextChannel(2, favorites, ChannelSortOrder.Name))
    }

    @Test
    fun `a number inside favorites is the position in the list`() = runTest {
        every { favoriteDao.observeFavoriteChannels() } returns
            flowOf(listOf(entity(7, "x", number = 101), entity(8, "y", number = 5)))

        assertEquals(8L, repository.channelIdByNumber(2, ChannelSource.Favorites, ChannelSortOrder.Default))
        assertEquals(7L, repository.channelIdByNumber(2, ChannelSource.Favorites, ChannelSortOrder.Number))
        assertNull(repository.channelIdByNumber(3, ChannelSource.Favorites, ChannelSortOrder.Default))
        coVerify(exactly = 0) { channelDao.idByNumber(any(), any()) }
    }

    @Test
    fun `recently watched zaps newest first and numbers its positions`() = runTest {
        every { historyDao.observe(50) } returns
            flowOf(listOf(WatchHistoryRow(entity(4, "d"), 30), WatchHistoryRow(entity(9, "e"), 20)))

        val recent = ChannelSource.Recent
        assertEquals(9L, repository.adjacentChannel(4, recent, ChannelSortOrder.Name, ZapDirection.NEXT))
        assertEquals(4L, repository.adjacentChannel(9, recent, ChannelSortOrder.Name, ZapDirection.NEXT))
        assertEquals(9L, repository.channelIdByNumber(2, recent, ChannelSortOrder.Default))
    }

    @Test
    fun `a custom group zaps and numbers like favorites`() = runTest {
        // Group order: b(1), c(2), a(3).
        every { customGroupDao.observeChannels(7) } returns
            flowOf(listOf(entity(1, "b"), entity(2, "c"), entity(3, "a")))

        val custom = ChannelSource.Custom(7)
        assertEquals(2L, repository.adjacentChannel(1, custom, ChannelSortOrder.Default, ZapDirection.NEXT))
        assertEquals(1L, repository.adjacentChannel(3, custom, ChannelSortOrder.Custom, ZapDirection.NEXT))
        assertEquals(1L, repository.adjacentChannel(3, custom, ChannelSortOrder.Name, ZapDirection.NEXT))
        assertNull(repository.nextChannel(3, custom, ChannelSortOrder.Default))
        assertEquals(2L, repository.channelIdByNumber(2, custom, ChannelSortOrder.Default))
        assertEquals(1L, repository.channelIdByNumber(2, custom, ChannelSortOrder.Name))
        coVerify(exactly = 0) { channelDao.idByNumber(any(), any()) }
    }

    @Test
    fun `a number inside a group prefers the group and falls back to all channels`() = runTest {
        coEvery { channelDao.idByNumber(12, "News") } returns null
        coEvery { channelDao.idByNumber(12, null) } returns 40

        assertEquals(40L, repository.channelIdByNumber(12, ChannelSource.Group("News"), ChannelSortOrder.Name))
    }

    @Test
    fun `search passes fts, like and substring arguments and maps the rows`() = runTest {
        coEvery { channelDao.search(any(), any(), any(), any()) } returns listOf(entity(1, "Sport 1"))
        coEvery { channelDao.searchGroups(any(), any()) } returns listOf(GroupSummaryRow("Sport", 3))

        val results = repository.search("spo", limit = 10)

        coVerify { channelDao.search("\"spo*\"", "%spo%", 0, 10) }
        assertEquals(listOf(ChannelGroupSummary("Sport", 3)), results.groups)
        assertEquals(listOf("Sport 1"), results.channels.map { it.name })
    }

    @Test
    fun `chinese search also matches names by substring`() = runTest {
        repository.search("新闻", limit = 5)

        coVerify { channelDao.search("\"新闻*\"", "%新闻%", 1, 5) }
    }

    @Test
    fun `blank search does not query`() = runTest {
        assertEquals(SearchResults(), repository.search("  "))
        coVerify(exactly = 0) { channelDao.search(any(), any(), any(), any()) }
    }
}
