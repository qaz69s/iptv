package com.streamdeck.player.feature.epg

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.streamdeck.player.core.data.Program
import com.streamdeck.player.core.database.ChannelEntity
import com.streamdeck.player.core.database.ReminderEntity
import com.streamdeck.player.core.designsystem.ChannelLogo
import com.streamdeck.player.core.designsystem.EmptyState
import com.streamdeck.player.core.designsystem.FocusSurface
import com.streamdeck.player.core.designsystem.KeyHint
import com.streamdeck.player.core.designsystem.LiveBadge
import com.streamdeck.player.core.designsystem.LoadingState
import com.streamdeck.player.core.designsystem.OutlineTag
import com.streamdeck.player.core.designsystem.SdButton
import com.streamdeck.player.core.designsystem.SdButtonStyle
import com.streamdeck.player.core.designsystem.SdChip
import com.streamdeck.player.core.designsystem.SdDialog
import com.streamdeck.player.core.designsystem.SdIconButton
import com.streamdeck.player.core.designsystem.SdTextField
import com.streamdeck.player.core.designsystem.SdTheme
import com.streamdeck.player.core.designsystem.SdTopBar
import com.streamdeck.player.core.designsystem.formatClock
import com.streamdeck.player.core.designsystem.rememberIsTv
import com.streamdeck.player.core.designsystem.rememberNow
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/**
 * Programme list of one channel (spec §4.4): 今天 / 明天 / 一周 tabs and a timeline in the style of the phone player's
 * schedule. OK / tap = watch (airing) · catch-up (aired, if the channel supports it) · remind (upcoming);
 * long press = details (with “auto switch”). Phone: top bar + segmented tabs. TV: channel panel left, timeline right.
 */
@Composable
fun ChannelProgramsScreen(
    onBack: () -> Unit,
    onPlay: (channelId: Long) -> Unit,
    onCatchup: (channelId: Long, startMs: Long, endMs: Long) -> Unit,
    vm: ChannelProgramsViewModel = hiltViewModel(),
) {
    val channel by vm.channel.collectAsStateWithLifecycle()
    val programs by vm.programs.collectAsStateWithLifecycle()
    val range by vm.range.collectAsStateWithLifecycle()
    val reminders by vm.upcoming.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<Program?>(null) }
    var assigning by remember { mutableStateOf(false) }
    val now by rememberNow()
    val tv = rememberIsTv()
    val c = channel

    val primaryAction: (Program) -> Unit = { p ->
        if (c != null) {
            when {
                p.isLive(now) -> onPlay(c.id)
                p.isPast(now) && catchupAvailable(c.catchupType, c.catchupDays, p, now) -> onCatchup(c.id, p.start, p.end)
                p.start > now -> vm.toggleReminder(c.playlistId, c.uniqueKey, c.name, p, autoSwitch = false)
                else -> selected = p
            }
        }
    }
    val content = TimelineContent(programs, range, now, c, reminders, primaryAction) { selected = it }

    if (tv) {
        TvPrograms(c, content, onRange = { vm.range.value = it }, onPlay = { c?.let { onPlay(it.id) } }, onAssign = { assigning = true })
    } else {
        PhonePrograms(c, content, onBack, onRange = { vm.range.value = it }, onPlay = { c?.let { onPlay(it.id) } }, onAssign = { assigning = true })
    }

    selected?.let { p ->
        if (c != null) {
            ProgramDetailDialog(
                program = p, channelName = c.name,
                catchupAvailable = catchupAvailable(c.catchupType, c.catchupDays, p),
                hasReminder = reminders.hasReminder(c, p),
                onWatch = { selected = null; onPlay(c.id) },
                onCatchup = { selected = null; onCatchup(c.id, p.start, p.end) },
                onToggleReminder = { auto -> vm.toggleReminder(c.playlistId, c.uniqueKey, c.name, p, auto); selected = null },
                onDismiss = { selected = null },
            )
        }
    }
    if (assigning) AssignEpgDialog(vm) { assigning = false }
}

/** Everything the timeline needs, shared by both layouts. */
private class TimelineContent(
    val programs: List<Program>?,
    val range: DayRange,
    val now: Long,
    val channel: ChannelEntity?,
    val reminders: List<ReminderEntity>,
    val onPrimary: (Program) -> Unit,
    val onDetails: (Program) -> Unit,
)

private fun List<ReminderEntity>.hasReminder(c: ChannelEntity,
    p: Program) = any { it.playlistId == c.playlistId && it.uniqueKey == c.uniqueKey && it.startAt == p.start }

// ------------------------------------------------------------------------------------------- phone

@Composable
private fun PhonePrograms(c: ChannelEntity?, t: TimelineContent, onBack: () -> Unit, onRange: (DayRange) -> Unit, onPlay: () -> Unit, onAssign: () -> Unit) {
    val p = SdTheme.palette
    Column(Modifier.fillMaxSize()) {
        SdTopBar(
            title = c?.name.orEmpty(), onBack = onBack, subtitle = c?.let { channelMeta(it) },
        ) {
            SdIconButton(Icons.Default.Link, stringResource(R.string.assign_epg), onAssign)
            SdIconButton(Icons.Default.PlayArrow, stringResource(R.string.watch_live), onPlay, container = p.accent, tint = p.onAccent)
        }
        Segmented(t.range, onRange, Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) { Timeline(t, large = false) }
    }
}

@Composable
private fun Segmented(range: DayRange, onRange: (DayRange) -> Unit, modifier: Modifier = Modifier) {
    val p = SdTheme.palette
    Row(modifier.fillMaxWidth().background(p.card, RoundedCornerShape(16.dp)).padding(5.dp)) {
        DayRange.entries.forEach { r ->
            val label = stringResource(r.labelRes())
            val on = r == range
            FocusSurface(
                onClick = { onRange(r) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                container = if (on) p.cardHigh else Color.Transparent, contentDescription = label,
            ) {
                Text(
                    label, color = if (on) p.textPrimary else p.textSecondary, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.align(Alignment.Center).padding(vertical = 11.dp),
                )
            }
        }
    }
}

// ------------------------------------------------------------------------------------------- TV

@Composable
private fun TvPrograms(c: ChannelEntity?, t: TimelineContent, onRange: (DayRange) -> Unit, onPlay: () -> Unit, onAssign: () -> Unit) {
    val p = SdTheme.palette
    val watch = remember { FocusRequester() }
    Row(Modifier.fillMaxSize().padding(start = 56.dp, end = 48.dp, top = 40.dp, bottom = 28.dp)) {
        Column(
            Modifier.width(380.dp).fillMaxHeight().background(p.card, RoundedCornerShape(28.dp)).border(1.dp, p.stroke,
                RoundedCornerShape(28.dp)).padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (c != null) {
                ChannelLogo(c.logoUrl, c.name, 96.dp, corner = 22.dp)
                Text(c.name, style = MaterialTheme.typography.headlineMedium, color = p.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(channelMeta(c), style = MaterialTheme.typography.titleMedium, color = p.textSecondary, maxLines = 1)
                t.programs?.firstOrNull { it.isLive(t.now) }?.let { live ->
                    Column(Modifier.fillMaxWidth().background(p.cardHigh, RoundedCornerShape(16.dp)).padding(14.dp)) {
                        Text(stringResource(R.string.on_air), color = p.accent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        Text(live.title, color = p.textPrimary, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("${formatClock(live.start)} – ${formatClock(live.end)}", color = p.textTertiary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Box(Modifier.weight(1f))
                SdButton(stringResource(R.string.watch_live), onPlay, Modifier.fillMaxWidth().focusRequester(watch), icon = Icons.Default.PlayArrow,
                    large = true)
                SdButton(stringResource(R.string.assign_epg), onAssign, Modifier.fillMaxWidth(), icon = Icons.Default.Link, style = SdButtonStyle.Secondary,
                    large = true)
            }
        }
        Column(Modifier.padding(start = 32.dp).weight(1f).fillMaxHeight()) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DayRange.entries.forEach { r -> SdChip(stringResource(r.labelRes()), selected = r == t.range, onClick = { onRange(r) }) }
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(top = 14.dp)) { Timeline(t, large = true) }
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                KeyHint(stringResource(R.string.key_ok), stringResource(R.string.hint_primary_action))
                KeyHint(stringResource(R.string.key_long_ok), stringResource(R.string.hint_details))
            }
        }
    }
    LaunchedEffect(c != null) { if (c != null) runCatching { watch.requestFocus() } }
}

// ------------------------------------------------------------------------------------------- timeline

@Composable
private fun Timeline(t: TimelineContent, large: Boolean) {
    val list = t.programs
    when {
        list == null -> LoadingState()
        list.isEmpty() -> EmptyState(stringResource(R.string.no_programme_hint), title = stringResource(R.string.no_programme), icon = Icons.Outlined.EventBusy)
        else -> {
            val state = rememberLazyListState()
            val zone = remember { ZoneId.systemDefault() }
            val grouped = remember(list, t.range) {
                if (t.range == DayRange.WEEK) list.groupBy { Instant.ofEpochMilli(it.start).atZone(zone).toLocalDate() } else mapOf(LocalDate.now(zone) to list)
            }
            // Jump to the airing programme (or the first upcoming one) when the list or the range changes.
            LaunchedEffect(list, t.range) {
                var index = 0
                var target = -1
                grouped.forEach { (_, dayItems) ->
                    if (t.range == DayRange.WEEK) index++
                    dayItems.forEach { p ->
                        if (target < 0 && p.end > t.now) target = index
                        index++
                    }
                }
                if (target > 0) state.scrollToItem((target - 1).coerceAtLeast(0))
            }
            LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp)) {
                grouped.forEach { (day, dayItems) ->
                    if (t.range == DayRange.WEEK) dayHeader(day, large)
                    items(dayItems, key = { it.id }) { prog -> TimelineRow(prog, t, large) }
                }
            }
        }
    }
}

private fun LazyListScope.dayHeader(day: LocalDate, large: Boolean) {
    item(key = "day-$day") {
        val ctx = LocalContext.current
        val zone = ZoneId.systemDefault()
        val label = DateFormat.getLongDateFormat(ctx).format(Date(day.atStartOfDay(zone).toInstant().toEpochMilli()))
        val today = LocalDate.now(zone)
        val prefix = when (day) {
            today -> stringResource(R.string.today) + " · "
            today.plusDays(1) -> stringResource(R.string.tomorrow) + " · "
            today.minusDays(1) -> stringResource(R.string.yesterday) + " · "
            else -> ""
        }
        Text(
            prefix + label, color = SdTheme.palette.textTertiary,
            style = if (large) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 6.dp),
        )
    }
}

@Composable
private fun TimelineRow(prog: Program, t: TimelineContent, large: Boolean) {
    val p = SdTheme.palette
    val c = t.channel
    val live = prog.isLive(t.now)
    val past = prog.isPast(t.now)
    val catchupOk = c != null && past && catchupAvailable(c.catchupType, c.catchupDays, prog, t.now)
    val reminded = c != null && !past && !live && t.reminders.hasReminder(c, prog)
    FocusSurface(
        onClick = { t.onPrimary(prog) }, onLongClick = { t.onDetails(prog) },
        modifier = Modifier.fillMaxWidth().padding(horizontal = if (large) 4.dp else 0.dp, vertical = if (large) 3.dp else 0.dp),
        shape = RoundedCornerShape(if (large) 16.dp else 0.dp), container = Color.Transparent, focusFill = large,
        contentDescription = "${formatClock(prog.start)} ${prog.title}",
    ) { focused ->
        val fgPrimary = if (focused && large) p.onFocusFill else p.textPrimary
        val fgMuted = if (focused && large) p.onFocusFill.copy(alpha = 0.7f) else p.textTertiary
        Row(
            Modifier.fillMaxWidth()
                .background(if (live && !focused) Brush.horizontalGradient(listOf(p.accent.copy(alpha = 0.18f),
                    Color.Transparent)) else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)))
                .heightIn(min = if (large) 72.dp else 64.dp)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                formatClock(prog.start), color = if (live && !focused) p.accent else fgMuted,
                style = if (large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                modifier = Modifier.width(if (large) 80.dp else 64.dp),
            )
            Box(
                Modifier.size(14.dp).background(if (live) p.accent.copy(alpha = 0.3f) else Color.Transparent, CircleShape).padding(3.dp)
                    .background(if (live) p.accent else p.textTertiary, CircleShape),
            )
            Column(Modifier.weight(1f).padding(start = 16.dp, end = 10.dp)) {
                Text(
                    prog.title.ifBlank { stringResource(R.string.untitled) }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (past && !focused) p.textSecondary else fgPrimary, fontWeight = if (live) FontWeight.Bold else FontWeight.Normal,
                    style = if (large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                )
                Text(
                    if (live) stringResource(R.string.live_now_left,
                        ((prog.end - t.now) / 60_000L).toInt().coerceAtLeast(1)) else durationText(prog.end - prog.start),
                    color = fgMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                )
            }
            when {
                live -> LiveBadge(small = !large)
                catchupOk -> OutlineTag(stringResource(R.string.tag_catchup), color = if (focused && large) p.onFocusFill else p.accent)
                reminded -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.NotificationsActive, null, tint = if (focused && large) p.onFocusFill else p.hueAmber, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.tag_reminded), color = if (focused && large) p.onFocusFill else p.hueAmber,
                        style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 4.dp))
                }
                !past -> OutlineTag(stringResource(R.string.tag_remind), color = if (focused && large) p.onFocusFill else p.textTertiary)
            }
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
private fun channelMeta(c: ChannelEntity): String = listOfNotNull(c.number?.toString(), c.groupName.takeIf { it.isNotBlank() }).joinToString(" · ")

private fun DayRange.labelRes() = when (this) {
    DayRange.TODAY -> R.string.today
    DayRange.TOMORROW -> R.string.tomorrow
    DayRange.WEEK -> R.string.week
}

// ------------------------------------------------------------------------------------------- manual EPG mapping

@Composable
private fun AssignEpgDialog(vm: ChannelProgramsViewModel, onDismiss: () -> Unit) {
    var q by remember { mutableStateOf(vm.channel.value?.name.orEmpty()) }
    val choices by vm.epgChoices.collectAsStateWithLifecycle()
    val p = SdTheme.palette
    val tv = rememberIsTv()
    LaunchedEffect(q) { delay(300); vm.searchEpg(q) }
    SdDialog(onDismiss = onDismiss, title = stringResource(R.string.assign_epg), message = stringResource(R.string.assign_epg_hint)) {
        SdTextField(q, { q = it }, stringResource(R.string.search_epg), large = tv)
        if (choices.isEmpty()) {
            Text(stringResource(R.string.no_epg_matches), color = p.textTertiary, style = MaterialTheme.typography.bodyMedium)
        } else {
            LazyColumn(Modifier.heightIn(max = 320.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(choices, key = { it.id }) { e ->
                    FocusSurface(
                        onClick = { vm.assign(e); onDismiss() }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                        container = p.cardHigh, focusFill = true, contentDescription = e.displayName,
                    ) { focused ->
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text(e.displayName, color = if (focused) p.onFocusFill else p.textPrimary, style = MaterialTheme.typography.bodyLarge)
                            Text(e.id, color = if (focused) p.onFocusFill.copy(alpha = 0.7f) else p.textTertiary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            SdButton(stringResource(R.string.close), onDismiss, style = SdButtonStyle.Secondary)
        }
    }
}
