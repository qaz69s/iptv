package com.streamdeck.player.core.designsystem

import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import java.util.Date
import kotlin.math.abs

// ---------------------------------------------------------------- focus surface

/**
 * Clickable surface shared by phone and TV. On TV the focused state is very visible (spec §8.6):
 * accent border, optional light fill ([focusFill]) and a small scale-up. [content] receives the focus state so
 * text can switch to the dark-on-light colours of the design.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FocusSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(14.dp),
    container: Color = SdTheme.palette.card,
    focusFill: Boolean = false,
    focusScale: Float = 1f,
    border: Color? = null,
    onLongClick: (() -> Unit)? = null,
    contentDescription: String? = null,
    enabled: Boolean = true,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val p = SdTheme.palette
    val bg = if (focused && focusFill) p.focusFill else container
    val ring = when {
        focused -> p.accent
        border != null -> border
        else -> Color.Transparent
    }
    Box(
        modifier
            .then(if (focusScale != 1f) Modifier.scale(if (focused) focusScale else 1f) else Modifier)
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .clip(shape)
            .then(if (bg != Color.Transparent) Modifier.background(bg, shape) else Modifier)
            .then(if (ring != Color.Transparent) Modifier.border(if (focused) 2.5.dp else 1.dp, ring, shape) else Modifier)
            .combinedClickable(enabled = enabled, role = Role.Button, onLongClick = onLongClick, onClick = onClick)
            .then(if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription } else Modifier),
    ) {
        CompositionLocalProvider(LocalContentColor provides if (focused && focusFill) p.onFocusFill else p.textPrimary) {
            content(focused)
        }
    }
}

/** Runs [block] when this element (or a child) gains focus — used by TV heroes that follow the focused card. */
fun Modifier.onFocusGained(block: () -> Unit): Modifier = this.onFocusChanged { if (it.hasFocus) block() }

// ---------------------------------------------------------------- chips, badges, progress

/** Pill chip with an optional count: `全部 763`. Selected = accent fill. */
@Composable
fun SdChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
    leading: ImageVector? = null,
    compact: Boolean = false,
) {
    val p = SdTheme.palette
    FocusSurface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        container = if (selected) p.accent else p.card,
        border = if (selected) null else p.stroke,
        focusFill = !selected,
        contentDescription = listOfNotNull(label, count?.toString()).joinToString(" "),
    ) { focused ->
        val fg = when {
            selected -> p.onAccent
            focused -> p.onFocusFill
            else -> p.textPrimary
        }
        Row(
            Modifier.padding(horizontal = if (compact) 12.dp else 14.dp, vertical = if (compact) 6.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (leading != null) Icon(leading, null, tint = fg, modifier = Modifier.size(16.dp))
            Text(label, color = fg, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (count != null) Text(count.toString(), color = fg.copy(alpha = 0.7f), style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

/** Red “LIVE” tag. */
@Composable
fun LiveBadge(modifier: Modifier = Modifier, small: Boolean = false) {
    Text(
        stringResource(R.string.ds_live),
        color = SdTheme.palette.onLive,
        fontWeight = FontWeight.Bold,
        fontSize = if (small) 10.sp else 12.sp,
        letterSpacing = 1.sp,
        modifier = modifier.background(SdTheme.palette.live, RoundedCornerShape(6.dp)).padding(horizontal = if (small) 5.dp else 8.dp, vertical = 2.dp),
    )
}

/** Outlined accent tag, e.g. “回看”. */
@Composable
fun OutlineTag(text: String, modifier: Modifier = Modifier, color: Color = SdTheme.palette.accent) {
    Text(
        text, color = color, style = MaterialTheme.typography.labelMedium,
        modifier = modifier.border(1.dp, color, RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Thin rounded progress bar used for programme progress everywhere. */
@Composable
fun SdProgress(progress: Float, modifier: Modifier = Modifier, height: Dp = 3.dp, color: Color = SdTheme.palette.accent) {
    val p = SdTheme.palette
    Box(
        modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(50)).background(p.track)
            .drawBehind {
                val w = size.width * progress.coerceIn(0f, 1f)
                drawRoundRect(color, size = size.copy(width = w), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
            },
    )
}

// ---------------------------------------------------------------- logo

private val LOGO_COLORS = listOf(
    Color(0xFF2257D6), Color(0xFFB4152E), Color(0xFF13926B), Color(0xFF6B3FD4),
    Color(0xFF0F2D5C), Color(0xFFD4520F), Color(0xFF1F6FD6), Color(0xFF8A1E5E),
)

private val INITIALS_SPLIT = Regex("[\\s_\\-|·.]+")

/** Short label for the placeholder block: initials or the first word (“Sky Sports F1” → “SKY”). */
fun logoInitials(name: String): String {
    val words = name.trim().split(INITIALS_SPLIT).filter { it.isNotBlank() }
    if (words.isEmpty()) return "TV"
    val first = words.first().filter { it.isLetterOrDigit() }
    return if (first.length in 2..4) first.uppercase() else words.take(3).mapNotNull { it.firstOrNull { c -> c.isLetterOrDigit() } }.joinToString("").uppercase().ifEmpty { "TV" }
}

/** Deterministic placeholder colour per channel name. */
fun logoColor(name: String): Color = LOGO_COLORS[abs(name.hashCode()) % LOGO_COLORS.size]

/**
 * Channel logo on a coloured rounded block. Without a logo (or while it loads / when it fails) the block shows the
 * channel initials, so lists never show empty holes. Requests are down-sampled to the displayed size (spec §5).
 */
@Composable
fun ChannelLogo(url: String?, name: String, size: Dp, modifier: Modifier = Modifier, corner: Dp = size / 5) {
    val ctx = LocalContext.current
    var loaded by remember(url) { mutableStateOf(false) }
    val shape = RoundedCornerShape(corner)
    val px = with(androidx.compose.ui.platform.LocalDensity.current) { size.roundToPx() }
    Box(
        modifier.size(size).clip(shape).background(remember(name) { logoColor(name) }, shape).semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        if (!loaded) {
            val label = remember(name) { logoInitials(name) }
            Text(
                label, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1,
                fontSize = (size.value * if (label.length > 3) 0.22f else 0.28f).sp,
            )
        }
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(ctx).data(url).size(px).crossfade(false).build(),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                onState = { s -> loaded = s is AsyncImagePainter.State.Success },
                modifier = Modifier.padding(size / 10).size(size),
            )
        }
    }
}

// ---------------------------------------------------------------- headers, search, key hints

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    count: Int? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    actionIcon: ImageVector? = null,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = SdTheme.palette.textPrimary)
        if (count != null) {
            Text(count.toString(), style = MaterialTheme.typography.bodyMedium, color = SdTheme.palette.textTertiary, modifier = Modifier.padding(start = 8.dp))
        }
        Box(Modifier.weight(1f))
        if (action != null && onAction != null) {
            FocusSurface(onClick = onAction, shape = RoundedCornerShape(8.dp), container = Color.Transparent, contentDescription = action) {
                Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(action, color = SdTheme.palette.accent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    if (actionIcon != null) Icon(actionIcon, null, tint = SdTheme.palette.accent, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** Rounded search box of the phone home (search-as-you-type; the ViewModel debounces). */
@Composable
fun SdSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val p = SdTheme.palette
    Row(
        modifier.fillMaxWidth().height(46.dp).clip(RoundedCornerShape(14.dp)).background(p.card).border(1.dp, p.stroke, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Search, null, tint = p.textSecondary, modifier = Modifier.size(22.dp))
        Box(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            if (value.isEmpty()) Text(placeholder, color = p.textTertiary, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = p.textPrimary),
                cursorBrush = SolidColor(p.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = placeholder },
            )
        }
        if (value.isNotEmpty()) {
            FocusSurface(onClick = { onValueChange("") }, shape = CircleShape, container = Color.Transparent, contentDescription = stringResource(R.string.ds_clear)) {
                Icon(Icons.Default.Close, null, tint = p.textSecondary, modifier = Modifier.padding(4.dp).size(18.dp))
            }
        }
    }
}

/** “[OK] 观看” remote hint of the TV screens. */
@Composable
fun KeyHint(key: String, label: String, modifier: Modifier = Modifier) {
    val p = SdTheme.palette
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            key, color = p.textPrimary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
            modifier = Modifier.background(p.cardHigh, RoundedCornerShape(5.dp)).padding(horizontal = 7.dp, vertical = 2.dp),
        )
        Text(label, color = p.textSecondary, style = MaterialTheme.typography.labelMedium)
    }
}

// ---------------------------------------------------------------- settings rows (我的)

/** Rounded group of settings rows with a small grey caption above it. */
@Composable
fun SettingsGroup(title: String?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth()) {
        if (title != null) {
            Text(title, color = SdTheme.palette.textTertiary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 18.dp, top = 20.dp, bottom = 10.dp))
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(SdTheme.palette.card)) { content() }
    }
}

/** Coloured icon tile used by settings rows. */
@Composable
fun IconTile(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(modifier.size(size).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * 0.55f))
    }
}

/**
 * One settings row: icon tile, title (+ optional hint), value text and chevron, or a [trailing] slot (switch).
 * [divider] draws the hairline between rows of a group.
 */
@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color = SdTheme.palette.accent,
    value: String? = null,
    hint: String? = null,
    divider: Boolean = true,
    enabled: Boolean = true,
    chevron: Boolean = true,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val p = SdTheme.palette
    FocusSurface(
        onClick = { onClick?.invoke() },
        enabled = enabled && onClick != null,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(0.dp),
        container = Color.Transparent,
        focusFill = false,
        contentDescription = listOfNotNull(title, value).joinToString(", "),
    ) {
        Column {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    IconTile(icon, tint)
                    Box(Modifier.size(14.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(title, color = if (enabled) p.textPrimary else p.textTertiary, style = MaterialTheme.typography.bodyLarge)
                    if (hint != null) Text(hint, color = p.textTertiary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (value != null) Text(value, color = p.textSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
                if (trailing != null) trailing()
                else if (chevron && onClick != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = p.textTertiary, modifier = Modifier.padding(start = 6.dp).size(20.dp))
            }
            if (divider) Box(Modifier.fillMaxWidth().padding(start = if (icon != null) 70.dp else 16.dp).height(0.5.dp).background(p.stroke))
        }
    }
}

/** Switch in the design colours (accent track, white thumb). The row handles the click. */
@Composable
fun SdSwitch(checked: Boolean, enabled: Boolean = true) {
    val p = SdTheme.palette
    Switch(
        checked = checked, onCheckedChange = null, enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedTrackColor = p.accent, checkedThumbColor = Color.White, checkedBorderColor = p.accent,
            uncheckedTrackColor = p.cardHigh, uncheckedThumbColor = p.textSecondary, uncheckedBorderColor = p.cardHigh,
        ),
    )
}

// ---------------------------------------------------------------- backdrop, time

/** Soft “blurred” colour glow used behind heroes and artwork placeholders; colours come from the palette (light/dark). */
fun Modifier.backdropGlow(base: Color? = null): Modifier = composed {
    val p = SdTheme.palette
    val fill = base ?: p.background
    this.drawBehind {
        drawRect(fill)
        drawRect(Brush.radialGradient(listOf(p.glowCool, Color.Transparent), center = Offset(size.width * 0.62f, size.height * 0.18f), radius = size.maxDimension * 0.55f))
        drawRect(Brush.radialGradient(listOf(p.accent.copy(alpha = 0.2f), Color.Transparent), center = Offset(size.width * 0.8f, size.height * 0.62f), radius = size.maxDimension * 0.42f))
        drawRect(Brush.radialGradient(listOf(p.glowWarm, Color.Transparent), center = Offset(size.width * 0.25f, size.height * 0.78f), radius = size.maxDimension * 0.3f))
    }
}

/** Logo-tinted tile background that keeps the right strength in light and dark mode. */
@Composable
fun tileTint(name: String): Color = logoColor(name).copy(alpha = SdTheme.palette.tileAlpha)

private const val SHARED_TICK_MS = 30_000L

/** One 30 s wall-clock ticker for the whole app (provided by the theme) instead of one coroutine per list row. */
val LocalNowTicker = androidx.compose.runtime.staticCompositionLocalOf<State<Long>?> { null }

@Composable
internal fun rememberNowTicker(): State<Long> = produceState(System.currentTimeMillis()) {
    while (true) {
        value = System.currentTimeMillis()
        delay(SHARED_TICK_MS)
    }
}

/** Wall clock that ticks every [intervalMs]; the default 30 s interval reads the shared ticker. */
@Composable
fun rememberNow(intervalMs: Long = SHARED_TICK_MS): State<Long> {
    val shared = LocalNowTicker.current
    if (intervalMs == SHARED_TICK_MS && shared != null) return shared
    return produceState(System.currentTimeMillis(), intervalMs) {
        while (true) {
            value = System.currentTimeMillis()
            delay(intervalMs)
        }
    }
}

/** HH:mm in the user's 12/24h preference. */
@Composable
fun formatClock(ms: Long): String {
    val ctx = LocalContext.current
    val fmt = remember(ctx) { DateFormat.getTimeFormat(ctx) }
    return fmt.format(Date(ms))
}

@Composable
fun ClockText(style: TextStyle = MaterialTheme.typography.titleLarge, color: Color = SdTheme.palette.textPrimary) {
    val now by rememberNow(1_000L)
    Text(formatClock(now), style = style, color = color)
}

object ProgramTime {
    fun progress(start: Long?, end: Long?, now: Long): Float =
        if (start == null || end == null || end <= start) 0f else ((now - start).toFloat() / (end - start)).coerceIn(0f, 1f)

    /** Whole minutes left, at least 1 while airing. */
    fun minutesLeft(end: Long?, now: Long): Int? = end?.let { ((it - now) / 60_000L).toInt().coerceAtLeast(1) }?.takeIf { end > now }
}
