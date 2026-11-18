package com.streambox.player.feature.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.streambox.player.core.designsystem.component.LoadingState
import com.streambox.player.core.designsystem.component.focusRing
import com.streambox.player.core.designsystem.icon.StreamBoxIcons
import com.streambox.player.core.model.AspectRatioMode
import com.streambox.player.core.model.BufferProfile
import com.streambox.player.core.model.PlaybackSettings
import com.streambox.player.core.model.SubtitleBackground
import com.streambox.player.core.model.SubtitleColor
import com.streambox.player.core.model.SubtitleSize
import com.streambox.player.core.model.SubtitleStyle
import com.streambox.player.core.ui.FormFactor
import com.streambox.player.core.ui.LocalFormFactor
import com.streambox.player.core.ui.labelRes
import com.streambox.player.core.ui.supportsPictureInPicture

@Composable
internal fun PlaybackSettingsRoute(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PlaybackSettingsScreen(
        uiState = uiState,
        onBack = onBack,
        actions = PlaybackSettingsActions(
            onBufferProfileSelected = viewModel::onBufferProfileSelected,
            onSoftwareDecodingChanged = viewModel::onSoftwareDecodingChanged,
            onReconnectAttemptsSelected = viewModel::onReconnectAttemptsSelected,
            onDefaultAspectRatioSelected = viewModel::onDefaultAspectRatioSelected,
            onSubtitleStyleChanged = viewModel::onSubtitleStyleChanged,
            onBackgroundPlaybackChanged = viewModel::onBackgroundPlaybackChanged,
            onPictureInPictureChanged = viewModel::onPictureInPictureChanged,
            onPlaybackGesturesChanged = viewModel::onPlaybackGesturesChanged,
            onResumeLastChannelChanged = viewModel::onResumeLastChannelChanged,
            onAutoPlayNextChanged = viewModel::onAutoPlayNextChanged,
        ),
    )
}

/** Callbacks of the "Playback" page. */
internal data class PlaybackSettingsActions(
    val onBufferProfileSelected: (BufferProfile) -> Unit = {},
    val onSoftwareDecodingChanged: (Boolean) -> Unit = {},
    val onReconnectAttemptsSelected: (Int) -> Unit = {},
    val onDefaultAspectRatioSelected: (AspectRatioMode) -> Unit = {},
    val onSubtitleStyleChanged: (SubtitleStyle) -> Unit = {},
    val onBackgroundPlaybackChanged: (Boolean) -> Unit = {},
    val onPictureInPictureChanged: (Boolean) -> Unit = {},
    val onPlaybackGesturesChanged: (Boolean) -> Unit = {},
    val onResumeLastChannelChanged: (Boolean) -> Unit = {},
    val onAutoPlayNextChanged: (Boolean) -> Unit = {},
)

/**
 * Second-level "Playback" page (K8). Single-choice options are one row each that opens a dialog, so the
 * page stays short for a remote; switches toggle in place. On TV the first row takes focus on entry.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaybackSettingsScreen(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: PlaybackSettingsActions = PlaybackSettingsActions(),
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_settings_section_playback)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.focusRing()) {
                        Icon(
                            painterResource(StreamBoxIcons.Back),
                            contentDescription = stringResource(R.string.feature_settings_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        when (uiState) {
            SettingsUiState.Loading -> LoadingState(
                message = stringResource(com.streambox.player.core.ui.R.string.core_ui_loading),
                modifier = Modifier.fillMaxSize().padding(padding),
            )

            is SettingsUiState.Success -> PlaybackSettingsList(
                playback = uiState.settings.playback,
                actions = actions,
                modifier = Modifier.fillMaxSize().padding(padding),
            )
        }
    }
}

@Composable
private fun PlaybackSettingsList(
    playback: PlaybackSettings,
    actions: PlaybackSettingsActions,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val formFactor = LocalFormFactor.current
    val isTv = formFactor == FormFactor.Tv
    // Hidden on TV and on devices without picture-in-picture support.
    val showPictureInPicture = remember(context, formFactor) { context.supportsPictureInPicture(formFactor) }
    val firstFocus = remember { FocusRequester() }
    if (isTv) {
        LaunchedEffect(firstFocus) { runCatching { firstFocus.requestFocus() } }
    }
    LazyColumn(modifier = modifier, contentPadding = PaddingValues(vertical = 8.dp)) {
        item {
            ChoiceSetting(
                title = stringResource(R.string.feature_settings_buffer_profile),
                options = BufferProfile.entries,
                selected = playback.bufferProfile,
                label = { stringResource(it.labelRes()) },
                onSelect = actions.onBufferProfileSelected,
                modifier = Modifier.focusRequester(firstFocus),
            )
        }
        item {
            ChoiceSetting(
                title = stringResource(R.string.feature_settings_reconnect_attempts),
                // Keep a stored value outside the offered choices visible and selected.
                options = (PlaybackSettings.RECONNECT_ATTEMPT_CHOICES + playback.maxReconnectAttempts)
                    .distinct()
                    .sorted(),
                selected = playback.maxReconnectAttempts,
                label = { reconnectLabel(it) },
                onSelect = actions.onReconnectAttemptsSelected,
            )
        }
        item {
            ChoiceSetting(
                title = stringResource(R.string.feature_settings_default_aspect_ratio),
                options = AspectRatioMode.entries,
                selected = playback.defaultAspectRatio,
                label = { stringResource(it.labelRes()) },
                onSelect = actions.onDefaultAspectRatioSelected,
            )
        }
        item {
            SwitchRow(
                label = stringResource(R.string.feature_settings_software_decoding),
                summary = stringResource(R.string.feature_settings_software_decoding_summary),
                checked = playback.softwareDecodingOnly,
                onCheckedChange = actions.onSoftwareDecodingChanged,
            )
        }
        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        behaviourItems(playback, actions, showPictureInPicture, showGestures = !isTv)
        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        subtitleStyleItems(playback.subtitleStyle, actions.onSubtitleStyleChanged)
    }
}

/** Background playback, continuation and (phones / tablets only) picture-in-picture and gestures. */
private fun LazyListScope.behaviourItems(
    playback: PlaybackSettings,
    actions: PlaybackSettingsActions,
    showPictureInPicture: Boolean,
    showGestures: Boolean,
) {
    item {
        SwitchRow(
            label = stringResource(R.string.feature_settings_background_playback),
            summary = stringResource(R.string.feature_settings_background_playback_summary),
            checked = playback.backgroundPlayback,
            onCheckedChange = actions.onBackgroundPlaybackChanged,
        )
    }
    item {
        SwitchRow(
            label = stringResource(R.string.feature_settings_resume_last_channel),
            summary = stringResource(R.string.feature_settings_resume_last_channel_summary),
            checked = playback.resumeLastChannel,
            onCheckedChange = actions.onResumeLastChannelChanged,
        )
    }
    item {
        SwitchRow(
            label = stringResource(R.string.feature_settings_auto_play_next),
            summary = stringResource(R.string.feature_settings_auto_play_next_summary),
            checked = playback.autoPlayNext,
            onCheckedChange = actions.onAutoPlayNextChanged,
        )
    }
    if (showPictureInPicture) {
        item {
            SwitchRow(
                label = stringResource(R.string.feature_settings_picture_in_picture),
                summary = stringResource(R.string.feature_settings_picture_in_picture_summary),
                checked = playback.pictureInPicture,
                onCheckedChange = actions.onPictureInPictureChanged,
            )
        }
    }
    if (showGestures) {
        item {
            SwitchRow(
                label = stringResource(R.string.feature_settings_playback_gestures),
                summary = stringResource(R.string.feature_settings_playback_gestures_summary),
                checked = playback.playbackGestures,
                onCheckedChange = actions.onPlaybackGesturesChanged,
            )
        }
    }
}

private fun LazyListScope.subtitleStyleItems(style: SubtitleStyle, onChange: (SubtitleStyle) -> Unit) {
    item { SectionHeader(stringResource(R.string.feature_settings_section_subtitles)) }
    item {
        ChoiceSetting(
            title = stringResource(com.streambox.player.core.ui.R.string.core_ui_subtitle_size),
            options = SubtitleSize.entries,
            selected = style.size,
            label = { stringResource(it.labelRes()) },
            onSelect = { onChange(style.copy(size = it)) },
        )
    }
    item {
        ChoiceSetting(
            title = stringResource(com.streambox.player.core.ui.R.string.core_ui_subtitle_color),
            options = SubtitleColor.entries,
            selected = style.color,
            label = { stringResource(it.labelRes()) },
            onSelect = { onChange(style.copy(color = it)) },
        )
    }
    item {
        ChoiceSetting(
            title = stringResource(com.streambox.player.core.ui.R.string.core_ui_subtitle_background),
            options = SubtitleBackground.entries,
            selected = style.background,
            label = { stringResource(it.labelRes()) },
            onSelect = { onChange(style.copy(background = it)) },
        )
    }
}

@Composable
private fun reconnectLabel(attempts: Int): String = if (attempts == 0) {
    stringResource(R.string.feature_settings_reconnect_off)
} else {
    pluralStringResource(R.plurals.feature_settings_reconnect_count, attempts, attempts)
}

private fun BufferProfile.labelRes(): Int = when (this) {
    BufferProfile.LowLatency -> R.string.feature_settings_buffer_low_latency
    BufferProfile.Balanced -> R.string.feature_settings_buffer_balanced
    BufferProfile.Stable -> R.string.feature_settings_buffer_stable
}
