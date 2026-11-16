package com.streambox.player.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.streambox.player.R
import com.streambox.player.core.designsystem.component.EmptyState
import com.streambox.player.core.designsystem.icon.StreamBoxIcons
import kotlinx.serialization.Serializable

/**
 * Stand-in for the player screen (next step of P1). Channels only reports `onChannelClick(channelId)`;
 * replace this route with the player feature's route and keep the same call site in StreamBoxNavHost.
 */
@Serializable
data class PlayerPlaceholderRoute(val channelId: Long)

fun NavController.navigateToPlayerPlaceholder(channelId: Long) = navigate(PlayerPlaceholderRoute(channelId))

fun NavGraphBuilder.playerPlaceholderScreen(onBack: () -> Unit) {
    composable<PlayerPlaceholderRoute> { entry ->
        val route = entry.toRoute<PlayerPlaceholderRoute>()
        PlayerPlaceholder(channelId = route.channelId, onBack = onBack)
    }
}

@Composable
private fun PlayerPlaceholder(channelId: Long, onBack: () -> Unit, modifier: Modifier = Modifier) {
    EmptyState(
        title = stringResource(R.string.player_placeholder_title),
        message = stringResource(R.string.player_placeholder_message, channelId.toInt()),
        icon = StreamBoxIcons.Channels,
        actionLabel = stringResource(R.string.player_placeholder_back),
        onAction = onBack,
        modifier = modifier,
    )
}
