package com.streambox.player.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.darkColorScheme as tvDarkColorScheme
import androidx.tv.material3.lightColorScheme as tvLightColorScheme
import com.streambox.player.core.designsystem.theme.StreamBoxColors as C
import com.streambox.player.core.model.ThemeMode

internal val LightColors = lightColorScheme(
    primary = C.Teal40,
    onPrimary = C.Neutral98,
    primaryContainer = C.Teal90,
    onPrimaryContainer = C.Teal10,
    secondary = C.Slate40,
    onSecondary = C.Neutral98,
    secondaryContainer = C.Slate90,
    onSecondaryContainer = C.Slate10,
    tertiary = C.Amber40,
    onTertiary = C.Neutral98,
    tertiaryContainer = C.Amber90,
    onTertiaryContainer = C.Amber10,
    error = C.Red40,
    onError = C.Neutral98,
    errorContainer = C.Red90,
    onErrorContainer = C.Red10,
    background = C.Neutral98,
    onBackground = C.Neutral10,
    surface = C.Neutral98,
    onSurface = C.Neutral10,
    surfaceVariant = C.NeutralVariant90,
    onSurfaceVariant = C.NeutralVariant30,
    outline = C.NeutralVariant50,
    outlineVariant = C.NeutralVariant80,
)

internal val DarkColors = darkColorScheme(
    primary = C.Teal80,
    onPrimary = C.Teal20,
    primaryContainer = C.Teal30,
    onPrimaryContainer = C.Teal90,
    secondary = C.Slate80,
    onSecondary = C.Slate20,
    secondaryContainer = C.Slate30,
    onSecondaryContainer = C.Slate90,
    tertiary = C.Amber80,
    onTertiary = C.Amber20,
    tertiaryContainer = C.Amber30,
    onTertiaryContainer = C.Amber90,
    error = C.Red80,
    onError = C.Red20,
    errorContainer = C.Red30,
    onErrorContainer = C.Red90,
    background = C.Neutral6,
    onBackground = C.Neutral90,
    surface = C.Neutral6,
    onSurface = C.Neutral90,
    surfaceVariant = C.NeutralVariant30,
    onSurfaceVariant = C.NeutralVariant80,
    surfaceContainer = C.Neutral12,
    surfaceContainerHigh = C.Neutral17,
    outline = C.NeutralVariant60,
    outlineVariant = C.NeutralVariant30,
)

/** True black surfaces for OLED panels; saves power and avoids grey glow in dark rooms. */
internal fun ColorScheme.toAmoled(): ColorScheme = copy(
    background = C.Neutral0,
    surface = C.Neutral0,
    surfaceContainerLowest = C.Neutral0,
    surfaceContainerLow = C.Neutral6,
    surfaceContainer = C.Neutral10,
    surfaceContainerHigh = C.Neutral12,
    surfaceContainerHighest = C.Neutral17,
)

@Composable
fun resolveColorScheme(themeMode: ThemeMode, useDynamicColor: Boolean): ColorScheme {
    val dark = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark, ThemeMode.Amoled -> true
    }
    val dynamicSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val base = when {
        useDynamicColor && dynamicSupported && dark -> dynamicDarkColorScheme(LocalContext.current)
        useDynamicColor && dynamicSupported -> dynamicLightColorScheme(LocalContext.current)
        dark -> DarkColors
        else -> LightColors
    }
    return if (themeMode == ThemeMode.Amoled) base.toAmoled() else base
}

/** Phone and tablet theme (Material 3). */
@Composable
fun StreamBoxTheme(
    themeMode: ThemeMode = ThemeMode.System,
    useDynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = resolveColorScheme(themeMode, useDynamicColor),
        typography = StreamBoxTypography,
        content = content,
    )
}

/**
 * TV theme. Provides both the Compose-for-TV theme (used by tv-material components) and the
 * Material 3 theme (used by shared screens), derived from the same palette so they match.
 */
@Composable
fun StreamBoxTvTheme(themeMode: ThemeMode = ThemeMode.Dark, content: @Composable () -> Unit) {
    // Dynamic color is a phone concept; TV always uses the brand palette.
    val m3 = resolveColorScheme(themeMode, useDynamicColor = false)
    val isDark = m3.background.luminanceIsDark()
    val tvColors = if (isDark) {
        tvDarkColorScheme(
            primary = m3.primary,
            onPrimary = m3.onPrimary,
            primaryContainer = m3.primaryContainer,
            onPrimaryContainer = m3.onPrimaryContainer,
            secondary = m3.secondary,
            onSecondary = m3.onSecondary,
            secondaryContainer = m3.secondaryContainer,
            onSecondaryContainer = m3.onSecondaryContainer,
            tertiary = m3.tertiary,
            onTertiary = m3.onTertiary,
            background = m3.background,
            onBackground = m3.onBackground,
            surface = m3.surface,
            onSurface = m3.onSurface,
            surfaceVariant = m3.surfaceVariant,
            onSurfaceVariant = m3.onSurfaceVariant,
            error = m3.error,
            onError = m3.onError,
            border = m3.outline,
            borderVariant = m3.outlineVariant,
        )
    } else {
        tvLightColorScheme(
            primary = m3.primary,
            onPrimary = m3.onPrimary,
            primaryContainer = m3.primaryContainer,
            onPrimaryContainer = m3.onPrimaryContainer,
            secondary = m3.secondary,
            onSecondary = m3.onSecondary,
            secondaryContainer = m3.secondaryContainer,
            onSecondaryContainer = m3.onSecondaryContainer,
            tertiary = m3.tertiary,
            onTertiary = m3.onTertiary,
            background = m3.background,
            onBackground = m3.onBackground,
            surface = m3.surface,
            onSurface = m3.onSurface,
            surfaceVariant = m3.surfaceVariant,
            onSurfaceVariant = m3.onSurfaceVariant,
            error = m3.error,
            onError = m3.onError,
            border = m3.outline,
            borderVariant = m3.outlineVariant,
        )
    }
    MaterialTheme(colorScheme = m3, typography = StreamBoxTypography) {
        TvMaterialTheme(colorScheme = tvColors, content = content)
    }
}

private fun androidx.compose.ui.graphics.Color.luminanceIsDark(): Boolean =
    (0.2126f * red + 0.7152f * green + 0.0722f * blue) < 0.5f
