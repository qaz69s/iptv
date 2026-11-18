package com.streambox.player.feature.playlist

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ListMovesTest {
    private val list = listOf("a", "b", "c")

    @Test
    fun `moving swaps with the neighbour`() {
        assertEquals(listOf("b", "a", "c"), list.movedOrNull(1, up = true))
        assertEquals(listOf("a", "c", "b"), list.movedOrNull(1, up = false))
    }

    @Test
    fun `moving past the ends or an unknown index does nothing`() {
        assertNull(list.movedOrNull(0, up = true))
        assertNull(list.movedOrNull(2, up = false))
        assertNull(list.movedOrNull(-1, up = false))
    }
}
