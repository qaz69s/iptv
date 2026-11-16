package com.streambox.player.feature.player.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.streambox.player.feature.player.PlayerEntry
import kotlinx.serialization.Serializable

/**
 * Full-screen playback of one channel. [groupName] is the channel-list filter the user came from
 * (null = all groups), so zapping stays inside that group. The argument names are read by
 * PlayerViewModel from SavedStateHandle; keep them in sync.
 */
@Serializable
data class PlayerRoute(val channelId: Long, val groupName: String? = null)

fun NavController.navigateToPlayer(channelId: Long, groupName: String?, navOptions: NavOptions? = null) =
    navigate(PlayerRoute(channelId, groupName), navOptions)

fun NavGraphBuilder.playerScreen(onBack: () -> Unit) {
    composable<PlayerRoute> { PlayerEntry(onBack = onBack) }
}
