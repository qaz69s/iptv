package com.streamdeck.player.feature.channels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.LoadState
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.streamdeck.player.core.data.ChannelRepository
import com.streamdeck.player.core.data.LibraryRepository
import com.streamdeck.player.core.database.ChannelListItem
import com.streamdeck.player.core.designsystem.EmptyState
import com.streamdeck.player.core.designsystem.LoadingState
import com.streamdeck.player.core.designsystem.SdTheme
import com.streamdeck.player.core.designsystem.rememberIsTv
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FavoritesViewModel @Inject constructor(
    channels: ChannelRepository,
    private val library: LibraryRepository,
) : ViewModel() {
    val items = channels.allFavorites().cachedIn(viewModelScope)
    fun toggle(item: ChannelListItem) { viewModelScope.launch { library.setFavorite(item.channel, !item.isFavorite) } }
}

/** Favourites of every playlist (TV rail “收藏”; phone reaches favourites through the chip). Long-press removes. */
@Composable
fun FavoritesScreen(onPlay: (ChannelListItem) -> Unit, vm: FavoritesViewModel = hiltViewModel()) {
    val items = vm.items.collectAsLazyPagingItems()
    val tv = rememberIsTv()
    val p = SdTheme.palette
    Column(Modifier.fillMaxSize().background(p.background).padding(horizontal = if (tv) 48.dp else 20.dp, vertical = if (tv) 32.dp else 20.dp)) {
        Text(stringResource(R.string.favorites), style = if (tv) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge, color = p.textPrimary)
        val refresh = items.loadState.refresh
        when {
            refresh is LoadState.Loading && items.itemCount == 0 -> LoadingState()
            items.itemCount == 0 -> EmptyState(stringResource(R.string.no_favorites))
            else -> LazyColumn(contentPadding = PaddingValues(top = 16.dp, bottom = 110.dp), verticalArrangement = Arrangement.spacedBy(if (tv) 6.dp else 0.dp)) {
                items(items.itemCount, key = items.itemKey { it.channel.id }) { i ->
                    val item = items[i] ?: return@items
                    if (tv) TvChannelRow(item, onPlay, vm::toggle) else ChannelRow(item, onPlay, vm::toggle)
                }
            }
        }
    }
}
