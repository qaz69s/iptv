package com.streambox.player.feature.playlist.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.streambox.player.feature.playlist.AddPlaylistEntry
import com.streambox.player.feature.playlist.CustomGroupEditorEntry
import com.streambox.player.feature.playlist.CustomGroupsEntry
import com.streambox.player.feature.playlist.GroupEditorEntry
import com.streambox.player.feature.playlist.ImportProgressEntry
import com.streambox.player.feature.playlist.PlaylistEditorEntry
import com.streambox.player.feature.playlist.PlaylistsEntry
import kotlinx.serialization.Serializable

@Serializable
data object AddPlaylistRoute

/** The argument name must stay `playlistId`: ImportProgressViewModel reads it from SavedStateHandle. */
@Serializable
data class ImportProgressRoute(val playlistId: Long)

@Serializable
data object PlaylistsRoute

/** List editor of one playlist (P3c). The argument name is read by PlaylistEditorViewModel. */
@Serializable
data class PlaylistEditorRoute(val playlistId: Long)

/**
 * Channels of one group ([groupName] as the playlist names it); read by GroupEditorViewModel. The name
 * has a default so it travels as a query argument: "" (channels without a group) is a valid name.
 */
@Serializable
data class GroupEditorRoute(val playlistId: Long, val groupName: String = "")

/** The user's custom groups (P3c). */
@Serializable
data object CustomGroupsRoute

/** Channels of one custom group; the argument name is read by CustomGroupEditorViewModel. */
@Serializable
data class CustomGroupEditorRoute(val groupId: Long)

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

/**
 * Playlist management plus its P3c editors (list editor per playlist, a group's channels, custom
 * groups and their channels). The editors navigate among themselves through [navController].
 */
fun NavGraphBuilder.playlistsScreen(
    navController: NavController,
    onAddPlaylist: () -> Unit,
    onOpenImport: (playlistId: Long) -> Unit,
) {
    val onBack: () -> Unit = { navController.popBackStack() }
    composable<PlaylistsRoute> {
        PlaylistsEntry(
            onBack = onBack,
            onAddPlaylist = onAddPlaylist,
            onOpenImport = onOpenImport,
            onEdit = { navController.navigate(PlaylistEditorRoute(it)) },
            onCustomGroups = { navController.navigate(CustomGroupsRoute) },
        )
    }
    composable<PlaylistEditorRoute> { entry ->
        PlaylistEditorEntry(
            onBack = onBack,
            onOpenGroup = { playlistId, group -> navController.navigate(GroupEditorRoute(playlistId, group)) },
            playlistId = entry.toRoute<PlaylistEditorRoute>().playlistId,
        )
    }
    composable<GroupEditorRoute> { GroupEditorEntry(onBack = onBack) }
    composable<CustomGroupsRoute> {
        CustomGroupsEntry(onBack = onBack, onOpenGroup = { navController.navigate(CustomGroupEditorRoute(it)) })
    }
    composable<CustomGroupEditorRoute> { CustomGroupEditorEntry(onBack = onBack) }
}
