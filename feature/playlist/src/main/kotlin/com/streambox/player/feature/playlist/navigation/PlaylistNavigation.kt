package com.streambox.player.feature.playlist.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.streambox.player.feature.playlist.AddPlaylistEntry
import com.streambox.player.feature.playlist.ImportProgressEntry
import com.streambox.player.feature.playlist.PlaylistsEntry
import kotlinx.serialization.Serializable

@Serializable
data object AddPlaylistRoute

/** The argument name must stay `playlistId`: ImportProgressViewModel reads it from SavedStateHandle. */
@Serializable
data class ImportProgressRoute(val playlistId: Long)

@Serializable
data object PlaylistsRoute

fun NavController.navigateToAddPlaylist(navOptions: NavOptions? = null) = navigate(AddPlaylistRoute, navOptions)

fun NavController.navigateToImportProgress(playlistId: Long, navOptions: NavOptions? = null) =
    navigate(ImportProgressRoute(playlistId), navOptions)

fun NavController.navigateToPlaylists(navOptions: NavOptions? = null) = navigate(PlaylistsRoute, navOptions)

fun NavGraphBuilder.addPlaylistScreen(onBack: () -> Unit, onPlaylistAdded: (playlistId: Long) -> Unit) {
    composable<AddPlaylistRoute> { AddPlaylistEntry(onBack = onBack, onPlaylistAdded = onPlaylistAdded) }
}

fun NavGraphBuilder.importProgressScreen(onBack: () -> Unit, onGoToChannels: () -> Unit) {
    composable<ImportProgressRoute> { ImportProgressEntry(onBack = onBack, onGoToChannels = onGoToChannels) }
}

fun NavGraphBuilder.playlistsScreen(
    onBack: () -> Unit,
    onAddPlaylist: () -> Unit,
    onOpenImport: (playlistId: Long) -> Unit,
) {
    composable<PlaylistsRoute> {
        PlaylistsEntry(onBack = onBack, onAddPlaylist = onAddPlaylist, onOpenImport = onOpenImport)
    }
}
