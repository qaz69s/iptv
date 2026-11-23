package com.streamdeck.player.feature.playerui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.streamdeck.player.core.database.ChannelEntity
import com.streamdeck.player.core.database.ChannelGroupEntity
import com.streamdeck.player.core.database.ChannelListItem
import com.streamdeck.player.core.designsystem.ChannelLogo
import com.streamdeck.player.core.designsystem.FocusSurface
import com.streamdeck.player.core.designsystem.OnVideo
import com.streamdeck.player.core.designsystem.ProgramTime
import com.streamdeck.player.core.designsystem.SdProgress
import com.streamdeck.player.core.designsystem.SdTheme
import com.streamdeck.player.core.designsystem.rememberNow

/** One tab of the channel list: favourites or a playlist group. */
internal data class GroupTab(val id: Long, val name: String)

@Composable
internal fun rememberGroupTabs(groups: List<ChannelGroupEntity>): List<GroupTab> {
    val favorites = stringResource(R.string.favorites_tab)
    return remember(groups, favorites) { listOf(GroupTab(PlayerViewModel.FAVORITES_GROUP, favorites)) + groups.map { GroupTab(it.id, it.name) } }
}

/**
 * Group chips shared by the tablet side panel and the TV zap list. [selected] is the browsed group id; chips are
 * [FocusSurface]s so the remote's D-pad can reach them.
 */
@Composable
internal fun GroupTabsRow(tabs: List<GroupTab>, selected: Long, onSelect: (Long) -> Unit, modifier: Modifier = Modifier) {
    val p = SdTheme.palette
    val state = rememberLazyListState()
    val index = tabs.indexOfFirst { it.id == selected }
    LaunchedEffect(index) { if (index > 0) state.animateScrollToItem((index - 1).coerceAtLeast(0)) }
    LazyRow(state = state, modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 2.dp)) {
        items(tabs, key = { it.id }) { tab ->
            val active = tab.id == selected
            FocusSurface(
                onClick = { onSelect(tab.id) }, shape = RoundedCornerShape(50),
                container = if (active) p.accent else Color.White.copy(alpha = 0.10f), focusFill = true, contentDescription = tab.name,
            ) {
                Text(
                    tab.name, color = if (active) Color.White else p.textPrimary, style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal, maxLines = 1,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                )
            }
        }
    }
}

/**
 * Tablet / phone landscape channel list: slides in from the right over the video, group tabs on top, tap a channel
 * to switch without leaving full screen. Taps outside the panel close it.
 */
@Suppress("LongParameterList")
@Composable
fun BoxScope.ChannelSidePanel(
    visible: Boolean,
    current: ChannelEntity?,
    groups: List<ChannelGroupEntity>,
    browseGroup: Long?,
    items: List<ChannelListItem>,
    onSelectGroup: (Long) -> Unit,
    onPlay: (Long) -> Unit,
    onClose: () -> Unit,
) {
    if (visible) {
        // Catch taps on the video beside the panel so they close it instead of toggling the overlay.
        Box(
            Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        )
    }
    AnimatedVisibility(
        visible,
        enter = slideInHorizontally { it } + fadeIn(),
        exit = slideOutHorizontally { it } + fadeOut(),
        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
    ) {
        OnVideo { SidePanelContent(current, groups, browseGroup, items, onSelectGroup, onPlay, onClose) }
    }
}

@Suppress("LongParameterList")
@Composable
private fun SidePanelContent(
    current: ChannelEntity?,
    groups: List<ChannelGroupEntity>,
    browseGroup: Long?,
    items: List<ChannelListItem>,
    onSelectGroup: (Long) -> Unit,
    onPlay: (Long) -> Unit,
    onClose: () -> Unit,
) {
    run {
        val tabs = rememberGroupTabs(groups)
        val selected = browseGroup ?: current?.groupId ?: PlayerViewModel.FAVORITES_GROUP
        Column(
            Modifier
                .fillMaxHeight()
                .width(PANEL_WIDTH)
                .background(Color(PANEL_COLOR))
                .pointerInput(Unit) { detectTapGestures { } }
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.End + WindowInsetsSides.Vertical))
                .padding(top = 12.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.channel_list), color = Color.White, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) { Icon(Icons.Default.Close, stringResource(R.string.close_panel), tint = Color.White.copy(alpha = 0.8f)) }
            }
            GroupTabsRow(tabs, selected, onSelectGroup, Modifier.padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 8.dp))
            ChannelRows(current, items, onPlay)
        }
    }
}

@Composable
private fun ChannelRows(current: ChannelEntity?, items: List<ChannelListItem>, onPlay: (Long) -> Unit) {
    val p = SdTheme.palette
    val now by rememberNow()
    val state = rememberLazyListState()
    val index = items.indexOfFirst { it.channel.id == current?.id }
    LaunchedEffect(index, items.size) { if (index > 1) state.scrollToItem(index - 1) }
    LazyColumn(state = state, contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items(items, key = { it.channel.id }) { item ->
            val c = item.channel
            val playing = c.id == current?.id
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (playing) p.accent.copy(alpha = 0.25f) else Color.Transparent)
                    .clickable { onPlay(c.id) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    c.number?.toString().orEmpty(), color = if (playing) Color.White else Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(34.dp),
                )
                ChannelLogo(c.logoUrl, c.name, 38.dp, corner = 8.dp)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            c.name, color = Color.White, style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (playing) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false),
                        )
                        if (playing) {
                            Text(
                                stringResource(R.string.now_watching), color = p.accent, style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                    item.nowTitle?.let {
                        Text(
                            it, color = Color.White.copy(alpha = 0.65f), style = MaterialTheme.typography.bodySmall,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (item.nowStart != null && item.nowEnd != null) {
                        SdProgress(
                            ProgramTime.progress(item.nowStart, item.nowEnd, now), Modifier.padding(top = 5.dp), height = 3.dp,
                            color = if (playing) p.accent else Color.White.copy(alpha = 0.55f),
                        )
                    }
                }
            }
        }
    }
}

private val PANEL_WIDTH = 360.dp
private const val PANEL_COLOR = 0xE6101218
