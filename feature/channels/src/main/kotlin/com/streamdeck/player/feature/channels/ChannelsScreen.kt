package com.streamdeck.player.feature.channels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.streamdeck.player.core.data.GroupFilter
import com.streamdeck.player.core.database.ChannelGroupEntity
import com.streamdeck.player.core.database.ChannelListItem
import com.streamdeck.player.core.datastore.ChannelSort
import com.streamdeck.player.core.datastore.ChannelViewMode
import com.streamdeck.player.core.designsystem.EmptyState
import com.streamdeck.player.core.designsystem.ErrorState
import com.streamdeck.player.core.designsystem.FocusSurface
import com.streamdeck.player.core.designsystem.LoadingState
import com.streamdeck.player.core.designsystem.SdChip
import com.streamdeck.player.core.designsystem.SdSearchField
import com.streamdeck.player.core.designsystem.SdTheme
import com.streamdeck.player.core.designsystem.SectionHeader
import com.streamdeck.player.core.designsystem.R as DR

/**
 * Phone live home (design 1): brand title + playlist pill, search, group chips with counts, “继续观看” rail and the
 * paged channel list. Grid view uses the same scrollable with full-span headers. [onPlay] receives the full row so
 * the host can enforce parental locks before navigating.
 */
@Composable
fun ChannelsScreen(
    onAddPlaylist: () -> Unit,
    onManagePlaylists: () -> Unit,
    onPlay: (ChannelListItem) -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp = 88.dp,
    vm: ChannelsViewModel = hiltViewModel(),
) {
    val playlists by vm.playlistList.collectAsStateWithLifecycle()
    val active by vm.activePlaylist.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val refresh by vm.refresh.collectAsStateWithLifecycle()
    val counts by vm.counts.collectAsStateWithLifecycle()
    val recent by vm.recent.collectAsStateWithLifecycle()
    val p = SdTheme.palette

    Column(Modifier.fillMaxSize().background(p.background).windowInsetsPadding(WindowInsets.statusBars)) {
        val list = playlists
        when {
            list == null -> LoadingState()
            list.isEmpty() -> {
                BrandHeader(null, 0, emptyList(), vm, onManagePlaylists, onAddPlaylist)
                EmptyState(stringResource(R.string.no_playlists), actionLabel = stringResource(R.string.add_playlist), onAction = onAddPlaylist)
            }
            else -> {
                val items = vm.items.collectAsLazyPagingItems()
                val grid = settings.channelView == ChannelViewMode.GRID
                val searching = query.isNotBlank()
                LazyVerticalGrid(
                    columns = if (grid) GridCells.Adaptive(150.dp) else GridCells.Fixed(1),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomPadding),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    full("brand") { BrandHeader(active?.name, counts.total, list.map { it.id to it.name }, vm, onManagePlaylists, onAddPlaylist) }
                    full("search") {
                        SdSearchField(query, { vm.query.value = it }, stringResource(R.string.search_hint), Modifier.padding(top = 4.dp, bottom = 10.dp))
                    }
                    full("chips") { GroupChips(groups, filter, counts) { vm.filter.value = it } }
                    full("refresh") { RefreshBanner(refresh, vm::dismissRefreshError) }
                    if (!searching && filter == GroupFilter.All && recent.isNotEmpty()) {
                        full("continue_h") {
                            SectionHeader(
                                stringResource(R.string.continue_watching), Modifier.padding(top = 14.dp, bottom = 8.dp),
                                action = stringResource(R.string.see_all), onAction = { vm.filter.value = GroupFilter.Recent },
                            )
                        }
                        full("continue") {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(recent, key = { it.channel.id }) { ContinueCard(it, onPlay) }
                            }
                        }
                    }
                    full("list_h") {
                        val title = when {
                            searching -> stringResource(R.string.search_results)
                            filter == GroupFilter.All -> stringResource(R.string.all_channels_title)
                            filter == GroupFilter.Favorites -> stringResource(R.string.favorites)
                            filter == GroupFilter.Recent -> stringResource(R.string.recent)
                            else -> groups.firstOrNull { GroupFilter.Group(it.id) == filter }?.name.orEmpty()
                        }
                        Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(title, style = MaterialTheme.typography.titleLarge, color = p.textPrimary, modifier = Modifier.weight(1f))
                            SortMenu(settings.channelSort, settings.channelView, vm::setSort, vm::setView)
                        }
                    }
                    channelItems(items, grid, settings.channelView == ChannelViewMode.COMPACT, onPlay, vm::toggleFavorite)
                }
            }
        }
    }
}

private fun LazyGridScope.full(key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }
}

private fun LazyGridScope.channelItems(
    items: LazyPagingItems<ChannelListItem>,
    grid: Boolean,
    compact: Boolean,
    onPlay: (ChannelListItem) -> Unit,
    onFavorite: (ChannelListItem) -> Unit,
) {
    val refresh = items.loadState.refresh
    when {
        refresh is LoadState.Loading && items.itemCount == 0 -> full("loading") { LoadingState(Modifier.height(200.dp)) }
        refresh is LoadState.Error -> full("error") { ErrorState(refresh.error.message.orEmpty(), items::retry, Modifier.height(240.dp)) }
        items.itemCount == 0 -> full("empty") { EmptyState(stringResource(R.string.no_channels), Modifier.height(240.dp)) }
        else -> items(items.itemCount, key = items.itemKey { it.channel.id }, contentType = { "channel" }) { i ->
            val item = items[i] ?: return@items
            if (grid) ChannelTile(item, onPlay, onFavorite) else ChannelRow(item, onPlay, onFavorite, compact = compact)
        }
    }
}

@Composable
private fun BrandHeader(
    name: String?,
    total: Int,
    playlists: List<Pair<Long, String>>,
    vm: ChannelsViewModel,
    onManage: () -> Unit,
    onAdd: () -> Unit,
) {
    val p = SdTheme.palette
    var menu by remember { mutableStateOf(false) }
    val activeId by vm.activeId.collectAsStateWithLifecycle()
    Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = p.textPrimary)) { append(stringResource(R.string.brand_stream)) }
                withStyle(SpanStyle(color = p.accent)) { append(stringResource(R.string.brand_deck)) }
            },
            fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f),
        )
        Box {
            FocusSurface(
                onClick = { menu = true }, shape = RoundedCornerShape(50), container = p.card, focusFill = true,
                contentDescription = stringResource(R.string.switch_playlist),
            ) { focused ->
                Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(p.accent, CircleShape))
                    Text(
                        if (name != null) stringResource(R.string.playlist_pill, name, total) else stringResource(R.string.playlists),
                        color = if (focused) p.onFocusFill else p.textSecondary, style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1, modifier = Modifier.padding(start = 8.dp),
                    )
                    Icon(Icons.Default.ArrowDropDown, null, tint = if (focused) p.onFocusFill else p.textSecondary)
                }
            }
            DropdownMenu(menu, { menu = false }, containerColor = p.cardHigh) {
                playlists.forEach { (id, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        leadingIcon = { if (id == activeId) Icon(Icons.Default.Check, null, tint = p.accent) else Box(Modifier.size(24.dp)) },
                        onClick = { menu = false; vm.selectPlaylist(id) },
                    )
                }
                if (playlists.isNotEmpty()) HorizontalDivider(color = p.stroke)
                activeId?.let { id ->
                    DropdownMenuItem(text = { Text(stringResource(R.string.refresh)) }, leadingIcon = { Icon(Icons.Default.Refresh, null) }, onClick = { menu = false; vm.refresh(id) })
                }
                DropdownMenuItem(text = { Text(stringResource(R.string.manage_playlists)) }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { menu = false; onManage() })
                DropdownMenuItem(text = { Text(stringResource(R.string.add_playlist)) }, leadingIcon = { Icon(Icons.Default.Add, null) }, onClick = { menu = false; onAdd() })
            }
        }
    }
}

@Composable
internal fun GroupChips(groups: List<ChannelGroupEntity>, selected: GroupFilter, counts: ChipCounts, onSelect: (GroupFilter) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item(key = "all") { SdChip(stringResource(R.string.all_channels), selected == GroupFilter.All, { onSelect(GroupFilter.All) }, count = counts.total) }
        item(key = "fav") {
            SdChip(stringResource(R.string.favorites), selected == GroupFilter.Favorites, { onSelect(GroupFilter.Favorites) }, count = counts.favorites, leading = Icons.Default.Star)
        }
        item(key = "recent") { SdChip(stringResource(R.string.recent), selected == GroupFilter.Recent, { onSelect(GroupFilter.Recent) }) }
        items(groups, key = { it.id }) { g ->
            val f = GroupFilter.Group(g.id)
            SdChip(g.name, selected == f, { onSelect(f) }, count = counts.groups[g.id])
        }
    }
}

@Composable
private fun RefreshBanner(refresh: RefreshState, onDismiss: () -> Unit) {
    val p = SdTheme.palette
    when (refresh) {
        is RefreshState.Running -> Column(Modifier.padding(top = 12.dp)) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = p.accent, trackColor = p.track)
            Text(stringResource(R.string.refreshing, refresh.count), color = p.textSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
        }
        is RefreshState.Failed -> Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.refresh_failed, refresh.reason), color = p.live, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text(stringResource(DR.string.ds_ok), color = p.accent) }
        }
        RefreshState.Idle -> Box(Modifier.size(0.dp))
    }
}

@Composable
internal fun SortMenu(sort: ChannelSort, view: ChannelViewMode, onSort: (ChannelSort) -> Unit, onView: (ChannelViewMode) -> Unit) {
    val p = SdTheme.palette
    var open by remember { mutableStateOf(false) }
    Box {
        FocusSurface(onClick = { open = true }, shape = RoundedCornerShape(8.dp), container = Color.Transparent, contentDescription = stringResource(R.string.sort)) {
            Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(sort.shortLabelRes()), color = p.accent, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                Icon(Icons.Default.ArrowDropDown, null, tint = p.accent)
            }
        }
        DropdownMenu(open, { open = false }, containerColor = p.cardHigh) {
            Text(stringResource(R.string.sort), color = p.textTertiary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            ChannelSort.entries.forEach { s ->
                DropdownMenuItem(
                    text = { Text(stringResource(s.labelRes())) },
                    trailingIcon = { if (s == sort) Icon(Icons.Default.Check, null, tint = p.accent) },
                    onClick = { open = false; onSort(s) },
                )
            }
            HorizontalDivider(color = p.stroke)
            Text(stringResource(R.string.view_mode), color = p.textTertiary, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            ChannelViewMode.entries.forEach { m ->
                DropdownMenuItem(
                    text = { Text(stringResource(m.labelRes())) },
                    trailingIcon = { if (m == view) Icon(Icons.Default.Check, null, tint = p.accent) },
                    onClick = { open = false; onView(m) },
                )
            }
        }
    }
}

internal fun ChannelViewMode.labelRes() = when (this) {
    ChannelViewMode.LIST -> R.string.view_list
    ChannelViewMode.GRID -> R.string.view_grid
    ChannelViewMode.COMPACT -> R.string.view_compact
}

internal fun ChannelSort.labelRes() = when (this) {
    ChannelSort.DEFAULT -> R.string.sort_default
    ChannelSort.NAME -> R.string.sort_name
    ChannelSort.NUMBER -> R.string.sort_number
    ChannelSort.CUSTOM -> R.string.sort_custom
}

internal fun ChannelSort.shortLabelRes() = when (this) {
    ChannelSort.DEFAULT -> R.string.sort_by_default
    ChannelSort.NAME -> R.string.sort_by_name
    ChannelSort.NUMBER -> R.string.sort_by_number
    ChannelSort.CUSTOM -> R.string.sort_by_custom
}
