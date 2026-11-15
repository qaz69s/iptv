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
import com.streambox.player.core.model.ThemeMode
import com.streambox.player.core.ui.FormFactor
import com.streambox.player.core.ui.LocalFormFactor

@Composable
internal fun SettingsRoute(viewModel: SettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsScreen(
        uiState = uiState,
        onThemeModeSelected = viewModel::onThemeModeSelected,
        onDynamicColorChanged = viewModel::onDynamicColorChanged,
    )
}

@Composable
internal fun SettingsScreen(
    uiState: SettingsUiState,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
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
            modifier = modifier,
        )
    }
}

@Composable
private fun SettingsContent(
    settings: AppSettings,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onDynamicColorChanged: (Boolean) -> Unit,
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
                    ThemeOptionRow(
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
private fun ThemeOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
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
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .focusRing()
            .toggleable(value = checked, onValueChange = onCheckedChange, role = Role.Switch)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
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
