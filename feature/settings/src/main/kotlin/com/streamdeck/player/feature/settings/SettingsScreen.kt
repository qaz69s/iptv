package com.streamdeck.player.feature.settings

import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.streamdeck.player.core.datastore.AspectMode
import com.streamdeck.player.core.datastore.BufferProfile
import com.streamdeck.player.core.datastore.DecoderMode
import com.streamdeck.player.core.datastore.LocaleStore
import com.streamdeck.player.core.datastore.ThemeMode
import com.streamdeck.player.core.designsystem.AccentColors
import com.streamdeck.player.core.designsystem.FocusSurface
import com.streamdeck.player.core.designsystem.SdSwitch
import com.streamdeck.player.core.designsystem.SdTheme
import com.streamdeck.player.core.designsystem.SettingsGroup
import com.streamdeck.player.core.designsystem.SettingsRow
import com.streamdeck.player.core.designsystem.findActivity
import com.streamdeck.player.core.designsystem.focusRing
import com.streamdeck.player.core.designsystem.rememberIsTv
import kotlin.math.roundToInt
import com.streamdeck.player.core.designsystem.R as DR

private data class Choice<T>(val value: T, val label: String)

private sealed interface Dialog {
    data class Pick(val title: String, val options: List<Choice<Any>>, val selected: Any, val onPick: (Any) -> Unit) : Dialog
    data object NewPin : Dialog
    data object Privacy : Dialog
}

private val RECONNECT_OPTIONS = listOf(0, 1, 2, 3, 5, 8, 10, 15, 20)
private val FONT_SCALES = listOf(0.8f, 0.9f, 1f, 1.1f, 1.2f, 1.3f, 1.4f)
private val OFFSET_STEPS = (-24..24).map { it * 30 }

/**
 * “我的” (design 2) — playlist card + grouped settings of spec §4.9, identical on phone and TV (rows are focusable).
 * Locked behind the PIN when “lock settings” is on.
 */
@Composable
fun SettingsScreen(onManagePlaylists: () -> Unit, bottomPadding: androidx.compose.ui.unit.Dp = 110.dp, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val hasPin by vm.hasPin.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val historyCount by vm.historyCount.collectAsStateWithLifecycle()
    val summary by vm.playlistSummary.collectAsStateWithLifecycle()
    val gate = rememberPinGate()
    var unlocked by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<Dialog?>(null) }
    val ctx = LocalContext.current
    val tv = rememberIsTv()
    val p = SdTheme.palette
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(vm::export) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::restore) }
    val pick: (String, List<Choice<Any>>, Any, (Any) -> Unit) -> Unit = { title, opts, sel, onPick -> dialog = Dialog.Pick(title, opts, sel, onPick) }

    Box(Modifier.fillMaxSize().background(p.background).then(if (tv) Modifier else Modifier.windowInsetsPadding(WindowInsets.statusBars))) {
        if (s.lockSettings && hasPin && !unlocked && !vm.parental.isUnlocked()) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Lock, null, tint = p.hueAmber, modifier = Modifier.size(44.dp))
                Text(stringResource(R.string.settings_locked), color = p.textPrimary, modifier = Modifier.padding(top = 12.dp))
                Button(
                    onClick = { gate.runUnlocked(true) { unlocked = true } }, modifier = Modifier.padding(16.dp).focusRing(RoundedCornerShape(50)),
                    colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.onAccent),
                ) { Text(stringResource(R.string.unlock)) }
            }
            gate.Host()
            return@Box
        }
        LazyColumn(
            Modifier.fillMaxSize().then(if (tv) Modifier.widthIn(max = 820.dp) else Modifier),
            contentPadding = PaddingValues(start = if (tv) 48.dp else 20.dp, end = 20.dp, top = if (tv) 32.dp else 16.dp, bottom = if (tv) 40.dp else bottomPadding),
        ) {
            item { Text(stringResource(R.string.mine_title), style = MaterialTheme.typography.headlineLarge, color = p.textPrimary, modifier = Modifier.padding(bottom = 20.dp)) }
            if (busy) item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 8.dp), color = p.accent, trackColor = p.track) }
            item { PlaylistCard(summary) { gate.runUnlocked(s.lockEditor) { onManagePlaylists() } } }

            item {
                SettingsGroup(stringResource(R.string.sec_playback)) {
                    val decoder = stringResource(R.string.decoder)
                    val decoderOpts = DecoderMode.entries.map { Choice<Any>(it, stringResource(it.label())) }
                    SettingsRow(decoder, icon = Icons.Default.PlayArrow, tint = p.hueGreen, value = stringResource(s.decoder.shortLabel())) {
                        pick(decoder, decoderOpts, s.decoder) { v -> vm.update { it.copy(decoder = v as DecoderMode) } }
                    }
                    val buffer = stringResource(R.string.buffer)
                    val bufferOpts = BufferProfile.entries.map { Choice<Any>(it, stringResource(it.label())) }
                    SettingsRow(buffer, icon = Icons.Default.Timelapse, tint = p.hueAmber, value = stringResource(s.buffer.label())) {
                        pick(buffer, bufferOpts, s.buffer) { v -> vm.update { it.copy(buffer = v as BufferProfile) } }
                    }
                    val aspect = stringResource(R.string.default_aspect)
                    val aspectOpts = AspectMode.entries.map { Choice<Any>(it, stringResource(it.label())) }
                    SettingsRow(aspect, icon = Icons.Default.AspectRatio, tint = p.hueBlue, value = stringResource(s.aspect.label())) {
                        pick(aspect, aspectOpts, s.aspect) { v -> vm.update { it.copy(aspect = v as AspectMode) } }
                    }
                    SettingsRow(stringResource(R.string.background_playback_short), icon = Icons.Default.MusicNote, tint = p.huePurple,
                        trailing = { SdSwitch(s.backgroundPlayback) }) { vm.update { it.copy(backgroundPlayback = !s.backgroundPlayback) } }
                    val reconnect = stringResource(R.string.reconnect_short)
                    SettingsRow(reconnect, icon = Icons.Default.Refresh, tint = p.hueRed, value = stringResource(R.string.times, s.reconnectMax)) {
                        pick(reconnect, RECONNECT_OPTIONS.map { Choice<Any>(it, ctx.getString(R.string.times, it)) }, s.reconnectMax) { v -> vm.update { it.copy(reconnectMax = v as Int) } }
                    }
                    SettingsRow(stringResource(R.string.auto_resume), icon = Icons.Default.History, tint = p.hueGreen,
                        trailing = { SdSwitch(s.autoResume) }) { vm.update { it.copy(autoResume = !s.autoResume) } }
                    SettingsRow(stringResource(R.string.autoplay_next), icon = Icons.Default.SkipNext, tint = p.hueBlue,
                        trailing = { SdSwitch(s.autoplayNext) }) { vm.update { it.copy(autoplayNext = !s.autoplayNext) } }
                    SettingsRow(stringResource(R.string.allow_metered), icon = Icons.Default.DataUsage, tint = p.hueAmber, hint = stringResource(R.string.allow_metered_hint), divider = false,
                        trailing = { SdSwitch(s.allowMetered) }) { vm.update { it.copy(allowMetered = !s.allowMetered) } }
                }
            }

            item {
                SettingsGroup(stringResource(R.string.sec_epg_guide)) {
                    val freq = stringResource(R.string.epg_auto_update)
                    SettingsRow(freq, icon = Icons.Default.Schedule, tint = p.hueGreen, value = stringResource(R.string.every_hours, s.epgUpdateHours)) {
                        pick(freq, listOf(6, 12, 24, 48).map { Choice<Any>(it, ctx.getString(R.string.every_hours, it)) }, s.epgUpdateHours) { v -> vm.update { it.copy(epgUpdateHours = v as Int) } }
                    }
                    val offset = stringResource(R.string.epg_offset_short)
                    SettingsRow(offset, icon = Icons.Default.Tune, tint = p.hueBlue, value = stringResource(R.string.hours_value, "%+.1f".format(s.epgOffsetMinutes / 60f))) {
                        pick(offset, OFFSET_STEPS.map { Choice<Any>(it, ctx.getString(R.string.hours_value, "%+.1f".format(it / 60f))) }, s.epgOffsetMinutes) { v -> vm.update { it.copy(epgOffsetMinutes = v as Int) } }
                    }
                    val keep = stringResource(R.string.epg_keep_short)
                    SettingsRow(keep, icon = Icons.Default.ViewList, tint = p.huePurple, value = stringResource(R.string.days_value, s.epgKeepDays)) {
                        pick(keep, (1..7).map { Choice<Any>(it, ctx.getString(R.string.days_value, it)) }, s.epgKeepDays) { v -> vm.update { it.copy(epgKeepDays = v as Int) } }
                    }
                    SettingsRow(stringResource(R.string.epg_update_now), icon = Icons.Default.Update, tint = p.hueAmber, divider = false) { vm.updateAllEpg() }
                }
            }

            item {
                SettingsGroup(stringResource(R.string.sec_security)) {
                    SettingsRow(stringResource(R.string.parental_lock), icon = Icons.Default.Lock, tint = p.hueAmber, hint = stringResource(R.string.pin_hint),
                        trailing = { SdSwitch(hasPin) }) { if (hasPin) gate.runUnlocked(true) { vm.clearPin() } else dialog = Dialog.NewPin }
                    if (hasPin) {
                        SettingsRow(stringResource(R.string.pin_change), icon = Icons.Default.Password, tint = p.hueAmber) { gate.runUnlocked(true) { dialog = Dialog.NewPin } }
                        SettingsRow(stringResource(R.string.lock_settings), icon = Icons.Default.Lock, tint = p.hueRed,
                            trailing = { SdSwitch(s.lockSettings) }) { gate.runUnlocked(true) { vm.update { it.copy(lockSettings = !s.lockSettings) } } }
                        SettingsRow(stringResource(R.string.lock_editor), icon = Icons.Default.EditNote, tint = p.hueRed, hint = stringResource(R.string.lock_channels_hint),
                            trailing = { SdSwitch(s.lockEditor) }) { gate.runUnlocked(true) { vm.update { it.copy(lockEditor = !s.lockEditor) } } }
                    }
                    SettingsRow(stringResource(R.string.backup_export), icon = Icons.Default.Backup, tint = p.textSecondary, hint = stringResource(R.string.backup_export_hint)) {
                        exportLauncher.launch("streamdeck-backup.json")
                    }
                    SettingsRow(stringResource(R.string.backup_import), icon = Icons.Default.Restore, tint = p.textSecondary) { importLauncher.launch(arrayOf("application/json", "*/*")) }
                    SettingsRow(stringResource(R.string.clear_history), icon = Icons.Default.DeleteSweep, tint = p.textSecondary, value = stringResource(R.string.history_count, historyCount), divider = false) { vm.clearHistory() }
                }
            }

            item {
                SettingsGroup(stringResource(R.string.sec_appearance)) {
                    val theme = stringResource(R.string.theme)
                    val themeOpts = ThemeMode.entries.map { Choice<Any>(it, stringResource(it.label())) }
                    SettingsRow(theme, icon = Icons.Default.DarkMode, tint = p.huePurple, value = stringResource(s.theme.label())) {
                        pick(theme, themeOpts, s.theme) { v -> vm.update { it.copy(theme = v as ThemeMode) } }
                    }
                    AccentRow(s.accentIndex) { i -> vm.update { it.copy(accentIndex = i) } }
                    val font = stringResource(R.string.font_size)
                    SettingsRow(font, icon = Icons.Default.FormatSize, tint = p.hueBlue, value = "${(s.fontScale * 100).roundToInt()}%") {
                        pick(font, FONT_SCALES.map { Choice<Any>(it, "${(it * 100).roundToInt()}%") }, s.fontScale) { v -> vm.update { it.copy(fontScale = v as Float) } }
                    }
                    LanguageRow(ctx) { dialog = it }
                }
            }

            item {
                SettingsGroup(stringResource(R.string.sec_about)) {
                    SettingsRow(stringResource(R.string.privacy_policy), icon = Icons.Default.Policy, tint = p.textSecondary) { dialog = Dialog.Privacy }
                    val version = remember(ctx) { runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull().orEmpty() }
                    SettingsRow(stringResource(R.string.version), icon = Icons.Default.Info, tint = p.textSecondary, value = version, divider = false, chevron = false)
                }
            }
            item { Text(stringResource(R.string.disclaimer_short), color = p.textTertiary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp)) }
        }
    }

    gate.Host()
    when (val d = dialog) {
        is Dialog.Pick -> PickDialog(d) { dialog = null }
        Dialog.NewPin -> NewPinDialog(vm.parental::isValidFormat, onSave = { vm.setPin(it); dialog = null }, onDismiss = { dialog = null })
        Dialog.Privacy -> AlertDialog(
            onDismissRequest = { dialog = null },
            containerColor = p.card,
            title = { Text(stringResource(R.string.privacy_policy)) },
            text = { Text(stringResource(R.string.privacy_text), Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text(stringResource(DR.string.ds_ok), color = p.accent) } },
        )
        null -> Unit
    }
    message?.let { m ->
        AlertDialog(
            onDismissRequest = { vm.message.value = null },
            containerColor = p.card,
            text = { Text(messageText(m)) },
            confirmButton = { TextButton(onClick = { vm.message.value = null }) { Text(stringResource(DR.string.ds_ok), color = p.accent) } },
        )
    }
}

/** Gradient “播放列表” card at the top of 我的. */
@Composable
private fun PlaylistCard(summary: PlaylistSummary?, onClick: () -> Unit) {
    val p = SdTheme.palette
    val sub = if (summary == null) {
        stringResource(R.string.no_playlist_hint)
    } else {
        val updated = if (summary.lastUpdated > 0) DateUtils.getRelativeTimeSpanString(summary.lastUpdated, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString() else stringResource(R.string.never_updated)
        stringResource(R.string.playlist_summary, summary.name, summary.channels, updated)
    }
    FocusSurface(
        onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), container = p.card,
        border = p.accent.copy(alpha = 0.35f), contentDescription = stringResource(R.string.playlists_card),
    ) {
        Row(
            Modifier.fillMaxWidth().background(Brush.horizontalGradient(listOf(p.accent.copy(alpha = 0.16f), Color.Transparent))).padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(60.dp).background(p.accent, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.PlaylistPlay, null, tint = p.onAccent, modifier = Modifier.size(30.dp))
            }
            Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.playlists_card), style = MaterialTheme.typography.titleLarge, color = p.textPrimary)
                Text(sub, style = MaterialTheme.typography.bodyMedium, color = p.textSecondary, maxLines = 2)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = p.accent)
        }
    }
}

@Composable
private fun messageText(m: SettingsMessage): String = when (m) {
    SettingsMessage.Exported -> stringResource(R.string.backup_done)
    SettingsMessage.HistoryCleared -> stringResource(R.string.history_cleared)
    SettingsMessage.EpgUpdated -> stringResource(R.string.epg_updated)
    is SettingsMessage.Failed -> stringResource(R.string.operation_failed, if (m.reason == "INVALID_BACKUP") stringResource(R.string.backup_invalid) else m.reason)
    is SettingsMessage.Restored -> buildString {
        append(stringResource(R.string.restore_done, m.result.playlists, m.result.favorites))
        if (m.result.needPassword.isNotEmpty()) append("\n").append(stringResource(R.string.restore_need_password, m.result.needPassword.joinToString()))
    }
}

@Composable
private fun LanguageRow(ctx: android.content.Context, open: (Dialog) -> Unit) {
    val current = LocaleStore.get(ctx)
    val labels = mapOf("" to stringResource(R.string.lang_system), "zh-CN" to "简体中文", "en" to "English")
    val title = stringResource(R.string.language)
    SettingsRow(title, icon = Icons.Default.Language, tint = SdTheme.palette.hueGreen, value = labels[current] ?: current, divider = false) {
        open(
            Dialog.Pick(title, LocaleStore.SUPPORTED.map { Choice<Any>(it, labels[it] ?: it) }, current) { v ->
                LocaleStore.set(ctx, v as String)
                ctx.findActivity()?.recreate()
            },
        )
    }
}

@Composable
private fun AccentRow(selected: Int, onPick: (Int) -> Unit) {
    val p = SdTheme.palette
    SettingsRow(stringResource(R.string.accent), icon = Icons.Default.Palette, tint = p.accent, trailing = {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AccentColors.forEachIndexed { i, c ->
                Surface(
                    color = c, shape = CircleShape,
                    border = if (i == selected) BorderStroke(3.dp, p.textPrimary) else null,
                    modifier = Modifier.size(28.dp).focusRing(RoundedCornerShape(50)),
                    onClick = { onPick(i) },
                ) {}
            }
        }
    })
}

@Composable
private fun PickDialog(d: Dialog.Pick, onDismiss: () -> Unit) {
    val p = SdTheme.palette
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.card,
        title = { Text(d.title) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                d.options.forEach { o ->
                    FocusSurface(onClick = { d.onPick(o.value); onDismiss() }, shape = RoundedCornerShape(10.dp), container = Color.Transparent, focusFill = true, contentDescription = o.label) { focused ->
                        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(o.value == d.selected, null, colors = RadioButtonDefaults.colors(selectedColor = p.accent))
                            Text(o.label, color = if (focused) p.onFocusFill else p.textPrimary, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(DR.string.ds_cancel), color = p.accent) } },
    )
}

private fun DecoderMode.shortLabel() = when (this) {
    DecoderMode.HARDWARE_PREFERRED -> R.string.decoder_hw_short
    DecoderMode.SOFTWARE_ONLY -> R.string.decoder_sw
}

private fun ThemeMode.label() = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
    ThemeMode.AMOLED -> R.string.theme_amoled
}

private fun DecoderMode.label() = when (this) {
    DecoderMode.HARDWARE_PREFERRED -> R.string.decoder_hw
    DecoderMode.SOFTWARE_ONLY -> R.string.decoder_sw
}

private fun BufferProfile.label() = when (this) {
    BufferProfile.LOW_LATENCY -> R.string.buffer_low
    BufferProfile.BALANCED -> R.string.buffer_balanced
    BufferProfile.STABLE -> R.string.buffer_stable
}

private fun AspectMode.label() = when (this) {
    AspectMode.FIT -> R.string.aspect_fit
    AspectMode.RATIO_16_9 -> R.string.aspect_16_9
    AspectMode.RATIO_4_3 -> R.string.aspect_4_3
    AspectMode.FILL -> R.string.aspect_fill
    AspectMode.STRETCH -> R.string.aspect_stretch
    AspectMode.ORIGINAL -> R.string.aspect_original
}
