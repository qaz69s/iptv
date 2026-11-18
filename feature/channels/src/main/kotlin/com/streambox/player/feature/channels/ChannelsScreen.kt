@file:Suppress("MatchingDeclarationName")

package com.streambox.player.feature.channels

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
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
import com.streambox.player.core.designsystem.component.EmptyState
import com.streambox.player.core.designsystem.component.LoadingState
import com.streambox.player.core.designsystem.component.focusRing
import com.streambox.player.core.designsystem.icon.StreamBoxIcons
import com.streambox.player.core.designsystem.theme.StreamBoxTheme
import com.streambox.player.core.model.Channel
import com.streambox.player.core.model.ChannelSortOrder
import com.streambox.player.core.model.ChannelSource
import com.streambox.player.core.model.ChannelViewMode
import com.streambox.player.core.model.CustomGroup
import com.streambox.player.core.ui.FormFactor
import com.streambox.player.core.ui.LocalFormFactor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Callbacks of the channel browser, grouped to keep the composables readable. */
internal data class ChannelsActions(
    val onSourceSelected: (ChannelSource) -> Unit = {},
    val onAddPlaylist: () -> Unit = {},
    val onManagePlaylists: () -> Unit = {},
    val onSearch: () -> Unit = {},
    val onViewModeSelected: (ChannelViewMode) -> Unit = {},
    val onSortOrderSelected: (ChannelSortOrder) -> Unit = {},
    /** Plays [Channel]; the player zaps through the source it was picked from. */
    val onChannelClick: (Channel, ChannelSource) -> Unit = { _, _ -> },
    val onToggleFavorite: (Channel) -> Unit = {},
    /** P3c quick edits from the channel menu. */
    val onHide: (Channel) -> Unit = {},
    val onRename: (Channel, String) -> Unit = { _, _ -> },
    val customGroupsOf: (Channel) -> Flow<Set<Long>> = { emptyFlow() },
    val onSetInCustomGroup: (groupId: Long, Channel, member: Boolean) -> Unit = { _, _, _ -> },
    val onCreateCustomGroup: (String, Channel) -> Unit = { _, _ -> },
)

/** A dialog opened from a channel's menu. */
private sealed interface ChannelDialog {
    val channel: Channel

    data class Rename(override val channel: Channel) : ChannelDialog

    data class Groups(override val channel: Channel) : ChannelDialog
}

/**
 * Channel browser. A fresh install has no playlists and therefore no channels; nothing is ever
 * bundled with the app. Playback is owned by the app module through [onChannelClick].
 */
@Composable
internal fun ChannelsEntry(
    onAddPlaylist: () -> Unit,
    onManagePlaylists: () -> Unit,
    onSearch: () -> Unit,
    onChannelClick: (Channel, ChannelSource) -> Unit,
    viewModel: ChannelsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val channels = viewModel.channels.collectAsLazyPagingItems()
    ChannelsScreen(
        state = state,
        channels = channels,
        actions = ChannelsActions(
            onSourceSelected = viewModel::onSourceSelected,
            onAddPlaylist = onAddPlaylist,
            onManagePlaylists = onManagePlaylists,
            onSearch = onSearch,
            onViewModeSelected = viewModel::onViewModeSelected,
            onSortOrderSelected = viewModel::onSortOrderSelected,
            onChannelClick = onChannelClick,
            onToggleFavorite = viewModel::onToggleFavorite,
            onHide = viewModel::onHideChannel,
            onRename = viewModel::onRenameChannel,
            customGroupsOf = viewModel::customGroupsOf,
            onSetInCustomGroup = viewModel::onSetInCustomGroup,
            onCreateCustomGroup = viewModel::onCreateCustomGroup,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChannelsScreen(
    state: ChannelsUiState,
    channels: LazyPagingItems<Channel>?,
    actions: ChannelsActions,
    modifier: Modifier = Modifier,
) {
    val content = state as? ChannelsUiState.Content
    // TV: coming back from the player puts focus on the channel that was played, not on the groups.
    var lastPlayedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var dialog by remember { mutableStateOf<ChannelDialog?>(null) }
    val pickActions = actions.copy(
        onChannelClick = { channel, source ->
            lastPlayedId = channel.id
            actions.onChannelClick(channel, source)
        },
        onSourceSelected = { source ->
            lastPlayedId = null
            actions.onSourceSelected(source)
        },
    )
    val menuFor: (Channel) -> List<ChannelMenuEntry> = { channel ->
        channelMenu(channel, content?.source, actions) { dialog = it }
    }
    val hasPlaylists = state !is ChannelsUiState.NoPlaylists && state !is ChannelsUiState.Loading
    val isTv = LocalFormFactor.current == FormFactor.Tv
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feature_channels_title)) },
                actions = {
                    if (content != null) {
                        IconButton(onClick = actions.onSearch, modifier = Modifier.focusRing()) {
                            Icon(
                                painterResource(StreamBoxIcons.Search),
                                contentDescription = stringResource(R.string.feature_channels_search),
                            )
                        }
                        ViewModeMenu(content.viewMode, actions.onViewModeSelected)
                        SortMenu(content.sortOrder, actions.onSortOrderSelected)
                    }
                    if (hasPlaylists) {
                        IconButton(onClick = actions.onManagePlaylists, modifier = Modifier.focusRing()) {
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
            // The empty state already has a big "Add playlist" button; TV adds playlists from the top bar.
            if (hasPlaylists && !isTv) {
                FloatingActionButton(onClick = actions.onAddPlaylist, modifier = Modifier.focusRing()) {
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

            ChannelsUiState.NoPlaylists -> NoPlaylistsContent(actions.onAddPlaylist, contentModifier)

            ChannelsUiState.NoChannels -> EmptyState(
                title = stringResource(R.string.feature_channels_no_channels_title),
                message = stringResource(R.string.feature_channels_no_channels_message),
                icon = StreamBoxIcons.Playlists,
                actionLabel = stringResource(R.string.feature_channels_manage_playlists),
                onAction = actions.onManagePlaylists,
                modifier = contentModifier,
            )

            is ChannelsUiState.Content -> if (isTv) {
                // TV (requirements §4.2): groups on the left, channels on the right.
                Row(contentModifier) {
                    GroupPane(
                        state = state,
                        onSelected = pickActions.onSourceSelected,
                        takeFocus = lastPlayedId == null,
                        modifier = Modifier.width(280.dp).fillMaxHeight(),
                    )
                    VerticalDivider()
                    ChannelPane(
                        state = state,
                        channels = channels,
                        actions = pickActions,
                        focusChannelId = lastPlayedId,
                        menuFor = menuFor,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            } else {
                Column(contentModifier) {
                    SourceChips(state, pickActions.onSourceSelected)
                    ChannelPane(state, channels, pickActions, lastPlayedId, menuFor, Modifier.weight(1f))
                }
            }
        }
    }
    ChannelDialogHost(dialog, content?.customGroups.orEmpty(), actions) { dialog = null }
}

/** The P3c entries of a channel's menu: hide, rename, custom groups (and leaving the group shown). */
private fun channelMenu(
    channel: Channel,
    source: ChannelSource?,
    actions: ChannelsActions,
    open: (ChannelDialog) -> Unit,
): List<ChannelMenuEntry> = buildList {
    add(ChannelMenuEntry(R.string.feature_channels_hide, StreamBoxIcons.Hidden) { actions.onHide(channel) })
    add(ChannelMenuEntry(R.string.feature_channels_rename, StreamBoxIcons.Edit) { open(ChannelDialog.Rename(channel)) })
    add(
        ChannelMenuEntry(R.string.feature_channels_add_to_group, StreamBoxIcons.NewGroup) {
            open(ChannelDialog.Groups(channel))
        },
    )
    if (source is ChannelSource.Custom) {
        add(
            ChannelMenuEntry(R.string.feature_channels_remove_from_group, StreamBoxIcons.Close) {
                actions.onSetInCustomGroup(source.groupId, channel, false)
            },
        )
    }
}

@Composable
private fun ChannelDialogHost(
    dialog: ChannelDialog?,
    customGroups: List<CustomGroup>,
    actions: ChannelsActions,
    onClose: () -> Unit,
) {
    when (dialog) {
        null -> Unit

        is ChannelDialog.Rename -> RenameChannelDialog(
            channel = dialog.channel,
            onConfirm = {
                actions.onRename(dialog.channel, it)
                onClose()
            },
            onDismiss = onClose,
        )

        is ChannelDialog.Groups -> {
            val membership by remember(dialog.channel.key) { actions.customGroupsOf(dialog.channel) }
                .collectAsStateWithLifecycle(initialValue = emptySet())
            CustomGroupsDialog(
                groups = customGroups,
                membership = membership,
                onToggle = { id, member -> actions.onSetInCustomGroup(id, dialog.channel, member) },
                onCreate = { actions.onCreateCustomGroup(it, dialog.channel) },
                onDismiss = onClose,
            )
        }
    }
}

/** The virtual groups first, then the playlist groups: one entry of the source list. */
private data class SourceEntry(val source: ChannelSource, val count: Int)

private fun ChannelsUiState.Content.sourceEntries(): List<SourceEntry> = buildList {
    add(SourceEntry(ChannelSource.All, totalChannels))
    add(SourceEntry(ChannelSource.Favorites, favoriteCount))
    add(SourceEntry(ChannelSource.Recent, recentCount))
    customGroups.forEach { add(SourceEntry(ChannelSource.Custom(it.id), it.channelCount)) }
    groups.forEach { add(SourceEntry(ChannelSource.Group(it.name), it.channelCount)) }
}

private fun ChannelSource.itemKey(): String = when (this) {
    ChannelSource.All -> "__all__"
    ChannelSource.Favorites -> "__favorites__"
    ChannelSource.Recent -> "__recent__"
    is ChannelSource.Group -> "g:$name"
    is ChannelSource.Custom -> "c:$groupId"
}

@Composable
private fun sourceLabel(entry: SourceEntry, custom: List<CustomGroup>): String = when (val source = entry.source) {
    ChannelSource.All -> stringResource(R.string.feature_channels_group_all, entry.count)

    ChannelSource.Favorites -> stringResource(R.string.feature_channels_group_favorites, entry.count)

    ChannelSource.Recent -> stringResource(R.string.feature_channels_group_recent, entry.count)

    is ChannelSource.Group -> stringResource(
        R.string.feature_channels_group_label,
        source.name.ifBlank { stringResource(R.string.feature_channels_group_ungrouped) },
        entry.count,
    )

    is ChannelSource.Custom -> stringResource(
        R.string.feature_channels_group_custom,
        custom.firstOrNull { it.id == source.groupId }?.name.orEmpty(),
        entry.count,
    )
}

@Composable
private fun SourceChips(state: ChannelsUiState.Content, onSelected: (ChannelSource) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.sourceEntries(), key = { it.source.itemKey() }) { entry ->
            FilterChip(
                selected = entry.source == state.source,
                onClick = { onSelected(entry.source) },
                label = { Text(sourceLabel(entry, state.customGroups), maxLines = 1) },
                modifier = Modifier.focusRing(shape = RoundedCornerShape(8.dp)),
            )
        }
    }
}

/**
 * TV left pane. OK selects a group (no reload while the remote scrolls past groups); Right moves into
 * the channels, Left opens the app drawer. Coming back from the channels focus returns to the group
 * that was focused last ([focusRestorer]); on first entry the selected group takes focus.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun GroupPane(
    state: ChannelsUiState.Content,
    onSelected: (ChannelSource) -> Unit,
    takeFocus: Boolean,
    modifier: Modifier,
) {
    val entries = state.sourceEntries()
    val selectedIndex = entries.indexOfFirst { it.source == state.source }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val selectedFocus = remember { FocusRequester() }
    if (takeFocus) LaunchedEffect(selectedFocus) { runCatching { selectedFocus.requestFocus() } }
    LazyColumn(
        state = listState,
        modifier = modifier.focusRestorer(),
        contentPadding = PaddingValues(vertical = 8.dp),
    ) {
        itemsIndexed(entries, key = { _, entry -> entry.source.itemKey() }) { index, entry ->
            val selected = entry.source == state.source
            val shape = RoundedCornerShape(8.dp)
            Text(
                text = sourceLabel(entry, state.customGroups),
                style = MaterialTheme.typography.bodyLarge,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp)
                    .then(if (index == selectedIndex) Modifier.focusRequester(selectedFocus) else Modifier)
                    .focusRing(shape = shape)
                    .clip(shape)
                    .clickable(role = Role.Tab, onClick = { onSelected(entry.source) })
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun ChannelPane(
    state: ChannelsUiState.Content,
    channels: LazyPagingItems<Channel>?,
    actions: ChannelsActions,
    focusChannelId: Long?,
    menuFor: (Channel) -> List<ChannelMenuEntry>,
    modifier: Modifier,
) {
    if (channels == null) return
    val refresh = channels.loadState.refresh
    when {
        refresh is LoadState.Error && channels.itemCount == 0 -> Column(
            modifier = modifier.fillMaxSize().padding(32.dp),
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
            modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }

        channels.itemCount == 0 && refresh is LoadState.NotLoading && state.source.isVirtual() ->
            VirtualGroupEmpty(state.source, modifier)

        else -> ChannelCollection(state, channels, actions, focusChannelId, menuFor, modifier)
    }
}

private fun ChannelSource.isVirtual() =
    this == ChannelSource.Favorites || this == ChannelSource.Recent || this is ChannelSource.Custom

@Composable
private fun VirtualGroupEmpty(source: ChannelSource, modifier: Modifier) {
    val (title, message) = when (source) {
        ChannelSource.Recent ->
            R.string.feature_channels_recent_empty_title to
                R.string.feature_channels_recent_empty_message

        is ChannelSource.Custom ->
            R.string.feature_channels_custom_empty_title to
                R.string.feature_channels_custom_empty_message

        else -> R.string.feature_channels_favorites_empty_title to R.string.feature_channels_favorites_empty_message
    }
    EmptyState(
        title = stringResource(title),
        message = stringResource(message),
        icon = if (source == ChannelSource.Recent) StreamBoxIcons.History else StreamBoxIcons.Favorites,
        modifier = modifier.fillMaxSize(),
    )
}

/** The channels of the selected source as a list, a grid or compact rows. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ChannelCollection(
    state: ChannelsUiState.Content,
    channels: LazyPagingItems<Channel>,
    actions: ChannelsActions,
    focusChannelId: Long?,
    menuFor: (Channel) -> List<ChannelMenuEntry>,
    modifier: Modifier,
) {
    val restoreFocus = remember { FocusRequester() }
    if (focusChannelId != null && LocalFormFactor.current == FormFactor.Tv) {
        LaunchedEffect(focusChannelId) { runCatching { restoreFocus.requestFocus() } }
    }
    val itemContent: @Composable (Channel) -> Unit = { channel ->
        ChannelItem(
            modifier = if (channel.id == focusChannelId) Modifier.focusRequester(restoreFocus) else Modifier,
            channel = channel,
            mode = state.viewMode,
            state = ChannelItemState(
                isFavorite = channel.key in state.favoriteKeys,
                allowCleartext = channel.playlistId in state.cleartextPlaylists,
            ),
            onClick = { actions.onChannelClick(channel, state.source) },
            onToggleFavorite = { actions.onToggleFavorite(channel) },
            menuEntries = menuFor(channel),
        )
    }
    val retryRow: @Composable () -> Unit = {
        TextButton(onClick = channels::retry, modifier = Modifier.fillMaxWidth().focusRing()) {
            Text(stringResource(R.string.feature_channels_retry))
        }
    }
    val appendFailed = channels.loadState.append is LoadState.Error
    if (state.viewMode == ChannelViewMode.Grid) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 148.dp),
            modifier = modifier.fillMaxSize().focusRestorer(),
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 96.dp),
        ) {
            items(count = channels.itemCount, key = channels.itemKey { it.id }) { index ->
                channels[index]?.let { itemContent(it) }
            }
            if (appendFailed) item(span = { GridItemSpan(maxLineSpan) }) { retryRow() }
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize().focusRestorer(),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            items(count = channels.itemCount, key = channels.itemKey { it.id }) { index ->
                val channel = channels[index]
                if (channel != null) {
                    itemContent(channel)
                    if (state.viewMode == ChannelViewMode.List) HorizontalDivider(Modifier.padding(start = 128.dp))
                }
            }
            if (appendFailed) item { retryRow() }
        }
    }
}

@Composable
private fun ViewModeMenu(current: ChannelViewMode, onSelect: (ChannelViewMode) -> Unit) {
    OptionsMenu(
        icon = current.icon(),
        description = stringResource(R.string.feature_channels_view),
        options = ChannelViewMode.entries,
        selected = current,
        label = { stringResource(it.labelRes()) },
        onSelect = onSelect,
    )
}

@Composable
private fun SortMenu(current: ChannelSortOrder, onSelect: (ChannelSortOrder) -> Unit) {
    OptionsMenu(
        icon = StreamBoxIcons.Sort,
        description = stringResource(R.string.feature_channels_sort),
        options = ChannelSortOrder.entries,
        selected = current,
        label = { stringResource(it.labelRes()) },
        onSelect = onSelect,
    )
}

/** Top-bar button with a single-choice dropdown (works with touch and D-pad). */
@Composable
private fun <T> OptionsMenu(
    @DrawableRes icon: Int,
    description: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.focusRing()) {
            Icon(painterResource(icon), contentDescription = description)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option)) },
                    leadingIcon = { RadioButton(selected = option == selected, onClick = null) },
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                    modifier = Modifier.focusRing(),
                )
            }
        }
    }
}

@DrawableRes
private fun ChannelViewMode.icon(): Int = when (this) {
    ChannelViewMode.List -> StreamBoxIcons.Playlists
    ChannelViewMode.Grid -> StreamBoxIcons.GridView
    ChannelViewMode.Compact -> StreamBoxIcons.CompactView
}

private fun ChannelViewMode.labelRes(): Int = when (this) {
    ChannelViewMode.List -> R.string.feature_channels_view_list
    ChannelViewMode.Grid -> R.string.feature_channels_view_grid
    ChannelViewMode.Compact -> R.string.feature_channels_view_compact
}

private fun ChannelSortOrder.labelRes(): Int = when (this) {
    ChannelSortOrder.Default -> R.string.feature_channels_sort_default
    ChannelSortOrder.Name -> R.string.feature_channels_sort_name
    ChannelSortOrder.Number -> R.string.feature_channels_sort_number
    ChannelSortOrder.Custom -> R.string.feature_channels_sort_custom
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

@Preview
@Composable
private fun ChannelsEmptyPreview() {
    StreamBoxTheme {
        ChannelsScreen(state = ChannelsUiState.NoPlaylists, channels = null, actions = ChannelsActions())
    }
}
