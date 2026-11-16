package com.streambox.player.feature.player

import androidx.media3.common.Player
import com.streambox.player.core.model.PlaybackError
import com.streambox.player.core.model.PlaybackItem
import com.streambox.player.core.player.PlaybackController
import com.streambox.player.core.player.PlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Records calls instead of decoding; tests push buffering / errors through [state]. */
class FakePlaybackController : PlaybackController {
    override val player: StateFlow<Player?> = MutableStateFlow(null)
    override val state = MutableStateFlow(PlaybackState())

    val played = mutableListOf<PlaybackItem>()
    var retries = 0
    var releases = 0

    override fun play(item: PlaybackItem) {
        played += item
        state.value = PlaybackState(item = item, isBuffering = true)
    }

    override fun retry() {
        retries++
        state.update { it.copy(error = null, isBuffering = true) }
    }

    override fun release() {
        releases++
        state.value = PlaybackState()
    }

    fun startPlaying() = state.update { it.copy(isBuffering = false, isPlaying = true) }

    fun fail(error: PlaybackError) = state.update { it.copy(isBuffering = false, isPlaying = false, error = error) }
}
