package com.streamdeck.player.feature.playerui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Player
import com.streamdeck.player.core.database.ChannelEntity
import com.streamdeck.player.core.database.ChannelListItem
import com.streamdeck.player.core.designsystem.ChannelLogo
import com.streamdeck.player.core.designsystem.FocusSurface
import com.streamdeck.player.core.designsystem.LiveBadge
import com.streamdeck.player.core.designsystem.OnVideo
import com.streamdeck.player.core.designsystem.ProgramTime
import com.streamdeck.player.core.designsystem.SdProgress
import com.streamdeck.player.core.designsystem.SdTheme
import com.streamdeck.player.core.designsystem.formatClock
import com.streamdeck.player.core.designsystem.rememberNow
import kotlinx.coroutines.delay
import com.streamdeck.player.core.designsystem.R as DR

/**
 * TV player overlay (design 5): zapping list of the current group on the left and the info bar with actions at
 * the bottom. Focus starts on play/pause; ◀▶▲▼ move between buttons and the list, OK activates, BACK closes.
 */
@Suppress("LongParameterList")
@Composable
fun BoxScope.TvPlayerOverlay(
    visible: Boolean,
    channel: ChannelEntity?,
    titleOverride: String?,
    groupItems: List<ChannelListItem>,
    program: OverlayProgram?,
    player: Player?,
    isFavorite: Boolean,
    catchup: CatchupWindow?,
    sleepLeftMs: Long?,
    canStartOver: Boolean,
    onPlayChannel: (Long) -> Unit,
    onTogglePause: () -> Unit,
    onStartOver: () -> Unit,
    onGoLive: () -> Unit,
    onOpenPanel: (Set<PanelSection>) -> Unit,
    onToggleFavorite: (() -> Unit)?,
) {
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
        OnVideo {
            val p = SdTheme.palette
            val primaryFocus = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { primaryFocus.requestFocus() } }
            Box(
                Modifier.fillMaxSize()
                    .background(Brush.horizontalGradient(0f to p.scrim, 0.45f to Color.Transparent))
                    .background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to p.scrim)),
            ) {
                if (titleOverride == null && groupItems.isNotEmpty()) {
                    ZapList(channel, groupItems, onPlayChannel, Modifier.align(Alignment.TopStart).padding(start = 32.dp, top = 28.dp))
                }
                InfoBar(
                    channel, titleOverride, program, player, isFavorite, catchup, sleepLeftMs, canStartOver, primaryFocus,
                    onTogglePause, onStartOver, onGoLive, onOpenPanel, onToggleFavorite,
                    Modifier.align(Alignment.BottomStart).padding(horizontal = 32.dp, vertical = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun ZapList(current: ChannelEntity?, items: List<ChannelListItem>, onPlay: (Long) -> Unit, modifier: Modifier) {
    val p = SdTheme.palette
    val state = rememberLazyListState()
    val index = items.indexOfFirst { it.channel.id == current?.id }
    LaunchedEffect(index) { if (index > 1) state.scrollToItem(index - 1) }
    Column(modifier.width(360.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(current?.groupName.orEmpty(), color = p.textPrimary, style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.group_info, items.size), color = p.textSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 10.dp, bottom = 2.dp))
        }
        LazyColumn(state = state, modifier = Modifier.padding(top = 10.dp).heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(items, key = { _, it -> it.channel.id }) { _, item ->
                val c = item.channel
                val playing = c.id == current?.id
                FocusSurface(
                    onClick = { onPlay(c.id) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(10.dp),
                    container = if (playing) p.accent.copy(alpha = 0.22f) else Color.Transparent, focusFill = true,
                    contentDescription = listOfNotNull(c.number?.toString(), c.name, item.nowTitle).joinToString(", "),
                ) { focused ->
                    val secondary = if (focused) p.onFocusFill.copy(alpha = 0.65f) else p.textSecondary
                    val bar = p.accent
                    Row(
                        Modifier
                            .then(if (playing && !focused) Modifier.drawBehind { drawRect(bar, size = Size(3.dp.toPx(), size.height)) } else Modifier)
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(c.number?.toString().orEmpty(), color = secondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(40.dp))
                        ChannelLogo(c.logoUrl, c.name, 34.dp, corner = 7.dp)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(c.name, color = LocalContentColor.current, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (playing) stringResource(R.string.now_watching) else item.nowTitle.orEmpty(),
                                color = secondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun InfoBar(
    channel: ChannelEntity?,
    titleOverride: String?,
    program: OverlayProgram?,
    player: Player?,
    isFavorite: Boolean,
    catchup: CatchupWindow?,
    sleepLeftMs: Long?,
    canStartOver: Boolean,
    primaryFocus: FocusRequester,
    onTogglePause: () -> Unit,
    onStartOver: () -> Unit,
    onGoLive: () -> Unit,
    onOpenPanel: (Set<PanelSection>) -> Unit,
    onToggleFavorite: (() -> Unit)?,
    modifier: Modifier,
) {
    val p = SdTheme.palette
    val now by rememberNow(15_000L)
    val quality = rememberVideoLabel(player)
    val playing = rememberIsPlaying(player)
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            channel?.let { ChannelLogo(it.logoUrl, it.name, 56.dp, corner = 12.dp) }
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(
                    titleOverride ?: program?.title ?: channel?.name.orEmpty(), color = p.textPrimary, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (titleOverride == null && channel != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Text(listOfNotNull(channel.number?.toString(), channel.name).joinToString(" "), color = p.textSecondary, style = MaterialTheme.typography.bodyLarge)
                        if (catchup == null) {
                            LiveBadge(Modifier.padding(horizontal = 8.dp), small = true)
                        } else {
                            Text(stringResource(R.string.catchup_badge, formatClock(catchup.start)), color = p.accent, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 8.dp))
                        }
                        Text(listOfNotNull(quality, program?.category).joinToString(" · "), color = p.textSecondary, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            val nextProg = program
            nextProg?.nextTitle?.let { next ->
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 16.dp)) {
                    Text(nextProg?.nextStartMs?.let { stringResource(R.string.next_at, formatClock(it)) } ?: stringResource(R.string.next_label), color = p.textSecondary, style = MaterialTheme.typography.bodyMedium)
                    Text(next, color = p.textPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        ProgressSection(program, player, now)
        sleepLeftMs?.let { Text(stringResource(R.string.sleep_remaining, formatDuration(it)), color = p.textSecondary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp)) }
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TvAction(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, stringResource(if (playing) R.string.pause else R.string.play), Modifier.focusRequester(primaryFocus), onTogglePause)
            when {
                catchup != null -> TvAction(Icons.Default.LiveTv, stringResource(R.string.go_live), onClick = onGoLive)
                canStartOver -> TvAction(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.start_over), onClick = onStartOver)
            }
            TvAction(Icons.Default.Subtitles, stringResource(R.string.subtitles)) { onOpenPanel(setOf(PanelSection.TEXT)) }
            TvAction(Icons.Default.GraphicEq, stringResource(R.string.audio_track)) { onOpenPanel(setOf(PanelSection.AUDIO, PanelSection.VIDEO)) }
            TvAction(Icons.Default.AspectRatio, stringResource(R.string.aspect)) { onOpenPanel(setOf(PanelSection.ASPECT)) }
            onToggleFavorite?.let { toggle ->
                TvAction(if (isFavorite) Icons.Default.Star else Icons.Outlined.StarOutline, stringResource(if (isFavorite) R.string.unfavorite else R.string.favorite), onClick = toggle)
            }
        }
    }
}

@Composable
private fun ProgressSection(program: OverlayProgram?, player: Player?, now: Long) {
    val p = SdTheme.palette
    val vod = player != null && !player.isCurrentMediaItemLive && player.isCurrentMediaItemSeekable && program == null
    if (vod && player != null) {
        val pos by produceState(player.currentPosition, player) {
            while (true) {
                value = player.currentPosition
                delay(1_000)
            }
        }
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: return
        SdProgress(pos.toFloat() / duration, Modifier.padding(top = 18.dp), height = 4.dp)
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(formatDuration(pos), color = p.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Text(formatDuration(duration), color = p.textSecondary, style = MaterialTheme.typography.bodySmall)
        }
        return
    }
    val prog = program ?: return
    SdProgress(ProgramTime.progress(prog.startMs, prog.endMs, now), Modifier.padding(top = 18.dp), height = 4.dp)
    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text(formatClock(prog.startMs), color = p.textSecondary, style = MaterialTheme.typography.bodySmall)
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            ProgramTime.minutesLeft(prog.endMs, now)?.let { Text(stringResource(DR.string.ds_minutes_left, it), color = p.textSecondary, style = MaterialTheme.typography.bodySmall) }
        }
        Text(formatClock(prog.endMs), color = p.textSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TvAction(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(10.dp), container = SdTheme.palette.cardHigh, focusFill = true, contentDescription = label) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = LocalContentColor.current, modifier = Modifier.padding(end = 6.dp))
            Text(label, color = LocalContentColor.current, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}
