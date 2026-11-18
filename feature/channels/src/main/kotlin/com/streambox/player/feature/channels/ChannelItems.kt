@file:Suppress("MatchingDeclarationName")

package com.streambox.player.feature.channels

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.streambox.player.core.designsystem.component.focusRing
import com.streambox.player.core.designsystem.icon.StreamBoxIcons
import com.streambox.player.core.model.Channel
import com.streambox.player.core.model.ChannelViewMode

/** What one channel item needs besides the channel. */
internal data class ChannelItemState(val isFavorite: Boolean, val allowCleartext: Boolean)

/**
 * One channel in [mode]. Click / OK plays it; long-press (touch), long OK or the Menu key (remote)
 * opens a small menu to add / remove the favorite.
 */
@Composable
internal fun ChannelItem(
    channel: Channel,
    mode: ChannelViewMode,
    state: ChannelItemState,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(modifier) {
        val itemModifier = Modifier.channelClickable(
            label = channel.name,
            onClick = onClick,
            onMenu = { menuOpen = true },
            shape = RoundedCornerShape(if (mode == ChannelViewMode.Grid) 12.dp else 8.dp),
        )
        when (mode) {
            ChannelViewMode.List -> ListRow(channel, state, itemModifier)
            ChannelViewMode.Compact -> CompactRow(channel, state, itemModifier)
            ChannelViewMode.Grid -> GridCard(channel, state, itemModifier)
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            if (state.isFavorite) {
                                R.string.feature_channels_favorite_remove
                            } else {
                                R.string.feature_channels_favorite_add
                            },
                        ),
                    )
                },
                leadingIcon = {
                    Icon(
                        painterResource(if (state.isFavorite) StreamBoxIcons.StarOutline else StreamBoxIcons.Favorites),
                        contentDescription = null,
                    )
                },
                onClick = {
                    menuOpen = false
                    onToggleFavorite()
                },
                modifier = Modifier.focusRing(),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.channelClickable(
    label: String,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    shape: RoundedCornerShape,
): Modifier = this
    .focusRing(shape = shape)
    .onKeyEvent { event ->
        // Remotes: the Menu key opens the item menu (long OK works too).
        val menu = event.key.nativeKeyCode == AndroidKeyEvent.KEYCODE_MENU
        if (menu && event.type == KeyEventType.KeyUp) onMenu()
        menu
    }
    .combinedClickable(role = Role.Button, onClickLabel = label, onClick = onClick, onLongClick = onMenu)
    .semantics { contentDescription = label }

@Composable
private fun ListRow(channel: Channel, state: ChannelItemState, modifier: Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NumberText(channel, Modifier.width(40.dp))
        Spacer(Modifier.width(16.dp))
        ChannelLogo(channel, state.allowCleartext)
        Spacer(Modifier.width(16.dp))
        Text(
            text = channel.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        FavoriteMark(state.isFavorite)
    }
}

@Composable
private fun CompactRow(channel: Channel, state: ChannelItemState, modifier: Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NumberText(channel, Modifier.width(40.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            text = channel.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        FavoriteMark(state.isFavorite)
    }
}

@Composable
private fun GridCard(channel: Channel, state: ChannelItemState, modifier: Modifier) {
    Card(modifier = modifier.padding(6.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(contentAlignment = Alignment.TopEnd) {
                ChannelLogo(channel, state.allowCleartext, size = DpSize(112.dp, 72.dp))
                FavoriteMark(state.isFavorite)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                NumberText(channel, Modifier)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun NumberText(channel: Channel, modifier: Modifier) {
    Text(
        text = channel.displayNumber.takeIf { it > 0 }?.toString().orEmpty(),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.End,
        modifier = modifier,
    )
}

@Composable
private fun FavoriteMark(isFavorite: Boolean) {
    if (isFavorite) {
        Icon(
            painterResource(StreamBoxIcons.Favorites),
            contentDescription = stringResource(R.string.feature_channels_is_favorite),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
    }
}
