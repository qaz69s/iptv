package com.streamdeck.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarViewMonth
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SlowMotionVideo
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.streamdeck.player.R
import com.streamdeck.player.core.data.ChannelRepository
import com.streamdeck.player.core.data.DeepLinkBus
import com.streamdeck.player.core.database.ChannelListItem
import com.streamdeck.player.core.database.PlaylistType
import com.streamdeck.player.core.datastore.SettingsRepository
import com.streamdeck.player.core.designsystem.FocusSurface
import com.streamdeck.player.core.designsystem.SdTheme
import com.streamdeck.player.core.designsystem.rememberIsTv
import com.streamdeck.player.feature.cast.CastButton
import com.streamdeck.player.feature.channels.ChannelsScreen
import com.streamdeck.player.feature.channels.FavoritesScreen
import com.streamdeck.player.feature.channels.TvBrowseScreen
import com.streamdeck.player.feature.channels.TvHomeScreen
import com.streamdeck.player.feature.epg.ChannelProgramsScreen
import com.streamdeck.player.feature.epg.EpgGridScreen
import com.streamdeck.player.feature.epg.EpgViewModelArgs
import com.streamdeck.player.feature.monetization.AdsProvider
import com.streamdeck.player.feature.playerui.PlayerArgs
import com.streamdeck.player.feature.playerui.PlayerScreen
import com.streamdeck.player.feature.playerui.PlayerViewModel
import com.streamdeck.player.feature.playlist.AddPlaylistScreen
import com.streamdeck.player.feature.playlist.OnboardingScreen
import com.streamdeck.player.feature.playlist.PlaylistEditorScreen
import com.streamdeck.player.feature.playlist.PlaylistEditorViewModel
import com.streamdeck.player.feature.playlist.PlaylistManagerScreen
import com.streamdeck.player.feature.settings.PinGate
import com.streamdeck.player.feature.settings.PlaylistEditDialogWithPolicy
import com.streamdeck.player.feature.settings.SettingsScreen
import com.streamdeck.player.feature.settings.rememberPinGate
import com.streamdeck.player.feature.vod.VodArgs
import com.streamdeck.player.feature.vod.VodDetailScreen
import com.streamdeck.player.feature.vod.VodHomeScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface StartState {
    data object Loading : StartState
    data class Ready(val onboarded: Boolean, val resumeChannelId: Long?) : StartState
}

@HiltViewModel
class RootViewModel @Inject constructor(
    settingsRepo: SettingsRepository,
    channels: ChannelRepository,
    bus: DeepLinkBus,
    /** No-op in this build (ads off by default, spec §4.10). */
    val ads: AdsProvider,
) : ViewModel() {
    /** Channel ids requested by reminders / notifications. */
    val deepLinks: Flow<Pair<Long, Boolean>> = bus.targets.mapNotNull { t -> channels.byKey(t.playlistId, t.uniqueKey)?.let { it.id to channels.isLocked(it) } }

    private val _start = MutableStateFlow<StartState>(StartState.Loading)
    val start: StateFlow<StartState> = _start
    val settings = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, com.streamdeck.player.core.datastore.AppSettings())

    init {
        viewModelScope.launch {
            val onboarded = settingsRepo.isOnboarded.first()
            val s = settingsRepo.current()
            val resume = if (onboarded && s.autoResume) {
                settingsRepo.lastChannel.first()?.let { channels.byKey(it.playlistId,
                    it.uniqueKey) }?.takeUnless { channels.isLocked(it) }?.id
            } else {
                null
            }
            _start.value = StartState.Ready(onboarded, resume)
        }
    }
}

object Routes {
    const val ONBOARDING = "onboarding"
    const val ADD = "add"
    const val ARG_ADD_TYPE = "type"

    /** Optional preselected method (from the onboarding cards); plain "add" still matches this pattern. */
    const val ADD_PATTERN = "add?$ARG_ADD_TYPE={$ARG_ADD_TYPE}"
    fun add(type: PlaylistType) = "add?$ARG_ADD_TYPE=${type.name}"
    const val CHANNELS = "channels"
    const val FAVORITES = "favorites"
    const val MANAGE = "manage"
    const val EDITOR = "editor/{${PlaylistEditorViewModel.ARG_PLAYLIST_ID}}"
    fun editor(id: Long) = "editor/$id"
    const val PLAYER = "player/{${PlayerViewModel.ARG_CHANNEL_ID}}"
    fun player(id: Long) = "player/$id"
    const val EPG = "epg"
    const val PROGRAMS = "programs/{${EpgViewModelArgs.CHANNEL_ID}}"
    fun programs(channelId: Long) = "programs/$channelId"
    const val VOD = "vod"
    const val VOD_DETAIL = "vod/{${VodArgs.PLAYLIST_ID}}/{${VodArgs.KIND}}/{${VodArgs.ITEM_ID}}"
    fun vodDetail(pid: Long, kind: String, id: Long) = "vod/$pid/$kind/$id"
    const val VOD_PLAYER = "vodplay/{${PlayerArgs.VOD_KEY}}"
    fun vodPlayer(key: String) = "vodplay/" + android.net.Uri.encode(key)
    const val CATCHUP_PLAYER = "catchup/{${PlayerViewModel.ARG_CHANNEL_ID}}/{${PlayerArgs.CATCHUP_START}}/{${PlayerArgs.CATCHUP_END}}"
    fun catchup(channelId: Long, startMs: Long, endMs: Long) = "catchup/$channelId/$startMs/$endMs"
    const val SETTINGS = "settings"
    const val TV_HOME = "tv_home"
    const val TV_SEARCH = "tv_search"

    /** Catch-up of a finished programme; live playback otherwise. */
    fun catchupOrLive(channelId: Long, startMs: Long, endMs: Long) = if (endMs <= System.currentTimeMillis()) {
        catchup(channelId, startMs,
            endMs)
    } else {
        player(channelId)
    }
}

private data class Tab(val route: String, val label: Int, val icon: ImageVector)

/** Phone: floating bottom bar 直播 / 节目单 / 点播 / 我的 (design 1). */
private val PHONE_TABS = listOf(
    Tab(Routes.CHANNELS, R.string.nav_live, Icons.Outlined.LiveTv),
    Tab(Routes.EPG, R.string.nav_epg, Icons.Outlined.CalendarViewMonth),
    Tab(Routes.VOD, R.string.nav_vod, Icons.Outlined.SlowMotionVideo),
    Tab(Routes.SETTINGS, R.string.nav_mine, Icons.Outlined.Person),
)

/** TV: narrow icon rail 搜索 / 直播 / 节目单 / 点播 / 收藏, settings pinned to the bottom (design 4). */
private val TV_TABS = listOf(
    Tab(Routes.TV_SEARCH, R.string.nav_search, Icons.Outlined.Search),
    Tab(Routes.TV_HOME, R.string.nav_live, Icons.Outlined.LiveTv),
    Tab(Routes.EPG, R.string.nav_epg, Icons.Outlined.CalendarViewMonth),
    Tab(Routes.VOD, R.string.nav_vod, Icons.Outlined.SlowMotionVideo),
    Tab(Routes.FAVORITES, R.string.nav_favorites, Icons.Outlined.StarOutline),
)
private val TV_SETTINGS = Tab(Routes.SETTINGS, R.string.nav_settings, Icons.Outlined.Settings)

/**
 * Root navigation. The same routes serve phone and TV, but the shell (bottom bar vs icon rail) and the home
 * destination differ; the choice is made at runtime from the UI mode (see `isTv`).
 */
@Composable
fun AppNav(root: RootViewModel = hiltViewModel()) {
    val start by root.start.collectAsStateWithLifecycle()
    val ready = start as? StartState.Ready ?: return
    val tv = rememberIsTv()
    val nav = rememberNavController()
    var resumed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(ready) {
        val id = ready.resumeChannelId
        if (!resumed && id != null) { resumed = true; nav.navigate(Routes.player(id)) }
    }
    val settingsState by root.settings.collectAsStateWithLifecycle()
    val gate = rememberPinGate()
    val play: (ChannelListItem) -> Unit = { item ->
        gate.runUnlocked(item.channel.locked || item.groupLocked) { nav.navigate(Routes.player(item.channel.id)) }
    }
    LaunchedEffect(Unit) { root.deepLinks.collect { (id, locked) -> gate.runUnlocked(locked) { nav.navigate(Routes.player(id)) } } }
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    val home = if (tv) Routes.TV_HOME else Routes.CHANNELS
    val p = SdTheme.palette

    Box(Modifier.fillMaxSize().background(p.background)) {
        if (tv) {
            val showRail = (TV_TABS + TV_SETTINGS).any { it.route == current }
            Row(Modifier.fillMaxSize()) {
                if (showRail) TvRail(nav, current)
                Graph(nav, ready.onboarded, home, tv = true, play = play, gate = gate, lockEditor = settingsState.lockEditor, modifier = Modifier.weight(1f))
            }
        } else {
            val showTabs = PHONE_TABS.any { it.route == current }
            Graph(nav, ready.onboarded, home, tv = false, play = play, gate = gate, lockEditor = settingsState.lockEditor, modifier = Modifier.fillMaxSize())
            if (showTabs) {
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
                    // Banner only on browsing tabs — never on the player (spec §4.10).
                    root.ads.Banner(Modifier)
                    FloatingTabs(nav, current)
                }
            }
        }
    }
    gate.Host()
}

@Suppress("LongParameterList")
@Composable
private fun Graph(
    nav: NavHostController,
    onboarded: Boolean,
    home: String,
    tv: Boolean,
    play: (ChannelListItem) -> Unit,
    gate: PinGate,
    lockEditor: Boolean,
    modifier: Modifier,
) {
    val manage = { gate.runUnlocked(lockEditor) { nav.navigate(Routes.MANAGE) } }
    NavHost(nav, startDestination = if (onboarded) home else Routes.ONBOARDING, modifier = modifier) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onDone = { type -> nav.navigate(Routes.add(type)) { popUpTo(Routes.ONBOARDING) { inclusive = true } } },
                onSkip = { nav.navigate(home) { popUpTo(Routes.ONBOARDING) { inclusive = true } } },
            )
        }
        composable(
            Routes.ADD_PATTERN,
            arguments = listOf(navArgument(Routes.ARG_ADD_TYPE) { type = NavType.StringType; nullable = true; defaultValue = null }),
        ) { entry ->
            val raw = entry.arguments?.getString(Routes.ARG_ADD_TYPE)
            val initial = PlaylistType.entries.firstOrNull { it.name == raw } ?: PlaylistType.M3U_URL
            AddPlaylistScreen(
                onImported = { nav.navigate(home) { popUpTo(0) } },
                onBack = { if (!nav.popBackStack()) nav.navigate(home) },
                initialType = initial,
            )
        }
        composable(Routes.MANAGE) {
            PlaylistManagerScreen(onBack = { nav.popBackStack() }, onAdd = { nav.navigate(Routes.ADD) }, onOpenEditor = { nav.navigate(Routes.editor(it)) },
                editDialog = { pl, dismiss, save -> PlaylistEditDialogWithPolicy(pl, dismiss, save) })
        }
        composable(Routes.EDITOR, arguments = listOf(navArgument(PlaylistEditorViewModel.ARG_PLAYLIST_ID) { type = NavType.LongType })) {
            PlaylistEditorScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.CHANNELS) {
            ChannelsScreen(onAddPlaylist = { nav.navigate(Routes.ADD) }, onManagePlaylists = manage, onPlay = play)
        }
        composable(Routes.TV_HOME) {
            TvHomeScreen(onPlay = play, onOpenGuide = { nav.navigate(Routes.programs(it)) }, onAddPlaylist = { nav.navigate(Routes.ADD) })
        }
        composable(Routes.TV_SEARCH) { TvBrowseScreen(onPlay = play, onAddPlaylist = { nav.navigate(Routes.ADD) }) }
        composable(Routes.FAVORITES) { FavoritesScreen(onPlay = play) }
        composable(Routes.EPG) {
            EpgGridScreen(
                onPlay = { id -> nav.navigate(Routes.player(id)) },
                onCatchup = { id, s, e -> nav.navigate(Routes.catchupOrLive(id, s, e)) },
                onOpenChannel = { nav.navigate(Routes.programs(it)) },
                bottomPadding = if (tv) 0.dp else 88.dp,
            )
        }
        composable(Routes.PROGRAMS, arguments = listOf(navArgument(EpgViewModelArgs.CHANNEL_ID) { type = NavType.LongType })) {
            ChannelProgramsScreen(onBack = { nav.popBackStack() }, onPlay = { id -> nav.navigate(Routes.player(id)) }, onCatchup = { id, s,
                                                                                                                                     e -> nav.navigate(Routes.catchupOrLive(id, s, e)) })
        }
        composable(Routes.VOD) {
            // The phone tab bar floats over content; keep the last VOD row above it.
            Box(Modifier.fillMaxSize().padding(bottom = if (tv) 0.dp else 80.dp)) {
                VodHomeScreen(onOpen = { pid, kind, id -> nav.navigate(Routes.vodDetail(pid, kind, id)) })
            }
        }
        composable(
            Routes.VOD_DETAIL,
            arguments = listOf(
                navArgument(VodArgs.PLAYLIST_ID) { type = NavType.LongType },
                navArgument(VodArgs.KIND) { type = NavType.StringType },
                navArgument(VodArgs.ITEM_ID) { type = NavType.LongType },
            ),
        ) { VodDetailScreen(onBack = { nav.popBackStack() }, onPlay = { key -> nav.navigate(Routes.vodPlayer(key)) }) }
        playerRoutes(nav)
        composable(Routes.SETTINGS) { SettingsScreen(onManagePlaylists = { nav.navigate(Routes.MANAGE) }, bottomPadding = if (tv) 0.dp else 88.dp) }
    }
}

private fun NavGraphBuilder.playerRoutes(nav: NavHostController) {
    composable(Routes.PLAYER, arguments = listOf(navArgument(PlayerViewModel.ARG_CHANNEL_ID) { type = NavType.LongType })) {
        PlayerScreen(onBack = { nav.popBackStack() }, castButton = { CastButton() })
    }
    composable(Routes.VOD_PLAYER, arguments = listOf(navArgument(PlayerArgs.VOD_KEY) { type = NavType.StringType })) {
        PlayerScreen(onBack = { nav.popBackStack() }, castButton = { CastButton() })
    }
    composable(
        Routes.CATCHUP_PLAYER,
        arguments = listOf(
            navArgument(PlayerViewModel.ARG_CHANNEL_ID) { type = NavType.LongType },
            navArgument(PlayerArgs.CATCHUP_START) { type = NavType.LongType },
            navArgument(PlayerArgs.CATCHUP_END) { type = NavType.LongType },
        ),
    ) { PlayerScreen(onBack = { nav.popBackStack() }, castButton = { CastButton() }) }
}

private fun NavHostController.switchTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun FloatingTabs(nav: NavHostController, current: String?) {
    val p = SdTheme.palette
    Row(
        Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(horizontal = 28.dp, vertical = 6.dp).fillMaxWidth()
            .background(p.card.copy(alpha = 0.97f), RoundedCornerShape(24.dp)).border(1.dp, p.stroke, RoundedCornerShape(24.dp))
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        PHONE_TABS.forEach { t ->
            val selected = current == t.route
            val label = stringResource(t.label)
            FocusSurface(
                onClick = { nav.switchTab(t.route) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(18.dp),
                container = androidx.compose.ui.graphics.Color.Transparent, contentDescription = label,
            ) {
                Column(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(t.icon, null, tint = if (selected) p.accent else p.textTertiary, modifier = Modifier.size(22.dp))
                    Text(label, color = if (selected) p.accent else p.textTertiary, fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium, modifier = Modifier.padding(top = 2.dp))
                }
            }
        }
    }
}

@Composable
private fun TvRail(nav: NavHostController, current: String?) {
    val p = SdTheme.palette
    Column(
        Modifier.fillMaxHeight().width(80.dp).padding(vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(40.dp).background(p.accent, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.rail_logo), color = p.onAccent, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        }
        Column(Modifier.weight(1f).padding(top = 28.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            TV_TABS.forEach { t -> RailItem(t, current == t.route) { nav.switchTab(t.route) } }
        }
        RailItem(TV_SETTINGS, current == TV_SETTINGS.route) { nav.switchTab(TV_SETTINGS.route) }
    }
}

@Composable
private fun RailItem(t: Tab, selected: Boolean, onClick: () -> Unit) {
    val p = SdTheme.palette
    val label = stringResource(t.label)
    FocusSurface(
        onClick = onClick, shape = RoundedCornerShape(14.dp), container = if (selected) p.cardHigh else androidx.compose.ui.graphics.Color.Transparent,
        focusFill = true, focusScale = 1.08f, contentDescription = label,
    ) { focused ->
        Icon(
            t.icon, null, modifier = Modifier.padding(11.dp).size(24.dp),
            tint = when {
                focused -> p.onFocusFill
                selected -> p.accent
                else -> p.textSecondary
            },
        )
    }
}
