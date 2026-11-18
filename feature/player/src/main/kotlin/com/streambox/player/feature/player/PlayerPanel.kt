@file:Suppress("MatchingDeclarationName")

package com.streambox.player.feature.player

import android.view.KeyEvent as AndroidKeyEvent
import androidx.annotation.DrawableRes
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.streambox.player.core.designsystem.component.focusRing
import com.streambox.player.core.designsystem.icon.StreamBoxIcons
import com.streambox.player.core.model.AspectRatioMode
import com.streambox.player.core.model.SubtitleBackground
import com.streambox.player.core.model.SubtitleColor
import com.streambox.player.core.model.SubtitleSize
import com.streambox.player.core.model.SubtitleStyle
import com.streambox.player.core.player.MediaTrackOption
import com.streambox.player.core.player.PlaybackTracks
import com.streambox.player.core.player.TrackId
import com.streambox.player.core.player.VideoTrackOption
import com.streambox.player.core.ui.LocalFormFactor
import com.streambox.player.core.ui.labelRes
import com.streambox.player.core.ui.supportsPictureInPicture
import java.util.Locale

/** Everything the panel can change; grouped so the composables stay readable. */
internal data class PlayerPanelActions(
    val onDismiss: () -> Unit,
    val onVideoTrack: (TrackId?) -> Unit,
    val onAudioTrack: (TrackId) -> Unit,
    val onSubtitleTrack: (TrackId?) -> Unit,
    val onLoadSubtitleFile: () -> Unit,
    val onAspectRatio: (AspectRatioMode) -> Unit,
    val onSubtitleStyle: (SubtitleStyle) -> Unit,
    /** Close the panel and shrink into picture-in-picture. */
    val onPictureInPicture: () -> Unit = {},
)

/** Touch: modal bottom sheet. TV: focusable side panel in a dialog; Menu or Back closes it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerPanel(state: PlayerUiState, isTv: Boolean, actions: PlayerPanelActions) {
    if (isTv) {
        Dialog(onDismissRequest = actions.onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            val firstFocus = remember { FocusRequester() }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
                Surface(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(400.dp)
                        .onPreviewKeyEvent { event ->
                            val menu = event.key.nativeKeyCode == AndroidKeyEvent.KEYCODE_MENU
                            if (menu && event.type == KeyEventType.KeyUp) actions.onDismiss()
                            menu
                        },
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    PanelContent(state = state, actions = actions, firstFocus = firstFocus)
                }
            }
            LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
        }
    } else {
        ModalBottomSheet(
            onDismissRequest = actions.onDismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        ) {
            PanelContent(state = state, actions = actions, firstFocus = null)
        }
    }
}

@Composable
private fun PanelContent(state: PlayerUiState, actions: PlayerPanelActions, firstFocus: FocusRequester?) {
    val context = LocalContext.current
    val formFactor = LocalFormFactor.current
    // Same gate as the PiP effect: phones / tablets on Android 8.0+ with system support (never TV).
    val pipSupported = remember(context, formFactor) { context.supportsPictureInPicture(formFactor) }
    LazyColumn(contentPadding = PaddingValues(vertical = 16.dp)) {
        item {
            Text(
                text = stringResource(R.string.feature_player_panel_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .semantics { heading() },
            )
        }
        if (pipSupported && state.pictureInPictureEnabled) {
            item(key = "pip") {
                ActionRow(
                    label = stringResource(R.string.feature_player_pip),
                    icon = StreamBoxIcons.PictureInPicture,
                    onClick = actions.onPictureInPicture,
                )
            }
        }
        aspectSection(state.aspectRatio, actions.onAspectRatio, firstFocus)
        tracksSections(state.tracks, actions)
        subtitleStyleSection(state.subtitleStyle, actions.onSubtitleStyle)
    }
}

private fun LazyListScope.aspectSection(
    current: AspectRatioMode,
    onSelect: (AspectRatioMode) -> Unit,
    firstFocus: FocusRequester?,
) {
    item { SectionHeader(stringResource(com.streambox.player.core.ui.R.string.core_ui_aspect_ratio)) }
    item {
        ChipRow(
            options = AspectRatioMode.entries,
            selected = current,
            label = { stringResource(it.labelRes()) },
            onSelect = onSelect,
            firstFocus = firstFocus,
        )
    }
}

private fun LazyListScope.tracksSections(tracks: PlaybackTracks, actions: PlayerPanelActions) {
    if (tracks.video.isNotEmpty()) {
        item { SectionHeader(stringResource(R.string.feature_player_quality)) }
        item {
            OptionRow(
                label = stringResource(R.string.feature_player_quality_auto),
                selected = tracks.videoAuto,
                onClick = { actions.onVideoTrack(null) },
            )
        }
        tracks.video.forEachIndexed { index, track ->
            item(key = "v${track.id}") {
                OptionRow(
                    label = videoLabel(track, index),
                    selected = !tracks.videoAuto && track.selected,
                    onClick = { actions.onVideoTrack(track.id) },
                )
            }
        }
    }
    if (tracks.audio.size > 1) {
        item { SectionHeader(stringResource(R.string.feature_player_audio)) }
        tracks.audio.forEachIndexed { index, track ->
            item(key = "a${track.id}") {
                OptionRow(
                    label = trackLabel(track, index),
                    selected = track.selected,
                    onClick = { actions.onAudioTrack(track.id) },
                )
            }
        }
    }
    item { SectionHeader(stringResource(R.string.feature_player_subtitles)) }
    item {
        OptionRow(
            label = stringResource(R.string.feature_player_subtitles_off),
            selected = tracks.subtitlesOff,
            onClick = { actions.onSubtitleTrack(null) },
        )
    }
    tracks.subtitles.forEachIndexed { index, track ->
        item(key = "s${track.id}") {
            OptionRow(
                label = trackLabel(track, index),
                selected = !tracks.subtitlesOff && track.selected,
                onClick = { actions.onSubtitleTrack(track.id) },
            )
        }
    }
    item {
        ActionRow(
            label = stringResource(R.string.feature_player_subtitles_load),
            icon = StreamBoxIcons.Subtitles,
            onClick = actions.onLoadSubtitleFile,
        )
    }
}

private fun LazyListScope.subtitleStyleSection(style: SubtitleStyle, onChange: (SubtitleStyle) -> Unit) {
    item { SectionHeader(stringResource(com.streambox.player.core.ui.R.string.core_ui_subtitle_size)) }
    item {
        ChipRow(SubtitleSize.entries, style.size, {
            stringResource(it.labelRes())
        }, { onChange(style.copy(size = it)) })
    }
    item { SectionHeader(stringResource(com.streambox.player.core.ui.R.string.core_ui_subtitle_color)) }
    item {
        ChipRow(SubtitleColor.entries, style.color, {
            stringResource(it.labelRes())
        }, { onChange(style.copy(color = it)) })
    }
    item { SectionHeader(stringResource(com.streambox.player.core.ui.R.string.core_ui_subtitle_background)) }
    item {
        ChipRow(
            SubtitleBackground.entries,
            style.background,
            { stringResource(it.labelRes()) },
            { onChange(style.copy(background = it)) },
        )
    }
}

@Composable
private fun videoLabel(track: VideoTrackOption, index: Int): String {
    val bitrate = track.bitrate?.let { stringResource(R.string.feature_player_mbps, it / BITS_PER_MEGABIT) }
    return when {
        track.height > 0 && bitrate != null -> stringResource(
            R.string.feature_player_quality_with_bitrate,
            track.height,
            bitrate,
        )

        track.height > 0 -> stringResource(R.string.feature_player_quality_height, track.height)

        bitrate != null -> bitrate

        else -> stringResource(R.string.feature_player_track_n, index + 1)
    }
}

@Composable
private fun trackLabel(track: MediaTrackOption, index: Int): String {
    val language = track.language
        ?.takeUnless { it.isBlank() || it == "und" }
        ?.let { Locale.forLanguageTag(it).getDisplayName(Locale.getDefault()).ifBlank { it } }
    val base =
        track.label?.takeIf { it.isNotBlank() } ?: language
            ?: stringResource(R.string.feature_player_track_n, index + 1)
    return if (track.external) stringResource(R.string.feature_player_track_external, base) else base
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .focusRing()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ActionRow(label: String, @DrawableRes icon: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .focusRing()
            .selectable(selected = false, onClick = onClick, role = Role.Button)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun <T> ChipRow(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    firstFocus: FocusRequester? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEachIndexed { index, option ->
            val focusModifier = if (index == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) },
                modifier = focusModifier.focusRing(),
            )
        }
    }
}

private const val BITS_PER_MEGABIT = 1_000_000f
