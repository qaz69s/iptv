package com.streambox.player.feature.playlist

import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
    )
}

@Composable
internal fun PlaylistsScreen(
    state: PlaylistsUiState,
    onBack: () -> Unit,
    onAddPlaylist: () -> Unit,
    onOpenImport: (Long) -> Unit,
    onRefresh: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onAllowCleartextChange: (Long, Boolean) -> Unit,
) {
    var pendingDelete by rememberSaveable { mutableStateOf<Long?>(null) }
    Scaffold(
        topBar = { PlaylistTopBar(stringResource(R.string.feature_playlist_list_title), onBack) },
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
                    items(state.rows, key = { it.playlist.id }) { row ->
                        PlaylistCard(
                            row = row,
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
    onOpen: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    onAllowCleartextChange: (Boolean) -> Unit,
) {
    val playlist = row.playlist
    Card(modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
        Column(Modifier.padding(vertical = 8.dp)) {
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
        }
    }
}

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
