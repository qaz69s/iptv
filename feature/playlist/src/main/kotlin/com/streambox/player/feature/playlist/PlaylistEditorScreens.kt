package com.streambox.player.feature.playlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.streambox.player.core.designsystem.component.EmptyState
import com.streambox.player.core.designsystem.component.LoadingState
import com.streambox.player.core.designsystem.component.TextInputDialog
import com.streambox.player.core.designsystem.component.TextInputDialogTexts
import com.streambox.player.core.designsystem.component.focusRing
import com.streambox.player.core.designsystem.icon.StreamBoxIcons
import com.streambox.player.core.model.EditorChannel
import com.streambox.player.core.model.EditorGroup

/** List editor, level 1: the groups of one playlist. OK / click on a group opens its channels. */
@Composable
internal fun PlaylistEditorEntry(
    onBack: () -> Unit,
    onOpenGroup: (playlistId: Long, groupName: String) -> Unit,
    playlistId: Long,
    viewModel: PlaylistEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf<EditorGroup?>(null) }
    val loaded = state as? PlaylistEditorUiState.Loaded
    Scaffold(
        topBar = {
            PlaylistTopBar(
                loaded?.let { stringResource(R.string.feature_playlist_editor_title, it.playlistName) }
                    ?: stringResource(R.string.feature_playlist_editor),
                onBack,
            )
        },
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding)
        when (val current = state) {
            PlaylistEditorUiState.Loading -> LoadingState(
                message = stringResource(com.streambox.player.core.ui.R.string.core_ui_loading),
                modifier = modifier,
            )

            PlaylistEditorUiState.Missing -> EmptyState(
                title = stringResource(R.string.feature_playlist_editor_missing),
                message = "",
                icon = StreamBoxIcons.Playlists,
                modifier = modifier,
            )

            is PlaylistEditorUiState.Loaded -> if (current.groups.isEmpty()) {
                EmptyState(
                    title = stringResource(R.string.feature_playlist_editor_empty_title),
                    message = stringResource(R.string.feature_playlist_editor_empty_message),
                    icon = StreamBoxIcons.Playlists,
                    modifier = modifier,
                )
            } else {
                EditorList(modifier) {
                    itemsIndexed(current.groups, key = { _, group -> group.name }) { index, group ->
                        EditorRow(
                            title = group.displayName.ifBlank { stringResource(R.string.feature_playlist_ungrouped) },
                            subtitle = groupSubtitle(group),
                            hidden = group.hidden,
                            actions = EditorRowActions(
                                onToggleVisible = { viewModel.setHidden(group, !group.hidden) },
                                onRename = { renaming = group },
                                onMoveUp = { viewModel.move(group, up = true) },
                                onMoveDown = { viewModel.move(group, up = false) },
                                onOpen = { onOpenGroup(playlistId, group.name) },
                            ),
                            canMoveUp = index > 0,
                            canMoveDown = index < current.groups.lastIndex,
                        )
                    }
                }
            }
        }
    }
    renaming?.let { group ->
        RenameDialog(
            initial = group.displayName,
            onConfirm = {
                viewModel.rename(group, it)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }
}

@Composable
private fun groupSubtitle(group: EditorGroup): String {
    val count = pluralStringResource(R.plurals.feature_playlist_channel_count, group.channelCount, group.channelCount)
    val original = group.customName?.let {
        stringResource(R.string.feature_playlist_editor_original, group.name.ifBlank { "—" })
    }
    val hidden = if (group.hidden) stringResource(R.string.feature_playlist_editor_hidden) else null
    return listOfNotNull(count, original, hidden).joinToString(" · ")
}

/** List editor, level 2: the channels of one group. The group itself can be shown / hidden at the top. */
@Composable
internal fun GroupEditorEntry(onBack: () -> Unit, viewModel: GroupEditorViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf<EditorChannel?>(null) }
    val loaded = state as? GroupEditorUiState.Loaded
    val group = loaded?.group
    Scaffold(
        topBar = {
            PlaylistTopBar(
                group?.displayName?.ifBlank { stringResource(R.string.feature_playlist_ungrouped) }
                    ?: stringResource(R.string.feature_playlist_editor),
                onBack,
            )
        },
    ) { padding ->
        val modifier = Modifier.fillMaxSize().padding(padding)
        when {
            loaded == null -> LoadingState(
                message = stringResource(com.streambox.player.core.ui.R.string.core_ui_loading),
                modifier = modifier,
            )

            group == null -> EmptyState(
                title = stringResource(R.string.feature_playlist_editor_group_missing),
                message = "",
                icon = StreamBoxIcons.Playlists,
                modifier = modifier,
            )

            else -> EditorList(modifier) {
                item(key = "__group__") { GroupVisibilitySwitch(group, viewModel::setGroupHidden) }
                itemsIndexed(loaded.channels, key = { _, channel -> channel.key.uniqueKey }) { index, channel ->
                    EditorRow(
                        title = channel.displayName,
                        subtitle = channelSubtitle(channel),
                        hidden = channel.hidden,
                        actions = EditorRowActions(
                            onToggleVisible = { viewModel.setHidden(channel, !channel.selfHidden) },
                            onRename = { renaming = channel },
                            onMoveUp = { viewModel.move(channel, up = true) },
                            onMoveDown = { viewModel.move(channel, up = false) },
                        ),
                        canMoveUp = index > 0,
                        canMoveDown = index < loaded.channels.lastIndex,
                    )
                }
            }
        }
    }
    renaming?.let { channel ->
        RenameDialog(
            initial = channel.displayName,
            onConfirm = {
                viewModel.rename(channel, it)
                renaming = null
            },
            onDismiss = { renaming = null },
        )
    }
}

@Composable
private fun channelSubtitle(channel: EditorChannel): String {
    val number = stringResource(R.string.feature_playlist_editor_number, channel.displayNumber)
    val original = channel.customName?.let { stringResource(R.string.feature_playlist_editor_original, channel.name) }
    val hidden = when {
        channel.selfHidden -> stringResource(R.string.feature_playlist_editor_hidden)
        channel.hiddenByGroup -> stringResource(R.string.feature_playlist_editor_hidden_by_group)
        else -> null
    }
    return listOfNotNull(number, original, hidden).joinToString(" · ")
}

@Composable
private fun GroupVisibilitySwitch(group: EditorGroup, onHiddenChange: (Boolean) -> Unit) {
    Card(Modifier.widthIn(max = 720.dp).fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .focusRing()
                .toggleable(value = !group.hidden, role = Role.Switch, onValueChange = { onHiddenChange(!it) })
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.feature_playlist_editor_show_group),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = !group.hidden, onCheckedChange = null)
        }
    }
}

/** The editors' list: centered cards, room at the bottom. */
@Composable
internal fun EditorList(modifier: Modifier, content: LazyListScope.() -> Unit) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

/** Rename a group or channel; a blank name restores the playlist's name. */
@Composable
internal fun RenameDialog(initial: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    TextInputDialog(
        texts = TextInputDialogTexts(
            title = stringResource(R.string.feature_playlist_editor_rename),
            label = stringResource(R.string.feature_playlist_name_label),
            confirm = stringResource(R.string.feature_playlist_save),
            dismiss = stringResource(R.string.feature_playlist_cancel),
            hint = stringResource(R.string.feature_playlist_editor_rename_hint),
        ),
        initial = initial,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        allowBlank = true,
    )
}
