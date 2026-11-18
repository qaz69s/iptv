package com.streambox.player.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.navOptions
import com.streambox.player.core.model.Channel
import com.streambox.player.core.model.ChannelSource
import com.streambox.player.feature.channels.navigation.ChannelsRoute
import com.streambox.player.feature.channels.navigation.backToChannelsWithGroup
import com.streambox.player.feature.channels.navigation.channelsScreen
import com.streambox.player.feature.channels.navigation.favoritesScreen
import com.streambox.player.feature.channels.navigation.navigateToSearch
import com.streambox.player.feature.channels.navigation.searchScreen
import com.streambox.player.feature.epg.navigation.epgScreen
import com.streambox.player.feature.onboarding.navigation.OnboardingGraphRoute
import com.streambox.player.feature.onboarding.navigation.onboardingGraph
import com.streambox.player.feature.player.navigation.navigateToPlayer
import com.streambox.player.feature.player.navigation.playerScreen
import com.streambox.player.feature.playlist.navigation.AddPlaylistRoute
import com.streambox.player.feature.playlist.navigation.addPlaylistScreen
import com.streambox.player.feature.playlist.navigation.importProgressScreen
import com.streambox.player.feature.playlist.navigation.navigateToAddPlaylist
import com.streambox.player.feature.playlist.navigation.navigateToImportProgress
import com.streambox.player.feature.playlist.navigation.navigateToPlaylists
import com.streambox.player.feature.playlist.navigation.playlistsScreen
import com.streambox.player.feature.settings.navigation.navigateToPlaybackSettings
import com.streambox.player.feature.settings.navigation.settingsScreen
import kotlinx.coroutines.launch

@Composable
fun StreamBoxNavHost(
    navController: NavHostController,
    onboardingComplete: Boolean,
    hasPlaylists: suspend () -> Boolean,
    modifier: Modifier = Modifier,
    resumeChannel: suspend () -> Long? = { null },
) {
    val scope = rememberCoroutineScope()
    val currentHasPlaylists by rememberUpdatedState(hasPlaylists)
    // "Continue last channel on launch": opens the player on top of Channels, so Back lands there.
    // Only after onboarding; resumeChannel answers once per cold start.
    if (onboardingComplete) {
        LaunchedEffect(Unit) {
            resumeChannel()?.let { channelId -> navController.navigateToPlayer(channelId, source = null) }
        }
    }
    NavHost(
        navController = navController,
        startDestination = if (onboardingComplete) ChannelsRoute else OnboardingGraphRoute,
        modifier = modifier,
    ) {
        onboardingGraph(
            navController = navController,
            onOnboardingComplete = {
                scope.launch {
                    val needsPlaylist = !currentHasPlaylists()
                    navController.navigate(ChannelsRoute) {
                        popUpTo(OnboardingGraphRoute) { inclusive = true }
                    }
                    // Channels stays underneath, so Back from "Add playlist" lands on the channel page.
                    if (needsPlaylist) navController.navigateToAddPlaylist()
                }
            },
        )
        // The player zaps through the list the channel was picked from; the key survives a refresh (K20).
        val playChannel: (Channel, ChannelSource) -> Unit = { channel, source ->
            navController.navigateToPlayer(channel.id, source, channel.key)
        }
        channelsScreen(
            onAddPlaylist = { navController.navigateToAddPlaylist() },
            onManagePlaylists = { navController.navigateToPlaylists() },
            onSearch = { navController.navigateToSearch() },
            onChannelClick = playChannel,
        )
        searchScreen(
            onBack = { navController.popBackStack() },
            onChannelClick = playChannel,
            onGroupClick = { groupName -> navController.backToChannelsWithGroup(groupName) },
        )
        epgScreen()
        favoritesScreen(onChannelClick = playChannel)
        settingsScreen(
            onOpenPlaybackSettings = { navController.navigateToPlaybackSettings() },
            onBack = { navController.popBackStack() },
        )

        addPlaylistScreen(
            onBack = { navController.popBackStack() },
            onPlaylistAdded = { playlistId ->
                // Replace the form, so Back from the progress page does not reopen a filled-in form.
                navController.navigateToImportProgress(
                    playlistId,
                    navOptions { popUpTo(AddPlaylistRoute) { inclusive = true } },
                )
            },
        )
        importProgressScreen(
            onBack = { navController.popBackStack() },
            onGoToChannels = { navController.backToChannels() },
        )
        playlistsScreen(
            navController = navController,
            onAddPlaylist = { navController.navigateToAddPlaylist() },
            onOpenImport = { playlistId -> navController.navigateToImportProgress(playlistId) },
        )
        playerScreen(onBack = { navController.popBackStack() })
    }
}

/** Channels is the root after onboarding: pop back to it, or open it if it is somehow missing. */
private fun NavHostController.backToChannels() {
    if (!popBackStack(ChannelsRoute, inclusive = false)) {
        navigate(ChannelsRoute) { launchSingleTop = true }
    }
}

/**
 * Switches top-level tabs. Channels is always the root after onboarding, so popping up to it
 * keeps one copy of each tab and makes Back return to Channels, then exit (same on phone and TV).
 */
fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(ChannelsRoute) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
