package com.streambox.player.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import com.streambox.player.feature.channels.navigation.ChannelsRoute
import com.streambox.player.feature.channels.navigation.channelsScreen
import com.streambox.player.feature.channels.navigation.favoritesScreen
import com.streambox.player.feature.epg.navigation.epgScreen
import com.streambox.player.feature.onboarding.navigation.OnboardingGraphRoute
import com.streambox.player.feature.onboarding.navigation.onboardingGraph
import com.streambox.player.feature.settings.navigation.settingsScreen

@Composable
fun StreamBoxNavHost(navController: NavHostController, onboardingComplete: Boolean, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = if (onboardingComplete) ChannelsRoute else OnboardingGraphRoute,
        modifier = modifier,
    ) {
        onboardingGraph(
            navController = navController,
            onOnboardingComplete = {
                navController.navigate(ChannelsRoute) {
                    popUpTo(OnboardingGraphRoute) { inclusive = true }
                }
            },
        )
        channelsScreen()
        epgScreen()
        favoritesScreen()
        settingsScreen()
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
