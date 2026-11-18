package com.streambox.player.feature.player.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.streambox.player.core.model.ChannelKey
import com.streambox.player.core.model.ChannelSource
import com.streambox.player.feature.player.PlayerEntry
import kotlinx.serialization.Serializable

/**
 * Full-screen playback of one channel. The argument names are read by PlayerViewModel from
 * SavedStateHandle; keep them in sync.
 * - [channelId]: the channel's row id when the user picked it.
 * - [source] + [groupName]: the list it was picked from, which zapping follows ([PlayerSource]).
 *   Null = opened without a list (continue last channel): zap through all channels, auto-play next
 *   inside the channel's own group (P2b behaviour).
 * - [playlistId] + [uniqueKey] (P3b, K20): the channel's stable key. Row ids change when a playlist
 *   refresh finishes, so the player resolves the key again when the id no longer matches it.
 *   [NO_PLAYLIST] / null when unknown.
 */
@Serializable
data class PlayerRoute(
    val channelId: Long,
    val groupName: String? = null,
    val source: String? = null,
    val playlistId: Long = NO_PLAYLIST,
    val uniqueKey: String? = null,
) {
    companion object {
        const val NO_PLAYLIST = -1L
    }
}

/**
 * How [PlayerRoute.source] names a [ChannelSource] (the group's name, or a custom group's id, travels in
 * [PlayerRoute.groupName]).
 */
object PlayerSource {
    const val ALL = "all"
    const val GROUP = "group"
    const val FAVORITES = "favorites"
    const val RECENT = "recent"

    /** A custom group (P3c); its id travels in [PlayerRoute.groupName]. */
    const val CUSTOM = "custom"

    fun encode(source: ChannelSource): Pair<String, String?> = when (source) {
        ChannelSource.All -> ALL to null
        is ChannelSource.Group -> GROUP to source.name
        ChannelSource.Favorites -> FAVORITES to null
        ChannelSource.Recent -> RECENT to null
        is ChannelSource.Custom -> CUSTOM to source.groupId.toString()
    }

    /** Null for routes without a source (or an unknown value). */
    fun decode(source: String?, groupName: String?): ChannelSource? = when (source) {
        ALL -> ChannelSource.All
        GROUP -> groupName?.let { ChannelSource.Group(it) } ?: ChannelSource.All
        FAVORITES -> ChannelSource.Favorites
        RECENT -> ChannelSource.Recent
        CUSTOM -> groupName?.toLongOrNull()?.let { ChannelSource.Custom(it) } ?: ChannelSource.All
        else -> null
    }
}

/** Opens the player for a channel picked from [source]; [key] lets it survive a refresh (K20). */
fun NavController.navigateToPlayer(
    channelId: Long,
    source: ChannelSource?,
    key: ChannelKey? = null,
    navOptions: NavOptions? = null,
) {
    val (sourceName, groupName) = source?.let(PlayerSource::encode) ?: (null to null)
    navigate(
        PlayerRoute(
            channelId = channelId,
            groupName = groupName,
            source = sourceName,
            playlistId = key?.playlistId ?: PlayerRoute.NO_PLAYLIST,
            uniqueKey = key?.uniqueKey,
        ),
        navOptions,
    )
}

fun NavGraphBuilder.playerScreen(onBack: () -> Unit) {
    composable<PlayerRoute> { PlayerEntry(onBack = onBack) }
}
