package com.streamdeck.player.feature.channels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.streamdeck.player.core.database.ChannelListItem
import com.streamdeck.player.core.designsystem.ChannelLogo
import com.streamdeck.player.core.designsystem.ClockText
import com.streamdeck.player.core.designsystem.EmptyState
import com.streamdeck.player.core.designsystem.FocusSurface
import com.streamdeck.player.core.designsystem.LiveBadge
import com.streamdeck.player.core.designsystem.LoadingState
import com.streamdeck.player.core.designsystem.ProgramTime
import com.streamdeck.player.core.designsystem.SdProgress
import com.streamdeck.player.core.designsystem.SdTheme
import com.streamdeck.player.core.designsystem.backdropGlow
import com.streamdeck.player.core.designsystem.formatClock
import com.streamdeck.player.core.designsystem.rememberNow
import com.streamdeck.player.core.designsystem.R as DR

/**
 * TV home (design 4). The hero follows the focused card; rows are favourites, recently watched and the first
 * groups. [onOpenGuide] opens the per-channel programme list (navigation is owned by the app module).
 */
@Composable
fun TvHomeScreen(
    onPlay: (ChannelListItem) -> Unit,
    onOpenGuide: (channelId: Long) -> Unit,
    onAddPlaylist: () -> Unit,
    vm: TvHomeViewModel = hiltViewModel(),
) {
    val playlists by vm.playlistList.collectAsStateWithLifecycle()
    val active by vm.active.collectAsStateWithLifecycle()
    val total by vm.total.collectAsStateWithLifecycle()
    val rows by vm.rows.collectAsStateWithLifecycle()
    var heroId by rememberSaveable { mutableStateOf<Long?>(null) }
    val p = SdTheme.palette
    val all = rows.orEmpty().flatMap { it.items }
    val hero = all.firstOrNull { it.channel.id == heroId } ?: all.firstOrNull()
    val watchFocus = remember { FocusRequester() }
    var initialFocusDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(hero != null) {
        if (hero != null && !initialFocusDone) {
            initialFocusDone = true
            runCatching { watchFocus.requestFocus() }
        }
    }

    Box(Modifier.fillMaxSize().backdropGlow()) {
        when {
            playlists == null || (playlists?.isNotEmpty() == true && rows == null) -> LoadingState()
            playlists?.isEmpty() == true -> EmptyState(stringResource(R.string.no_playlists), actionLabel = stringResource(R.string.add_playlist), onAction = onAddPlaylist)
            all.isEmpty() -> EmptyState(stringResource(R.string.no_channels))
            else -> Column(Modifier.fillMaxSize()) {
                hero?.let { Hero(it, watchFocus, onPlay, vm::toggleFavorite, onOpenGuide) }
                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(start = 56.dp, end = 32.dp, bottom = 32.dp, top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    items(rows.orEmpty(), key = { it.key }) { row ->
                        Column {
                            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(bottom = 10.dp)) {
                                Text(row.title ?: stringResource(row.titleRes ?: R.string.all_channels), style = MaterialTheme.typography.titleLarge, color = p.textPrimary)
                                row.count?.let { Text(it.toString(), color = p.textTertiary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 8.dp, bottom = 2.dp)) }
                            }
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp)) {
                                items(row.items, key = { "${row.key}_${it.channel.id}" }) { item ->
                                    TvChannelCard(item, onPlay, onFocused = { heroId = it.channel.id })
                                }
                            }
                        }
                    }
                }
            }
        }
        Row(Modifier.align(Alignment.TopEnd).padding(top = 28.dp, end = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            active?.let { Text(stringResource(R.string.tv_playlist_info, it.name, total), color = p.textSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 14.dp)) }
            ClockText(MaterialTheme.typography.titleLarge)
        }
    }
}

@Composable
private fun Hero(
    item: ChannelListItem,
    watchFocus: FocusRequester,
    onPlay: (ChannelListItem) -> Unit,
    onFavorite: (ChannelListItem) -> Unit,
    onOpenGuide: (Long) -> Unit,
) {
    val c = item.channel
    val p = SdTheme.palette
    val now by rememberNow()
    Column(Modifier.padding(start = 56.dp, top = 40.dp, end = 32.dp).widthIn(max = 560.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChannelLogo(c.logoUrl, c.name, 44.dp, corner = 10.dp)
            Text(listOfNotNull(c.number?.toString(), c.name).joinToString("  "), color = p.textSecondary, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 12.dp))
            LiveBadge(small = true)
        }
        Text(
            item.nowTitle ?: c.name, color = p.textPrimary, fontSize = 40.sp, lineHeight = 48.sp, fontWeight = FontWeight.ExtraBold,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 16.dp),
        )
        Text(c.groupName, color = p.textSecondary, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
        if (item.nowStart != null && item.nowEnd != null) {
            SdProgress(ProgramTime.progress(item.nowStart, item.nowEnd, now), Modifier.width(350.dp).padding(top = 16.dp))
            Row(Modifier.width(350.dp).padding(top = 8.dp)) {
                Text("${formatClock(item.nowStart ?: 0)} – ${formatClock(item.nowEnd ?: 0)}", color = p.textSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                ProgramTime.minutesLeft(item.nowEnd, now)?.let { Text(stringResource(DR.string.ds_minutes_left, it), color = p.textSecondary, style = MaterialTheme.typography.bodyMedium) }
            }
        } else {
            Box(Modifier.height(16.dp))
        }
        Row(Modifier.padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HeroButton(Icons.Default.PlayArrow, stringResource(R.string.watch_now), Modifier.focusRequester(watchFocus)) { onPlay(item) }
            HeroButton(
                if (item.isFavorite) Icons.Default.Star else Icons.Outlined.StarOutline,
                stringResource(if (item.isFavorite) R.string.favorited else R.string.favorite),
            ) { onFavorite(item) }
            HeroButton(Icons.AutoMirrored.Filled.List, stringResource(R.string.guide)) { onOpenGuide(c.id) }
        }
    }
}

@Composable
private fun HeroButton(icon: ImageVector, label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FocusSurface(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(12.dp), container = SdTheme.palette.cardHigh, focusFill = true, focusScale = 1.04f, contentDescription = label) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = LocalContentColor.current)
            Text(label, color = LocalContentColor.current, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
        }
    }
}
