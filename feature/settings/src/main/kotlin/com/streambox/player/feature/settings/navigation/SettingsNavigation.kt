package com.streambox.player.feature.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.streambox.player.feature.settings.PlaybackSettingsRoute
import com.streambox.player.feature.settings.SettingsRoute
import kotlinx.serialization.Serializable

@Serializable
data object SettingsDestination

/** Second-level "Playback" settings page (K8). */
@Serializable
data object PlaybackSettingsDestination

fun NavController.navigateToSettings(navOptions: NavOptions? = null) = navigate(SettingsDestination, navOptions)

fun NavController.navigateToPlaybackSettings(navOptions: NavOptions? = null) =
    navigate(PlaybackSettingsDestination, navOptions)

fun NavGraphBuilder.settingsScreen(onOpenPlaybackSettings: () -> Unit, onBack: () -> Unit) {
    composable<SettingsDestination> { SettingsRoute(onOpenPlaybackSettings = onOpenPlaybackSettings) }
    composable<PlaybackSettingsDestination> { PlaybackSettingsRoute(onBack = onBack) }
}
