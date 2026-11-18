package com.streambox.player.core.domain.channel

import com.streambox.player.core.model.Channel
import com.streambox.player.core.model.ChannelSortOrder
import com.streambox.player.core.model.ZapDirection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ChannelSequenceTest {
    private val items = listOf(10L, 20L, 30L)

    @Test
    fun `next and previous wrap around`() {
        assertEquals(20L, ChannelSequence.adjacent(items, 10L, ZapDirection.NEXT))
        assertEquals(10L, ChannelSequence.adjacent(items, 30L, ZapDirection.NEXT))
        assertEquals(30L, ChannelSequence.adjacent(items, 10L, ZapDirection.PREVIOUS))
        assertEquals(20L, ChannelSequence.adjacent(items, 30L, ZapDirection.PREVIOUS))
    }

    @Test
    fun `a channel missing from the list starts at the first or the last`() {
        assertEquals(10L, ChannelSequence.adjacent(items, 99L, ZapDirection.NEXT))
        assertEquals(30L, ChannelSequence.adjacent(items, 99L, ZapDirection.PREVIOUS))
        assertNull(ChannelSequence.adjacent(emptyList(), 1L, ZapDirection.NEXT))
    }

    @Test
    fun `a single channel wraps onto itself`() {
        assertEquals(10L, ChannelSequence.adjacent(listOf(10L), 10L, ZapDirection.NEXT))
    }

    @Test
    fun `next without wrap-around stops after the last`() {
        assertEquals(30L, ChannelSequence.next(items, 20L))
        assertNull(ChannelSequence.next(items, 30L))
        assertNull(ChannelSequence.next(items, 99L))
    }

    @Test
    fun `numbers are 1-based positions`() {
        assertEquals(10L, ChannelSequence.atNumber(items, 1))
        assertEquals(30L, ChannelSequence.atNumber(items, 3))
        assertNull(ChannelSequence.atNumber(items, 0))
        assertNull(ChannelSequence.atNumber(items, 4))
    }

    private fun channel(id: Long, name: String, number: Int? = null) = Channel(
        id = id,
        playlistId = 1,
        groupId = null,
        uniqueKey = "k$id",
        name = name,
        logoUrl = null,
        streamUrl = "https://s/$id",
        number = number,
        position = id.toInt(),
    )

    @Test
    fun `sorting by name ignores case and keeps ties in order`() {
        val list = listOf(channel(1, "beta"), channel(2, "Alpha"), channel(3, "alpha"))
        assertEquals(listOf(2L, 3L, 1L), ChannelSorting.sort(list, ChannelSortOrder.Name).map { it.id })
    }

    @Test
    fun `sorting by number uses tvg-chno, else the playlist position`() {
        val list = listOf(channel(1, "a", number = 50), channel(2, "b"), channel(3, "c", number = 1))
        assertEquals(listOf(3L, 2L, 1L), ChannelSorting.sort(list, ChannelSortOrder.Number).map { it.id })
    }

    @Test
    fun `default and custom keep the list order`() {
        val list = listOf(channel(2, "b"), channel(1, "a"))
        assertEquals(list, ChannelSorting.sort(list, ChannelSortOrder.Default))
        assertEquals(list, ChannelSorting.sort(list, ChannelSortOrder.Custom))
    }
}
