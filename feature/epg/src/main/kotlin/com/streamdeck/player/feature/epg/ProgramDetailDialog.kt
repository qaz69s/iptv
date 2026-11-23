package com.streamdeck.player.feature.epg

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.SwapHoriz
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.streamdeck.player.core.data.Program
import com.streamdeck.player.core.designsystem.OutlineTag
import com.streamdeck.player.core.designsystem.SdButton
import com.streamdeck.player.core.designsystem.SdButtonStyle
import com.streamdeck.player.core.designsystem.SdDialog
import com.streamdeck.player.core.designsystem.SdTheme
import com.streamdeck.player.core.designsystem.SwitchRow
import com.streamdeck.player.core.designsystem.rememberIsTv
import java.util.Date
import com.streamdeck.player.core.designsystem.R as DR

/**
 * Programme details (spec §4.4) with context actions: watch live, catch-up for past programmes
 * (when the channel supports it) and reminders for future ones (optionally switching channel when it starts).
 */
@Composable
fun ProgramDetailDialog(
    program: Program,
    channelName: String,
    catchupAvailable: Boolean,
    hasReminder: Boolean,
    onWatch: () -> Unit,
    onCatchup: () -> Unit,
    onToggleReminder: (autoSwitch: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val ctx = LocalContext.current
    val now = System.currentTimeMillis()
    val date = DateFormat.getMediumDateFormat(ctx)
    val time = DateFormat.getTimeFormat(ctx)
    var autoSwitch by remember { mutableStateOf(false) }
    val p = SdTheme.palette
    val tv = rememberIsTv()
    val primary = remember { FocusRequester() }
    val status = when {
        program.isLive(now) -> stringResource(R.string.on_air)
        program.isPast(now) -> stringResource(R.string.aired)
        else -> stringResource(R.string.upcoming)
    }
    SdDialog(onDismiss = onDismiss, title = program.title.ifBlank { stringResource(R.string.untitled) }) {
        Column(Modifier.heightIn(max = if (tv) 380.dp else 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "$status · ${date.format(Date(program.start))}  ${time.format(Date(program.start))} – ${time.format(Date(program.end))}",
                color = p.accent, style = MaterialTheme.typography.labelLarge,
            )
            Text(channelName, color = p.textSecondary, style = MaterialTheme.typography.bodyMedium)
            program.category?.takeIf { it.isNotBlank() }?.let { OutlineTag(it, color = p.textSecondary) }
            program.iconUrl?.let {
                AsyncImage(
                    it, stringResource(R.string.poster), Modifier.fillMaxWidth().heightIn(max = 180.dp).clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
            program.description?.takeIf { it.isNotBlank() }?.let { Text(it, color = p.textPrimary, style = MaterialTheme.typography.bodyMedium) }
            if (program.start > now && !hasReminder) {
                SwitchRow(stringResource(R.string.auto_switch), autoSwitch, { autoSwitch = it }, icon = Icons.Outlined.SwapHoriz, tint = p.hueBlue)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
            SdButton(stringResource(DR.string.ds_back), onDismiss, style = SdButtonStyle.Secondary)
            when {
                program.isLive(now) -> SdButton(stringResource(R.string.watch_live), onWatch, Modifier.focusRequester(primary), icon = Icons.Default.PlayArrow)
                program.isPast(now) && catchupAvailable -> SdButton(stringResource(R.string.catchup), onCatchup, Modifier.focusRequester(primary),
                    icon = Icons.Default.History)
                program.start > now -> SdButton(
                    stringResource(if (hasReminder) R.string.cancel_reminder else R.string.remind_me), { onToggleReminder(autoSwitch) },
                    Modifier.focusRequester(primary), icon = if (hasReminder) Icons.Default.NotificationsOff else Icons.Default.NotificationsActive,
                )
            }
        }
        LaunchedEffect(Unit) { runCatching { primary.requestFocus() } }
    }
}

/** Catch-up is offered when the channel declares it and the programme lies within the archive window. */
fun catchupAvailable(catchupType: String?, catchupDays: Int?, p: Program, now: Long = System.currentTimeMillis()): Boolean {
    if (catchupType.isNullOrBlank() && (catchupDays ?: 0) <= 0) return false
    val days = (catchupDays ?: DEFAULT_ARCHIVE_DAYS).coerceAtLeast(1)
    return p.isPast(now) && p.start >= now - days * 86_400_000L
}

private const val DEFAULT_ARCHIVE_DAYS = 1
