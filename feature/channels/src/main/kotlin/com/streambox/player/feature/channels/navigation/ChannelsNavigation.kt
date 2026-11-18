package com.streambox.player.feature.channels.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.streambox.player.core.model.Channel
import com.streambox.player.core.model.ChannelSource
import com.streambox.player.feature.channels.ChannelsEntry
import com.streambox.player.feature.channels.ChannelsViewModel
import com.streambox.player.feature.channels.FavoritesEntry
import com.streambox.player.feature.channels.SearchEntry
import kotlinx.serialization.Serializable

@Serializable
data object ChannelsRoute

@Serializable
data object FavoritesRoute

/** Global channel / group search (P3b). */
@Serializable
data object SearchRoute

fun NavController.navigateToChannels(navOptions: NavOptions? = null) = navigate(ChannelsRoute, navOptions)

fun NavController.navigateToFavorites(navOptions: NavOptions? = null) = navigate(FavoritesRoute, navOptions)

fun NavController.navigateToSearch(navOptions: NavOptions? = null) = navigate(SearchRoute, navOptions)

/**
 * From search back to the channel browser with [groupName] selected: the browser's ViewModel reads its
 * selection from the back stack entry's SavedStateHandle.
 */
fun NavController.backToChannelsWithGroup(groupName: String) {
    previousBackStackEntry?.savedStateHandle?.let { handle ->
        handle[ChannelsViewModel.KEY_GROUP] = groupName
        handle[ChannelsViewModel.KEY_VIRTUAL] = null
    }
    popBackStack()
}

/** Navigation targets live in other features, so the app module supplies every callback. */
fun NavGraphBuilder.channelsScreen(
    onAddPlaylist: () -> Unit,
    onManagePlaylists: () -> Unit,
    onSearch: () -> Unit,
    /** [source] is the list the channel was picked from (all, a group, favorites, recent), for zapping. */
    onChannelClick: (channel: Channel, source: ChannelSource) -> Unit,
) {
    composable<ChannelsRoute> {
        ChannelsEntry(
            onAddPlaylist = onAddPlaylist,
            onManagePlaylists = onManagePlaylists,
            onSearch = onSearch,
            onChannelClick = onChannelClick,
        )
    }
}

fun NavGraphBuilder.favoritesScreen(onChannelClick: (channel: Channel, source: ChannelSource) -> Unit) {
    composable<FavoritesRoute> { FavoritesEntry(onChannelClick = onChannelClick) }
}

fun NavGraphBuilder.searchScreen(
    onBack: () -> Unit,
    onChannelClick: (channel: Channel, source: ChannelSource) -> Unit,
    onGroupClick: (groupName: String) -> Unit,
) {
    composable<SearchRoute> {
        SearchEntry(onBack = onBack, onChannelClick = onChannelClick, onGroupClick = onGroupClick)
    }
}
