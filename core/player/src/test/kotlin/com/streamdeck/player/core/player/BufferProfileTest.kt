package com.streamdeck.player.core.player

import com.streamdeck.player.core.datastore.BufferProfile
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BufferProfileTest {
    @Test fun `profiles are ordered and internally consistent`() {
        val all = BufferProfile.entries.map { it.durations() }
        all.forEach { d ->
            assertTrue(d.startMs <= d.minMs && d.rebufferMs <= d.minMs && d.minMs <= d.maxMs)
        }
        assertTrue(all[0].maxMs < all[1].maxMs && all[1].maxMs < all[2].maxMs)
    }

    @Test fun `recoverable errors`() {
        assertTrue(PlaybackError.Network.isRecoverable())
        assertTrue(PlaybackError.Http(503).isRecoverable())
        assertFalse(PlaybackError.Http(404).isRecoverable())
        assertFalse(PlaybackError.Decoder("x").isRecoverable())
        assertFalse(PlaybackError.CleartextBlocked.isRecoverable())
    }
}
