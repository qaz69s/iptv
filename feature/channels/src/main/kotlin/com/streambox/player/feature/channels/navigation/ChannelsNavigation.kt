package com.streambox.player.feature.channels.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.streambox.player.feature.channels.ChannelsScreen
import com.streambox.player.feature.channels.FavoritesScreen
import kotlinx.serialization.Serializable

@Serializable
data object ChannelsRoute

@Serializable
data object FavoritesRoute

fun NavController.navigateToChannels(navOptions: NavOptions? = null) = navigate(ChannelsRoute, navOptions)

fun NavController.navigateToFavorites(navOptions: NavOptions? = null) = navigate(FavoritesRoute, navOptions)

fun NavGraphBuilder.channelsScreen() {
    composable<ChannelsRoute> { ChannelsScreen() }
}

fun NavGraphBuilder.favoritesScreen() {
    composable<FavoritesRoute> { FavoritesScreen() }
}
