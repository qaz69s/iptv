@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.streamdeck.player.feature.playerui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import androidx.media3.ui.SubtitleView
import com.streamdeck.player.core.datastore.AspectMode
import com.streamdeck.player.core.datastore.SubtitleStyle

/** Video + subtitles with the six aspect modes of spec §4.3. Our own overlay replaces the stock controller. */
@Composable
fun VideoSurface(player: Player?, aspect: AspectMode, style: SubtitleStyle, modifier: Modifier = Modifier) {
    var videoSize by remember { mutableStateOf(VideoSize.UNKNOWN) }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onVideoSizeChanged(size: VideoSize) { videoSize = size }
        }
        player?.addListener(l)
        videoSize = player?.videoSize ?: VideoSize.UNKNOWN
        onDispose { player?.removeListener(l) }
    }
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
        val sized = when (aspect) {
            AspectMode.RATIO_16_9 -> Modifier.aspectRatio(16f / 9f)
            AspectMode.RATIO_4_3 -> Modifier.aspectRatio(4f / 3f)
            AspectMode.ORIGINAL -> if (videoSize.width > 0 && videoSize.height > 0) {
                with(density) { Modifier.requiredSize(videoSize.width.toDp(), videoSize.height.toDp()) }
            } else {
                Modifier.fillMaxSize()
            }
            else -> Modifier.fillMaxSize()
        }
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    isFocusable = false
                }
            },
            update = { v ->
                if (v.player !== player) v.player = player
                v.resizeMode = when (aspect) {
                    AspectMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    AspectMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    else -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                }
                v.subtitleView?.apply {
                    setStyle(
                        CaptionStyleCompat(
                            style.textColor, style.backgroundColor, android.graphics.Color.TRANSPARENT,
                            CaptionStyleCompat.EDGE_TYPE_OUTLINE, android.graphics.Color.BLACK, null,
                        ),
                    )
                    setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * style.scale)
                }
            },
            onRelease = { it.player = null },
            modifier = sized,
        )
    }
}
