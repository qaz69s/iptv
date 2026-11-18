package com.streambox.player.core.data.repository

import com.streambox.player.core.database.dao.ChannelDao
import com.streambox.player.core.database.dao.ChannelKeyRow
import com.streambox.player.core.database.dao.FavoriteDao
import com.streambox.player.core.database.dao.KeyPositionRow
import com.streambox.player.core.database.dao.OverrideDao
import com.streambox.player.core.database.dao.WatchHistoryDao
import com.streambox.player.core.database.dao.WatchHistoryRow
import com.streambox.player.core.database.entity.ChannelEntity
import com.streambox.player.core.database.entity.ChannelOverrideEntity
import com.streambox.player.core.database.entity.FavoriteEntity
import com.streambox.player.core.database.entity.GroupOverrideEntity
import com.streambox.player.core.database.entity.WatchHistoryEntity
import com.streambox.player.core.domain.repository.WatchHistoryRepository
import com.streambox.player.core.model.ChannelKey
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class UserLibraryRepositoriesTest {
    private val clock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_000), ZoneOffset.UTC)

    private fun entity(id: Long, key: String, customName: String? = null) = ChannelEntity(
        id = id,
        playlistId = 1,
        groupId = null,
        uniqueKey = key,
        name = "Original $key",
        logoUrl = null,
        streamUrl = "https://s/$key",
        tvgId = null,
        tvgName = null,
        number = null,
        catchupType = null,
        catchupSource = null,
        catchupDays = null,
        timeshift = null,
        userAgent = null,
        referrer = null,
        sortOrder = 0,
        generation = 1,
        customName = customName,
    )

    // --- Favorites ---------------------------------------------------------------------------------

    @Test
    fun `adding a favorite stores the key with the current UTC time`() = runTest {
        val dao = mockk<FavoriteDao>(relaxed = true)

        DefaultFavoritesRepository(dao, clock).add(ChannelKey(1, "a"))

        coVerify { dao.insert(FavoriteEntity(1, "a", addedAt = 1_700_000_000_000, customOrder = null)) }
    }

    @Test
    fun `favorites show the user's channel name and keys are a set`() = runTest {
        val dao = mockk<FavoriteDao>(relaxed = true)
        every { dao.observeFavoriteChannels() } returns
            flowOf(listOf(entity(5, "a", customName = "Mine"), entity(6, "b")))
        every { dao.observeKeys() } returns flowOf(listOf(ChannelKeyRow(1, "a"), ChannelKeyRow(2, "a")))
        val repository = DefaultFavoritesRepository(dao, clock)

        assertEquals(listOf("Mine", "Original b"), repository.favorites.first().map { it.name })
        assertEquals(setOf(ChannelKey(1, "a"), ChannelKey(2, "a")), repository.favoriteKeys.first())
    }

    @Test
    fun `reordering favorites drops duplicate keys`() = runTest {
        val dao = mockk<FavoriteDao>(relaxed = true)

        DefaultFavoritesRepository(
            dao,
            clock,
        ).reorder(listOf(ChannelKey(1, "b"), ChannelKey(1, "a"), ChannelKey(1, "b")))

        coVerify { dao.reorder(listOf(ChannelKeyRow(1, "b"), ChannelKeyRow(1, "a"))) }
    }

    // --- Watch history -----------------------------------------------------------------------------

    @Test
    fun `recording history uses the current time and the 50 entry cap`() = runTest {
        val dao = mockk<WatchHistoryDao>(relaxed = true)

        DefaultWatchHistoryRepository(dao, clock).record(ChannelKey(2, "x"))

        coVerify { dao.record(WatchHistoryEntity(2, "x", watchedAt = 1_700_000_000_000), cap = 50) }
        assertEquals(50, WatchHistoryRepository.MAX_ENTRIES)
    }

    @Test
    fun `history entries carry the channel and when it was watched`() = runTest {
        val dao = mockk<WatchHistoryDao>(relaxed = true)
        every { dao.observe(50) } returns flowOf(listOf(WatchHistoryRow(entity(9, "z"), watchedAt = 123)))

        val entry = DefaultWatchHistoryRepository(dao, clock).history.first().single()

        assertEquals(9L, entry.channel.id)
        assertEquals(123L, entry.watchedAtUtcMs)
    }

    // --- Overrides ---------------------------------------------------------------------------------

    @Test
    fun `renaming trims the name and a blank name restores the original`() = runTest {
        val overrideDao = mockk<OverrideDao>(relaxed = true)
        val channelTransform = slot<(ChannelOverrideEntity) -> ChannelOverrideEntity>()
        val groupTransform = slot<(GroupOverrideEntity) -> GroupOverrideEntity>()
        coEvery { overrideDao.updateChannel(1, "a", capture(channelTransform)) } returns Unit
        coEvery { overrideDao.updateGroup(1, "News", capture(groupTransform)) } returns Unit
        val repository = DefaultChannelOverridesRepository(overrideDao, mockk())
        val start = ChannelOverrideEntity(1, "a", customName = "Old")

        repository.renameChannel(ChannelKey(1, "a"), "  Mine  ")
        assertEquals("Mine", channelTransform.captured(start).customName)
        repository.renameChannel(ChannelKey(1, "a"), "   ")
        assertEquals(null, channelTransform.captured(start).customName)
        repository.renameGroup(1, "News", "\tWorld ")
        assertEquals("World", groupTransform.captured(GroupOverrideEntity(1, "News")).customName)
    }

    @Test
    fun `reordering channels permutes the slots they occupy now`() = runTest {
        val overrideDao = mockk<OverrideDao>(relaxed = true)
        val channelDao = mockk<ChannelDao>()
        coEvery { channelDao.channelPositions(1, listOf("c", "a", "gone")) } returns
            listOf(KeyPositionRow("a", 2), KeyPositionRow("c", 7))

        DefaultChannelOverridesRepository(overrideDao, channelDao).reorderChannels(1, listOf("c", "a", "gone", "c"))

        coVerify { overrideDao.updateChannelPositions(1, mapOf("c" to 2, "a" to 7)) }
    }

    @Test
    fun `reordering nothing does nothing`() = runTest {
        val overrideDao = mockk<OverrideDao>(relaxed = true)
        val channelDao = mockk<ChannelDao>()

        DefaultChannelOverridesRepository(overrideDao, channelDao).reorderGroups(1, emptyList())

        coVerify(exactly = 0) { overrideDao.updateGroupPositions(any(), any()) }
    }

    @Test
    fun `permuted positions keep untouched slots and skip unknown items`() {
        val current = listOf(KeyPositionRow("x", 4), KeyPositionRow("y", 1), KeyPositionRow("z", 9))

        assertEquals(mapOf("z" to 1, "x" to 4, "y" to 9), permutedPositions(listOf("z", "x", "y"), current))
        assertEquals(mapOf("y" to 1), permutedPositions(listOf("missing", "y"), current))
        assertEquals(emptyMap<String, Int>(), permutedPositions(emptyList(), current))
    }

    // --- Channel keys ------------------------------------------------------------------------------

    @Test
    fun `a channel key resolves through the dao`() = runTest {
        val channelDao = mockk<ChannelDao>(relaxed = true)
        coEvery { channelDao.idByKey(4, "tvg:a") } returns 99

        assertEquals(99L, DefaultChannelRepository(channelDao).channelIdByKey(ChannelKey(4, "tvg:a")))
    }
}
