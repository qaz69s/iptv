package com.streambox.player.feature.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import androidx.media3.ui.compose.ContentFrame
import com.streambox.player.core.designsystem.component.focusRing
import com.streambox.player.core.designsystem.icon.StreamBoxIcons
import com.streambox.player.core.model.ZapDirection
import com.streambox.player.core.ui.FormFactor
import com.streambox.player.core.ui.LocalFormFactor

private val SWIPE_THRESHOLD = 72.dp

/**
 * Full-screen live player. Touch: tap toggles the overlay, swipe up / down zaps. TV: D-pad up / down
 * and CHANNEL_UP / CHANNEL_DOWN zap, OK shows the overlay. Playback runs only while the screen is
 * started; leaving it releases the player and restores the system bars and orientation.
 */
@Composable
internal fun PlayerEntry(onBack: () -> Unit, viewModel: PlayerViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val player by viewModel.player.collectAsStateWithLifecycle()
    val isTv = LocalFormFactor.current == FormFactor.Tv

    LifecycleStartEffect(viewModel) {
        viewModel.onStart()
        onStopOrDispose { viewModel.onStop() }
    }
    KeepScreenOn(enabled = state.error == null && (state.isPlaying || state.isBuffering))
    ImmersiveLandscape(forceLandscape = !isTv)

    PlayerScreen(
        state = state,
        player = player,
        isTv = isTv,
        onBack = onBack,
        onZap = viewModel::zap,
        onRetry = viewModel::retry,
        onToggleOverlay = viewModel::toggleOverlay,
        onShowOverlay = viewModel::showOverlay,
    )
}

@Suppress("LongMethod", "LongParameterList")
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
) {
    val rootFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }
    val currentState by rememberUpdatedState(state)
    val zap by rememberUpdatedState(onZap)
    val toggle by rememberUpdatedState(onToggleOverlay)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onPreviewKeyEvent { event ->
                handleKey(event, currentState.overlayVisible, onZap = zap, onShowOverlay = onShowOverlay)
            }
            .focusRequester(rootFocus)
            .focusable()
            .pointerInput(Unit) { detectTapGestures(onTap = { toggle() }) }
            .pointerInput(Unit) {
                val threshold = SWIPE_THRESHOLD.toPx()
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        when {
                            total <= -threshold -> zap(ZapDirection.NEXT)
                            total >= threshold -> zap(ZapDirection.PREVIOUS)
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        total += dragAmount
                    },
                )
            },
    ) {
        if (player != null) {
            ContentFrame(
                player = player,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit,
            )
        }

        if (state.isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(56.dp),
                color = Color.White,
            )
        }

        AnimatedVisibility(
            visible = state.overlayVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize()) {
                TopBar(state = state, showBack = !isTv, onBack = onBack, modifier = Modifier.align(Alignment.TopStart))
                if (!isTv) {
                    ZapButtons(onZap = onZap, modifier = Modifier.align(Alignment.CenterEnd))
                }
                Text(
                    text = stringResource(
                        if (isTv) R.string.feature_player_hint_tv else R.string.feature_player_hint_touch,
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

        state.error?.let { error ->
            ErrorPanel(
                message = error.message(),
                onRetry = onRetry,
                retryFocus = retryFocus,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }

    // TV: the retry button takes focus while an error is shown; otherwise the root keeps it for keys.
    val hasError = state.error != null
    LaunchedEffect(hasError) {
        runCatching { if (hasError && isTv) retryFocus.requestFocus() else rootFocus.requestFocus() }
    }
}

private fun handleKey(
    event: KeyEvent,
    overlayVisible: Boolean,
    onZap: (ZapDirection) -> Unit,
    onShowOverlay: () -> Unit,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    return when (event.key.nativeKeyCode) {
        AndroidKeyEvent.KEYCODE_DPAD_UP, AndroidKeyEvent.KEYCODE_CHANNEL_UP -> {
            onZap(ZapDirection.NEXT)
            true
        }

        AndroidKeyEvent.KEYCODE_DPAD_DOWN, AndroidKeyEvent.KEYCODE_CHANNEL_DOWN -> {
            onZap(ZapDirection.PREVIOUS)
            true
        }

        AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER, AndroidKeyEvent.KEYCODE_NUMPAD_ENTER,
        AndroidKeyEvent.KEYCODE_INFO,
        -> {
            // OK shows channel info; when the overlay is already up, let the focused button handle it.
            if (overlayVisible) {
                false
            } else {
                onShowOverlay()
                true
            }
        }

        else -> false
    }
}

@Composable
private fun TopBar(state: PlayerUiState, showBack: Boolean, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
            .safeDrawingPadding()
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) {
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
            )
        }
    }
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
