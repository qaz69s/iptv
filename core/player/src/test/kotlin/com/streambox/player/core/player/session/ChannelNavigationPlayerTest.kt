package com.streambox.player.core.player.session

import android.os.Looper
import androidx.media3.common.Player
import com.streambox.player.core.model.ZapDirection
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChannelNavigationPlayerTest {
    private val inner = mockk<Player>(relaxed = true) {
        every { applicationLooper } returns mockk<Looper>()
        every { isCommandAvailable(any()) } returns false
    }
    private val zaps = mutableListOf<ZapDirection>()
    private var releases = 0
    private val player = ChannelNavigationPlayer(inner, onZap = { zaps += it }, onRelease = { releases++ })

    @Test
    fun `next and previous zap channels instead of seeking the single live item`() {
        player.seekToNext()
        player.seekToNextMediaItem()
        player.seekToPrevious()
        player.seekToPreviousMediaItem()

        assertEquals(
            listOf(ZapDirection.NEXT, ZapDirection.NEXT, ZapDirection.PREVIOUS, ZapDirection.PREVIOUS),
            zaps,
        )
        verify(exactly = 0) { inner.seekToNext() }
        verify(exactly = 0) { inner.seekToPrevious() }
        verify(exactly = 0) { inner.seekToNextMediaItem() }
        verify(exactly = 0) { inner.seekToPreviousMediaItem() }
    }

    @Test
    fun `navigation commands are always available, others come from the player`() {
        assertTrue(player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT))
        assertTrue(player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS))
        assertTrue(player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
        assertTrue(player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM))
        assertFalse(player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE))
    }

    @Test
    fun `release goes to the owner instead of the wrapped player`() {
        player.release()

        assertEquals(1, releases)
        verify(exactly = 0) { inner.release() }
    }

    @Test
    fun `listeners are wrapped once and removed by the original instance`() {
        val registered = slot<Player.Listener>()
        every { inner.addListener(capture(registered)) } returns Unit
        val listener = mockk<Player.Listener>(relaxed = true)

        player.addListener(listener)
        registered.captured.onIsPlayingChanged(true)
        player.removeListener(listener)

        verify { listener.onIsPlayingChanged(true) }
        verify { inner.removeListener(any()) }
    }

    @Test
    fun `forwarding listener passes events through and maps the available commands`() {
        val delegate = mockk<Player.Listener>(relaxed = true)
        val raw = mockk<Player.Commands>()
        val mapped = mockk<Player.Commands>()
        val listener = NavigationCommandsListener(delegate) { commands ->
            assertSame(raw, commands)
            mapped
        }
        val events = mockk<Player.Events>()

        listener.onAvailableCommandsChanged(raw)
        listener.onEvents(player, events)
        listener.onIsPlayingChanged(false)
        listener.onPlaybackStateChanged(Player.STATE_READY)
        listener.onDeviceVolumeChanged(3, false)

        verify { delegate.onAvailableCommandsChanged(mapped) }
        verify { delegate.onEvents(player, events) }
        verify { delegate.onIsPlayingChanged(false) }
        verify { delegate.onPlaybackStateChanged(Player.STATE_READY) }
        verify { delegate.onDeviceVolumeChanged(3, false) }
    }
}
