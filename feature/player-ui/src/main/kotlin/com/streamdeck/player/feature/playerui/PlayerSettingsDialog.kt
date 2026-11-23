package com.streamdeck.player.feature.playerui

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import com.streamdeck.player.core.datastore.AspectMode
import com.streamdeck.player.core.datastore.SubtitleStyle
import com.streamdeck.player.core.designsystem.SdTheme
import com.streamdeck.player.core.designsystem.focusRing
import com.streamdeck.player.core.player.TrackKind
import com.streamdeck.player.core.player.TrackMenu
import com.streamdeck.player.core.player.TrackOptions

private val SUB_SCALES = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
private val SUB_COLORS = listOf(0xFFFFFFFF.toInt(), 0xFFFFEB3B.toInt(), 0xFF8BC34A.toInt(), 0xFF4FC3F7.toInt())
private val SUB_BACKGROUNDS = listOf(0x00000000, 0x99000000.toInt(), 0xFF000000.toInt())
private val SLEEP_PRESETS = listOf(15, 30, 60, 90)

/** Parts of the player panel; quick buttons (字幕 / 音轨 / 画质 / 画面比例) open a single part. */
enum class PanelSection { VIDEO, AUDIO, TEXT, ASPECT, SLEEP }

/** Tracks, subtitles, aspect and sleep timer. Opened by the settings button (phone) or MENU key (TV). */
@Composable
fun PlayerSettingsDialog(
    player: Player?,
    aspect: AspectMode,
    subtitleStyle: SubtitleStyle,
    sleepLeftMs: Long?,
    onAspect: (AspectMode) -> Unit,
    onSubtitleStyle: (SubtitleStyle) -> Unit,
    onAddSubtitle: (uri: String, name: String) -> Unit,
    onSleep: (minutes: Int?) -> Unit,
    onDismiss: () -> Unit,
    sections: Set<PanelSection> = PanelSection.entries.toSet(),
) {
    val ctx = LocalContext.current
    var tracksVersion by remember { mutableIntStateOf(0) }
    DisposableEffect(player) {
        val l = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) { tracksVersion++ }
            override fun onTrackSelectionParametersChanged(parameters: androidx.media3.common.TrackSelectionParameters) { tracksVersion++ }
        }
        player?.addListener(l)
        onDispose { player?.removeListener(l) }
    }
    val menus = remember(player, tracksVersion, sections) {
        player?.let { p -> TrackKind.entries.filter { it.section() in sections }.map { TrackOptions.menu(p, it) } }.orEmpty()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) onAddSubtitle(uri.toString(), displayName(ctx, uri))
    }
    var customMinutes by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.large, color = SdTheme.palette.card, modifier = Modifier.widthIn(max = 520.dp).heightIn(max = 640.dp)) {
            LazyColumn(Modifier.padding(vertical = 12.dp)) {
                menus.forEach { menu ->
                    item(key = "h_${menu.kind}") { Header(stringResource(menu.kind.titleRes())) }
                    trackItems(menu, player)
                }
                if (PanelSection.TEXT in sections) item(key = "sub_add") { Option(stringResource(R.string.load_subtitle), false) { picker.launch(arrayOf("application/x-subrip", "text/vtt", "text/plain", "*/*")) } }
                if (PanelSection.TEXT in sections) item(key = "sub_style") {
                    Column {
                    Header(stringResource(R.string.subtitle_style))
                    ChipRow(SUB_SCALES, { "${(it * 100).toInt()}%" }, subtitleStyle.scale) { onSubtitleStyle(subtitleStyle.copy(scale = it)) }
                    ChipRow(SUB_COLORS.indices.toList(), { stringResource(COLOR_NAMES[it]) }, SUB_COLORS.indexOf(subtitleStyle.textColor)) {
                        onSubtitleStyle(subtitleStyle.copy(textColor = SUB_COLORS[it]))
                    }
                    ChipRow(SUB_BACKGROUNDS.indices.toList(), { stringResource(BG_NAMES[it]) }, SUB_BACKGROUNDS.indexOf(subtitleStyle.backgroundColor)) {
                        onSubtitleStyle(subtitleStyle.copy(backgroundColor = SUB_BACKGROUNDS[it]))
                    }
                    }
                }
                if (PanelSection.ASPECT in sections) {
                    item(key = "h_aspect") { Header(stringResource(R.string.aspect)) }
                    items(AspectMode.entries, key = { "a_$it" }) { mode -> Option(stringResource(mode.labelRes()), mode == aspect) { onAspect(mode) } }
                }
                if (PanelSection.SLEEP in sections) {
                item(key = "h_sleep") {
                    Header(
                        if (sleepLeftMs != null) stringResource(R.string.sleep_remaining, formatDuration(sleepLeftMs)) else stringResource(R.string.sleep_timer),
                    )
                }
                item(key = "sleep_off") { Option(stringResource(R.string.sleep_off), sleepLeftMs == null) { onSleep(null) } }
                items(SLEEP_PRESETS, key = { "s_$it" }) { m -> Option(stringResource(R.string.minutes, m), false) { onSleep(m); onDismiss() } }
                item(key = "sleep_custom") {
                    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            customMinutes, { v -> customMinutes = v.filter(Char::isDigit).take(3) }, Modifier.weight(1f),
                            label = { Text(stringResource(R.string.sleep_custom)) }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        Button(onClick = { customMinutes.toIntOrNull()?.takeIf { it > 0 }?.let { onSleep(it); onDismiss() } }) {
                            Text(stringResource(R.string.sleep_start))
                        }
                    }
                }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.trackItems(menu: TrackMenu, player: Player?) {
    val p = player ?: return
    item(key = "auto_${menu.kind}") {
        val label = if (menu.kind == TrackKind.TEXT) R.string.subtitles_off else R.string.track_auto
        val selected = if (menu.kind == TrackKind.TEXT) menu.isDisabled || menu.options.none { it.selected } else menu.isAuto
        Option(stringResource(label), selected) {
            if (menu.kind == TrackKind.TEXT) TrackOptions.disable(p, menu.kind) else TrackOptions.auto(p, menu.kind)
        }
    }
    if (menu.options.isEmpty()) {
        item(key = "none_${menu.kind}") { Text(stringResource(R.string.no_tracks), Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
    }
    menu.options.forEachIndexed { i, opt ->
        item(key = "t_${menu.kind}_$i") {
            val label = TrackOptions.label(opt.format).ifBlank { stringResource(R.string.track_n, i + 1) }
            Option(label, opt.selected && !menu.isAuto) { TrackOptions.select(p, opt) }
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = SdTheme.palette.accent, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp))
}

@Composable
private fun Option(label: String, selected: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = { if (selected) Icon(Icons.Default.Check, null) },
        modifier = Modifier.fillMaxWidth().focusRing().clickable(onClick = onClick),
    )
}

@Composable
private fun <T> ChipRow(values: List<T>, label: @Composable (T) -> String, selected: Any?, onPick: (T) -> Unit) {
    LazyRow(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(values.size) { i ->
            val v = values[i]
            FilterChip(selected = v == selected || i == selected, onClick = { onPick(v) }, label = { Text(label(v)) })
        }
    }
}

private val COLOR_NAMES = listOf(R.string.color_white, R.string.color_yellow, R.string.color_green, R.string.color_blue)
private val BG_NAMES = listOf(R.string.bg_none, R.string.bg_semi, R.string.bg_solid)

private fun TrackKind.section() = when (this) {
    TrackKind.VIDEO -> PanelSection.VIDEO
    TrackKind.AUDIO -> PanelSection.AUDIO
    TrackKind.TEXT -> PanelSection.TEXT
}

private fun TrackKind.titleRes() = when (this) {
    TrackKind.VIDEO -> R.string.video_quality
    TrackKind.AUDIO -> R.string.audio_track
    TrackKind.TEXT -> R.string.subtitles
}

internal fun AspectMode.labelRes() = when (this) {
    AspectMode.FIT -> R.string.aspect_fit
    AspectMode.RATIO_16_9 -> R.string.aspect_16_9
    AspectMode.RATIO_4_3 -> R.string.aspect_4_3
    AspectMode.FILL -> R.string.aspect_fill
    AspectMode.STRETCH -> R.string.aspect_stretch
    AspectMode.ORIGINAL -> R.string.aspect_original
}

private fun displayName(ctx: android.content.Context, uri: Uri): String =
    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    } ?: uri.lastPathSegment ?: "subtitle.srt"
