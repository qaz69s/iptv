package com.streambox.player.feature.playlist

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.streambox.player.core.designsystem.component.EmptyState
import com.streambox.player.core.designsystem.component.LoadingState
import com.streambox.player.core.designsystem.component.TextInputDialog
import com.streambox.player.core.designsystem.component.TextInputDialogTexts
import com.streambox.player.core.designsystem.component.focusRing
import com.streambox.player.core.designsystem.icon.StreamBoxIcons
import com.streambox.player.core.model.Channel
import com.streambox.player.core.model.CustomGroup

/** The user's custom groups (P3c): create, rename, delete, move; OK / click opens a group's channels. */
@Composable
internal fun CustomGroupsEntry(
    onBack: () -> Unit,
    onOpenGroup: (Long) -> Unit,
    viewModel: CustomGroupsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var creating by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<CustomGroup?>(null) }
    var deleting by remember { mutableStateOf<CustomGroup?>(null) }
    Scaffold(
        topBar = { PlaylistTopBar(stringResource(R.string.feature_playlist_custom_groups), onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }, modifier = Modifier.focusRing()) {
                Icon(
                    painterResource(StreamBoxIcons.NewGroup),
                    contentDescription = stringResource(R.string.feature_playlist_custom_group_new),
                )
            }
        },
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding)
        val groups = state.groups
        when {
            groups == null -> LoadingState(
                message = stringResource(com.streambox.player.core.ui.R.string.core_ui_loading),
                modifier = modifier,
            )

            groups.isEmpty() -> EmptyState(
                title = stringResource(R.string.feature_playlist_custom_groups_empty_title),
                message = stringResource(R.string.feature_playlist_custom_groups_empty_message),
                icon = StreamBoxIcons.NewGroup,
                actionLabel = stringResource(R.string.feature_playlist_custom_group_new),
                onAction = { creating = true },
                modifier = modifier,
            )

            else -> EditorList(modifier) {
                itemsIndexed(groups, key = { _, group -> group.id }) { index, group ->
                    EditorRow(
                        title = group.name,
                        subtitle = pluralStringResource(
                            R.plurals.feature_playlist_channel_count,
                            group.channelCount,
                            group.channelCount,
                        ),
                        hidden = false,
                        actions = EditorRowActions(
                            onRename = { renaming = group },
                            onMoveUp = { viewModel.move(group, up = true) },
                            onMoveDown = { viewModel.move(group, up = false) },
                            onDelete = { deleting = group },
                            onOpen = { onOpenGroup(group.id) },
                        ),
                        canMoveUp = index > 0,
                        canMoveDown = index < groups.lastIndex,
                    )
                }
            }
        }
    }
    if (creating) {
        GroupNameDialog(
            title = stringResource(R.string.feature_playlist_custom_group_new),
            confirm = stringResource(R.string.feature_playlist_create),
            initial = "",
            onConfirm = {
                viewModel.create(it)
                creating = false
            },
            onDismiss = { creating = false },
        )
    }
    renaming?.let { group ->
        GroupNameDialog(
            title = stringResource(R.string.feature_playlist_editor_rename),
            confirm = stringResource(R.string.feature_playlist_save),
            initial = group.name,
            onConfirm = {
                viewModel.rename(group, it)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }
    deleting?.let { group ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.feature_playlist_delete_title, group.name)) },
            text = { Text(stringResource(R.string.feature_playlist_custom_group_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(group)
                        deleting = null
                    },
                    modifier = Modifier.focusRing(),
                ) { Text(stringResource(R.string.feature_playlist_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }, modifier = Modifier.focusRing()) {
                    Text(stringResource(R.string.feature_playlist_cancel))
                }
            },
        )
    }
}

@Composable
private fun GroupNameDialog(
    title: String,
    confirm: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    TextInputDialog(
        texts = TextInputDialogTexts(
            title = title,
            label = stringResource(R.string.feature_playlist_custom_group_name),
            confirm = confirm,
            dismiss = stringResource(R.string.feature_playlist_cancel),
        ),
        initial = initial,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

/** The channels of one custom group: move up / down, remove. Channels are added from the channel menu. */
@Composable
internal fun CustomGroupEditorEntry(onBack: () -> Unit, viewModel: CustomGroupEditorViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            PlaylistTopBar(state.group?.name ?: stringResource(R.string.feature_playlist_custom_groups), onBack)
        },
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding)
        when {
            !state.loaded -> LoadingState(
                message = stringResource(com.streambox.player.core.ui.R.string.core_ui_loading),
                modifier = modifier,
            )

            state.channels.isEmpty() -> EmptyState(
                title = stringResource(R.string.feature_playlist_custom_group_empty_title),
                message = stringResource(R.string.feature_playlist_custom_group_empty_message),
                icon = StreamBoxIcons.NewGroup,
                modifier = modifier,
            )

            else -> EditorList(modifier) {
                val keyOf: (Int, Channel) -> Any = { _, channel -> "${channel.playlistId}/${channel.uniqueKey}" }
                itemsIndexed(state.channels, key = keyOf) { index, channel ->
                    EditorRow(
                        title = channel.name,
                        subtitle = stringResource(R.string.feature_playlist_editor_number, channel.displayNumber),
                        hidden = false,
                        actions = EditorRowActions(
                            onMoveUp = { viewModel.move(channel, up = true) },
                            onMoveDown = { viewModel.move(channel, up = false) },
                            onDelete = { viewModel.remove(channel) },
                            deleteLabel = R.string.feature_playlist_custom_group_remove,
                        ),
                        canMoveUp = index > 0,
                        canMoveDown = index < state.channels.lastIndex,
                    )
                }
            }
        }
    }
}
