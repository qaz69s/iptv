package com.streamdeck.player.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Visible focus ring for D-pad navigation (spec §8.6). Apply before clickable/focusable. */
fun Modifier.focusRing(shape: RoundedCornerShape = RoundedCornerShape(10.dp), width: Dp = 2.dp): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val color = if (focused) SdTheme.palette.accent else Color.Transparent
    this.onFocusChanged { focused = it.hasFocus }.border(width, color, shape)
}

// Unified page states (spec §9): every screen shows the same loading / empty / error look, with the concrete reason
// and a retry action. On TV the primary action takes focus so the remote never lands on nothing.

@Composable
fun LoadingState(modifier: Modifier = Modifier, message: String? = null) {
    val p = SdTheme.palette
    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(color = p.accent, trackColor = p.track, strokeWidth = 4.dp, strokeCap = StrokeCap.Round, modifier = Modifier.size(44.dp))
        Text(message ?: stringResource(R.string.ds_loading), Modifier.padding(top = 18.dp), color = p.textSecondary, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun EmptyState(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    title: String? = null,
    icon: ImageVector = Icons.Outlined.Inbox,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    StateLayout(
        modifier = modifier, icon = icon, tint = SdTheme.palette.accent, title = title, message = message, detail = null,
        primary = if (actionLabel != null && onAction != null) actionLabel to onAction else null,
        primaryIcon = null,
        secondary = if (secondaryLabel != null && onSecondary != null) secondaryLabel to onSecondary else null,
    )
}

/** [message] is the concrete reason (HTTP status, timeout, parser error …); it is shown verbatim in a detail box. */
@Composable
fun ErrorState(
    message: String,
    onRetry: (() -> Unit)?,
    modifier: Modifier = Modifier,
    title: String? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    StateLayout(
        modifier = modifier, icon = Icons.Outlined.ErrorOutline, tint = SdTheme.palette.live,
        title = title ?: stringResource(R.string.ds_error_title), message = null, detail = message,
        primary = onRetry?.let { stringResource(R.string.ds_retry) to it },
        primaryIcon = Icons.Default.Refresh,
        secondary = if (secondaryLabel != null && onSecondary != null) secondaryLabel to onSecondary else null,
    )
}

@Composable
private fun StateLayout(
    modifier: Modifier,
    icon: ImageVector,
    tint: Color,
    title: String?,
    message: String?,
    detail: String?,
    primary: Pair<String, () -> Unit>?,
    primaryIcon: ImageVector?,
    secondary: Pair<String, () -> Unit>?,
) {
    val p = SdTheme.palette
    val tv = rememberIsTv()
    val focus = remember { FocusRequester() }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(if (tv) 88.dp else 76.dp).background(tint.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(if (tv) 44.dp else 38.dp))
            }
            if (title != null) {
                Text(
                    title, color = p.textPrimary, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold,
                    style = if (tv) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 18.dp),
                )
            }
            if (message != null) {
                Text(
                    message, color = p.textSecondary, textAlign = TextAlign.Center,
                    style = if (tv) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = if (title != null) 8.dp else 18.dp).widthIn(max = 480.dp),
                )
            }
            if (detail != null) {
                Column(
                    Modifier.padding(top = 14.dp).widthIn(max = 520.dp).background(p.cardHigh, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp,
                        vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.ds_reason), color = p.textTertiary, style = MaterialTheme.typography.labelMedium)
                    Text(detail, color = p.textPrimary, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 2.dp))
                }
            }
            if (primary != null || secondary != null) {
                Row(Modifier.padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (secondary != null) SdButton(secondary.first, secondary.second, style = SdButtonStyle.Secondary, large = tv)
                    if (primary != null) {
                        SdButton(primary.first, primary.second, icon = primaryIcon, large = tv, modifier = Modifier.focusRequester(focus))
                    }
                }
            }
        }
    }
    if (tv && primary != null) LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

/**
 * Long-running work with a big ring (import, refresh). [progress] null = indeterminate; [value] is shown in the
 * ring centre (e.g. parsed entries so far).
 */
@Composable
fun ProgressState(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    progress: Float? = null,
    cancelLabel: String? = null,
    onCancel: (() -> Unit)? = null,
) {
    val p = SdTheme.palette
    val tv = rememberIsTv()
    val ring = if (tv) 200.dp else 168.dp
    val focus = remember { FocusRequester() }
    Column(modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(ring), contentAlignment = Alignment.Center) {
            if (progress == null) {
                CircularProgressIndicator(color = p.accent, trackColor = p.track, strokeWidth = 10.dp, strokeCap = StrokeCap.Round,
                    modifier = Modifier.size(ring))
            } else {
                CircularProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) }, color = p.accent, trackColor = p.track, strokeWidth = 10.dp,
                    strokeCap = StrokeCap.Round, modifier = Modifier.size(ring),
                )
            }
            Text(value, color = p.textPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineMedium)
        }
        Text(title, color = p.textPrimary, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 26.dp))
        if (detail != null) {
            Text(detail, color = p.textSecondary, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp))
        }
        if (cancelLabel != null && onCancel != null) {
            SdButton(cancelLabel, onCancel, style = SdButtonStyle.Secondary, large = tv, modifier = Modifier.padding(top = 26.dp).focusRequester(focus))
            if (tv) LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        }
    }
}

/** Centred box helper used by the logo placeholder and badges. */
@Composable
internal fun CenterBox(modifier: Modifier, content: @Composable () -> Unit) {
    Box(modifier, contentAlignment = Alignment.Center) { content() }
}
