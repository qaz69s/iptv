package com.streambox.player.feature.player

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import androidx.media3.ui.compose.ContentFrame
import com.streambox.player.core.model.AspectRatioMode
import com.streambox.player.core.model.SubtitleBackground
import com.streambox.player.core.model.SubtitleColor
import com.streambox.player.core.model.SubtitleSize
import com.streambox.player.core.model.SubtitleStyle

/** Video in the chosen aspect ratio; 16:9 / 4:3 force that frame, the rest map to a [ContentScale]. */
@Composable
internal fun VideoSurface(player: Player, aspectRatio: AspectRatioMode, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val forcedRatio = when (aspectRatio) {
            AspectRatioMode.Ratio16x9 -> 16f / 9f
            AspectRatioMode.Ratio4x3 -> 4f / 3f
            else -> null
        }
        if (forcedRatio != null) {
            ContentFrame(
                player = player,
                modifier = Modifier.aspectRatio(forcedRatio),
                contentScale = ContentScale.FillBounds,
            )
        } else {
            ContentFrame(
                player = player,
                modifier = Modifier.fillMaxSize(),
                contentScale = when (aspectRatio) {
                    AspectRatioMode.Fill -> ContentScale.Crop
                    AspectRatioMode.Stretch -> ContentScale.FillBounds
                    AspectRatioMode.Original -> ContentScale.None
                    else -> ContentScale.Fit
                },
            )
        }
    }
}

/**
 * Renders the player's cues with Media3's SubtitleView (media3-ui-compose has no subtitle composable
 * in 1.11) styled by the user's [SubtitleStyle]; embedded font sizes are ignored so the size setting wins.
 */
@Composable
internal fun SubtitleLayer(player: Player, style: SubtitleStyle, modifier: Modifier = Modifier) {
    var cues by remember(player) { mutableStateOf<List<Cue>>(emptyList()) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onCues(cueGroup: CueGroup) {
                cues = cueGroup.cues
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            cues = emptyList()
        }
    }
    AndroidView(
        factory = { context -> SubtitleView(context) },
        modifier = modifier.fillMaxSize(),
        update = { view ->
            view.setApplyEmbeddedFontSizes(false)
            view.setStyle(style.toCaptionStyle())
            view.setFractionalTextSize(SubtitleView.DEFAULT_TEXT_SIZE_FRACTION * style.size.scale())
            view.setCues(cues)
        },
    )
}

private fun SubtitleSize.scale(): Float = when (this) {
    SubtitleSize.Small -> SCALE_SMALL
    SubtitleSize.Medium -> 1f
    SubtitleSize.Large -> SCALE_LARGE
    SubtitleSize.ExtraLarge -> SCALE_EXTRA_LARGE
}

private const val SCALE_SMALL = 0.8f
private const val SCALE_LARGE = 1.3f
private const val SCALE_EXTRA_LARGE = 1.6f

private fun SubtitleStyle.toCaptionStyle(): CaptionStyleCompat {
    val foreground = when (color) {
        SubtitleColor.White -> AndroidColor.WHITE
        SubtitleColor.Yellow -> AndroidColor.YELLOW
        SubtitleColor.Green -> AndroidColor.GREEN
        SubtitleColor.Cyan -> AndroidColor.CYAN
    }
    val backgroundColor = when (background) {
        SubtitleBackground.None -> AndroidColor.TRANSPARENT
        SubtitleBackground.SemiTransparent -> SEMI_TRANSPARENT_BLACK
        SubtitleBackground.Opaque -> AndroidColor.BLACK
    }
    // Without a box the text needs an outline to stay readable on bright video.
    val edge = if (background == SubtitleBackground.None) {
        CaptionStyleCompat.EDGE_TYPE_OUTLINE
    } else {
        CaptionStyleCompat.EDGE_TYPE_NONE
    }
    return CaptionStyleCompat(foreground, backgroundColor, AndroidColor.TRANSPARENT, edge, AndroidColor.BLACK, null)
}

private const val SEMI_TRANSPARENT_BLACK = 0x99000000.toInt()
