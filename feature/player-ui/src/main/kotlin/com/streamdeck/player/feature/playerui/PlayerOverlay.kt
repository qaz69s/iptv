package com.streamdeck.player.feature.playerui

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.streamdeck.player.core.database.ChannelEntity
import com.streamdeck.player.core.player.PlaybackError
import com.streamdeck.player.core.player.PlaybackState
import kotlinx.coroutines.delay
import java.util.Date
import com.streamdeck.player.core.designsystem.R as DR

/** Extra overlay content supplied by later phases (EPG now/next, catch-up controls). */
data class OverlayProgram(
    val title: String,
    val startMs: Long,
    val endMs: Long,
    val nextTitle: String?,
    val nextStartMs: Long? = null,
    val category: String? = null,
)

@Composable
fun BoxScope.PlayerOverlay(
    visible: Boolean,
    tv: Boolean,
    channel: ChannelEntity?,
    player: Player?,
    sleepLeftMs: Long?,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onInteraction: () -> Unit,
    castButton: @Composable () -> Unit,
    program: OverlayProgram? = null,
    titleOverride: String? = null,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    catchup: CatchupWindow? = null,
    onGoLive: () -> Unit = {},
) {
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
        Row(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!tv) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(DR.string.ds_back), tint = Color.White) }
            channel?.logoUrl?.let {
                AsyncImage(model = it, contentDescription = stringResource(R.string.player_logo), modifier = Modifier.size(40.dp))
            }
            Column(Modifier.weight(1f)) {
                val title = titleOverride ?: channel?.let { listOfNotNull(it.number?.toString(), it.name).joinToString("  ") }.orEmpty()
                Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                sleepLeftMs?.let {
                    Text(stringResource(R.string.sleep_remaining, formatDuration(it)), color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.labelSmall)
                }
            }
            Clock()
            if (!tv && onToggleFavorite != null) IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    stringResource(if (isFavorite) R.string.unfavorite else R.string.favorite), tint = Color.White,
                )
            }
            castButton()
            if (!tv) IconButton(onClick = onOpenSettings) { Icon(Icons.Default.Settings, stringResource(R.string.player_settings), tint = Color.White) }
        }
    }
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
        Column(
            Modifier.fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
                .padding(16.dp),
        ) {
            program?.let { ProgramBar(it) }
            catchup?.let { w -> CatchupBar(w, onGoLive) }
            player?.let { if (!it.isCurrentMediaItemLive && it.isCurrentMediaItemSeekable) VodControls(it, tv, onInteraction) }
        }
    }
}

@Composable
private fun CatchupBar(w: CatchupWindow, onGoLive: () -> Unit) {
    val ctx = LocalContext.current
    val fmt = remember(ctx) { DateFormat.getTimeFormat(ctx) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.catchup_badge, fmt.format(Date(w.start))),
            color = Color.Black, style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
        )
        Button(onClick = onGoLive) { Text(stringResource(R.string.go_live)) }
    }
}

@Composable
private fun Clock() {
    val ctx = LocalContext.current
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000)
        }
    }
    Text(DateFormat.getTimeFormat(ctx).format(Date(now)), color = Color.White, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun ProgramBar(p: OverlayProgram) {
    val ctx = LocalContext.current
    val fmt = remember(ctx) { DateFormat.getTimeFormat(ctx) }
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(15_000)
        }
    }
    val progress = if (p.endMs > p.startMs) ((now - p.startMs).toFloat() / (p.endMs - p.startMs)).coerceIn(0f, 1f) else 0f
    Text("${fmt.format(Date(p.startMs))}–${fmt.format(Date(p.endMs))}  ${p.title}", color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
    androidx.compose.material3.LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
    p.nextTitle?.let { Text(stringResource(R.string.next_program, it), color = Color.White.copy(alpha = 0.7f), maxLines = 1) }
}

@Composable
private fun VodControls(player: Player, tv: Boolean, onInteraction: () -> Unit) {
    val pos by produceState(player.currentPosition, player) {
        while (true) {
            value = player.currentPosition
            delay(500)
        }
    }
    val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: return
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!tv) {
            IconButton(onClick = { player.playWhenReady = !player.playWhenReady; onInteraction() }) {
                val playing = player.playWhenReady
                Icon(
                    if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    stringResource(if (playing) R.string.pause else R.string.play),
                    tint = Color.White,
                )
            }
        }
        Text(formatDuration(if (dragging) (dragValue * duration).toLong() else pos), color = Color.White)
        Slider(
            value = if (dragging) dragValue else (pos.toFloat() / duration).coerceIn(0f, 1f),
            onValueChange = { dragging = true; dragValue = it; onInteraction() },
            onValueChangeFinished = { player.seekTo((dragValue * duration).toLong()); dragging = false },
            modifier = Modifier.weight(1f),
        )
        Text(formatDuration(duration), color = Color.White)
    }
}

@Composable
fun BoxScope.StatusLayer(state: PlaybackState, onRetry: () -> Unit) {
    when (state) {
        PlaybackState.Buffering -> CircularProgressIndicator(Modifier.align(Alignment.Center))
        is PlaybackState.Reconnecting -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text(
                stringResource(R.string.reconnecting, state.attempt, state.maxAttempts),
                color = Color.White, modifier = Modifier.padding(12.dp),
            )
        }
        is PlaybackState.Error -> Column(
            Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(12.dp)).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.playback_error, state.error.message()), color = Color.White)
            Button(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) { Text(stringResource(DR.string.ds_retry)) }
        }
        else -> Unit
    }
}

@Composable
fun BoxScope.CenterHint(text: String?) {
    if (text.isNullOrEmpty()) return
    Box(
        Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(12.dp)).padding(horizontal = 24.dp, vertical = 12.dp),
    ) { Text(text, color = Color.White, style = MaterialTheme.typography.headlineMedium) }
}

@Composable
fun PlaybackError.message(): String = when (this) {
    is PlaybackError.Http -> stringResource(R.string.err_http, code)
    PlaybackError.Timeout -> stringResource(R.string.err_timeout)
    PlaybackError.Network -> stringResource(R.string.err_network)
    is PlaybackError.Decoder -> stringResource(R.string.err_decoder, detail)
    PlaybackError.CleartextBlocked -> stringResource(R.string.err_cleartext)
    PlaybackError.MeteredBlocked -> stringResource(R.string.err_metered)
    is PlaybackError.Other -> stringResource(R.string.err_other, detail)
}
