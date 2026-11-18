package com.streambox.player.feature.settings

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.streambox.player.core.designsystem.component.LoadingState
import com.streambox.player.core.designsystem.component.focusRing
import com.streambox.player.core.designsystem.theme.StreamBoxTheme
import com.streambox.player.core.model.AppSettings
import com.streambox.player.core.model.AspectRatioMode
import com.streambox.player.core.model.BufferProfile
import com.streambox.player.core.model.PlaybackSettings
import com.streambox.player.core.model.SubtitleBackground
import com.streambox.player.core.model.SubtitleColor
import com.streambox.player.core.model.SubtitleSize
import com.streambox.player.core.model.SubtitleStyle
import com.streambox.player.core.model.ThemeMode
import com.streambox.player.core.ui.FormFactor
import com.streambox.player.core.ui.LocalFormFactor
import com.streambox.player.core.ui.labelRes
import com.streambox.player.core.ui.supportsPictureInPicture

@Composable
internal fun SettingsRoute(viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        uiState = uiState,
        onThemeModeSelected = viewModel::onThemeModeSelected,
        onDynamicColorChanged = viewModel::onDynamicColorChanged,
        playbackActions = PlaybackSettingsActions(
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

/** Callbacks of the "Playback" section. */
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

@Composable
internal fun SettingsScreen(
    uiState: SettingsUiState,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    playbackActions: PlaybackSettingsActions = PlaybackSettingsActions(),
) {
    when (uiState) {
        SettingsUiState.Loading -> LoadingState(
            message = stringResource(com.streambox.player.core.ui.R.string.core_ui_loading),
            modifier = modifier,
        )

        is SettingsUiState.Success -> SettingsContent(
            settings = uiState.settings,
            onThemeModeSelected = onThemeModeSelected,
            onDynamicColorChanged = onDynamicColorChanged,
            playbackActions = playbackActions,
            modifier = modifier,
        )
    }
}

@Composable
private fun SettingsContent(
    settings: AppSettings,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    playbackActions: PlaybackSettingsActions,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val versionName = remember(context) { context.appVersionName() }
    // Wallpaper-based color only exists on Android 12+ and is meaningless on TV.
    val showDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        LocalFormFactor.current != FormFactor.Tv
    // Hidden on TV and on devices without picture-in-picture support.
    val formFactor = LocalFormFactor.current
    val showPictureInPicture = remember(context, formFactor) { context.supportsPictureInPicture(formFactor) }
    // Touch gestures do not exist on TV (remote only).
    val showGestures = formFactor != FormFactor.Tv

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp),
    ) {
        item { SectionHeader(stringResource(R.string.feature_settings_section_appearance)) }
        item {
            Column(Modifier.selectableGroup()) {
                ThemeMode.entries.forEach { mode ->
                    OptionRow(
                        label = stringResource(mode.labelRes()),
                        selected = settings.themeMode == mode,
                        onClick = { onThemeModeSelected(mode) },
                    )
                }
            }
        }
        if (showDynamicColor) {
            item {
                SwitchRow(
                    label = stringResource(R.string.feature_settings_dynamic_color),
                    checked = settings.useDynamicColor,
                    onCheckedChange = onDynamicColorChanged,
                )
            }
        }
        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        playbackSection(settings.playback, playbackActions, showPictureInPicture, showGestures)
        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item { SectionHeader(stringResource(R.string.feature_settings_section_about)) }
        item {
            Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text(
                    text = stringResource(R.string.feature_settings_version, versionName),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.padding(top = 8.dp))
                Text(
                    text = stringResource(R.string.feature_settings_disclaimer),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.widthIn(max = 640.dp),
                )
            }
        }
    }
}

private fun LazyListScope.playbackSection(
    playback: PlaybackSettings,
    actions: PlaybackSettingsActions,
    showPictureInPicture: Boolean,
    showGestures: Boolean,
) {
    item { SectionHeader(stringResource(R.string.feature_settings_section_playback)) }
    item {
        ChoiceGroup(
            title = stringResource(R.string.feature_settings_buffer_profile),
            options = BufferProfile.entries,
            selected = playback.bufferProfile,
            label = { stringResource(it.labelRes()) },
            onSelect = actions.onBufferProfileSelected,
        )
    }
    item {
        SwitchRow(
            label = stringResource(R.string.feature_settings_background_playback),
            summary = stringResource(R.string.feature_settings_background_playback_summary),
            checked = playback.backgroundPlayback,
            onCheckedChange = actions.onBackgroundPlaybackChanged,
        )
    }
    continuationItems(playback, actions)
    touchOnlyItems(playback, actions, showPictureInPicture, showGestures)
    item {
        SwitchRow(
            label = stringResource(R.string.feature_settings_software_decoding),
            summary = stringResource(R.string.feature_settings_software_decoding_summary),
            checked = playback.softwareDecodingOnly,
            onCheckedChange = actions.onSoftwareDecodingChanged,
        )
    }
    item {
        ChoiceGroup(
            title = stringResource(R.string.feature_settings_reconnect_attempts),
            // Keep a stored value outside the offered choices visible and selected.
            options = (PlaybackSettings.RECONNECT_ATTEMPT_CHOICES + playback.maxReconnectAttempts).distinct().sorted(),
            selected = playback.maxReconnectAttempts,
            label = { reconnectLabel(it) },
            onSelect = actions.onReconnectAttemptsSelected,
        )
    }
    item {
        ChoiceGroup(
            title = stringResource(R.string.feature_settings_default_aspect_ratio),
            options = AspectRatioMode.entries,
            selected = playback.defaultAspectRatio,
            label = { stringResource(it.labelRes()) },
            onSelect = actions.onDefaultAspectRatioSelected,
        )
    }
    subtitleStyleItems(playback.subtitleStyle, actions.onSubtitleStyleChanged)
}

private fun LazyListScope.subtitleStyleItems(style: SubtitleStyle, onChange: (SubtitleStyle) -> Unit) {
    item {
        ChoiceGroup(
            title = stringResource(com.streambox.player.core.ui.R.string.core_ui_subtitle_size),
            options = SubtitleSize.entries,
            selected = style.size,
            label = { stringResource(it.labelRes()) },
            onSelect = { onChange(style.copy(size = it)) },
        )
    }
    item {
        ChoiceGroup(
            title = stringResource(com.streambox.player.core.ui.R.string.core_ui_subtitle_color),
            options = SubtitleColor.entries,
            selected = style.color,
            label = { stringResource(it.labelRes()) },
            onSelect = { onChange(style.copy(color = it)) },
        )
    }
    item {
        ChoiceGroup(
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

/** A titled single-choice radio group. */
@Composable
private fun <T> ChoiceGroup(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )
        Column(Modifier.selectableGroup()) {
            options.forEach { option ->
                OptionRow(
                    label = label(option),
                    selected = option == selected,
                    onClick = { onSelect(option) },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(horizontal = 24.dp, vertical = 12.dp)
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
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, summary: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .focusRing()
            .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.System -> R.string.feature_settings_theme_system
    ThemeMode.Light -> R.string.feature_settings_theme_light
    ThemeMode.Dark -> R.string.feature_settings_theme_dark
    ThemeMode.Amoled -> R.string.feature_settings_theme_amoled
}

private fun BufferProfile.labelRes(): Int = when (this) {
    BufferProfile.LowLatency -> R.string.feature_settings_buffer_low_latency
    BufferProfile.Balanced -> R.string.feature_settings_buffer_balanced
    BufferProfile.Stable -> R.string.feature_settings_buffer_stable
}

private fun Context.appVersionName(): String = try {
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0)
    }
    info.versionName.orEmpty()
} catch (_: PackageManager.NameNotFoundException) {
    ""
}

@Preview
@Composable
private fun SettingsScreenPreview() {
    StreamBoxTheme {
        SettingsScreen(
            uiState = SettingsUiState.Success(AppSettings(themeMode = ThemeMode.Dark)),
            onThemeModeSelected = {},
            onDynamicColorChanged = {},
        )
    }
}

/** "Continue last channel on launch" and "auto play next" (phones, tablets and TV). */
private fun LazyListScope.continuationItems(playback: PlaybackSettings, actions: PlaybackSettingsActions) {
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
}

/** Picture-in-picture and touch gestures: phones / tablets only (hidden on TV). */
private fun LazyListScope.touchOnlyItems(
    playback: PlaybackSettings,
    actions: PlaybackSettingsActions,
    showPictureInPicture: Boolean,
    showGestures: Boolean,
) {
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
