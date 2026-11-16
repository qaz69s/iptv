package com.streambox.player.feature.channels.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.streambox.player.feature.channels.ChannelsEntry
import com.streambox.player.feature.channels.FavoritesScreen
import kotlinx.serialization.Serializable

@Serializable
data object ChannelsRoute

@Serializable
data object FavoritesRoute

fun NavController.navigateToChannels(navOptions: NavOptions? = null) = navigate(ChannelsRoute, navOptions)

fun NavController.navigateToFavorites(navOptions: NavOptions? = null) = navigate(FavoritesRoute, navOptions)

/** Navigation targets live in other features, so the app module supplies every callback. */
fun NavGraphBuilder.channelsScreen(
    onAddPlaylist: () -> Unit,
    onManagePlaylists: () -> Unit,
    onChannelClick: (channelId: Long) -> Unit,
) {
    composable<ChannelsRoute> {
        ChannelsEntry(
            onAddPlaylist = onAddPlaylist,
            onManagePlaylists = onManagePlaylists,
            onChannelClick = onChannelClick,
        )
    }
}

fun NavGraphBuilder.favoritesScreen() {
    composable<FavoritesRoute> { FavoritesScreen() }
}
