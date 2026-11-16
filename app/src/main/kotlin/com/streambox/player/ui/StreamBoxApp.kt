package com.streambox.player.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffoldDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.Icon as TvIcon
import androidx.tv.material3.NavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.Text as TvText
import com.streambox.player.core.ui.FormFactor
import com.streambox.player.navigation.StreamBoxNavHost
import com.streambox.player.navigation.TopLevelDestination
import com.streambox.player.navigation.navigateToTopLevel

@Composable
fun StreamBoxApp(
    formFactor: FormFactor,
    onboardingComplete: Boolean,
    hasPlaylists: suspend () -> Boolean,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentTopLevel = backStackEntry?.destination.topLevelDestination()

    if (formFactor == FormFactor.Tv) {
        TvAppLayout(navController, currentTopLevel, onboardingComplete, hasPlaylists)
    } else {
        TouchAppLayout(navController, currentTopLevel, onboardingComplete, hasPlaylists)
    }
}

/** Phone: bottom bar. Tablet / large windows: navigation rail. Hidden during onboarding. */
@Composable
private fun TouchAppLayout(
    navController: NavHostController,
    currentTopLevel: TopLevelDestination?,
    onboardingComplete: Boolean,
    hasPlaylists: suspend () -> Boolean,
) {
    val layoutType = if (currentTopLevel == null) {
        NavigationSuiteType.None
    } else {
        NavigationSuiteScaffoldDefaults.calculateFromAdaptiveInfo(currentWindowAdaptiveInfoV2())
    }
    NavigationSuiteScaffold(
        layoutType = layoutType,
        navigationSuiteItems = {
            TopLevelDestination.entries.forEach { destination ->
                item(
                    selected = destination == currentTopLevel,
                    onClick = { navController.navigateToTopLevel(destination) },
                    icon = { Icon(painterResource(destination.icon), contentDescription = null) },
                    label = { Text(stringResource(destination.label)) },
                )
            }
        },
    ) {
        StreamBoxNavHost(
            navController = navController,
            onboardingComplete = onboardingComplete,
            hasPlaylists = hasPlaylists,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** TV: Compose-for-TV side drawer that expands when focused; D-pad left opens it. */
@Composable
private fun TvAppLayout(
    navController: NavHostController,
    currentTopLevel: TopLevelDestination?,
    onboardingComplete: Boolean,
    hasPlaylists: suspend () -> Boolean,
) {
    Surface(modifier = Modifier.fillMaxSize()) {
        // The NavHost always stays in the same place in the tree; only the drawer's items are hidden on
        // onboarding and sub-pages (add playlist, import, player). Moving the NavHost between two
        // branches would recreate it and lose screen state.
        NavigationDrawer(
            drawerContent = {
                if (currentTopLevel != null) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(12.dp)
                            .selectableGroup(),
                        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                    ) {
                        TopLevelDestination.entries.forEach { destination ->
                            NavigationDrawerItem(
                                selected = destination == currentTopLevel,
                                onClick = { navController.navigateToTopLevel(destination) },
                                leadingContent = {
                                    TvIcon(painterResource(destination.icon), contentDescription = null)
                                },
                            ) {
                                TvText(stringResource(destination.label))
                            }
                        }
                    }
                }
            },
        ) {
            StreamBoxNavHost(
                navController = navController,
                onboardingComplete = onboardingComplete,
                hasPlaylists = hasPlaylists,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = if (currentTopLevel != null) 8.dp else 0.dp),
            )
        }
    }
}

private fun NavDestination?.topLevelDestination(): TopLevelDestination? {
    if (this == null) return null
    return TopLevelDestination.entries.firstOrNull { destination ->
        hierarchy.any { it.hasRoute(destination.routeClass) }
    }
}
