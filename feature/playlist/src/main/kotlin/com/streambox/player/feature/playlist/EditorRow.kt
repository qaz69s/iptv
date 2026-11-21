package com.streambox.player.feature.playlist

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.streambox.player.core.designsystem.component.focusRing
import com.streambox.player.core.designsystem.icon.StreamBoxIcons

/**
 * One row of the list editors (P3c). Hidden items are drawn greyed out (user decision 2). Every
 * button is a separate focus stop with a visible focus ring, so the row works with a remote; the move
 * buttons are disabled at the ends of the list.
 */
@Composable
internal fun EditorRow(
    title: String,
    subtitle: String?,
    hidden: Boolean,
    actions: EditorRowActions,
    modifier: Modifier = Modifier,
    canMoveUp: Boolean = true,
    canMoveDown: Boolean = true,
) {
    Card(modifier = modifier.widthIn(max = 720.dp).fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            val textModifier = Modifier.weight(1f).alpha(if (hidden) HIDDEN_ALPHA else 1f)
            Column(
                modifier = actions.onOpen?.let { open ->
                    textModifier.focusRing().clickable(onClick = open)
                } ?: textModifier,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 4.dp),
                    )
                }
            }
            actions.onToggleVisible?.let {
                EditorButton(
                    icon = if (hidden) StreamBoxIcons.Hidden else StreamBoxIcons.Visible,
                    description = stringResource(
                        if (hidden) R.string.feature_playlist_editor_show else R.string.feature_playlist_editor_hide,
                    ),
                    onClick = it,
                )
            }
            actions.onRename?.let {
                EditorButton(StreamBoxIcons.Edit, stringResource(R.string.feature_playlist_editor_rename), it)
            }
            actions.onMoveUp?.let {
                EditorButton(StreamBoxIcons.ArrowUp, stringResource(R.string.feature_playlist_editor_up), it, canMoveUp)
            }
            actions.onMoveDown?.let {
                EditorButton(
                    StreamBoxIcons.ArrowDown,
                    stringResource(R.string.feature_playlist_editor_down),
                    it,
                    canMoveDown,
                )
            }
            actions.onDelete?.let {
                EditorButton(StreamBoxIcons.Delete, stringResource(actions.deleteLabel), it)
            }
        }
    }
}

@Composable
private fun EditorButton(@DrawableRes icon: Int, description: String, onClick: () -> Unit, enabled: Boolean = true) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.focusRing()) {
        Icon(painterResource(icon), contentDescription = description)
    }
}

private const val HIDDEN_ALPHA = 0.45f
