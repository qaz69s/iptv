package com.streambox.player.feature.onboarding.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.streambox.player.feature.onboarding.DisclaimerRoute
import com.streambox.player.feature.onboarding.WelcomeScreen
import kotlinx.serialization.Serializable

/** Entry point of the first-launch flow: welcome -> disclaimer. */
@Serializable
data object OnboardingGraphRoute

@Serializable
internal data object WelcomeDestination

@Serializable
internal data object DisclaimerDestination

fun NavGraphBuilder.onboardingGraph(navController: NavController, onOnboardingComplete: () -> Unit) {
    navigation<OnboardingGraphRoute>(startDestination = WelcomeDestination) {
        composable<WelcomeDestination> {
            WelcomeScreen(onContinue = { navController.navigate(DisclaimerDestination) })
        }
        composable<DisclaimerDestination> {
            DisclaimerRoute(
                onAccepted = onOnboardingComplete,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
