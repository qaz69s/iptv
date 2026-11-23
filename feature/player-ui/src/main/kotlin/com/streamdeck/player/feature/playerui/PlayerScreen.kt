@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.streamdeck.player.feature.playerui

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioManager
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.streamdeck.player.core.designsystem.findActivity
import com.streamdeck.player.core.designsystem.isTv
import com.streamdeck.player.core.player.PlaybackService
import com.streamdeck.player.core.player.PlaybackState
import kotlinx.coroutines.delay

private const val OVERLAY_TIMEOUT_MS = 4_000L
private const val TV_OVERLAY_TIMEOUT_MS = 5_000L
private const val SEEK_STEP_MS = 10_000L
private const val TIMESHIFT_STEP_MS = 60_000L

/**
 * Full-screen player. Phone: gestures + overlay buttons. TV: remote keys (spec §4.3).
 * [castButton] and [overlayExtras] are slots filled by the app module so this feature does not depend on other features.
 */
@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    castButton: @Composable () -> Unit = {},
    vm: PlayerViewModel = hiltViewModel(),
) {
    val ctx = LocalContext.current
    val activity = remember(ctx) { ctx.findActivity() }
    val tv = remember(ctx) { ctx.isTv() }
    val state by vm.controller.state.collectAsStateWithLifecycle()
    val player by vm.controller.playerFlow.collectAsStateWithLifecycle()
    val channel by vm.channel.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val aspectOverride by vm.aspect.collectAsStateWithLifecycle()
    val inPip by vm.pip.inPip.collectAsStateWithLifecycle()
    val numberBuffer by vm.numberInput.collectAsStateWithLifecycle()
    val sleepLeft by vm.sleep.remainingMs.collectAsStateWithLifecycle()
    val favorite by vm.isFavorite.collectAsStateWithLifecycle()
    val program by vm.program.collectAsStateWithLifecycle()
    val catchup by vm.catchup.collectAsStateWithLifecycle()
    val vodTitle by vm.vodTitle.collectAsStateWithLifecycle()
    val groupItems by vm.groupItems.collectAsStateWithLifecycle()
    val schedule by vm.schedule.collectAsStateWithLifecycle()
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // VOD keeps the full-screen layout; live channels on a portrait phone get the detail layout of design 6.
    val fullscreen = tv || inPip || landscape || vodTitle != null
    // The video sits under the status bar, so its icons stay light while the player is open (even in light mode).
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(activity) {
        val c = activity?.window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        val prev = c?.isAppearanceLightStatusBars
        c?.isAppearanceLightStatusBars = false
        onDispose { if (prev != null) c.isAppearanceLightStatusBars = prev }
    }

    var overlayVisible by remember { mutableStateOf(true) }
    var overlayTick by remember { mutableIntStateOf(0) }
    var panel by remember { mutableStateOf<Set<PanelSection>?>(null) }
    var gestureHint by remember { mutableStateOf<String?>(null) }
    var forcedLandscape by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val showOverlay: () -> Unit = { overlayVisible = true; overlayTick++ }
    val openPanel: (Set<PanelSection>) -> Unit = { panel = it }

    KeepScreenOn(activity, state is PlaybackState.Playing)
    Immersive(activity, fullscreen)
    OrientationReset(activity)
    MediaSessionBinding(ctx)
    RequestNotificationPermission(ctx)
    PlaybackLifecycle(vm, settings.backgroundPlayback)

    DisposableEffect(Unit) {
        vm.pip.playerScreenVisible.value = true
        onDispose { vm.pip.playerScreenVisible.value = false }
    }
    LaunchedEffect(overlayVisible, overlayTick, panel) {
        if (overlayVisible && panel == null) {
            delay(if (tv) TV_OVERLAY_TIMEOUT_MS else OVERLAY_TIMEOUT_MS)
            overlayVisible = false
        }
    }
    LaunchedEffect(state) { if (state is PlaybackState.Ended) vm.onEnded() }
    LaunchedEffect(gestureHint) { if (gestureHint != null) { delay(1_200); gestureHint = null } }
    LaunchedEffect(panel, overlayVisible) { if (panel == null && !(tv && overlayVisible)) runCatching { focus.requestFocus() } }
    BackHandler {
        when {
            numberBuffer.isNotEmpty() -> vm.commitNumber()
            else -> onBack()
        }
    }
    // Registered later → wins while enabled: BACK first closes the TV overlay / leaves forced full screen.
    BackHandler(enabled = tv && overlayVisible && numberBuffer.isEmpty()) { overlayVisible = false }
    BackHandler(enabled = !tv && forcedLandscape && landscape) {
        forcedLandscape = false
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    val keyHandler = rememberUpdatedState { e: KeyEvent ->
        if (tv && overlayVisible) {
            // Overlay open: let D-pad move focus between the channel list and the buttons; keys keep it alive.
            if (e.type == KeyEventType.KeyDown) showOverlay()
            handleOverlayKey(e, vm) { openPanel(PanelSection.entries.toSet()) }
        } else {
            handleKey(e, vm, ctx, player, showOverlay, toggleOverlay = { if (overlayVisible) overlayVisible = false else showOverlay() }) {
                openPanel(PanelSection.entries.toSet())
            }
        }
    }
    val gestures: Modifier = if (tv || inPip) {
        Modifier
    } else {
        Modifier.playerGestures(
            activity = activity,
            controller = vm.controller,
            onTap = { if (overlayVisible) overlayVisible = false else showOverlay() },
            onHint = { gestureHint = it },
            hintFormatter = HintFormatter(ctx),
            onLiveSeek = if (vm.supportsCatchup) vm::timeshift else null,
        )
    }
    val toggleFullscreen: () -> Unit = {
        forcedLandscape = !landscape
        activity?.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    if (!fullscreen) {
        PhonePlayerPortrait(
            vm = vm, player = player, state = state, channel = channel, program = program, catchup = catchup,
            favorite = favorite, schedule = schedule, groupItems = groupItems,
            aspect = aspectOverride ?: settings.aspect, subtitleStyle = settings.subtitle,
            controlsVisible = overlayVisible, hint = numberBuffer.ifEmpty { gestureHint },
            gestures = gestures, onBack = onBack, castButton = castButton,
            onOpenPanel = openPanel, onFullscreen = toggleFullscreen,
            modifier = Modifier.focusRequester(focus).focusable().onPreviewKeyEvent { keyHandler.value(it) },
        )
    } else {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusRequester(focus)
                .focusable()
                .onPreviewKeyEvent { keyHandler.value(it) }
                .then(gestures),
        ) {
            VideoSurface(player, aspectOverride ?: settings.aspect, settings.subtitle)
            if (!inPip) {
                if (tv) {
                    TvPlayerOverlay(
                        visible = overlayVisible,
                        channel = channel,
                        titleOverride = vodTitle,
                        groupItems = groupItems,
                        program = if (catchup == null) program else null,
                        player = player,
                        isFavorite = favorite,
                        catchup = catchup,
                        sleepLeftMs = sleepLeft,
                        canStartOver = vm.canStartOver && catchup == null,
                        onPlayChannel = vm::playChannel,
                        onTogglePause = vm.controller::togglePause,
                        onStartOver = vm::startOver,
                        onGoLive = vm::goLive,
                        onOpenPanel = openPanel,
                        onToggleFavorite = if (vodTitle == null) vm::toggleFavorite else null,
                    )
                } else {
                    PlayerOverlay(
                        visible = overlayVisible,
                        tv = false,
                        channel = channel,
                        player = player,
                        sleepLeftMs = sleepLeft,
                        onBack = { if (forcedLandscape) toggleFullscreen() else onBack() },
                        onOpenSettings = { openPanel(PanelSection.entries.toSet()) },
                        onInteraction = showOverlay,
                        castButton = castButton,
                        isFavorite = favorite,
                        program = if (catchup == null) program else null,
                        catchup = catchup,
                        titleOverride = vodTitle,
                        onGoLive = vm::goLive,
                        onToggleFavorite = if (vodTitle == null) vm::toggleFavorite else null,
                    )
                }
                StatusLayer(state, onRetry = vm.controller::retry)
                CenterHint(numberBuffer.ifEmpty { gestureHint })
            }
        }
    }

    panel?.let { sections ->
        PlayerSettingsDialog(
            player = player,
            aspect = aspectOverride ?: settings.aspect,
            subtitleStyle = settings.subtitle,
            sleepLeftMs = sleepLeft,
            onAspect = vm::setAspect,
            onSubtitleStyle = vm::setSubtitleStyle,
            onAddSubtitle = vm::addSubtitle,
            onSleep = { minutes -> if (minutes == null) vm.sleep.cancel() else vm.sleep.start(minutes) },
            onDismiss = { panel = null },
            sections = sections,
        )
    }
}

/** Keys while the TV overlay is open: digits, menu and media keys; everything else navigates focus. */
private fun handleOverlayKey(e: KeyEvent, vm: PlayerViewModel, openPanel: () -> Unit): Boolean {
    if (e.type != KeyEventType.KeyDown) return false
    DIGIT_KEYS[e.key]?.let { vm.digit(it); return true }
    when (e.key) {
        Key.Menu, Key.Settings -> openPanel()
        Key.MediaPlayPause -> vm.controller.togglePause()
        Key.ChannelUp -> vm.next()
        Key.ChannelDown -> vm.previous()
        else -> return false
    }
    return true
}

private val DIGIT_KEYS = mapOf(
    Key.Zero to 0, Key.One to 1, Key.Two to 2, Key.Three to 3, Key.Four to 4,
    Key.Five to 5, Key.Six to 6, Key.Seven to 7, Key.Eight to 8, Key.Nine to 9,
    Key.NumPad0 to 0, Key.NumPad1 to 1, Key.NumPad2 to 2, Key.NumPad3 to 3, Key.NumPad4 to 4,
    Key.NumPad5 to 5, Key.NumPad6 to 6, Key.NumPad7 to 7, Key.NumPad8 to 8, Key.NumPad9 to 9,
)

@Suppress("LongParameterList")
private fun handleKey(
    e: KeyEvent,
    vm: PlayerViewModel,
    ctx: Context,
    player: Player?,
    showOverlay: () -> Unit,
    toggleOverlay: () -> Unit,
    openPanel: () -> Unit,
): Boolean {
    if (e.type != KeyEventType.KeyDown) return false
    val seekable = player != null && player.isCurrentMediaItemSeekable && !player.isCurrentMediaItemLive
    DIGIT_KEYS[e.key]?.let { vm.digit(it); return true }
    when (e.key) {
        Key.DirectionUp, Key.ChannelDown -> { vm.previous(); showOverlay() }
        Key.DirectionDown, Key.ChannelUp -> { vm.next(); showOverlay() }
        Key.DirectionLeft -> if (seekable) { vm.controller.seekBy(-SEEK_STEP_MS); showOverlay() } else adjustVolume(ctx, AudioManager.ADJUST_LOWER)
        Key.DirectionRight -> if (seekable) { vm.controller.seekBy(SEEK_STEP_MS); showOverlay() } else adjustVolume(ctx, AudioManager.ADJUST_RAISE)
        Key.MediaRewind -> if (seekable) vm.controller.seekBy(-SEEK_STEP_MS) else { vm.timeshift(-TIMESHIFT_STEP_MS); showOverlay() }
        Key.MediaFastForward -> if (seekable) vm.controller.seekBy(SEEK_STEP_MS) else vm.timeshift(TIMESHIFT_STEP_MS)
        Key.MediaStop, Key.MediaNext -> if (vm.catchup.value != null) vm.goLive() else return false
        Key.DirectionCenter, Key.Enter, Key.NumPadEnter ->
            if (vm.numberInput.value.isNotEmpty()) vm.commitNumber() else toggleOverlay()
        Key.MediaPlayPause, Key.Spacebar -> { vm.controller.togglePause(); showOverlay() }
        Key.MediaPlay -> vm.controller.resume()
        Key.MediaPause -> vm.controller.pause()
        Key.Menu, Key.Settings, Key.Info -> openPanel()
        else -> return false
    }
    return true
}

private fun adjustVolume(ctx: Context, direction: Int) {
    ctx.getSystemService(AudioManager::class.java)?.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
}

/** FLAG_KEEP_SCREEN_ON only while actually playing; always cleared on exit (spec §8.7). */
@Composable
private fun KeepScreenOn(activity: Activity?, playing: Boolean) {
    DisposableEffect(activity, playing) {
        val w = activity?.window
        if (playing) w?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else w?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { w?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
}

/** Hides system bars while the player is full screen; restores them in portrait and on exit. */
@Composable
private fun Immersive(activity: Activity?, fullscreen: Boolean) {
    DisposableEffect(activity, fullscreen) {
        val window = activity?.window
        val ctl = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        if (fullscreen) {
            ctl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctl?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            ctl?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { ctl?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}

/** The full-screen button may force landscape; leaving the player always hands orientation back to the system. */
@Composable
private fun OrientationReset(activity: Activity?) {
    DisposableEffect(activity) {
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
}

/** Connecting a MediaController starts [PlaybackService], which posts the media notification while playing. */
@Composable
private fun MediaSessionBinding(ctx: Context) {
    DisposableEffect(Unit) {
        val token = SessionToken(ctx, ComponentName(ctx, PlaybackService::class.java))
        val future = MediaController.Builder(ctx, token).buildAsync()
        onDispose { MediaController.releaseFuture(future) }
    }
}

@Composable
private fun RequestNotificationPermission(ctx: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/** Pause on background unless background playback is enabled; a dismissed PiP window always pauses. */
@Composable
private fun PlaybackLifecycle(vm: PlayerViewModel, backgroundPlayback: Boolean) {
    val owner = LocalLifecycleOwner.current
    val bg by rememberUpdatedState(backgroundPlayback)
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && (vm.pip.inPip.value || !bg)) vm.controller.pause()
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
}
