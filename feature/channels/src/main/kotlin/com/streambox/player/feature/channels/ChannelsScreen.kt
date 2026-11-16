package com.streambox.player.feature.channels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil3.compose.AsyncImage
import com.streambox.player.core.designsystem.component.EmptyState
import com.streambox.player.core.designsystem.component.LoadingState
import com.streambox.player.core.designsystem.component.focusRing
import com.streambox.player.core.designsystem.icon.StreamBoxIcons
import com.streambox.player.core.designsystem.theme.StreamBoxTheme
import com.streambox.player.core.model.Channel
import com.streambox.player.core.model.ChannelGroupSummary
import com.streambox.player.core.ui.FormFactor
import com.streambox.player.core.ui.LocalFormFactor

/**
 * Channel browser. A fresh install has no playlists and therefore no channels; nothing is ever
 * bundled with the app. Playback is owned by the app module through [onChannelClick].
 */
@Composable
internal fun ChannelsEntry(
    onAddPlaylist: () -> Unit,
    onManagePlaylists: () -> Unit,
    onChannelClick: (channelId: Long) -> Unit,
    viewModel: ChannelsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val channels = viewModel.channels.collectAsLazyPagingItems()
    ChannelsScreen(
        state = state,
        channels = channels,
        onGroupSelected = viewModel::onGroupSelected,
        onAddPlaylist = onAddPlaylist,
        onManagePlaylists = onManagePlaylists,
        onChannelClick = onChannelClick,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChannelsScreen(
    state: ChannelsUiState,
    channels: LazyPagingItems<Channel>?,
    onGroupSelected: (String?) -> Unit,
    onAddPlaylist: () -> Unit,
    onManagePlaylists: () -> Unit,
    onChannelClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasPlaylists = state !is ChannelsUiState.NoPlaylists && state !is ChannelsUiState.Loading
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_channels_title)) },
                actions = {
                    if (hasPlaylists) {
                        IconButton(onClick = onManagePlaylists, modifier = Modifier.focusRing()) {
                            Icon(
                                painterResource(StreamBoxIcons.Playlists),
                                contentDescription = stringResource(R.string.feature_channels_manage_playlists),
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            // The empty state already has a big "Add playlist" button; avoid two identical actions there.
            if (hasPlaylists) {
                FloatingActionButton(onClick = onAddPlaylist, modifier = Modifier.focusRing()) {
                    Icon(
                        painterResource(StreamBoxIcons.Add),
                        contentDescription = stringResource(R.string.feature_channels_add_playlist),
                    )
                }
            }
        },
    ) { padding ->
        val contentModifier = Modifier.fillMaxSize().padding(padding)
        when (state) {
            ChannelsUiState.Loading -> LoadingState(
                message = stringResource(com.streambox.player.core.ui.R.string.core_ui_loading),
                modifier = contentModifier,
            )

            ChannelsUiState.NoPlaylists -> NoPlaylistsContent(onAddPlaylist, contentModifier)

            ChannelsUiState.NoChannels -> EmptyState(
                title = stringResource(R.string.feature_channels_no_channels_title),
                message = stringResource(R.string.feature_channels_no_channels_message),
                icon = StreamBoxIcons.Playlists,
                actionLabel = stringResource(R.string.feature_channels_manage_playlists),
                onAction = onManagePlaylists,
                modifier = contentModifier,
            )

            is ChannelsUiState.Content -> Column(contentModifier) {
                GroupChips(state.groups, state.totalChannels, state.selectedGroup, onGroupSelected)
                if (channels != null) ChannelList(channels, onChannelClick)
            }
        }
    }
}

/** The primary call to action of a fresh install: a real, focusable button (not a decorative icon). */
@Composable
private fun NoPlaylistsContent(onAddPlaylist: () -> Unit, modifier: Modifier = Modifier) {
    val focusRequester = remember { FocusRequester() }
    val isTv = LocalFormFactor.current == FormFactor.Tv
    if (isTv) {
        LaunchedEffect(focusRequester) { runCatching { focusRequester.requestFocus() } }
    }
    Column(
        modifier = modifier.padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painter = painterResource(StreamBoxIcons.PlaylistAdd),
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            stringResource(R.string.feature_channels_empty_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.feature_channels_empty_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 480.dp),
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onAddPlaylist,
            modifier = Modifier
                .focusRequester(focusRequester)
                .focusRing(),
        ) {
            Icon(painterResource(StreamBoxIcons.Add), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.feature_channels_add_playlist))
        }
    }
}

@Composable
private fun GroupChips(
    groups: List<ChannelGroupSummary>,
    totalChannels: Int,
    selectedGroup: String?,
    onGroupSelected: (String?) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "__all__") {
            GroupChip(
                label = stringResource(R.string.feature_channels_group_all, totalChannels),
                selected = selectedGroup == null,
                onClick = { onGroupSelected(null) },
            )
        }
        items(groups, key = { "g:" + it.name }) { group ->
            GroupChip(
                label = stringResource(
                    R.string.feature_channels_group_label,
                    group.name.ifBlank { stringResource(R.string.feature_channels_group_ungrouped) },
                    group.channelCount,
                ),
                selected = selectedGroup == group.name,
                onClick = { onGroupSelected(group.name) },
            )
        }
    }
}

@Composable
private fun GroupChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, maxLines = 1) },
        modifier = Modifier.focusRing(shape = RoundedCornerShape(8.dp)),
    )
}

@Composable
private fun ChannelList(channels: LazyPagingItems<Channel>, onChannelClick: (Long) -> Unit) {
    val refresh = channels.loadState.refresh
    when {
        refresh is LoadState.Error && channels.itemCount == 0 -> Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.feature_channels_load_failed, refresh.error.message.orEmpty()),
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = channels::retry, modifier = Modifier.focusRing()) {
                Text(stringResource(R.string.feature_channels_retry))
            }
        }

        refresh is LoadState.Loading && channels.itemCount == 0 -> Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }

        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            items(count = channels.itemCount, key = channels.itemKey { it.id }) { index ->
                val channel = channels[index]
                if (channel != null) {
                    ChannelRow(channel, onClick = { onChannelClick(channel.id) })
                    HorizontalDivider(Modifier.padding(start = 128.dp))
                }
            }
            if (channels.loadState.append is LoadState.Error) {
                item {
                    TextButton(
                        onClick = channels::retry,
                        modifier = Modifier.fillMaxWidth().focusRing(),
                    ) { Text(stringResource(R.string.feature_channels_retry)) }
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(channel: Channel, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusRing(shape = RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = channel.displayNumber.takeIf { it > 0 }?.toString().orEmpty(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(40.dp),
        )
        Spacer(Modifier.width(16.dp))
        ChannelLogo(channel)
        Spacer(Modifier.width(16.dp))
        Text(
            text = channel.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ChannelLogo(channel: Channel) {
    val shape = RoundedCornerShape(8.dp)
    val boxModifier = Modifier
        .size(width = 56.dp, height = 40.dp)
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceVariant)
    Box(boxModifier, contentAlignment = Alignment.Center) {
        // Initial shows while the logo loads, and stays when there is none or it fails.
        Text(
            channel.name.trim().take(1).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!channel.logoUrl.isNullOrBlank()) {
            AsyncImage(
                model = channel.logoUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(4.dp),
            )
        }
    }
}

@Composable
internal fun FavoritesScreen(modifier: Modifier = Modifier) {
    EmptyState(
        title = stringResource(R.string.feature_channels_favorites_empty_title),
        message = stringResource(R.string.feature_channels_favorites_empty_message),
        icon = StreamBoxIcons.Favorites,
        modifier = modifier,
    )
}

@Preview
@Composable
private fun ChannelsEmptyPreview() {
    StreamBoxTheme {
        ChannelsScreen(
            state = ChannelsUiState.NoPlaylists,
            channels = null,
            onGroupSelected = {},
            onAddPlaylist = {},
            onManagePlaylists = {},
            onChannelClick = {},
        )
    }
}
