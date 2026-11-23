package com.streamdeck.player.feature.channels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.streamdeck.player.core.database.ChannelListItem
import com.streamdeck.player.core.designsystem.ChannelLogo
import com.streamdeck.player.core.designsystem.FocusSurface
import com.streamdeck.player.core.designsystem.LiveBadge
import com.streamdeck.player.core.designsystem.ProgramTime
import com.streamdeck.player.core.designsystem.SdProgress
import com.streamdeck.player.core.designsystem.SdTheme
import com.streamdeck.player.core.designsystem.backdropGlow
import com.streamdeck.player.core.designsystem.formatClock
import com.streamdeck.player.core.designsystem.onFocusGained
import com.streamdeck.player.core.designsystem.tileTint
import com.streamdeck.player.core.designsystem.rememberNow
import com.streamdeck.player.core.designsystem.R as DR

/** “Amanpour · 下一档 21:00 Quest…” — the second line of a channel row. */
@Composable
internal fun programLine(item: ChannelListItem): String? {
    val now = item.nowTitle
    val next = item.nextTitle?.let { t -> item.nextStart?.let { stringResource(R.string.next_short, formatClock(it), t) } ?: t }
    return when {
        now != null && next != null -> "$now · $next"
        now != null -> now
        next != null -> next
        else -> null
    }
}

/**
 * Phone channel row (design 1): number, logo block, name + favourite star, now · next, programme progress and
 * minutes left. Long-press toggles the favourite. [compact] drops the logo and the progress line.
 */
@Composable
fun ChannelRow(
    item: ChannelListItem,
    onClick: (ChannelListItem) -> Unit,
    onLongClick: ((ChannelListItem) -> Unit)?,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    showNumber: Boolean = true,
) {
    val c = item.channel
    val p = SdTheme.palette
    val now by rememberNow()
    val progress = ProgramTime.progress(item.nowStart, item.nowEnd, now)
    val left = ProgramTime.minutesLeft(item.nowEnd, now)
    FocusSurface(
        onClick = { onClick(item) },
        onLongClick = onLongClick?.let { { it(item) } },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        container = Color.Transparent,
        focusFill = false,
        contentDescription = listOfNotNull(c.number?.toString(), c.name, item.nowTitle).joinToString(", "),
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = if (compact) 6.dp else 7.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showNumber) {
                Text(
                    c.number?.toString().orEmpty(), color = p.textTertiary, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(36.dp), maxLines = 1,
                )
            }
            if (!compact) {
                ChannelLogo(c.logoUrl, c.name, 46.dp, corner = 11.dp)
                Box(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        c.name, style = MaterialTheme.typography.titleSmall, color = p.textPrimary,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                    )
                    if (item.isFavorite) Icon(Icons.Default.Star, stringResource(R.string.favorited), tint = p.star, modifier = Modifier.padding(start = 6.dp).size(16.dp))
                    if (c.locked || item.groupLocked) Icon(Icons.Default.Lock, stringResource(R.string.locked), tint = p.textTertiary, modifier = Modifier.padding(start = 6.dp).size(14.dp))
                }
                if (!compact) {
                    programLine(item)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = p.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 1.dp))
                    }
                    if (item.nowStart != null) SdProgress(progress, Modifier.padding(top = 6.dp, end = 24.dp), height = 2.5.dp)
                }
            }
            if (left != null) {
                Text(stringResource(DR.string.ds_minutes_short, left), color = p.textSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

/** Grid tile of the phone “网格” view. */
@Composable
fun ChannelTile(item: ChannelListItem, onClick: (ChannelListItem) -> Unit, onLongClick: ((ChannelListItem) -> Unit)?, modifier: Modifier = Modifier) {
    val c = item.channel
    val p = SdTheme.palette
    val now by rememberNow()
    FocusSurface(
        onClick = { onClick(item) },
        onLongClick = onLongClick?.let { { it(item) } },
        modifier = modifier.padding(6.dp).fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        contentDescription = c.name,
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(86.dp).background(tileTint(c.name)), contentAlignment = Alignment.Center) {
                ChannelLogo(c.logoUrl, c.name, 48.dp)
                c.number?.let { Text(it.toString(), color = p.textSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) }
                if (item.isFavorite) Icon(Icons.Default.Star, stringResource(R.string.favorited), tint = p.star, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(14.dp))
            }
            Column(Modifier.padding(10.dp)) {
                Text(c.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.nowTitle.orEmpty(), style = MaterialTheme.typography.bodySmall, color = p.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                SdProgress(ProgramTime.progress(item.nowStart, item.nowEnd, now), Modifier.padding(top = 6.dp))
            }
        }
    }
}

/** “继续观看” card: glow artwork with LIVE tag and logo, then name, programme and progress. */
@Composable
fun ContinueCard(item: ChannelListItem, onClick: (ChannelListItem) -> Unit, modifier: Modifier = Modifier) {
    val c = item.channel
    val p = SdTheme.palette
    val now by rememberNow()
    FocusSurface(
        onClick = { onClick(item) },
        modifier = modifier.width(220.dp),
        shape = RoundedCornerShape(16.dp),
        contentDescription = listOfNotNull(c.name, item.nowTitle).joinToString(", "),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(110.dp).background(p.cardHigh).background(tileTint(c.name)).backdropGlow(Color.Transparent)) {
                LiveBadge(Modifier.padding(10.dp))
                ChannelLogo(c.logoUrl, c.name, 44.dp, modifier = Modifier.align(Alignment.Center))
            }
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(c.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = LocalContentColor.current)
                Text(
                    item.nowTitle ?: c.groupName, style = MaterialTheme.typography.bodySmall, color = p.textSecondary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp),
                )
                SdProgress(ProgramTime.progress(item.nowStart, item.nowEnd, now), Modifier.padding(top = 8.dp))
            }
        }
    }
}

/**
 * TV card (design 4, “收藏频道”): tinted top with number and logo, name, programme and progress.
 * Focus = accent ring + scale; [onFocused] lets the home hero follow the focused channel.
 */
@Composable
fun TvChannelCard(
    item: ChannelListItem,
    onClick: (ChannelListItem) -> Unit,
    onFocused: (ChannelListItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = item.channel
    val p = SdTheme.palette
    val now by rememberNow()
    FocusSurface(
        onClick = { onClick(item) },
        modifier = modifier.width(200.dp).onFocusGained { onFocused(item) },
        shape = RoundedCornerShape(14.dp),
        focusScale = 1.05f,
        contentDescription = listOfNotNull(c.number?.toString(), c.name, item.nowTitle).joinToString(", "),
    ) {
        Column {
            Box(Modifier.fillMaxWidth().height(100.dp).background(tileTint(c.name)), contentAlignment = Alignment.Center) {
                c.number?.let { Text(it.toString(), color = p.textSecondary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.align(Alignment.TopStart).padding(10.dp)) }
                ChannelLogo(c.logoUrl, c.name, 58.dp, corner = 12.dp)
            }
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(c.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.nowTitle ?: c.groupName, style = MaterialTheme.typography.bodySmall, color = p.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                SdProgress(ProgramTime.progress(item.nowStart, item.nowEnd, now), Modifier.padding(top = 6.dp))
            }
        }
    }
}

/** TV list row (browse screen): focused row turns light like the design's zapping list. */
@Composable
fun TvChannelRow(item: ChannelListItem, onClick: (ChannelListItem) -> Unit, onLongClick: ((ChannelListItem) -> Unit)?, modifier: Modifier = Modifier) {
    val c = item.channel
    val p = SdTheme.palette
    val now by rememberNow()
    FocusSurface(
        onClick = { onClick(item) },
        onLongClick = onLongClick?.let { { it(item) } },
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        container = Color.Transparent,
        focusFill = true,
        contentDescription = listOfNotNull(c.number?.toString(), c.name, item.nowTitle).joinToString(", "),
    ) { focused ->
        val secondary = if (focused) p.onFocusFill.copy(alpha = 0.7f) else p.textSecondary
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(c.number?.toString().orEmpty(), color = secondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(44.dp))
            ChannelLogo(c.logoUrl, c.name, 40.dp, corner = 8.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(c.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (item.isFavorite) Icon(Icons.Default.Star, stringResource(R.string.favorited), tint = p.star, modifier = Modifier.padding(start = 6.dp).size(14.dp))
                    if (c.locked || item.groupLocked) Icon(Icons.Default.Lock, stringResource(R.string.locked), tint = secondary, modifier = Modifier.padding(start = 6.dp).size(14.dp))
                }
                programLine(item)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = secondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
            if (item.nowStart != null) {
                SdProgress(ProgramTime.progress(item.nowStart, item.nowEnd, now), Modifier.width(80.dp).padding(start = 12.dp))
            }
        }
    }
}
