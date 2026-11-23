package com.streamdeck.player.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamdeck.player.core.datastore.ThemeMode

/** Accent choices; index 0 (teal) is the StreamDeck brand colour. */
val AccentColors = listOf(Color(0xFF2EE6C5), Color(0xFF4C8DFF), Color(0xFF9B7BFF), Color(0xFFFF6FA8), Color(0xFFFF9A3D))

/** Tokens that Material 3 has no role for (LIVE badge, card layers, TV focus fill). */
@Immutable
data class SdPalette(
    val background: Color,
    val card: Color,
    val cardHigh: Color,
    val stroke: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val onAccent: Color,
    val live: Color,
    val onLive: Color,
    val star: Color,
    val track: Color,
    /** Light fill of a focused TV element (design: near-white card with dark text). */
    val focusFill: Color,
    val onFocusFill: Color,
    /** Backdrop glow colours (hero / video placeholder). */
    val glowCool: Color,
    val glowWarm: Color,
    /** Tint behind logo placeholder tiles (alpha applied to the logo colour). */
    val tileAlpha: Float,
    /** Scrim over video; always dark because it sits on top of the picture in both modes. */
    val scrim: Color,
    val onScrim: Color,
    /** Icon-tile hues of the settings rows (design 2), tuned per mode for contrast. */
    val hueGreen: Color,
    val hueAmber: Color,
    val hueBlue: Color,
    val huePurple: Color,
    val hueRed: Color,
    val isDark: Boolean,
    /** Accent as chosen by the user, before light-mode darkening (used by [OnVideo]). */
    val rawAccent: Color = accent,
)

private fun darkPalette(accent: Color, amoled: Boolean) = SdPalette(
    background = if (amoled) Color.Black else Color(0xFF0A0E14),
    card = if (amoled) Color(0xFF0E1218) else Color(0xFF141B26),
    cardHigh = if (amoled) Color(0xFF161C26) else Color(0xFF1C2533),
    stroke = Color(0xFF263042),
    textPrimary = Color(0xFFEEF2F7),
    textSecondary = Color(0xFF97A3B6),
    textTertiary = Color(0xFF5E6B7E),
    accent = accent,
    onAccent = Color(0xFF04211C),
    live = Color(0xFFF0505A),
    onLive = Color.White,
    star = Color(0xFFF5B83D),
    track = Color(0xFF263042),
    focusFill = Color(0xFFEEF2F8),
    onFocusFill = Color(0xFF0A0E14),
    glowCool = Color(0x553D5A8A),
    glowWarm = Color(0x33C07A3A),
    tileAlpha = 0.28f,
    scrim = Color(0xCC05080C),
    onScrim = Color(0xFFF2F5F9),
    hueGreen = Color(0xFF2EE6C5),
    hueAmber = Color(0xFFF5B83D),
    hueBlue = Color(0xFF6E9BFF),
    huePurple = Color(0xFFB08CFF),
    hueRed = Color(0xFFFF5D6C),
    isDark = true,
)

/**
 * Light mode: light grey-white background, white cards, dark ink. The teal accent is darkened (~#1C8E7A for the
 * default) so text/icons in accent keep ≥ 4.5:1 on white; LIVE red is slightly deeper; the TV focus fill inverts to
 * dark ink with white text so focus stays obvious on a light screen.
 */
private fun lightPalette(accent: Color) = SdPalette(
    background = Color(0xFFF3F5F9),
    card = Color(0xFFFFFFFF),
    cardHigh = Color(0xFFE8ECF2),
    stroke = Color(0xFFD7DEE8),
    textPrimary = Color(0xFF0E141C),
    textSecondary = Color(0xFF4E5B6E),
    textTertiary = Color(0xFF7D8899),
    accent = accent.darken(),
    rawAccent = accent,
    onAccent = Color.White,
    live = Color(0xFFDC2F3C),
    onLive = Color.White,
    star = Color(0xFFD9940F),
    track = Color(0xFFDDE3EB),
    focusFill = Color(0xFF111821),
    onFocusFill = Color(0xFFFFFFFF),
    glowCool = Color(0x406E8FC8),
    glowWarm = Color(0x30F0A860),
    tileAlpha = 0.16f,
    scrim = Color(0xCC05080C),
    onScrim = Color(0xFFF2F5F9),
    hueGreen = Color(0xFF119C83),
    hueAmber = Color(0xFFC98200),
    hueBlue = Color(0xFF2F64D8),
    huePurple = Color(0xFF7A4FD6),
    hueRed = Color(0xFFD23445),
    isDark = false,
)

val LocalSdPalette = staticCompositionLocalOf { darkPalette(AccentColors.first(), amoled = false) }

/** Access to the StreamDeck tokens: `SdTheme.palette.live`. */
object SdTheme {
    val palette: SdPalette
        @Composable get() = LocalSdPalette.current
}

private val SdTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Bold),
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
    )
}

/** Global density factor of the phone UI. */
const val PHONE_UI_SCALE = 0.9f

/** Resolved dark/light of a [ThemeMode] (SYSTEM follows the device). */
@Composable
fun ThemeMode.resolvesDark(): Boolean = when (this) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK, ThemeMode.AMOLED -> true
}

private val SdShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun StreamDeckTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    accentIndex: Int = 0,
    fontScale: Float = 1f,
    content: @Composable () -> Unit,
) {
    val accent = AccentColors.getOrElse(accentIndex) { AccentColors.first() }
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
    }
    val palette = if (dark) darkPalette(accent, amoled = mode == ThemeMode.AMOLED) else lightPalette(accent)
    val scheme = if (dark) {
        darkColorScheme(
            primary = palette.accent, onPrimary = palette.onAccent,
            primaryContainer = palette.accent.copy(alpha = 0.18f), onPrimaryContainer = palette.accent,
            secondary = palette.star, onSecondary = Color.Black,
            tertiary = palette.accent,
            background = palette.background, onBackground = palette.textPrimary,
            surface = palette.background, onSurface = palette.textPrimary,
            surfaceVariant = palette.card, onSurfaceVariant = palette.textSecondary,
            surfaceContainerLowest = palette.background, surfaceContainerLow = palette.card,
            surfaceContainer = palette.card, surfaceContainerHigh = palette.cardHigh, surfaceContainerHighest = palette.cardHigh,
            outline = palette.stroke, outlineVariant = palette.stroke,
            error = palette.live,
        )
    } else {
        lightColorScheme(
            primary = palette.accent, onPrimary = palette.onAccent,
            primaryContainer = palette.accent.copy(alpha = 0.14f), onPrimaryContainer = palette.accent,
            secondary = palette.star, onSecondary = Color.White, tertiary = palette.accent,
            background = palette.background, onBackground = palette.textPrimary,
            surface = palette.background, onSurface = palette.textPrimary,
            surfaceVariant = palette.card, onSurfaceVariant = palette.textSecondary,
            surfaceContainerLowest = palette.card, surfaceContainerLow = palette.card,
            surfaceContainer = palette.card, surfaceContainerHigh = palette.cardHigh, surfaceContainerHighest = palette.cardHigh,
            outline = palette.stroke, outlineVariant = palette.stroke,
            error = palette.live,
        )
    }
    val density = LocalDensity.current
    // Phones get a slightly denser UI (everything ×0.9); TV keeps the 10-foot sizes.
    val ui = if (rememberIsTv()) 1f else PHONE_UI_SCALE
    val now = rememberNowTicker()
    CompositionLocalProvider(
        LocalDensity provides Density(density.density * ui, density.fontScale * fontScale),
        LocalSdPalette provides palette,
        LocalNowTicker provides now,
    ) {
        MaterialTheme(colorScheme = scheme, typography = SdTypography, shapes = SdShapes, content = content)
    }
}

private fun Color.darken(): Color = Color(red = red * 0.62f, green = green * 0.62f, blue = blue * 0.62f, alpha = alpha)

/**
 * Player overlays sit on top of the picture, so they always use the dark tokens (light text, light focus fill)
 * even when the app runs in light mode.
 */
@Composable
fun OnVideo(content: @Composable () -> Unit) {
    val current = LocalSdPalette.current
    val dark = if (current.isDark) current else darkPalette(current.rawAccent, amoled = false)
    CompositionLocalProvider(LocalSdPalette provides dark, content = content)
}
