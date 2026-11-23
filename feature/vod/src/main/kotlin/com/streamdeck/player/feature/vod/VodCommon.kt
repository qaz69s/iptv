package com.streamdeck.player.feature.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.streamdeck.player.core.database.VodProgressEntity
import com.streamdeck.player.core.designsystem.FocusSurface
import com.streamdeck.player.core.designsystem.SdProgress
import com.streamdeck.player.core.designsystem.SdTheme
import com.streamdeck.player.core.designsystem.backdropGlow
import com.streamdeck.player.core.designsystem.logoInitials
import com.streamdeck.player.core.designsystem.tileTint

/** 0..1 watched share, or null when nothing meaningful is stored. */
internal fun VodProgressEntity?.fraction(): Float? =
    this?.takeIf { it.durationMs > 0 && it.positionMs > 0 && !it.watched }?.let { (it.positionMs.toFloat() / it.durationMs).coerceIn(0f, 1f) }

internal fun VodProgressEntity?.resumable(): Boolean = this != null && !watched && positionMs > 0

/** 1:02:03 / 12:34 */
internal fun formatPosition(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/**
 * Artwork with a tinted placeholder (initials) underneath, so missing or failing posters never leave holes.
 * The placeholder colour adapts to light / dark through [tileTint].
 */
@Composable
internal fun Artwork(url: String?, title: String, modifier: Modifier = Modifier, corner: Dp = 14.dp, overlay: @Composable BoxScope.() -> Unit = {}) {
    var loaded by remember(url) { mutableStateOf(false) }
    val shape = RoundedCornerShape(corner)
    Box(modifier.clip(shape).background(tileTint(title), shape)) {
        if (!loaded) {
            Text(
                logoInitials(title), color = SdTheme.palette.textSecondary, fontWeight = FontWeight.Bold, fontSize = 22.sp,
                textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.align(Alignment.Center).padding(8.dp),
            )
        }
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url, contentDescription = null, contentScale = ContentScale.Crop,
                onState = { loaded = it is AsyncImagePainter.State.Success }, modifier = Modifier.fillMaxSize(),
            )
        }
        overlay()
    }
}

/** Small ★ rating pill drawn over artwork (always on a dark scrim, readable in both themes). */
@Composable
internal fun RatingPill(rating: Float?, modifier: Modifier = Modifier) {
    val r = rating?.takeIf { it > 0 } ?: return
    val p = SdTheme.palette
    Row(
        modifier.background(p.scrim, RoundedCornerShape(8.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Star, null, tint = p.star, modifier = Modifier.size(12.dp))
        Text("%.1f".format(r), color = p.onScrim, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(start = 3.dp))
    }
}

/** Poster tile of the grids and rails: 2:3 artwork, rating, resume bar, title under it. */
@Composable
internal fun PosterCard(
    title: String,
    image: String?,
    rating: Float?,
    progress: Float?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tv: Boolean = false,
    subtitle: String? = null,
) {
    val p = SdTheme.palette
    FocusSurface(
        onClick = onClick, modifier = modifier, shape = RoundedCornerShape(16.dp), container = Color.Transparent,
        focusScale = if (tv) 1.06f else 1f, contentDescription = title,
    ) {
        Column(Modifier.padding(if (tv) 6.dp else 0.dp)) {
            Artwork(image, title, Modifier.fillMaxWidth().aspectRatio(2f / 3f)) {
                RatingPill(rating, Modifier.align(Alignment.TopEnd).padding(6.dp))
                if (progress != null) {
                    Box(
                        Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, p.scrim))).padding(horizontal = 8.dp, vertical = 8.dp),
                    ) { SdProgress(progress, height = 3.dp) }
                }
            }
            Text(
                title, color = p.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = if (tv) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp),
            )
            if (subtitle != null) {
                Text(subtitle, color = p.textTertiary, style = MaterialTheme.typography.bodySmall, maxLines = 1, modifier = Modifier.padding(start = 2.dp,
                    top = 2.dp))
            }
        }
    }
}

/** “★ 8.1 · 2023 · 128 分钟 · 剧情” */
@Composable
internal fun metaParts(rating: Float?, year: String?, durationSec: Int?, genre: String?, extra: String? = null): List<String> = listOfNotNull(
    rating?.takeIf { it > 0 }?.let { "★ %.1f".format(it) },
    year?.take(4)?.takeIf { it.isNotBlank() },
    durationSec?.takeIf { it > 0 }?.let { stringResource(R.string.minutes_short, it / 60) },
    extra,
    genre?.takeIf { it.isNotBlank() },
)

@Composable
internal fun MetaLine(parts: List<String>, modifier: Modifier = Modifier, large: Boolean = false) {
    if (parts.isEmpty()) return
    val p = SdTheme.palette
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        parts.forEachIndexed { i, s ->
            if (i > 0) Box(Modifier.size(4.dp).background(p.textTertiary, CircleShape))
            Text(
                s, color = if (i == 0 && s.startsWith("★")) p.star else p.textSecondary, maxLines = 1,
                style = if (large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** Full-bleed backdrop: artwork when available, the soft glow otherwise; fades into the page background. */
@Composable
internal fun Backdrop(url: String?, modifier: Modifier = Modifier, horizontalFade: Boolean = false) {
    val p = SdTheme.palette
    Box(modifier.backdropGlow()) {
        if (!url.isNullOrBlank()) {
            AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(), alpha = if (p.isDark) 0.55f else 0.4f)
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to p.background)))
        if (horizontalFade) {
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to p.background, 0.6f to p.background.copy(alpha = 0.3f),
                1f to Color.Transparent)))
        }
    }
}

/** Green “已看” check used on episodes. */
@Composable
internal fun WatchedMark(modifier: Modifier = Modifier) {
    val p = SdTheme.palette
    Icon(Icons.Default.CheckCircle, stringResource(R.string.watched), tint = p.accent, modifier = modifier.size(20.dp))
}
