@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.streamdeck.player.feature.playerui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.streamdeck.player.core.data.Program
import com.streamdeck.player.core.database.ChannelEntity
import com.streamdeck.player.core.database.ChannelListItem
import com.streamdeck.player.core.datastore.AspectMode
import com.streamdeck.player.core.datastore.SubtitleStyle
import com.streamdeck.player.core.designsystem.ChannelLogo
import com.streamdeck.player.core.designsystem.FocusSurface
import com.streamdeck.player.core.designsystem.LiveBadge
import com.streamdeck.player.core.designsystem.OutlineTag
import com.streamdeck.player.core.designsystem.ProgramTime
import com.streamdeck.player.core.designsystem.SdProgress
import com.streamdeck.player.core.designsystem.SdTheme
import com.streamdeck.player.core.designsystem.formatClock
import com.streamdeck.player.core.designsystem.rememberNow
import com.streamdeck.player.core.player.PlaybackState
import com.streamdeck.player.core.designsystem.R as DR

/**
 * Portrait phone player (design 6): 16:9 video with its own controls, then channel info, the “正在播出” card,
 * round quick actions and a segmented list (today's programmes / same-group channels). Rotating or the
 * full-screen button switches to the immersive landscape player.
 */
@Suppress("LongParameterList")
@Composable
internal fun PhonePlayerPortrait(
    vm: PlayerViewModel,
    player: Player?,
    state: PlaybackState,
    channel: ChannelEntity?,
    program: OverlayProgram?,
    catchup: CatchupWindow?,
    favorite: Boolean,
    schedule: List<Program>,
    groupItems: List<ChannelListItem>,
    aspect: AspectMode,
    subtitleStyle: SubtitleStyle,
    controlsVisible: Boolean,
    hint: String?,
    gestures: Modifier,
    onBack: () -> Unit,
    castButton: @Composable () -> Unit,
    onOpenPanel: (Set<PanelSection>) -> Unit,
    onFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = SdTheme.palette
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val now by rememberNow()
    val liveIndex = schedule.indexOfFirst { it.isLive(now) }
    LaunchedEffect(liveIndex >= 0, tab) {
        if (tab == 0 && liveIndex >= 0) runCatching { listState.scrollToItem(HEADER_ITEMS + (liveIndex - 1).coerceAtLeast(0)) }
    }

    Column(modifier.fillMaxSize().background(p.background)) {
        Box(Modifier.fillMaxWidth().background(Color.Black).windowInsetsPadding(WindowInsets.statusBars)) {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).then(gestures)) {
                VideoSurface(player, aspect, subtitleStyle)
                VideoControls(controlsVisible, catchup, onBack, castButton, { onOpenPanel(PanelSection.entries.toSet()) }, onFullscreen, vm::goLive)
                StatusLayer(state, onRetry = vm.controller::retry)
                CenterHint(hint)
            }
        }
        LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 32.dp), modifier = Modifier.fillMaxSize()) {
            item(key = "info") { InfoRow(channel, player, favorite, vm::toggleFavorite) }
            item(key = "now") { program?.let { NowCard(it, now) } }
            item(key = "actions") { QuickActions(vm, player, program != null && catchup == null && vm.supportsCatchup, onOpenPanel) }
            item(key = "tabs") { Segmented(tab) { tab = it } }
            if (tab == 0) {
                if (schedule.isEmpty()) item(key = "empty") { EmptyLine(stringResource(R.string.no_schedule)) }
                items(schedule, key = { "p_${it.id}" }) { prog ->
                    ScheduleRow(prog, now, vm.canCatchup(prog, now), catchup?.start == prog.start) { vm.playProgram(prog) }
                }
            } else {
                if (groupItems.isEmpty()) item(key = "empty") { EmptyLine(stringResource(R.string.no_group_channels)) }
                items(groupItems, key = { "c_${it.channel.id}" }) { item ->
                    GroupChannelRow(item, item.channel.id == channel?.id, now) { vm.playChannel(item.channel.id) }
                }
            }
        }
    }
}

private const val HEADER_ITEMS = 4

@Composable
private fun VideoControls(
    visible: Boolean,
    catchup: CatchupWindow?,
    onBack: () -> Unit,
    castButton: @Composable () -> Unit,
    onSettings: () -> Unit,
    onFullscreen: () -> Unit,
    onGoLive: () -> Unit,
) {
    val p = SdTheme.palette
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(p.scrim, Color.Transparent, p.scrim)))) {
                Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    OverlayIcon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(DR.string.ds_back), onBack)
                    Box(Modifier.weight(1f))
                    castButton()
                    OverlayIcon(Icons.Default.Settings, stringResource(R.string.player_settings), onSettings)
                }
                OverlayIcon(Icons.Default.Fullscreen, stringResource(R.string.fullscreen), onFullscreen, Modifier.align(Alignment.BottomEnd))
            }
        }
        Row(Modifier.align(Alignment.BottomStart).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (catchup == null) {
                LiveBadge()
            } else {
                Text(
                    stringResource(R.string.catchup_badge, formatClock(catchup.start)), color = p.onAccent, style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.background(p.accent, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 2.dp),
                )
                FocusSurface(onClick = onGoLive, shape = RoundedCornerShape(6.dp), container = p.live, contentDescription = stringResource(R.string.go_live)) {
                    Text(stringResource(R.string.go_live), color = p.onLive, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
        }
    }
}

@Composable
private fun OverlayIcon(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = SdTheme.palette
    FocusSurface(onClick = onClick, modifier = modifier.padding(4.dp), shape = CircleShape, container = Color.Transparent, contentDescription = label) {
        Icon(icon, null, tint = p.onScrim, modifier = Modifier.padding(8.dp).size(20.dp))
    }
}

@Composable
private fun InfoRow(channel: ChannelEntity?, player: Player?, favorite: Boolean, onFavorite: () -> Unit) {
    val p = SdTheme.palette
    val c = channel ?: return
    val quality = rememberVideoLabel(player)
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        ChannelLogo(c.logoUrl, c.name, 48.dp, corner = 12.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(c.name, style = MaterialTheme.typography.titleLarge, color = p.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(c.number?.toString(), c.groupName.ifBlank { null }, quality).joinToString(" · "), color = p.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
        FocusSurface(
            onClick = onFavorite, shape = RoundedCornerShape(12.dp), container = p.card,
            contentDescription = stringResource(if (favorite) R.string.unfavorite else R.string.favorite),
        ) {
            Icon(if (favorite) Icons.Default.Star else Icons.Outlined.StarOutline, null, tint = if (favorite) p.star else p.textSecondary, modifier = Modifier.padding(9.dp).size(22.dp))
        }
    }
}

@Composable
private fun NowCard(prog: OverlayProgram, now: Long) {
    val p = SdTheme.palette
    Column(
        Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp).fillMaxWidth()
            .background(p.card, RoundedCornerShape(16.dp)).border(1.dp, p.stroke, RoundedCornerShape(16.dp)).padding(14.dp),
    ) {
        Text(stringResource(R.string.on_air), color = p.accent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Text(prog.title, color = p.textPrimary, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
        SdProgress(ProgramTime.progress(prog.startMs, prog.endMs, now), Modifier.padding(top = 10.dp))
        Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text(formatClock(prog.startMs), color = p.textSecondary, style = MaterialTheme.typography.bodySmall)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                ProgramTime.minutesLeft(prog.endMs, now)?.let { Text(stringResource(DR.string.ds_minutes_left, it), color = p.textSecondary, style = MaterialTheme.typography.bodySmall) }
            }
            Text(formatClock(prog.endMs), color = p.textSecondary, style = MaterialTheme.typography.bodySmall)
        }
        prog.nextTitle?.let { next ->
            Box(Modifier.fillMaxWidth().padding(vertical = 10.dp).height(0.5.dp).background(p.stroke))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(prog.nextStartMs?.let { stringResource(DR.string.ds_next_at, formatClock(it)) } ?: stringResource(R.string.next_label), color = p.textSecondary, style = MaterialTheme.typography.bodyMedium)
                Text(next, color = p.textPrimary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun QuickActions(vm: PlayerViewModel, player: Player?, canStartOver: Boolean, onOpenPanel: (Set<PanelSection>) -> Unit) {
    val playing = rememberIsPlaying(player)
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.SpaceAround) {
        RoundAction(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, stringResource(if (playing) R.string.pause else R.string.play), primary = true) { vm.controller.togglePause() }
        RoundAction(Icons.Default.FastRewind, stringResource(R.string.catchup_action), enabled = canStartOver) { vm.startOver() }
        RoundAction(Icons.Default.Subtitles, stringResource(R.string.subtitles)) { onOpenPanel(setOf(PanelSection.TEXT)) }
        RoundAction(Icons.Default.GraphicEq, stringResource(R.string.audio_track)) { onOpenPanel(setOf(PanelSection.AUDIO)) }
        RoundAction(Icons.Default.Hd, stringResource(R.string.quality_short)) { onOpenPanel(setOf(PanelSection.VIDEO, PanelSection.ASPECT)) }
    }
}

@Composable
private fun RoundAction(icon: ImageVector, label: String, primary: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val p = SdTheme.palette
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FocusSurface(
            onClick = onClick, enabled = enabled, shape = RoundedCornerShape(16.dp),
            container = if (primary) p.accent else p.card, modifier = Modifier.size(46.dp), contentDescription = label,
        ) {
            Icon(
                icon, null, modifier = Modifier.align(Alignment.Center).size(22.dp),
                tint = when {
                    primary -> p.onAccent
                    enabled -> p.textPrimary
                    else -> p.textTertiary
                },
            )
        }
        Text(label, color = p.textSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 5.dp))
    }
}

@Composable
private fun Segmented(selected: Int, onSelect: (Int) -> Unit) {
    val p = SdTheme.palette
    Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().background(p.card, RoundedCornerShape(12.dp)).padding(4.dp)) {
        listOf(R.string.tab_schedule, R.string.tab_group).forEachIndexed { i, res ->
            val label = stringResource(res)
            FocusSurface(
                onClick = { onSelect(i) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(9.dp),
                container = if (i == selected) p.cardHigh else Color.Transparent, contentDescription = label,
            ) {
                Text(
                    label, color = if (i == selected) p.textPrimary else p.textSecondary,
                    fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal,
                    style = MaterialTheme.typography.labelLarge, modifier = Modifier.align(Alignment.Center).padding(vertical = 8.dp),
                )
            }
        }
    }
    Box(Modifier.height(8.dp))
}

@Composable
private fun ScheduleRow(prog: Program, now: Long, catchupOk: Boolean, playingCatchup: Boolean, onClick: () -> Unit) {
    val p = SdTheme.palette
    val live = prog.isLive(now)
    val past = prog.isPast(now)
    val highlight = live || playingCatchup
    FocusSurface(
        onClick = onClick, enabled = live || catchupOk, modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp), container = Color.Transparent, contentDescription = "${formatClock(prog.start)} ${prog.title}",
    ) {
        Row(
            Modifier.fillMaxWidth()
                .background(if (highlight) Brush.horizontalGradient(listOf(p.accent.copy(alpha = 0.18f), Color.Transparent)) else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)))
                .padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(formatClock(prog.start), color = if (highlight) p.accent else p.textTertiary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(54.dp))
            Box(
                Modifier.size(12.dp).background(if (highlight) p.accent.copy(alpha = 0.3f) else Color.Transparent, CircleShape).padding(3.dp)
                    .background(if (highlight) p.accent else p.textTertiary, CircleShape),
            )
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    prog.title, color = if (past && !highlight) p.textSecondary else p.textPrimary,
                    fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal, style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (live) stringResource(R.string.live_now) else durationText(prog.end - prog.start),
                    color = p.textTertiary, style = MaterialTheme.typography.bodySmall,
                )
            }
            if (catchupOk && !playingCatchup) OutlineTag(stringResource(R.string.catchup_action))
        }
    }
}

@Composable
private fun durationText(ms: Long): String {
    val minutes = (ms / 60_000L).toInt().coerceAtLeast(1)
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> stringResource(R.string.duration_minutes, m)
        m == 0 -> stringResource(R.string.duration_hours, h)
        else -> stringResource(R.string.duration_h_m, h, m)
    }
}

@Composable
private fun GroupChannelRow(item: ChannelListItem, current: Boolean, now: Long, onClick: () -> Unit) {
    val p = SdTheme.palette
    val c = item.channel
    FocusSurface(
        onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(0.dp),
        container = if (current) p.accent.copy(alpha = 0.12f) else Color.Transparent, contentDescription = c.name,
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(c.number?.toString().orEmpty(), color = if (current) p.accent else p.textTertiary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(36.dp))
            ChannelLogo(c.logoUrl, c.name, 38.dp, corner = 9.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(c.name, color = p.textPrimary, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (current) stringResource(R.string.now_watching) else item.nowTitle.orEmpty(),
                    color = if (current) p.accent else p.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                if (item.nowStart != null) SdProgress(ProgramTime.progress(item.nowStart, item.nowEnd, now), Modifier.padding(top = 6.dp, end = 16.dp), height = 2.dp)
            }
        }
    }
}

@Composable
private fun EmptyLine(text: String) {
    Text(text, color = SdTheme.palette.textTertiary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp))
}
