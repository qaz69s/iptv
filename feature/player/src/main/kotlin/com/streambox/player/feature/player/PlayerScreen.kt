package com.streambox.player.feature.player

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.ActivityInfo
import android.net.Uri
import android.provider.OpenableColumns
import android.view.KeyEvent as AndroidKeyEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.streambox.player.core.designsystem.component.focusRing
import com.streambox.player.core.designsystem.icon.StreamBoxIcons
import com.streambox.player.core.model.ZapDirection
import com.streambox.player.core.ui.FormFactor
import com.streambox.player.core.ui.LocalFormFactor
import com.streambox.player.core.ui.labelRes
import kotlinx.coroutines.delay

private val SWIPE_THRESHOLD = 72.dp
private const val FEEDBACK_VISIBLE_MS = 700L

/**
 * Full-screen live player. Touch: tap toggles the overlay, swipe up / down zaps; with playback gestures on
 * (phones / tablets) zapping moves to the middle third, the left / right thirds change brightness / volume,
 * horizontal swipes and side double-taps seek and a double-tap in the middle pauses (see [PlayerGestures]).
 * No touch handling in picture-in-picture. TV: D-pad up / down
 * and CHANNEL_UP / CHANNEL_DOWN zap, OK shows the overlay. Playback runs only while the screen is
 * started; leaving it releases the player and restores the system bars and orientation. With background
 * playback on, sending the app to the background keeps the audio playing (see [BackgroundPlaybackEffect]).
 */
@Composable
internal fun PlayerEntry(onBack: () -> Unit, viewModel: PlayerViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()
    val isTv = LocalFormFactor.current == FormFactor.Tv
    val context = LocalContext.current

    val activity = LocalActivity.current
    LifecycleStartEffect(viewModel) {
        viewModel.onStart()
        onStopOrDispose {
            viewModel.onStop(
                appInBackground = activity.isMovingToBackground(),
                inPictureInPicture = activity?.isInPictureInPictureMode == true,
            )
        }
    }
    var videoBounds by remember { mutableStateOf<Rect?>(null) }
    PictureInPictureEffect(
        player = player,
        aspectMode = state.aspectRatio,
        canEnter = state.pictureInPictureEnabled && state.channel != null && state.error == null && !state.panelOpen,
        isPlaying = state.isPlaying,
        videoBounds = videoBounds,
        onAction = viewModel::onPipAction,
        onModeChanged = viewModel::onPictureInPictureModeChanged,
        enterRequests = viewModel.pictureInPictureRequests,
    )
    BackgroundPlaybackEffect(enabled = state.backgroundPlayback, hasPlayer = player != null)
    KeepScreenOn(enabled = state.error == null && (state.isPlaying || state.isBuffering))
    ImmersiveLandscape(forceLandscape = !isTv)

    // External subtitles: SAF picker (no storage permission needed).
    val subtitlePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val loaded = viewModel.loadExternalSubtitle(
                uri = uri.toString(),
                displayName = context.displayName(uri),
                providerMimeType = context.contentResolver.getType(uri),
            )
            if (!loaded) context.toast(R.string.feature_player_subtitles_unsupported)
        }
    }

    PlayerScreen(
        state = state,
        player = player,
        isTv = isTv,
        onBack = onBack,
        onZap = viewModel::zap,
        onRetry = viewModel::retry,
        onToggleOverlay = viewModel::toggleOverlay,
        onShowOverlay = viewModel::showOverlay,
        keys = PlayerKeyActions(
            onDigit = viewModel::onDigit,
            onCommitNumber = viewModel::commitNumberInput,
            onBackspace = viewModel::onNumberBackspace,
            onHorizontal = viewModel::onHorizontalKey,
            onTogglePanel = viewModel::togglePanel,
            onOpenPanel = viewModel::openPanel,
            onMediaKey = viewModel::onMediaKey,
        ),
        onCycleAspect = viewModel::cycleAspectRatio,
        onVideoBounds = { videoBounds = it },
        panelActions = PlayerPanelActions(
            onDismiss = viewModel::closePanel,
            onVideoTrack = viewModel::selectVideoTrack,
            onAudioTrack = viewModel::selectAudioTrack,
            onSubtitleTrack = viewModel::selectSubtitleTrack,
            onLoadSubtitleFile = {
                try {
                    subtitlePicker.launch(SubtitleFiles.PICKER_MIME_TYPES)
                } catch (_: ActivityNotFoundException) {
                    context.toast(R.string.feature_player_no_file_picker)
                }
            },
            onAspectRatio = viewModel::setAspectRatio,
            onSubtitleStyle = viewModel::setSubtitleStyle,
            onPictureInPicture = viewModel::requestPictureInPicture,
            onSleepTimer = viewModel::setSleepTimer,
            onToggleFavorite = viewModel::toggleFavorite,
        ),
        gestureActions = PlayerGestureActions(
            onSeek = viewModel::seekByGesture,
            onDoubleTap = viewModel::onDoubleTap,
        ),
    )
}

/** Remote-control callbacks beyond zapping (P2a). */
internal data class PlayerKeyActions(
    val onDigit: (Int) -> Unit = {},
    /** @return true when a typed number was committed (OK consumed). */
    val onCommitNumber: () -> Boolean = { false },
    /** @return true when a typed digit was removed (key consumed). */
    val onBackspace: () -> Boolean = { false },
    val onHorizontal: (forward: Boolean) -> Unit = {},
    val onTogglePanel: () -> Unit = {},
    val onOpenPanel: () -> Unit = {},
    val onMediaKey: (MediaKeyAction) -> Unit = {},
)

@Suppress("LongMethod", "LongParameterList", "CyclomaticComplexMethod")
@Composable
internal fun PlayerScreen(
    state: PlayerUiState,
    player: Player?,
    isTv: Boolean,
    onBack: () -> Unit,
    onZap: (ZapDirection) -> Unit,
    onRetry: () -> Unit,
    onToggleOverlay: () -> Unit,
    onShowOverlay: () -> Unit,
    modifier: Modifier = Modifier,
    keys: PlayerKeyActions = PlayerKeyActions(),
    onCycleAspect: () -> Unit = {},
    panelActions: PlayerPanelActions? = null,
    onVideoBounds: (Rect) -> Unit = {},
    gestureActions: PlayerGestureActions = PlayerGestureActions(),
) {
    val inPip = state.pictureInPicture
    val rootFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }
    val currentState by rememberUpdatedState(state)
    val zap by rememberUpdatedState(onZap)
    val toggle by rememberUpdatedState(onToggleOverlay)
    val keyActions by rememberUpdatedState(keys)
    val gestures by rememberUpdatedState(gestureActions)
    val currentPlayer by rememberUpdatedState(player)

    // Touch: nothing in picture-in-picture (the system owns the window); full gestures only off TV.
    val touchEnabled = !inPip
    val gesturesOn = !isTv && state.gesturesEnabled && touchEnabled
    val levels = rememberDeviceLevels()
    var feedback by remember { mutableStateOf<GestureFeedback?>(null) }
    LaunchedEffect(feedback) {
        if (feedback?.active == false) {
            delay(FEEDBACK_VISIBLE_MS)
            feedback = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                handleKey(event, currentState, onZap = zap, onShowOverlay = onShowOverlay, keys = keyActions)
            }
            .focusRequester(rootFocus)
            .focusable()
            .then(
                if (touchEnabled) {
                    Modifier
                        .pointerInput(gesturesOn) {
                            detectTapGestures(
                                onTap = { toggle() },
                                // Only with gestures on, so a single tap is not delayed otherwise.
                                onDoubleTap = if (gesturesOn) {
                                    { offset ->
                                        val wasPlaying = currentState.isPlaying
                                        val zone = PlayerGestures.zoneOf(offset.x, size.width.toFloat())
                                        gestures.onDoubleTap(zone)?.let { action ->
                                            feedback = GestureFeedback.DoubleTap(action, nowPlaying = !wasPlaying)
                                        }
                                    }
                                } else {
                                    null
                                },
                            )
                        }
                        .pointerInput(gesturesOn, levels) {
                            val threshold = SWIPE_THRESHOLD.toPx()
                            detectPlayerDrags(
                                PlayerDragHandler(
                                    canSeek = { currentState.isSeekable },
                                    gesturesEnabled = { gesturesOn },
                                    player = { currentPlayer },
                                    levels = levels,
                                    zapThresholdPx = { threshold },
                                    onZap = { zap(it) },
                                    onSeek = { gestures.onSeek(it) },
                                    onFeedback = { feedback = it },
                                ),
                            )
                        }
                } else {
                    Modifier
                },
            ),
    ) {
        if (player != null) {
            VideoSurface(
                player = player,
                aspectRatio = state.aspectRatio,
                modifier = Modifier.onGloballyPositioned { onVideoBounds(it.boundsInWindow()) },
            )
            SubtitleLayer(player = player, style = state.subtitleStyle)
        }

        if (state.isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(if (inPip) 32.dp else 56.dp),
                color = Color.White,
            )
        }

        feedback?.let { current ->
            if (!inPip) GestureFeedbackBadge(feedback = current, modifier = Modifier.align(Alignment.Center))
        }

        AnimatedVisibility(
            visible = state.overlayVisible && !inPip,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize()) {
                TopBar(
                    state = state,
                    isTv = isTv,
                    onBack = onBack,
                    onOpenPanel = keys.onOpenPanel,
                    onCycleAspect = onCycleAspect,
                    modifier = Modifier.align(Alignment.TopStart),
                )
                if (!isTv) {
                    ZapButtons(onZap = onZap, modifier = Modifier.align(Alignment.CenterEnd))
                }
                Text(
                    text = stringResource(
                        when {
                            !isTv && state.gesturesEnabled -> R.string.feature_player_hint_touch_gestures
                            !isTv -> R.string.feature_player_hint_touch
                            state.isSeekable -> R.string.feature_player_hint_tv_vod
                            else -> R.string.feature_player_hint_tv_live
                        },
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .safeDrawingPadding()
                        .padding(16.dp),
                )
            }
        }

        // Picture-in-picture: only the picture, the buffering spinner and a short error notice.
        if (inPip) {
            if (state.error != null) PipErrorBadge(modifier = Modifier.align(Alignment.Center))
            return@Box
        }

        StatusMessages(state = state, modifier = Modifier.align(Alignment.TopCenter))
        state.numberInput?.let { digits ->
            NumberInputBadge(digits = digits, modifier = Modifier.align(Alignment.TopEnd))
        }

        state.error?.let { error ->
            ErrorPanel(
                message = error.message(),
                onRetry = onRetry,
                retryFocus = retryFocus,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }

    // Remote Back while typing a channel number deletes a digit; otherwise Back keeps leaving the player.
    BackHandler(enabled = state.numberInput != null && !state.panelOpen && !inPip) { keyActions.onBackspace() }

    if (state.panelOpen && panelActions != null && !inPip) {
        PlayerPanel(state = state, isTv = isTv, actions = panelActions)
    }

    // TV: the retry button takes focus while an error is shown; otherwise the root keeps it for keys.
    val hasError = state.error != null
    val panelOpen = state.panelOpen
    LaunchedEffect(hasError, panelOpen) {
        if (!panelOpen) runCatching { if (hasError && isTv) retryFocus.requestFocus() else rootFocus.requestFocus() }
    }
}

@Suppress("CyclomaticComplexMethod", "ReturnCount")
private fun handleKey(
    event: KeyEvent,
    state: PlayerUiState,
    onZap: (ZapDirection) -> Unit,
    onShowOverlay: () -> Unit,
    keys: PlayerKeyActions,
): Boolean {
    val code = event.key.nativeKeyCode
    // The panel (sheet / dialog) handles its own keys; Menu closes it there.
    if (state.panelOpen) return false
    // K14: play / pause keys in the foreground. Key up is consumed too, so no half press reaches the session.
    mediaKeyActionOf(code)?.let { action ->
        if (event.type == KeyEventType.KeyDown && event.nativeKeyEvent.repeatCount == 0) keys.onMediaKey(action)
        return true
    }
    if (event.type != KeyEventType.KeyDown) return false
    digitOf(code)?.let { digit ->
        keys.onDigit(digit)
        return true
    }
    return when (code) {
        AndroidKeyEvent.KEYCODE_DEL -> keys.onBackspace()

        AndroidKeyEvent.KEYCODE_MENU, AndroidKeyEvent.KEYCODE_SETTINGS -> {
            keys.onTogglePanel()
            true
        }

        AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_CHANNEL_UP -> {
            onZap(ZapDirection.NEXT)
            true
        }

        AndroidKeyEvent.KEYCODE_DPAD_DOWN, AndroidKeyEvent.KEYCODE_CHANNEL_DOWN -> {
            onZap(ZapDirection.PREVIOUS)
            true
        }

        AndroidKeyEvent.KEYCODE_DPAD_LEFT, AndroidKeyEvent.KEYCODE_MEDIA_REWIND -> {
            if (state.error != null) return false
            keys.onHorizontal(false)
            true
        }

        AndroidKeyEvent.KEYCODE_DPAD_RIGHT, AndroidKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
            if (state.error != null) return false
            keys.onHorizontal(true)
            true
        }

        AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER, AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
        AndroidKeyEvent.KEYCODE_INFO,
        -> when {
            keys.onCommitNumber() -> true

            // Holding OK opens the settings panel (for remotes without a Menu key).
            event.nativeKeyEvent.repeatCount == 1 && state.error == null &&
                code != AndroidKeyEvent.KEYCODE_INFO -> {
                keys.onOpenPanel()
                true
            }

            event.nativeKeyEvent.repeatCount > 0 -> true

            // OK shows channel info; when the overlay is already up, let the focused button handle it.
            state.overlayVisible -> false

            else -> {
                onShowOverlay()
                true
            }
        }

        else -> false
    }
}

private fun digitOf(keyCode: Int): Int? = when (keyCode) {
    in AndroidKeyEvent.KEYCODE_0..AndroidKeyEvent.KEYCODE_9 -> keyCode - AndroidKeyEvent.KEYCODE_0
    in AndroidKeyEvent.KEYCODE_NUMPAD_0..AndroidKeyEvent.KEYCODE_NUMPAD_9 -> keyCode - AndroidKeyEvent.KEYCODE_NUMPAD_0
    else -> null
}

@Suppress("LongParameterList")
@Composable
private fun TopBar(
    state: PlayerUiState,
    isTv: Boolean,
    onBack: () -> Unit,
    onOpenPanel: () -> Unit,
    onCycleAspect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
            .safeDrawingPadding()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!isTv) {
            IconButton(onClick = onBack, modifier = Modifier.focusRing()) {
                Icon(
                    painter = painterResource(StreamBoxIcons.Back),
                    contentDescription = stringResource(R.string.feature_player_back),
                    tint = Color.White,
                )
            }
        } else {
            Spacer(Modifier.width(16.dp))
        }
        val channel = state.channel
        if (channel != null) {
            Text(
                text = channel.displayNumber.toString(),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 12.dp),
            )
            Text(
                text = channel.name,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        // TV uses Menu / long-press OK instead, so D-pad up / down stay free for zapping.
        if (!isTv) {
            IconButton(onClick = onCycleAspect, modifier = Modifier.focusRing()) {
                Icon(
                    painter = painterResource(StreamBoxIcons.AspectRatio),
                    contentDescription = stringResource(
                        R.string.feature_player_aspect_button,
                        stringResource(state.aspectRatio.labelRes()),
                    ),
                    tint = Color.White,
                )
            }
            IconButton(onClick = onOpenPanel, modifier = Modifier.focusRing()) {
                Icon(
                    painter = painterResource(StreamBoxIcons.Settings),
                    contentDescription = stringResource(R.string.feature_player_settings),
                    tint = Color.White,
                )
            }
        }
    }
}

/** Reconnect progress, decoder fallback and "no such channel number" notices. */
@Composable
private fun StatusMessages(state: PlayerUiState, modifier: Modifier = Modifier) {
    val messages = buildList {
        state.reconnect?.let {
            add(stringResource(R.string.feature_player_reconnecting, it.attempt, it.maxAttempts))
        }
        state.numberNotFound?.let { add(stringResource(R.string.feature_player_number_not_found, it)) }
        if (state.softwareDecoderFallback && state.overlayVisible) {
            add(stringResource(R.string.feature_player_software_decoder))
        }
    }
    if (messages.isEmpty()) return
    Column(
        modifier = modifier
            .safeDrawingPadding()
            .padding(top = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        messages.forEach { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

/** Big channel number being typed on the remote, top-right like a set-top box. */
@Composable
private fun NumberInputBadge(digits: String, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.feature_player_number_input, digits)
    Text(
        text = digits,
        style = MaterialTheme.typography.displayMedium,
        color = Color.White,
        modifier = modifier
            .safeDrawingPadding()
            .padding(24.dp)
            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .semantics { contentDescription = description },
    )
}

@Composable
private fun ZapButtons(onZap: (ZapDirection) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .safeDrawingPadding()
            .padding(16.dp)
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(28.dp)),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = { onZap(ZapDirection.NEXT) }, modifier = Modifier.focusRing()) {
            Icon(
                painter = painterResource(StreamBoxIcons.ArrowUp),
                contentDescription = stringResource(R.string.feature_player_channel_up),
                tint = Color.White,
            )
        }
        IconButton(onClick = { onZap(ZapDirection.PREVIOUS) }, modifier = Modifier.focusRing()) {
            Icon(
                painter = painterResource(StreamBoxIcons.ArrowDown),
                contentDescription = stringResource(R.string.feature_player_channel_down),
                tint = Color.White,
            )
        }
    }
}

/** Compact error notice for the small picture-in-picture window. */
@Composable
private fun PipErrorBadge(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(StreamBoxIcons.Error),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = stringResource(R.string.feature_player_pip_error),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun ErrorPanel(
    message: String,
    onRetry: () -> Unit,
    retryFocus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(24.dp)
            .widthIn(max = 480.dp)
            .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(16.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(painterResource(StreamBoxIcons.Error), contentDescription = null, tint = MaterialTheme.colorScheme.error)
        Text(
            text = stringResource(R.string.feature_player_error_title),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onRetry,
            modifier = Modifier
                .focusRequester(retryFocus)
                .focusRing(),
        ) { Text(stringResource(R.string.feature_player_retry)) }
    }
}

/** Keeps the display on while video plays or buffers; cleared when playback stops or the screen leaves. */
@Composable
private fun KeepScreenOn(enabled: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        view.keepScreenOn = enabled
        onDispose { view.keepScreenOn = false }
    }
}

/** Hides the system bars (swipe to peek) and, on touch devices, turns the screen sideways; restores both on leave. */
@Composable
private fun ImmersiveLandscape(forceLandscape: Boolean) {
    val activity: Activity = LocalActivity.current ?: return
    DisposableEffect(activity, forceLandscape) {
        val window = activity.window
        val insets = WindowCompat.getInsetsController(window, window.decorView)
        val previousBehavior = insets.systemBarsBehavior
        val previousOrientation = activity.requestedOrientation
        insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insets.hide(WindowInsetsCompat.Type.systemBars())
        if (forceLandscape) activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            insets.show(WindowInsetsCompat.Type.systemBars())
            insets.systemBarsBehavior = previousBehavior
            if (forceLandscape) activity.requestedOrientation = previousOrientation
        }
    }
}

private fun Context.displayName(uri: Uri): String? = runCatching {
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}.getOrNull()

private fun Context.toast(message: Int) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
