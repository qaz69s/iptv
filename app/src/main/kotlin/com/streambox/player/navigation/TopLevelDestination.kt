package com.streambox.player.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.streambox.player.R
import com.streambox.player.core.designsystem.icon.StreamBoxIcons
import com.streambox.player.feature.channels.navigation.ChannelsRoute
import com.streambox.player.feature.channels.navigation.FavoritesRoute
import com.streambox.player.feature.epg.navigation.EpgRoute
import com.streambox.player.feature.settings.navigation.SettingsDestination
import kotlin.reflect.KClass

/** Destinations shown in the bottom bar (phone), rail (tablet) or side drawer (TV). */
enum class TopLevelDestination(
    val route: Any,
    val routeClass: KClass<*>,
    @DrawableRes val icon: Int,
    @StringRes val label: Int,
) {
    Channels(ChannelsRoute, ChannelsRoute::class, StreamBoxIcons.Channels, R.string.nav_channels),
    Epg(EpgRoute, EpgRoute::class, StreamBoxIcons.Epg, R.string.nav_epg),
    Favorites(FavoritesRoute, FavoritesRoute::class, StreamBoxIcons.Favorites, R.string.nav_favorites),
    Settings(SettingsDestination, SettingsDestination::class, StreamBoxIcons.Settings, R.string.nav_settings),
}
