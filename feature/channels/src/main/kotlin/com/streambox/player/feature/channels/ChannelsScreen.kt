package com.streambox.player.feature.channels

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.streambox.player.core.designsystem.component.EmptyState
import com.streambox.player.core.designsystem.icon.StreamBoxIcons
import com.streambox.player.core.designsystem.theme.StreamBoxTheme

/**
 * Channel browser. A fresh install has no playlists and therefore no channels;
 * nothing is ever bundled with the app.
 */
@Composable
internal fun ChannelsScreen(modifier: Modifier = Modifier) {
    EmptyState(
        title = stringResource(R.string.feature_channels_empty_title),
        message = stringResource(R.string.feature_channels_empty_message),
        icon = StreamBoxIcons.PlaylistAdd,
        modifier = modifier,
    )
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
private fun ChannelsScreenPreview() {
    StreamBoxTheme { ChannelsScreen() }
}
