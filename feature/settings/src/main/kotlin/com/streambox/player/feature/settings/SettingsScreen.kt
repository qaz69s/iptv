package com.streambox.player.feature.settings

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.streambox.player.core.designsystem.component.LoadingState
import com.streambox.player.core.designsystem.theme.StreamBoxTheme
import com.streambox.player.core.model.AppSettings
import com.streambox.player.core.model.ThemeMode
import com.streambox.player.core.ui.FormFactor
import com.streambox.player.core.ui.LocalFormFactor

@Composable
internal fun SettingsRoute(onOpenPlaybackSettings: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        uiState = uiState,
        onThemeModeSelected = viewModel::onThemeModeSelected,
        onDynamicColorChanged = viewModel::onDynamicColorChanged,
        onOpenPlaybackSettings = onOpenPlaybackSettings,
    )
}

/**
 * Top-level settings: appearance, an entry to the "Playback" page and about. Playback options live on
 * their own page (K8): on TV the flat list had become too long to scroll with a remote.
 */
@Composable
internal fun SettingsScreen(
    uiState: SettingsUiState,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onOpenPlaybackSettings: () -> Unit = {},
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
            onOpenPlaybackSettings = onOpenPlaybackSettings,
            modifier = modifier,
        )
    }
}

@Composable
private fun SettingsContent(
    settings: AppSettings,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    onOpenPlaybackSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val versionName = remember(context) { context.appVersionName() }
    // Wallpaper-based color only exists on Android 12+ and is meaningless on TV.
    val showDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
        LocalFormFactor.current != FormFactor.Tv

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
        item {
            NavigationRow(
                title = stringResource(R.string.feature_settings_section_playback),
                summary = stringResource(R.string.feature_settings_playback_entry_summary),
                onClick = onOpenPlaybackSettings,
            )
        }
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

private fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.System -> R.string.feature_settings_theme_system
    ThemeMode.Light -> R.string.feature_settings_theme_light
    ThemeMode.Dark -> R.string.feature_settings_theme_dark
    ThemeMode.Amoled -> R.string.feature_settings_theme_amoled
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
