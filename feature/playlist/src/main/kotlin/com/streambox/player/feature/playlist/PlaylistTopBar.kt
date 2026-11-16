package com.streambox.player.feature.playlist

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.streambox.player.core.designsystem.component.focusRing
import com.streambox.player.core.designsystem.icon.StreamBoxIcons

/** Shared top bar of the playlist screens: title plus an always-visible, focusable Back button. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaylistTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.focusRing()) {
                Icon(
                    painter = painterResource(StreamBoxIcons.Back),
                    contentDescription = stringResource(R.string.feature_playlist_back),
                )
            }
        },
        actions = actions,
        modifier = modifier,
    )
}
