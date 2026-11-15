package com.streambox.player.feature.epg.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.streambox.player.feature.epg.EpgScreen
import kotlinx.serialization.Serializable

@Serializable
data object EpgRoute

fun NavController.navigateToEpg(navOptions: NavOptions? = null) = navigate(EpgRoute, navOptions)

fun NavGraphBuilder.epgScreen() {
    composable<EpgRoute> { EpgScreen() }
}
