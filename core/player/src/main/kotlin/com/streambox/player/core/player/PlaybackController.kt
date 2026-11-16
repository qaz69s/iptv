package com.streambox.player.core.player

import androidx.media3.common.Player
import com.streambox.player.core.model.PlaybackError
import com.streambox.player.core.model.PlaybackItem
import kotlinx.coroutines.flow.StateFlow

data class PlaybackState(
    val item: PlaybackItem? = null,
    val isBuffering: Boolean = false,
    val isPlaying: Boolean = false,
    val error: PlaybackError? = null,
)

/**
 * App-wide player (requirements §8.8: not tied to an Activity, survives configuration changes).
 * Must be called on the main thread. P2 adds tracks, reconnect policy, PiP and background playback.
 */
interface PlaybackController {
    /** The Media3 player to render, or null when released. */
    val player: StateFlow<Player?>

    val state: StateFlow<PlaybackState>

    fun play(item: PlaybackItem)

    fun retry()

    /** Stops playback and frees decoders / network; the next [play] creates a fresh player. */
    fun release()
}
