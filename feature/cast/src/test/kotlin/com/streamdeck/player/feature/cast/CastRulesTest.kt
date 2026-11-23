package com.streamdeck.player.feature.cast

import com.streamdeck.player.core.player.StreamRequest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CastRulesTest {
    @Test fun `plain streams are castable`() { assertTrue(CastController.isCastable(StreamRequest("https://h/a.m3u8", "A"))) }

    @Test fun `custom headers are not castable`() {
        assertFalse(CastController.isCastable(StreamRequest("https://h/a.m3u8", "A", referrer = "https://r")))
        assertFalse(CastController.isCastable(StreamRequest("https://h/a.m3u8", "A", userAgent = "VLC")))
        assertFalse(CastController.isCastable(StreamRequest("https://h/a.m3u8", "A", headers = mapOf("X" to "1"))))
        assertFalse(CastController.isCastable(StreamRequest("udp://@239.0.0.1:1234", "U")))
    }

    @Test fun `mime detection`() {
        assertEquals("application/x-mpegURL", CastController.mimeFor("http://h/x.M3U8?t=1"))
        assertEquals("video/mp2t", CastController.mimeFor("http://h/live/1.ts"))
    }
}
