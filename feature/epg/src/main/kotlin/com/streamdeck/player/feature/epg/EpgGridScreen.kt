package com.streamdeck.player.feature.epg

import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.streamdeck.player.core.data.Program
import com.streamdeck.player.core.database.GridChannel
import com.streamdeck.player.core.designsystem.ChannelLogo
import com.streamdeck.player.core.designsystem.EmptyState
import com.streamdeck.player.core.designsystem.FocusSurface
import com.streamdeck.player.core.designsystem.KeyHint
import com.streamdeck.player.core.designsystem.LoadingState
import com.streamdeck.player.core.designsystem.ProgramTime
import com.streamdeck.player.core.designsystem.SdChip
import com.streamdeck.player.core.designsystem.SdTheme
import com.streamdeck.player.core.designsystem.backdropGlow
import com.streamdeck.player.core.designsystem.formatClock
import com.streamdeck.player.core.designsystem.onFocusGained
import com.streamdeck.player.core.designsystem.rememberIsTv
import com.streamdeck.player.core.designsystem.rememberNow
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import com.streamdeck.player.core.designsystem.R as DR

private const val SLOT_MIN = 30
private const val HOURS_BEFORE_NOW = 3
private const val EXTRA_HOURS = 3
private const val MINUTE_MS = 60_000L
private const val HOUR_MS = 60 * MINUTE_MS

private data class Selected(val row: GridChannel, val program: Program)

/** Geometry differs between phone and TV; everything else is shared. */
private data class GridMetrics(val dpPerMinute: Dp, val rowHeight: Dp, val channelCol: Dp, val gap: Dp)

private val PhoneMetrics = GridMetrics(4.dp, 50.dp, 112.dp, 5.dp)
private val TvMetrics = GridMetrics(6.dp, 60.dp, 184.dp, 8.dp)

/**
 * Grid EPG (spec §4.4, design 3). Time runs horizontally, channels vertically; one [ScrollState] is shared by the
 * header and every row so they scroll together, and focusing a programme scrolls it into view (TV D-pad).
 * TV adds the preview header and remote hints: OK = watch / catch-up, long-press OK (or MENU) = details/reminder.
 */
@Composable
fun EpgGridScreen(
    onPlay: (channelId: Long) -> Unit,
    onCatchup: (channelId: Long, startMs: Long, endMs: Long) -> Unit,
    onOpenChannel: (channelId: Long) -> Unit,
    bottomPadding: Dp = 110.dp,
    vm: EpgGridViewModel = hiltViewModel(),
) {
    val tv = rememberIsTv()
    val m = if (tv) TvMetrics else PhoneMetrics
    val rows by vm.rows.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val group by vm.selectedGroup.collectAsStateWithLifecycle()
    val refresh by vm.refresh.collectAsStateWithLifecycle()
    val hasSource by vm.hasEpgSource.collectAsStateWithLifecycle()
    val reminders by vm.upcoming.collectAsStateWithLifecycle()
    val day by vm.dayOffset.collectAsStateWithLifecycle()
    val keepDays by vm.keepDays.collectAsStateWithLifecycle()
    val p = SdTheme.palette

    val (windowStart, windowEnd) = remember(day) { window(day) }
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    var detail by remember { mutableStateOf<Selected?>(null) }
    var preview by remember { mutableStateOf<Selected?>(null) }
    val firstLive = remember { FocusRequester() }

    LaunchedEffect(windowStart) {
        val now = System.currentTimeMillis()
        val minutes = if (now in windowStart until windowEnd) (now - windowStart) / MINUTE_MS - 20 else 0
        scroll.scrollTo(with(density) { (m.dpPerMinute * minutes.toFloat()).roundToPx() }.coerceAtLeast(0))
    }

    val activate: (GridChannel, Program) -> Unit = { row, prog ->
        val now = System.currentTimeMillis()
        when {
            tv && prog.isLive(now) -> onPlay(row.id)
            tv && catchupAvailable(row.catchupType, row.catchupDays, prog, now) -> onCatchup(row.id, prog.start, prog.end)
            else -> detail = Selected(row, prog)
        }
    }

    Column(
        Modifier.fillMaxSize().background(p.background)
            .then(if (tv) Modifier.padding(start = 24.dp, top = 24.dp, end = 32.dp, bottom = 16.dp) else Modifier.windowInsetsPadding(WindowInsets.statusBars)),
    ) {
        if (tv) {
            Preview(preview)
        } else {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.epg_title), style = MaterialTheme.typography.headlineSmall, color = p.textPrimary, modifier = Modifier.weight(1f))
                FocusSurface(onClick = vm::refreshEpg, shape = RoundedCornerShape(50), container = Color.Transparent, contentDescription = stringResource(R.string.update_epg)) {
                    Icon(Icons.Default.Refresh, null, tint = p.textSecondary, modifier = Modifier.padding(10.dp))
                }
            }
        }
        RefreshLine(refresh, vm::dismissError)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = if (tv) 0.dp else 16.dp, vertical = if (tv) 12.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { SdChip(stringResource(R.string.all), group == null, { vm.selectedGroup.value = null }, compact = true) }
                items(groups, key = { it.id }) { g -> SdChip(g.name, group == g.id, { vm.selectedGroup.value = g.id }, compact = true) }
            }
            DayPicker(day, keepDays) { vm.dayOffset.value = it }
        }
        val list = rows
        when {
            list == null -> LoadingState()
            !hasSource -> EmptyState(stringResource(R.string.no_epg_source))
            list.isEmpty() -> EmptyState(stringResource(R.string.no_channels))
            else -> {
                TimeHeader(windowStart, windowEnd, scroll, m, if (tv) 0.dp else 12.dp)
                LazyColumn(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = if (tv) 0.dp else 12.dp, end = if (tv) 0.dp else 12.dp, bottom = if (tv) 8.dp else bottomPadding),
                    verticalArrangement = Arrangement.spacedBy(m.gap),
                ) {
                    itemsIndexed(list, key = { _, r -> r.id }, contentType = { _, _ -> "row" }) { i, row ->
                        GridRow(
                            row = row, vm = vm, start = windowStart, end = windowEnd, scroll = scroll, m = m, tv = tv,
                            liveFocus = if (tv && i == 0) firstLive else null,
                            onOpenChannel = onOpenChannel,
                            onFocus = { prog -> preview = Selected(row, prog) },
                            onClick = { prog -> activate(row, prog) },
                            onLongClick = { prog -> detail = Selected(row, prog) },
                        )
                    }
                }
                if (tv) {
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.End)) {
                        KeyHint(stringResource(R.string.key_ok), stringResource(R.string.hint_watch))
                        KeyHint(stringResource(R.string.key_long_ok), stringResource(R.string.hint_remind_catchup))
                        KeyHint("◀ ▶", stringResource(R.string.hint_switch_time))
                    }
                }
            }
        }
    }

    detail?.let { (row, prog) ->
        val has = reminders.any { it.playlistId == row.playlistId && it.uniqueKey == row.uniqueKey && it.startAt == prog.start }
        ProgramDetailDialog(
            program = prog,
            channelName = row.name,
            catchupAvailable = catchupAvailable(row.catchupType, row.catchupDays, prog),
            hasReminder = has,
            onWatch = { detail = null; onPlay(row.id) },
            onCatchup = { detail = null; onCatchup(row.id, prog.start, prog.end) },
            onToggleReminder = { auto -> vm.toggleReminder(row.playlistId, row.uniqueKey, row.name, prog, auto); detail = null },
            onDismiss = { detail = null },
        )
    }
}

/** [start, end) of the grid for a day offset; today starts a few hours before now. */
private fun window(day: Int): Pair<Long, Long> {
    val zone = ZoneId.systemDefault()
    val dayStart = LocalDate.now(zone).plusDays(day.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
    val slot = SLOT_MIN * MINUTE_MS
    val start = if (day == 0) maxOf(dayStart, (System.currentTimeMillis() - HOURS_BEFORE_NOW * HOUR_MS) / slot * slot) else dayStart
    return start to dayStart + (24 + EXTRA_HOURS) * HOUR_MS
}

@Composable
private fun RefreshLine(refresh: EpgRefreshState, onDismiss: () -> Unit) {
    val p = SdTheme.palette
    when (refresh) {
        is EpgRefreshState.Running -> Column(Modifier.padding(horizontal = 20.dp)) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = p.accent, trackColor = p.track)
            Text(stringResource(R.string.epg_updating, refresh.count), color = p.textSecondary, style = MaterialTheme.typography.labelSmall)
        }
        is EpgRefreshState.Failed -> Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.epg_failed, errorText(refresh.reason)), color = p.live, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text(stringResource(DR.string.ds_ok), color = p.accent) }
        }
        EpgRefreshState.Idle -> Unit
    }
}

@Composable
private fun dayLabel(offset: Int): String {
    val ctx = LocalContext.current
    val date = LocalDate.now().plusDays(offset.toLong())
    val pattern = remember(ctx) { DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMd") }
    val ms = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val md = DateFormat.format(pattern, ms).toString()
    val prefix = when (offset) {
        -1 -> stringResource(R.string.yesterday)
        0 -> stringResource(R.string.today)
        1 -> stringResource(R.string.tomorrow)
        else -> DateFormat.format("EEE", ms).toString()
    }
    return "$prefix $md"
}

@Composable
private fun DayPicker(day: Int, keepDays: Int, onPick: (Int) -> Unit) {
    val p = SdTheme.palette
    var open by remember { mutableStateOf(false) }
    Box(Modifier.padding(start = 12.dp)) {
        FocusSurface(onClick = { open = true }, shape = RoundedCornerShape(10.dp), container = p.card, focusFill = true, contentDescription = stringResource(R.string.pick_day)) {
            Row(Modifier.padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(dayLabel(day), color = LocalContentColor.current, style = MaterialTheme.typography.labelLarge)
                Icon(Icons.Default.ArrowDropDown, null, tint = LocalContentColor.current)
            }
        }
        DropdownMenu(open, { open = false }, containerColor = p.cardHigh) {
            (-1..keepDays.coerceAtLeast(1)).forEach { d ->
                DropdownMenuItem(
                    text = { Text(dayLabel(d)) },
                    trailingIcon = { if (d == day) Icon(Icons.Default.Check, null, tint = p.accent) },
                    onClick = { open = false; onPick(d) },
                )
            }
        }
    }
}

/** TV preview of the focused programme (design 3 top area). */
@Composable
private fun Preview(sel: Selected?) {
    val p = SdTheme.palette
    val now by rememberNow()
    Row(Modifier.fillMaxWidth().height(178.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.width(300.dp).fillMaxHeight().background(p.card, RoundedCornerShape(16.dp)).clipToBounds()
                .backdropGlow(p.cardHigh),
            contentAlignment = Alignment.Center,
        ) {
            sel?.let { ChannelLogo(it.row.logoUrl, it.row.name, 64.dp) }
        }
        Column(Modifier.weight(1f).padding(start = 28.dp)) {
            if (sel == null) {
                Text(stringResource(R.string.epg_title), style = MaterialTheme.typography.headlineMedium, color = p.textPrimary)
                return@Column
            }
            val prog = sel.program
            val status = when {
                prog.isLive(now) -> stringResource(R.string.on_air)
                prog.isPast(now) -> stringResource(R.string.aired)
                else -> stringResource(R.string.upcoming)
            }
            Text("$status · ${formatClock(prog.start)} – ${formatClock(prog.end)}", color = p.accent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(prog.title.ifBlank { stringResource(R.string.untitled) }, color = p.textPrimary, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
            Text(listOfNotNull(sel.row.name, prog.category).joinToString(" · "), color = p.textSecondary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 6.dp))
            prog.description?.let { Text(it, color = p.textSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp)) }
        }
    }
}

@Composable
private fun TimeHeader(start: Long, end: Long, scroll: ScrollState, m: GridMetrics, startPad: Dp) {
    val p = SdTheme.palette
    val now by rememberNow()
    Row(Modifier.fillMaxWidth().height(30.dp).padding(start = startPad)) {
        Box(Modifier.width(m.channelCol + m.gap))
        Box(Modifier.horizontalScroll(scroll).fillMaxHeight()) {
            Row {
                var t = start
                while (t < end) {
                    Text(
                        formatClock(t), style = MaterialTheme.typography.labelMedium, color = p.textSecondary,
                        modifier = Modifier.width(m.dpPerMinute * SLOT_MIN).padding(start = 2.dp),
                    )
                    t += SLOT_MIN * MINUTE_MS
                }
            }
            if (now in start until end) {
                val x = m.dpPerMinute * ((now - start) / MINUTE_MS).toInt()
                Text(
                    formatClock(now), color = p.onLive, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                    modifier = Modifier.offset(x = x - 18.dp).align(Alignment.BottomStart).background(p.live, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp),
                )
            }
        }
    }
}

@Suppress("LongParameterList")
@Composable
private fun GridRow(
    row: GridChannel,
    vm: EpgGridViewModel,
    start: Long,
    end: Long,
    scroll: ScrollState,
    m: GridMetrics,
    tv: Boolean,
    liveFocus: FocusRequester?,
    onOpenChannel: (Long) -> Unit,
    onFocus: (Program) -> Unit,
    onClick: (Program) -> Unit,
    onLongClick: (Program) -> Unit,
) {
    val p = SdTheme.palette
    val programs by remember(row.id, start, end) { vm.programs(row, start, end) }.collectAsStateWithLifecycle(emptyList())
    val now by rememberNow()
    // Only compose the programmes around the visible part of the 27 h strip (re-evaluated per scrolled hour,
    // not per frame) — composing every cell of every row was what made the grid stutter.
    val density = LocalDensity.current
    val pxPerHour = with(density) { (m.dpPerMinute * 60).toPx() }.coerceAtLeast(1f)
    val viewHours = (LocalConfiguration.current.screenWidthDp / (m.dpPerMinute.value * 60f * 0.85f)).toInt() + 1
    val hour by remember(scroll, pxPerHour) { derivedStateOf { (scroll.value / pxPerHour).toInt() } }
    val visible = remember(programs, hour, start, viewHours) {
        val from = start + (hour - 1) * HOUR_MS
        val to = start + (hour + viewHours + 2) * HOUR_MS
        programs.filter { it.end > from && it.start < to }
    }
    Row(Modifier.fillMaxWidth().height(m.rowHeight)) {
        FocusSurface(
            onClick = { onOpenChannel(row.id) },
            modifier = Modifier.width(m.channelCol).fillMaxHeight(),
            shape = RoundedCornerShape(12.dp),
            focusFill = true,
            contentDescription = row.name,
        ) { focused ->
            Row(Modifier.fillMaxSize().padding(horizontal = if (tv) 10.dp else 8.dp), verticalAlignment = Alignment.CenterVertically) {
                ChannelLogo(row.logoUrl, row.name, if (tv) 36.dp else 30.dp, corner = 8.dp)
                Column(Modifier.padding(start = 8.dp)) {
                    Text(row.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = LocalContentColor.current)
                    row.number?.let { Text(it.toString(), style = MaterialTheme.typography.labelSmall, color = if (focused) p.onFocusFill.copy(alpha = 0.6f) else p.textTertiary) }
                }
            }
        }
        Box(Modifier.width(m.gap))
        Box(Modifier.weight(1f).fillMaxHeight().horizontalScroll(scroll)) {
            Box(Modifier.width(m.dpPerMinute * ((end - start) / MINUTE_MS).toInt()).fillMaxHeight())
            if (row.epgChannelId == null || programs.isEmpty()) {
                Box(Modifier.fillMaxHeight().width(m.dpPerMinute * 120).background(p.card, RoundedCornerShape(12.dp)), contentAlignment = Alignment.CenterStart) {
                    Text(stringResource(R.string.no_programme), style = MaterialTheme.typography.bodySmall, color = p.textTertiary, modifier = Modifier.padding(horizontal = 12.dp))
                }
            }
            var liveAssigned = false
            visible.forEach { prog ->
                val requester = if (liveFocus != null && !liveAssigned && prog.isLive(now)) { liveAssigned = true; liveFocus } else null
                androidx.compose.runtime.key(prog.start) { ProgramCell(prog, start, end, now, m, tv, requester, onFocus, onClick, onLongClick) }
            }
            if (now in start until end) {
                Box(Modifier.offset(x = m.dpPerMinute * ((now - start) / MINUTE_MS).toInt()).width(2.dp).fillMaxHeight().background(p.live))
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Suppress("LongParameterList")
@Composable
private fun ProgramCell(
    prog: Program,
    start: Long,
    end: Long,
    now: Long,
    m: GridMetrics,
    tv: Boolean,
    requester: FocusRequester?,
    onFocus: (Program) -> Unit,
    onClick: (Program) -> Unit,
    onLongClick: (Program) -> Unit,
) {
    val s = maxOf(prog.start, start)
    val e = minOf(prog.end, end)
    if (e <= s) return
    val p = SdTheme.palette
    val x = m.dpPerMinute * ((s - start) / MINUTE_MS).toInt()
    val w = (m.dpPerMinute * ((e - s) / MINUTE_MS).toInt() - 6.dp).coerceAtLeast(8.dp)
    val live = prog.isLive(now)
    val past = prog.isPast(now)
    if (requester != null) LaunchedEffect(requester) { runCatching { requester.requestFocus() } }
    FocusSurface(
        onClick = { onClick(prog) },
        onLongClick = { onLongClick(prog) },
        modifier = Modifier.offset(x = x).width(w).fillMaxHeight()
            .then(if (requester != null) Modifier.focusRequester(requester) else Modifier)
            .onFocusGained { onFocus(prog) }
            .onPreviewKeyEvent { ev ->
                if (tv && ev.type == KeyEventType.KeyUp && ev.key == Key.Menu) { onLongClick(prog); true } else false
            },
        shape = RoundedCornerShape(12.dp),
        container = if (live) p.cardHigh else p.card,
        focusFill = true,
        contentDescription = "${prog.title}, ${formatClock(prog.start)} – ${formatClock(prog.end)}",
    ) { focused ->
        val primary = when {
            focused -> p.onFocusFill
            past -> p.textTertiary
            else -> p.textPrimary
        }
        val secondary = if (focused) p.onFocusFill.copy(alpha = 0.65f) else p.textTertiary
        Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 4.dp), verticalArrangement = Arrangement.Center) {
            Text(prog.title.ifBlank { stringResource(R.string.untitled) }, color = primary, style = MaterialTheme.typography.bodyMedium, fontWeight = if (live) FontWeight.SemiBold else null, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val time = "${formatClock(prog.start)} – ${formatClock(prog.end)}"
            val left = if (focused && live) ProgramTime.minutesLeft(prog.end, now)?.let { stringResource(DR.string.ds_minutes_left, it) } else null
            Text(listOfNotNull(time, left).joinToString(" · "), color = secondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun errorText(reason: String): String = when (reason) {
    com.streamdeck.player.core.data.EpgRepository.NO_EPG -> stringResource(R.string.no_epg_source)
    else -> reason
}
