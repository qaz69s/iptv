package com.streambox.player.feature.player

import com.streambox.player.core.model.AspectRatioMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PictureInPictureTest {
    @Test
    fun `16x9 and 4x3 modes force their ratio whatever the video`() {
        assertEquals(16 to 9, pipAspectRatioOf(AspectRatioMode.Ratio16x9, 720, 576, 1f))
        assertEquals(4 to 3, pipAspectRatioOf(AspectRatioMode.Ratio4x3, 1920, 1080, 1f))
    }

    @Test
    fun `other modes follow the video size`() {
        assertEquals(1920 to 1080, pipAspectRatioOf(AspectRatioMode.Fit, 1920, 1080, 1f))
        assertEquals(640 to 480, pipAspectRatioOf(AspectRatioMode.Fill, 640, 480, 1f))
        assertEquals(1280 to 720, pipAspectRatioOf(AspectRatioMode.Stretch, 1280, 720, 1f))
    }

    @Test
    fun `pixel aspect ratio widens anamorphic video`() {
        // PAL 720x576 with 16:15 pixels shows as 768x576 (4:3).
        assertEquals(768 to 576, pipAspectRatioOf(AspectRatioMode.Fit, 720, 576, 16f / 15f))
    }

    @Test
    fun `invalid pixel ratio counts as square pixels`() {
        assertEquals(1280 to 720, pipAspectRatioOf(AspectRatioMode.Fit, 1280, 720, 0f))
        assertEquals(1280 to 720, pipAspectRatioOf(AspectRatioMode.Fit, 1280, 720, Float.NaN))
    }

    @Test
    fun `unknown video size falls back to 16x9`() {
        assertEquals(16 to 9, pipAspectRatioOf(AspectRatioMode.Fit, 0, 0, 1f))
        assertEquals(16 to 9, pipAspectRatioOf(AspectRatioMode.Original, 1920, 0, 1f))
    }

    @Test
    fun `extreme ratios are clamped to the platform limits`() {
        assertEquals(239 to 100, pipAspectRatioOf(AspectRatioMode.Fit, 3000, 1000, 1f))
        assertEquals(100 to 239, pipAspectRatioOf(AspectRatioMode.Fit, 400, 1000, 1f))
        // Exactly at the limit stays as is.
        assertEquals(239 to 100, pipAspectRatioOf(AspectRatioMode.Fit, 239, 100, 1f))
    }

    @Test
    fun `source rect fits the ratio centred in a wider area`() {
        assertEquals(
            PipRect(left = 240, top = 0, right = 1680, bottom = 1080),
            pipSourceRect(PipRect(0, 0, 1920, 1080), 4, 3),
        )
    }

    @Test
    fun `source rect fits the ratio centred in a taller area`() {
        assertEquals(
            PipRect(left = 0, top = 735, right = 1080, bottom = 1343),
            pipSourceRect(PipRect(0, 432, 1080, 1647), 16, 9),
        )
    }

    @Test
    fun `source rect keeps a matching area and its offset`() {
        assertEquals(PipRect(10, 20, 1930, 1100), pipSourceRect(PipRect(10, 20, 1930, 1100), 16, 9))
    }

    @Test
    fun `empty area has no source rect`() {
        assertNull(pipSourceRect(PipRect(0, 0, 0, 1080), 16, 9))
        assertNull(pipSourceRect(PipRect(0, 0, 1920, 1080), 0, 9))
    }
}
