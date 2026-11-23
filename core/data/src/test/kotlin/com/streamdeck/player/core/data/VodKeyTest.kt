package com.streamdeck.player.core.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class VodKeyTest {
    @Test fun `movie round trip`() {
        val k = VodKey.Movie(3, 42, "mkv")
        assertEquals(k, VodKey.decode(k.encode()))
        assertEquals("movie:42", k.progressKey)
    }

    @Test fun `episode round trip`() {
        val k = VodKey.Episode(3, 7, 99, "mp4")
        assertEquals(k, VodKey.decode(k.encode()))
        assertEquals("episode:99", k.progressKey)
    }

    @Test fun `garbage rejected`() {
        assertNull(VodKey.decode("nope"))
        assertNull(VodKey.decode("1|movie|x|mp4"))
    }
}
