package com.streamdeck.player.feature.channels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.streamdeck.player.core.data.GroupFilter
import com.streamdeck.player.core.database.ChannelListItem
import com.streamdeck.player.core.designsystem.EmptyState
import com.streamdeck.player.core.designsystem.ErrorState
import com.streamdeck.player.core.designsystem.FocusSurface
import com.streamdeck.player.core.designsystem.LoadingState
import com.streamdeck.player.core.designsystem.SdSearchField
import com.streamdeck.player.core.designsystem.SdTheme

/**
 * TV browser (rail “搜索”): left column = search box + groups with counts, right = channel list.
 * Focus moves left/right between the panes; every row is focusable, nothing traps focus.
 */
@Composable
fun TvBrowseScreen(
    onPlay: (ChannelListItem) -> Unit,
    onAddPlaylist: () -> Unit,
    focusSearch: Boolean = true,
    vm: ChannelsViewModel = hiltViewModel(),
) {
    val playlists by vm.playlistList.collectAsStateWithLifecycle()
    val groups by vm.groups.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val counts by vm.counts.collectAsStateWithLifecycle()
    val active by vm.activePlaylist.collectAsStateWithLifecycle()
    val p = SdTheme.palette
    val searchFocus = remember { FocusRequester() }
    LaunchedEffect(focusSearch) { if (focusSearch) runCatching { searchFocus.requestFocus() } }

    if (playlists?.isEmpty() == true) {
        EmptyState(stringResource(R.string.no_playlists), actionLabel = stringResource(R.string.add_playlist), onAction = onAddPlaylist)
        return
    }
    Row(Modifier.fillMaxSize().padding(start = 24.dp, top = 28.dp, end = 32.dp)) {
        Column(Modifier.width(300.dp).fillMaxHeight()) {
            Text(active?.name ?: stringResource(R.string.playlists), style = MaterialTheme.typography.headlineSmall, color = p.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            SdSearchField(query, { vm.query.value = it }, stringResource(R.string.search_hint), Modifier.padding(vertical = 14.dp).focusRequester(searchFocus))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                item(key = "all") { GroupItem(stringResource(R.string.all_channels), counts.total, filter == GroupFilter.All) { vm.filter.value = GroupFilter.All } }
                item(key = "fav") { GroupItem(stringResource(R.string.favorites), counts.favorites, filter == GroupFilter.Favorites) { vm.filter.value = GroupFilter.Favorites } }
                item(key = "recent") { GroupItem(stringResource(R.string.recent), null, filter == GroupFilter.Recent) { vm.filter.value = GroupFilter.Recent } }
                items(groups, key = { it.id }) { g ->
                    val f = GroupFilter.Group(g.id)
                    GroupItem(g.name, counts.groups[g.id], filter == f) { vm.filter.value = f }
                }
            }
        }
        val items = vm.items.collectAsLazyPagingItems()
        Box(Modifier.weight(1f).fillMaxHeight().padding(start = 24.dp)) {
            val refresh = items.loadState.refresh
            when {
                refresh is LoadState.Loading && items.itemCount == 0 -> LoadingState()
                refresh is LoadState.Error -> ErrorState(refresh.error.message.orEmpty(), items::retry)
                items.itemCount == 0 -> EmptyState(stringResource(R.string.no_channels))
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp)) {
                    items(items.itemCount, key = items.itemKey { it.channel.id }) { i ->
                        val item = items[i] ?: return@items
                        TvChannelRow(item, onPlay, vm::toggleFavorite)
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupItem(label: String, count: Int?, selected: Boolean, onClick: () -> Unit) {
    val p = SdTheme.palette
    FocusSurface(
        onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        container = if (selected) p.accent.copy(alpha = 0.16f) else Color.Transparent, focusFill = true,
        contentDescription = listOfNotNull(label, count?.toString()).joinToString(" "),
    ) { focused ->
        val fg = when {
            focused -> p.onFocusFill
            selected -> p.accent
            else -> p.textPrimary
        }
        Row(Modifier.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = fg, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            count?.let { Text(it.toString(), color = fg.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall) }
        }
    }
}
