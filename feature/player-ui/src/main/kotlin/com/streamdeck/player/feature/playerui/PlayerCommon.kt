package com.streamdeck.player.feature.playerui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import androidx.media3.common.VideoSize

/** “1080p” style label of the current video, null until the first frame size is known. */
@Composable
fun rememberVideoLabel(player: Player?): String? {
    var height by remember(player) { mutableStateOf(player?.videoSize?.height ?: 0) }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) { height = videoSize.height }
        }
        player?.addListener(l)
        onDispose { player?.removeListener(l) }
    }
    return when {
        height <= 0 -> null
        height >= 2000 -> "4K"
        else -> "${height}p"
    }
}

/** Tracks play/pause so the round button flips immediately. */
@Composable
fun rememberIsPlaying(player: Player?): Boolean {
    var playing by remember(player) { mutableStateOf(player?.playWhenReady == true) }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { playing = playWhenReady }
        }
        player?.addListener(l)
        onDispose { player?.removeListener(l) }
    }
    return playing
}
