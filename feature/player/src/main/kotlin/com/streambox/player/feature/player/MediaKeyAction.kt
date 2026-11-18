package com.streambox.player.feature.player

import android.view.KeyEvent

/** What a remote / keyboard media key does on the foreground player (K14). */
enum class MediaKeyAction { TOGGLE, PLAY, PAUSE }

/**
 * Media keys handled by the player screen itself while it is in the foreground. In the background the
 * MediaSession handles them (P2b); in the foreground the key reaches the focused screen first.
 */
internal fun mediaKeyActionOf(keyCode: Int): MediaKeyAction? = when (keyCode) {
    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_HEADSETHOOK -> MediaKeyAction.TOGGLE
    KeyEvent.KEYCODE_MEDIA_PLAY -> MediaKeyAction.PLAY
    KeyEvent.KEYCODE_MEDIA_PAUSE, KeyEvent.KEYCODE_MEDIA_STOP -> MediaKeyAction.PAUSE
    else -> null
}
