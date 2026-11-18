package com.streambox.player.core.domain.usecase

import com.streambox.player.core.domain.repository.ChannelRepository
import com.streambox.player.core.domain.repository.FavoritesRepository
import com.streambox.player.core.domain.repository.LastChannelRepository
import com.streambox.player.core.domain.repository.WatchHistoryRepository
import com.streambox.player.core.model.ChannelKey
import com.streambox.player.core.model.LastPlayedChannel
import com.streambox.player.core.model.PlaybackItem
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FavoritesAndHistoryUseCasesTest {
    private val key = ChannelKey(3, "tvg:news")

    @Test
    fun `toggling a channel that is not a favorite adds it`() = runTest {
        val favorites = mockk<FavoritesRepository>(relaxed = true)
        every { favorites.isFavorite(key) } returns flowOf(false)

        assertTrue(ToggleFavoriteUseCase(favorites)(key))

        coVerify { favorites.add(key) }
        coVerify(exactly = 0) { favorites.remove(any()) }
    }

    @Test
    fun `toggling a favorite removes it`() = runTest {
        val favorites = mockk<FavoritesRepository>(relaxed = true)
        every { favorites.isFavorite(key) } returns flowOf(true)

        assertFalse(ToggleFavoriteUseCase(favorites)(key))

        coVerify { favorites.remove(key) }
        coVerify(exactly = 0) { favorites.add(any()) }
    }

    @Test
    fun `a playback start is remembered as last channel and recorded in the history by key`() = runTest {
        val last = mockk<LastChannelRepository>(relaxed = true)
        val history = mockk<WatchHistoryRepository>(relaxed = true)
        val item = PlaybackItem(
            channelId = 42,
            name = "News",
            logoUrl = null,
            streamUrl = "https://s/news.m3u8",
            userAgent = null,
            referrer = null,
            allowCleartext = false,
            displayNumber = 1,
            playlistId = 3,
            uniqueKey = "tvg:news",
        )

        RecordPlaybackStartUseCase(last, history)(item)

        coVerifyOrder {
            last.save(LastPlayedChannel(playlistId = 3, uniqueKey = "tvg:news"))
            history.record(key)
        }
    }

    @Test
    fun `the last channel resolves to the current id of its key`() = runTest {
        val last = mockk<LastChannelRepository>(relaxed = true)
        val channels = mockk<ChannelRepository>()
        every { last.lastChannel } returns flowOf(LastPlayedChannel(3, "tvg:news"))
        coEvery { channels.channelIdByKey(key) } returns 77

        assertEquals(77L, ResolveLastChannelUseCase(last, channels)())
        coVerify(exactly = 0) { last.clear() }
    }

    @Test
    fun `a last channel that is gone is forgotten`() = runTest {
        val last = mockk<LastChannelRepository>(relaxed = true)
        val channels = mockk<ChannelRepository>()
        every { last.lastChannel } returns flowOf(LastPlayedChannel(3, "tvg:news"))
        coEvery { channels.channelIdByKey(key) } returns null

        assertNull(ResolveLastChannelUseCase(last, channels)())
        coVerify { last.clear() }
    }

    @Test
    fun `nothing remembered resolves to nothing`() = runTest {
        val last = mockk<LastChannelRepository>(relaxed = true)
        val channels = mockk<ChannelRepository>()
        every { last.lastChannel } returns flowOf(null)

        assertNull(ResolveLastChannelUseCase(last, channels)())
        coVerify(exactly = 0) { last.clear() }
    }
}
