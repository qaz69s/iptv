package com.streambox.player.feature.playlist

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.streambox.player.core.designsystem.component.EmptyState
import com.streambox.player.core.designsystem.component.LoadingState
import com.streambox.player.core.designsystem.component.focusRing
import com.streambox.player.core.designsystem.icon.StreamBoxIcons
import com.streambox.player.core.designsystem.theme.StreamBoxTheme
import com.streambox.player.core.model.ImportStatus
import com.streambox.player.core.model.Playlist
import com.streambox.player.core.model.PlaylistType

@Composable
internal fun PlaylistsEntry(
    onBack: () -> Unit,
    onAddPlaylist: () -> Unit,
    onOpenImport: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    onCustomGroups: () -> Unit,
    viewModel: PlaylistsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PlaylistsScreen(
        state = state,
        onBack = onBack,
        onAddPlaylist = onAddPlaylist,
        onOpenImport = onOpenImport,
        onRefresh = { id ->
            viewModel.refresh(id)
            onOpenImport(id)
        },
        onDelete = viewModel::delete,
        onAllowCleartextChange = viewModel::setAllowCleartext,
        editActions = PlaylistEditActions(
            onEdit = onEdit,
            onMove = viewModel::move,
            onEnabledChange = viewModel::setEnabled,
            onCustomGroups = onCustomGroups,
        ),
    )
}

/** P3c: list editor entry, order (up / down) and enable / disable of each playlist; custom groups. */
internal data class PlaylistEditActions(
    val onEdit: (Long) -> Unit = {},
    val onMove: (Long, up: Boolean) -> Unit = { _, _ -> },
    val onEnabledChange: (Long, Boolean) -> Unit = { _, _ -> },
    val onCustomGroups: () -> Unit = {},
)

@Composable
internal fun PlaylistsScreen(
    state: PlaylistsUiState,
    onBack: () -> Unit,
    onAddPlaylist: () -> Unit,
    onOpenImport: (Long) -> Unit,
    onRefresh: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onAllowCleartextChange: (Long, Boolean) -> Unit,
    editActions: PlaylistEditActions = PlaylistEditActions(),
) {
    var pendingDelete by rememberSaveable { mutableStateOf<Long?>(null) }
    Scaffold(
        topBar = {
            PlaylistTopBar(stringResource(R.string.feature_playlist_list_title), onBack) {
                IconButton(onClick = editActions.onCustomGroups, modifier = Modifier.focusRing()) {
                    Icon(
                        painterResource(StreamBoxIcons.NewGroup),
                        contentDescription = stringResource(R.string.feature_playlist_custom_groups),
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddPlaylist, modifier = Modifier.focusRing()) {
                Icon(
                    painterResource(StreamBoxIcons.Add),
                    contentDescription = stringResource(R.string.feature_playlist_add_title),
                )
            }
        },
    ) { padding ->
        when (state) {
            PlaylistsUiState.Loading -> LoadingState(
                message = stringResource(com.streambox.player.core.ui.R.string.core_ui_loading),
                modifier = Modifier.padding(padding),
            )

            is PlaylistsUiState.Loaded -> if (state.rows.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.feature_playlist_list_empty_title),
                    message = stringResource(R.string.feature_playlist_list_empty_message),
                    icon = StreamBoxIcons.PlaylistAdd,
                    actionLabel = stringResource(R.string.feature_playlist_add_title),
                    onAction = onAddPlaylist,
                    modifier = Modifier.padding(padding),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    itemsIndexed(state.rows, key = { _, row -> row.playlist.id }) { index, row ->
                        PlaylistCard(
                            row = row,
                            position = index to state.rows.size,
                            editActions = editActions,
                            onOpen = { onOpenImport(row.playlist.id) },
                            onRefresh = { onRefresh(row.playlist.id) },
                            onDelete = { pendingDelete = row.playlist.id },
                            onAllowCleartextChange = { onAllowCleartextChange(row.playlist.id, it) },
                        )
                    }
                }
            }
        }
    }

    val toDelete = (state as? PlaylistsUiState.Loaded)?.rows?.firstOrNull { it.playlist.id == pendingDelete }?.playlist
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.feature_playlist_delete_title, toDelete.name)) },
            text = { Text(stringResource(R.string.feature_playlist_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(toDelete.id)
                        pendingDelete = null
                    },
                    modifier = Modifier.focusRing(),
                ) { Text(stringResource(R.string.feature_playlist_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }, modifier = Modifier.focusRing()) {
                    Text(stringResource(R.string.feature_playlist_cancel))
                }
            },
        )
    }
}

@Composable
private fun PlaylistCard(
    row: PlaylistRow,
    /** Index and size of the list, for the move buttons. */
    position: Pair<Int, Int>,
    editActions: PlaylistEditActions,
    onOpen: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    onAllowCleartextChange: (Boolean) -> Unit,
) {
    val playlist = row.playlist
    Card(modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp).alpha(if (playlist.enabled) 1f else DISABLED_ALPHA)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRing()
                    .clickable(onClick = onOpen)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        playlist.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(playlist.type.labelRes()) + " · " +
                            pluralStringResource(
                                R.plurals.feature_playlist_channel_count,
                                playlist.channelCount,
                                playlist.channelCount,
                            ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    StatusLine(row)
                }
                IconButton(onClick = onRefresh, enabled = !row.isImporting, modifier = Modifier.focusRing()) {
                    Icon(
                        painterResource(StreamBoxIcons.Refresh),
                        contentDescription = stringResource(R.string.feature_playlist_refresh),
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.focusRing()) {
                    Icon(
                        painterResource(StreamBoxIcons.Delete),
                        contentDescription = stringResource(R.string.feature_playlist_delete),
                    )
                }
            }
            if (row.isImporting) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp))
            }
            if (playlist.type == PlaylistType.M3U_URL) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRing()
                        .toggleable(
                            value = playlist.allowCleartext,
                            role = Role.Switch,
                            onValueChange = onAllowCleartextChange,
                        )
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.feature_playlist_allow_http_short),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = playlist.allowCleartext, onCheckedChange = null)
                }
            }
            SwitchRow(
                label = stringResource(R.string.feature_playlist_enabled),
                checked = playlist.enabled,
                onCheckedChange = { editActions.onEnabledChange(playlist.id, it) },
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { editActions.onEdit(playlist.id) }, modifier = Modifier.focusRing()) {
                    Icon(painterResource(StreamBoxIcons.Edit), contentDescription = null)
                    Text(stringResource(R.string.feature_playlist_editor), Modifier.padding(start = 8.dp))
                }
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = { editActions.onMove(playlist.id, true) },
                    enabled = position.first > 0,
                    modifier = Modifier.focusRing(),
                ) {
                    Icon(
                        painterResource(StreamBoxIcons.ArrowUp),
                        contentDescription = stringResource(R.string.feature_playlist_editor_up),
                    )
                }
                IconButton(
                    onClick = { editActions.onMove(playlist.id, false) },
                    enabled = position.first < position.second - 1,
                    modifier = Modifier.focusRing(),
                ) {
                    Icon(
                        painterResource(StreamBoxIcons.ArrowDown),
                        contentDescription = stringResource(R.string.feature_playlist_editor_down),
                    )
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusRing()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = null)
    }
}

private const val DISABLED_ALPHA = 0.6f

@Composable
private fun StatusLine(row: PlaylistRow) {
    val playlist = row.playlist
    val failed = row.status as? ImportStatus.Failed
    val updated = playlist.lastUpdated
    val (text, isError) = when {
        row.isImporting -> stringResource(R.string.feature_playlist_importing) to false

        failed != null -> stringResource(R.string.feature_playlist_last_error) + ": " + failed.reason.message() to true

        playlist.lastError != null -> stringResource(R.string.feature_playlist_last_error) to true

        updated != null -> stringResource(
            R.string.feature_playlist_updated,
            DateUtils.getRelativeTimeSpanString(updated).toString(),
        ) to false

        else -> stringResource(R.string.feature_playlist_never_updated) to false
    }
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun PlaylistType.labelRes(): Int = when (this) {
    PlaylistType.M3U_URL -> R.string.feature_playlist_type_url
    PlaylistType.M3U_FILE -> R.string.feature_playlist_type_file
    PlaylistType.XTREAM -> R.string.feature_playlist_type_xtream
}

@Preview
@Composable
private fun PlaylistsScreenPreview() {
    StreamBoxTheme {
        PlaylistsScreen(
            state = PlaylistsUiState.Loaded(
                listOf(
                    PlaylistRow(
                        Playlist(1, "Home IPTV", PlaylistType.M3U_URL, "http://x", channelCount = 812),
                        ImportStatus.Idle,
                    ),
                ),
            ),
            onBack = {},
            onAddPlaylist = {},
            onOpenImport = {},
            onRefresh = {},
            onDelete = {},
            onAllowCleartextChange = { _, _ -> },
        )
    }
}
