package com.streambox.player.feature.player

import com.streambox.player.core.model.ZapDirection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PlayerGesturesTest {
    private val width = 900f
    private val height = 400f
    private val slop = 10f

    private fun lock(dx: Float, dy: Float, startX: Float = 450f, canSeek: Boolean = true, enabled: Boolean = true) =
        PlayerGestures.lockDrag(dx, dy, slop, startX, width, canSeek, enabled)

    @Test
    fun `zones split the width in thirds`() {
        assertEquals(TouchZone.LEFT, PlayerGestures.zoneOf(0f, width))
        assertEquals(TouchZone.LEFT, PlayerGestures.zoneOf(299f, width))
        assertEquals(TouchZone.CENTER, PlayerGestures.zoneOf(450f, width))
        assertEquals(TouchZone.RIGHT, PlayerGestures.zoneOf(601f, width))
        assertEquals(TouchZone.RIGHT, PlayerGestures.zoneOf(width, width))
        assertEquals(TouchZone.CENTER, PlayerGestures.zoneOf(10f, 0f))
    }

    @Test
    fun `direction stays undecided within the slop`() {
        assertNull(lock(dx = 9f, dy = -10f))
        assertNull(lock(dx = 0f, dy = 0f))
    }

    @Test
    fun `horizontal drag seeks only when seekable`() {
        assertEquals(DragKind.SEEK, lock(dx = 30f, dy = 5f))
        assertEquals(DragKind.SEEK, lock(dx = -30f, dy = 29f))
        assertEquals(DragKind.NONE, lock(dx = 30f, dy = 5f, canSeek = false))
    }

    @Test
    fun `vertical drag picks brightness, zap or volume by start zone`() {
        assertEquals(DragKind.BRIGHTNESS, lock(dx = 2f, dy = -30f, startX = 100f))
        assertEquals(DragKind.ZAP, lock(dx = 2f, dy = 30f, startX = 450f))
        assertEquals(DragKind.VOLUME, lock(dx = -2f, dy = 30f, startX = 800f))
        // A perfect diagonal counts as vertical.
        assertEquals(DragKind.VOLUME, lock(dx = 30f, dy = 30f, startX = 800f))
    }

    @Test
    fun `with gestures off vertical zaps anywhere and horizontal is ignored`() {
        assertEquals(DragKind.ZAP, lock(dx = 0f, dy = -30f, startX = 50f, enabled = false))
        assertEquals(DragKind.ZAP, lock(dx = 0f, dy = 30f, startX = 850f, enabled = false))
        assertEquals(DragKind.NONE, lock(dx = 40f, dy = 0f, enabled = false))
    }

    @Test
    fun `seek offset scales with the player width`() {
        assertEquals(PlayerGestures.SEEK_FULL_WIDTH_MS, PlayerGestures.seekOffsetMs(width, width))
        assertEquals(-PlayerGestures.SEEK_FULL_WIDTH_MS / 2, PlayerGestures.seekOffsetMs(-width / 2, width))
        assertEquals(0L, PlayerGestures.seekOffsetMs(100f, 0f))
    }

    @Test
    fun `seek target stays inside the content`() {
        assertEquals(40_000L, PlayerGestures.seekTarget(30_000L, 10_000L, 60_000L))
        assertEquals(0L, PlayerGestures.seekTarget(5_000L, -10_000L, 60_000L))
        assertEquals(60_000L, PlayerGestures.seekTarget(55_000L, 10_000L, 60_000L))
        // Unknown duration: no upper bound.
        assertEquals(70_000L, PlayerGestures.seekTarget(60_000L, 10_000L, -1L))
    }

    @Test
    fun `level rises when dragging up and is clamped`() {
        assertEquals(0.75f, PlayerGestures.adjustLevel(0.5f, -height / 4, height), 1e-4f)
        assertEquals(0.25f, PlayerGestures.adjustLevel(0.5f, height / 4, height), 1e-4f)
        assertEquals(1f, PlayerGestures.adjustLevel(0.9f, -height, height))
        assertEquals(0f, PlayerGestures.adjustLevel(0.1f, height, height))
        assertEquals(0.3f, PlayerGestures.adjustLevel(0.3f, -50f, 0f))
    }

    @Test
    fun `volume level and index convert both ways`() {
        assertEquals(0, PlayerGestures.volumeIndex(0f, 15))
        assertEquals(8, PlayerGestures.volumeIndex(0.5f, 15))
        assertEquals(15, PlayerGestures.volumeIndex(1.2f, 15))
        assertEquals(3, PlayerGestures.volumeIndex(0f, 15, minIndex = 3))
        assertEquals(0, PlayerGestures.volumeIndex(0.5f, 0))
        assertEquals(0.5f, PlayerGestures.volumeLevel(5, 10), 1e-4f)
        assertEquals(0f, PlayerGestures.volumeLevel(5, 0))
        assertEquals(1f, PlayerGestures.volumeLevel(20, 10))
    }

    @Test
    fun `percent rounds and clamps`() {
        assertEquals(0, PlayerGestures.percent(-0.2f))
        assertEquals(47, PlayerGestures.percent(0.466f))
        assertEquals(100, PlayerGestures.percent(1.5f))
    }

    @Test
    fun `zap direction needs the threshold`() {
        assertEquals(ZapDirection.NEXT, PlayerGestures.zapDirection(-80f, 72f))
        assertEquals(ZapDirection.PREVIOUS, PlayerGestures.zapDirection(72f, 72f))
        assertNull(PlayerGestures.zapDirection(-71f, 72f))
    }

    @Test
    fun `double-tap sides need seekable content`() {
        assertEquals(DoubleTapAction.REWIND, PlayerGestures.doubleTapAction(TouchZone.LEFT, canSeek = true))
        assertEquals(DoubleTapAction.FORWARD, PlayerGestures.doubleTapAction(TouchZone.RIGHT, canSeek = true))
        assertEquals(DoubleTapAction.PLAY_PAUSE, PlayerGestures.doubleTapAction(TouchZone.CENTER, canSeek = false))
        assertNull(PlayerGestures.doubleTapAction(TouchZone.LEFT, canSeek = false))
        assertNull(PlayerGestures.doubleTapAction(TouchZone.RIGHT, canSeek = false))
    }

    @Test
    fun `times and offsets are formatted`() {
        assertEquals("0:00", PlayerGestures.formatTime(-5L))
        assertEquals("1:05", PlayerGestures.formatTime(65_999L))
        assertEquals("1:02:03", PlayerGestures.formatTime(3_723_000L))
        assertEquals("+0:15", PlayerGestures.formatOffset(15_000L))
        assertEquals("\u22121:05", PlayerGestures.formatOffset(-65_000L))
    }
}
