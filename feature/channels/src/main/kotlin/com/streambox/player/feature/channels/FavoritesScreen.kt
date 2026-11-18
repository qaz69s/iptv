@file:Suppress("MatchingDeclarationName")

package com.streambox.player.feature.channels

import android.text.format.DateUtils
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.streambox.player.core.designsystem.component.EmptyState
import com.streambox.player.core.designsystem.component.LoadingState
import com.streambox.player.core.designsystem.component.focusRing
import com.streambox.player.core.designsystem.icon.StreamBoxIcons
import com.streambox.player.core.model.Channel
import com.streambox.player.core.model.ChannelKey
import com.streambox.player.core.model.ChannelSource

/** Callbacks of the favorites / history page. */
internal data class FavoritesActions(
    val onTab: (LibraryTab) -> Unit = {},
    val onPlay: (Channel, ChannelSource) -> Unit = { _, _ -> },
    val onMove: (ChannelKey, up: Boolean) -> Unit = { _, _ -> },
    val onRemoveFavorite: (ChannelKey) -> Unit = {},
    val onResetOrder: () -> Unit = {},
    val onRemoveFromHistory: (ChannelKey) -> Unit = {},
    val onClearHistory: () -> Unit = {},
)

@Composable
internal fun FavoritesEntry(
    onChannelClick: (Channel, ChannelSource) -> Unit,
    viewModel: FavoritesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    FavoritesScreen(
        state = state,
        actions = FavoritesActions(
            onTab = viewModel::selectTab,
            onPlay = onChannelClick,
            onMove = viewModel::moveFavorite,
            onRemoveFavorite = viewModel::removeFavorite,
            onResetOrder = viewModel::resetFavoriteOrder,
            onRemoveFromHistory = viewModel::removeFromHistory,
            onClearHistory = viewModel::clearHistory,
        ),
    )
}

/**
 * Favorites and recently watched in two tabs. Every row plays on click / OK; its buttons (move up /
 * down, remove) are separate focus stops, so the remote reaches them with Right.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FavoritesScreen(state: FavoritesUiState, actions: FavoritesActions, modifier: Modifier = Modifier) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.feature_channels_favorites_title)) },
            actions = {
                if (state.tab == LibraryTab.Favorites && state.favorites.size > 1) {
                    TextButton(onClick = actions.onResetOrder, modifier = Modifier.focusRing()) {
                        Text(stringResource(R.string.feature_channels_reset_order))
                    }
                }
                if (state.tab == LibraryTab.Recent && state.history.isNotEmpty()) {
                    TextButton(onClick = { confirmClear = true }, modifier = Modifier.focusRing()) {
                        Text(stringResource(R.string.feature_channels_clear_history))
                    }
                }
            },
        )
        PrimaryTabRow(selectedTabIndex = state.tab.ordinal) {
            LibraryTab.entries.forEach { tab ->
                Tab(
                    selected = tab == state.tab,
                    onClick = { actions.onTab(tab) },
                    text = { Text(stringResource(tab.labelRes())) },
                    modifier = Modifier.focusRing(shape = RoundedCornerShape(4.dp)),
                )
            }
        }
        val listModifier = Modifier.fillMaxSize()
        when {
            state.loading -> LoadingState(
                message = stringResource(com.streambox.player.core.ui.R.string.core_ui_loading),
                modifier = listModifier,
            )

            state.tab == LibraryTab.Favorites -> FavoritesList(state, actions, listModifier)

            else -> HistoryList(state, actions, listModifier)
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.feature_channels_clear_history)) },
            text = { Text(stringResource(R.string.feature_channels_clear_history_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        actions.onClearHistory()
                    },
                    modifier = Modifier.focusRing(),
                ) { Text(stringResource(R.string.feature_channels_clear_history)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }, modifier = Modifier.focusRing()) {
                    Text(stringResource(R.string.feature_channels_cancel))
                }
            },
        )
    }
}

@Composable
private fun FavoritesList(state: FavoritesUiState, actions: FavoritesActions, modifier: Modifier) {
    if (state.favorites.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.feature_channels_favorites_empty_title),
            message = stringResource(R.string.feature_channels_favorites_empty_message),
            icon = StreamBoxIcons.Favorites,
            modifier = modifier,
        )
        return
    }
    LazyColumn(modifier, contentPadding = PaddingValues(vertical = 8.dp)) {
        itemsIndexed(state.favorites, key = { _, channel ->
            "${channel.playlistId}/${channel.uniqueKey}"
        }) { index, channel ->
            LibraryRow(
                channel = channel,
                allowCleartext = channel.playlistId in state.cleartextPlaylists,
                supporting = null,
                onClick = { actions.onPlay(channel, ChannelSource.Favorites) },
            ) {
                RowButton(
                    icon = StreamBoxIcons.ArrowUp,
                    description = stringResource(R.string.feature_channels_move_up, channel.name),
                    enabled = index > 0,
                    onClick = { actions.onMove(channel.key, true) },
                )
                RowButton(
                    icon = StreamBoxIcons.ArrowDown,
                    description = stringResource(R.string.feature_channels_move_down, channel.name),
                    enabled = index < state.favorites.lastIndex,
                    onClick = { actions.onMove(channel.key, false) },
                )
                RowButton(
                    icon = StreamBoxIcons.Delete,
                    description = stringResource(R.string.feature_channels_remove_favorite, channel.name),
                    onClick = { actions.onRemoveFavorite(channel.key) },
                )
            }
            HorizontalDivider(Modifier.padding(start = 88.dp))
        }
    }
}

@Composable
private fun HistoryList(state: FavoritesUiState, actions: FavoritesActions, modifier: Modifier) {
    if (state.history.isEmpty()) {
        EmptyState(
            title = stringResource(R.string.feature_channels_recent_empty_title),
            message = stringResource(R.string.feature_channels_recent_empty_message),
            icon = StreamBoxIcons.History,
            modifier = modifier,
        )
        return
    }
    LazyColumn(modifier, contentPadding = PaddingValues(vertical = 8.dp)) {
        itemsIndexed(state.history, key = { _, entry ->
            "${entry.channel.playlistId}/${entry.channel.uniqueKey}"
        }) { _, entry ->
            val channel = entry.channel
            LibraryRow(
                channel = channel,
                allowCleartext = channel.playlistId in state.cleartextPlaylists,
                supporting = stringResource(
                    R.string.feature_channels_watched_at,
                    DateUtils.getRelativeTimeSpanString(entry.watchedAtUtcMs).toString(),
                ),
                onClick = { actions.onPlay(channel, ChannelSource.Recent) },
            ) {
                RowButton(
                    icon = StreamBoxIcons.Close,
                    description = stringResource(R.string.feature_channels_remove_history, channel.name),
                    onClick = { actions.onRemoveFromHistory(channel.key) },
                )
            }
            HorizontalDivider(Modifier.padding(start = 88.dp))
        }
    }
}

@Composable
private fun LibraryRow(
    channel: Channel,
    allowCleartext: Boolean,
    supporting: String?,
    onClick: () -> Unit,
    buttons: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val shape = RoundedCornerShape(8.dp)
        Row(
            modifier = Modifier
                .weight(1f)
                .focusRing(shape = shape)
                .clip(shape)
                .clickable(role = Role.Button, onClickLabel = channel.name, onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelLogo(channel, allowCleartext)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    channel.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (supporting != null) {
                    Text(
                        supporting,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) { buttons() }
    }
}

@Composable
private fun RowButton(@DrawableRes icon: Int, description: String, onClick: () -> Unit, enabled: Boolean = true) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.focusRing()) {
        Icon(painterResource(icon), contentDescription = description)
    }
}

private fun LibraryTab.labelRes(): Int = when (this) {
    LibraryTab.Favorites -> R.string.feature_channels_tab_favorites
    LibraryTab.Recent -> R.string.feature_channels_tab_recent
}
