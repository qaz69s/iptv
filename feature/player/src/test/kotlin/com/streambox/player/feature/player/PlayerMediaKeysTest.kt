package com.streambox.player.feature.player

import android.view.KeyEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** K14: the foreground player handles the TV remote's play / pause keys. */
class PlayerMediaKeysTest {
    @Test
    fun `play pause and headset hook toggle (K14)`() {
        assertEquals(MediaKeyAction.TOGGLE, mediaKeyActionOf(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE))
        assertEquals(MediaKeyAction.TOGGLE, mediaKeyActionOf(KeyEvent.KEYCODE_HEADSETHOOK))
    }

    @Test
    fun `play resumes, pause and stop pause (K14)`() {
        assertEquals(MediaKeyAction.PLAY, mediaKeyActionOf(KeyEvent.KEYCODE_MEDIA_PLAY))
        assertEquals(MediaKeyAction.PAUSE, mediaKeyActionOf(KeyEvent.KEYCODE_MEDIA_PAUSE))
        assertEquals(MediaKeyAction.PAUSE, mediaKeyActionOf(KeyEvent.KEYCODE_MEDIA_STOP))
    }

    @Test
    fun `other keys are not media keys`() {
        assertNull(mediaKeyActionOf(KeyEvent.KEYCODE_DPAD_CENTER))
        assertNull(mediaKeyActionOf(KeyEvent.KEYCODE_MEDIA_NEXT))
    }
}
