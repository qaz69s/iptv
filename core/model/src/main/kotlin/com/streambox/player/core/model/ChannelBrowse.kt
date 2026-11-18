package com.streambox.player.core.model

/**
 * Channel order of the browser and of zapping (requirements §4.2).
 * - [Default]: the playlist's own order (playlist by playlist, import order inside each).
 * - [Name]: display name (the user's name if renamed), ignoring ASCII case, across all playlists.
 * - [Number]: display number (tvg-chno, else playlist position), across all playlists.
 * - [Custom]: the user's own order (list editor, P3c; favorites: drag order), else the playlist's.
 */
enum class ChannelSortOrder { Default, Name, Number, Custom }

/** How the channel browser draws channels: rows with logo, a logo grid, or dense text rows. */
enum class ChannelViewMode { List, Grid, Compact }

/** Persisted channel-browser choices (DataStore). */
data class BrowseSettings(
    val viewMode: ChannelViewMode = ChannelViewMode.List,
    val sortOrder: ChannelSortOrder = ChannelSortOrder.Default,
)

/**
 * Where the user picked a channel, which is also the list the player zaps through: everything, one
 * group (by display name), or the virtual groups "favorites" and "recently watched".
 */
sealed interface ChannelSource {
    data object All : ChannelSource

    data class Group(val name: String) : ChannelSource

    data object Favorites : ChannelSource

    data object Recent : ChannelSource
}

/** Global search hits (requirements §4.2): matching groups and channels. */
data class SearchResults(
    val groups: List<ChannelGroupSummary> = emptyList(),
    val channels: List<Channel> = emptyList(),
) {
    val isEmpty: Boolean get() = groups.isEmpty() && channels.isEmpty()
}
