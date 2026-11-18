package com.streambox.player.feature.channels

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.streambox.player.core.designsystem.component.TextInputDialog
import com.streambox.player.core.designsystem.component.TextInputDialogTexts
import com.streambox.player.core.designsystem.component.focusRing
import com.streambox.player.core.designsystem.icon.StreamBoxIcons
import com.streambox.player.core.model.Channel
import com.streambox.player.core.model.CustomGroup

/** Rename one channel from the channel menu; a blank name restores the playlist's name. */
@Composable
internal fun RenameChannelDialog(channel: Channel, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    TextInputDialog(
        texts = TextInputDialogTexts(
            title = stringResource(R.string.feature_channels_rename_title),
            label = stringResource(R.string.feature_channels_rename_label),
            confirm = stringResource(R.string.feature_channels_save),
            dismiss = stringResource(R.string.feature_channels_cancel),
            hint = stringResource(R.string.feature_channels_rename_hint),
        ),
        initial = channel.name,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        allowBlank = true,
    )
}

/**
 * The custom groups with a check box each (is [channel] in it?) plus "New group". Changes apply at
 * once; the dialog only closes with Done.
 */
@Composable
internal fun CustomGroupsDialog(
    groups: List<CustomGroup>,
    membership: Set<Long>,
    onToggle: (groupId: Long, member: Boolean) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by remember { mutableStateOf(false) }
    if (creating) {
        TextInputDialog(
            texts = TextInputDialogTexts(
                title = stringResource(R.string.feature_channels_new_group),
                label = stringResource(R.string.feature_channels_group_name),
                confirm = stringResource(R.string.feature_channels_create),
                dismiss = stringResource(R.string.feature_channels_cancel),
            ),
            initial = "",
            onConfirm = {
                creating = false
                onCreate(it)
            },
            onDismiss = { creating = false },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.feature_channels_add_to_group)) },
        text = {
            Column {
                if (groups.isEmpty()) {
                    Text(
                        stringResource(R.string.feature_channels_no_custom_groups),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(groups, key = { it.id }) { group ->
                        val checked = group.id in membership
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRing()
                                .toggleable(
                                    value = checked,
                                    role = Role.Checkbox,
                                    onValueChange = { onToggle(group.id, it) },
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = checked, onCheckedChange = null)
                            Text(group.name, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
                TextButton(onClick = { creating = true }, modifier = Modifier.focusRing()) {
                    Icon(painterResource(StreamBoxIcons.NewGroup), contentDescription = null)
                    Text(stringResource(R.string.feature_channels_new_group), modifier = Modifier.padding(start = 8.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.focusRing()) {
                Text(stringResource(R.string.feature_channels_done))
            }
        },
    )
}
