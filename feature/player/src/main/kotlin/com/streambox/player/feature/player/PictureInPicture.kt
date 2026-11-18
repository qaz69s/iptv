package com.streambox.player.feature.player

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import com.streambox.player.core.designsystem.icon.StreamBoxIcons
import com.streambox.player.core.model.AspectRatioMode
import com.streambox.player.core.ui.LocalFormFactor
import com.streambox.player.core.ui.supportsPictureInPicture
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.Flow

/** Platform limits of a picture-in-picture window: between 1:2.39 and 2.39:1. */
private const val PIP_RATIO_LIMIT_NUM = 239
private const val PIP_RATIO_LIMIT_DEN = 100
private const val PIP_RATIO_MAX = PIP_RATIO_LIMIT_NUM.toDouble() / PIP_RATIO_LIMIT_DEN
private val RATIO_16_9 = 16 to 9
private val RATIO_4_3 = 4 to 3

/**
 * Window aspect ratio (width to height) for picture-in-picture. 16:9 and 4:3 modes force that frame;
 * every other mode follows the video (width × pixel aspect ratio : height), 16:9 while unknown. Clamped
 * to the platform's 100/239..239/100 range.
 */
internal fun pipAspectRatioOf(
    aspectMode: AspectRatioMode,
    videoWidth: Int,
    videoHeight: Int,
    pixelRatio: Float,
): Pair<Int, Int> {
    val raw = when (aspectMode) {
        AspectRatioMode.Ratio16x9 -> RATIO_16_9
        AspectRatioMode.Ratio4x3 -> RATIO_4_3
        else -> videoRatio(videoWidth, videoHeight, pixelRatio) ?: RATIO_16_9
    }
    val ratio = raw.first.toDouble() / raw.second
    return when {
        ratio > PIP_RATIO_MAX -> PIP_RATIO_LIMIT_NUM to PIP_RATIO_LIMIT_DEN
        ratio < 1 / PIP_RATIO_MAX -> PIP_RATIO_LIMIT_DEN to PIP_RATIO_LIMIT_NUM
        else -> raw
    }
}

private fun videoRatio(width: Int, height: Int, pixelRatio: Float): Pair<Int, Int>? {
    if (width <= 0 || height <= 0) return null
    val par = if (pixelRatio.isFinite() && pixelRatio > 0f) pixelRatio else 1f
    val displayWidth = (width * par).roundToInt()
    return if (displayWidth > 0) displayWidth to height else null
}

/** [pipAspectRatioOf] as the platform type the PiP params take. */
internal fun pipAspectRatio(aspectMode: AspectRatioMode, videoWidth: Int, videoHeight: Int, pixelRatio: Float) =
    pipAspectRatioOf(aspectMode, videoWidth, videoHeight, pixelRatio).let { (w, h) -> Rational(w, h) }

/** Integer window rectangle (left, top, right, bottom), JVM-testable stand-in for [android.graphics.Rect]. */
internal data class PipRect(val left: Int, val top: Int, val right: Int, val bottom: Int)

/**
 * The part of [bounds] (the video area in window coordinates) the picture actually covers: the largest
 * rectangle of [ratioWidth]:[ratioHeight] centred inside it. Null when either side is empty.
 */
internal fun pipSourceRect(bounds: PipRect, ratioWidth: Int, ratioHeight: Int): PipRect? {
    val width = bounds.right - bounds.left
    val height = bounds.bottom - bounds.top
    val empty = width <= 0 || height <= 0
    if (empty || ratioWidth <= 0 || ratioHeight <= 0) return null
    val target = ratioWidth.toDouble() / ratioHeight
    return if (width.toDouble() / height > target) {
        val fitted = (height * target).roundToInt()
        val left = bounds.left + (width - fitted) / 2
        PipRect(left, bounds.top, left + fitted, bounds.bottom)
    } else {
        val fitted = (width / target).roundToInt()
        val top = bounds.top + (height - fitted) / 2
        PipRect(bounds.left, top, bounds.right, top + fitted)
    }
}

/**
 * Picture-in-picture for the player on phones and tablets (never TV, see [supportsPictureInPicture]).
 * Keeps the activity's PiP params current (aspect ratio, source rect for the enter animation, previous /
 * play-pause / next buttons) and enters PiP when the user leaves with Home while [canEnter]: automatically
 * on Android 12+, from the user-leave hint on 8.0–11. Reports mode changes to [onModeChanged] with
 * stopped = the activity is no longer started (the window was closed rather than expanded). Each
 * emission of [enterRequests] (the panel's PiP action) enters PiP right away with the current params.
 */
@Suppress("LongParameterList")
@Composable
internal fun PictureInPictureEffect(
    player: Player?,
    aspectMode: AspectRatioMode,
    canEnter: Boolean,
    isPlaying: Boolean,
    videoBounds: Rect?,
    onAction: (PipAction) -> Unit,
    onModeChanged: (inPip: Boolean, stopped: Boolean) -> Unit,
    enterRequests: Flow<Unit>? = null,
) {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    val formFactor = LocalFormFactor.current
    val supported = remember(activity, formFactor) { activity.supportsPictureInPicture(formFactor) }
    if (supported && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        PictureInPictureController(
            activity = activity,
            player = player,
            aspectMode = aspectMode,
            canEnter = canEnter,
            isPlaying = isPlaying,
            videoBounds = videoBounds,
            onAction = onAction,
            onModeChanged = onModeChanged,
            enterRequests = enterRequests,
        )
    }
}

@Suppress("LongParameterList", "LongMethod")
@RequiresApi(Build.VERSION_CODES.O)
@Composable
private fun PictureInPictureController(
    activity: ComponentActivity,
    player: Player?,
    aspectMode: AspectRatioMode,
    canEnter: Boolean,
    isPlaying: Boolean,
    videoBounds: Rect?,
    onAction: (PipAction) -> Unit,
    onModeChanged: (inPip: Boolean, stopped: Boolean) -> Unit,
    enterRequests: Flow<Unit>?,
) {
    val currentOnAction by rememberUpdatedState(onAction)
    val currentOnModeChanged by rememberUpdatedState(onModeChanged)

    var videoSize by remember(player) { mutableStateOf(player?.videoSize ?: VideoSize.UNKNOWN) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(size: VideoSize) {
                videoSize = size
            }
        }
        player?.addListener(listener)
        onDispose { player?.removeListener(listener) }
    }

    val params = remember(activity, aspectMode, videoSize, videoBounds, isPlaying, canEnter) {
        val (ratioWidth, ratioHeight) =
            pipAspectRatioOf(aspectMode, videoSize.width, videoSize.height, videoSize.pixelWidthHeightRatio)
        val sourceRect = videoBounds
            ?.let { PipRect(it.left.roundToInt(), it.top.roundToInt(), it.right.roundToInt(), it.bottom.roundToInt()) }
            ?.let { pipSourceRect(it, ratioWidth, ratioHeight) }
            ?.let { android.graphics.Rect(it.left, it.top, it.right, it.bottom) }
        buildPipParams(activity, Rational(ratioWidth, ratioHeight), sourceRect, isPlaying, autoEnter = canEnter)
    }
    val currentParams by rememberUpdatedState(params)
    val currentCanEnter by rememberUpdatedState(canEnter)

    LaunchedEffect(params) {
        runCatching { activity.setPictureInPictureParams(params) }
    }

    LaunchedEffect(activity, enterRequests) {
        enterRequests?.collect { enterPictureInPicture(activity, currentParams) }
    }

    // Window buttons: an app-private broadcast back into this screen.
    DisposableEffect(activity) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val action = intent.getStringExtra(EXTRA_PIP_ACTION)
                    ?.let { name -> PipAction.entries.firstOrNull { it.name == name } }
                    ?: return
                currentOnAction(action)
            }
        }
        val registered = runCatching {
            ContextCompat.registerReceiver(
                activity,
                receiver,
                IntentFilter(pipControlAction(activity)),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }.isSuccess
        onDispose { if (registered) runCatching { activity.unregisterReceiver(receiver) } }
    }

    DisposableEffect(activity) {
        val modeListener = Consumer<PictureInPictureModeChangedInfo> { info ->
            val stopped = !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
            currentOnModeChanged(info.isInPictureInPictureMode, stopped)
        }
        activity.addOnPictureInPictureModeChangedListener(modeListener)
        // Android 8.0–11 have no auto-enter: shrink into PiP when the user leaves with Home.
        val leaveHint = Runnable {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && currentCanEnter) {
                enterPictureInPicture(activity, currentParams)
            }
        }
        activity.addOnUserLeaveHintListener(leaveHint)
        onDispose {
            activity.removeOnPictureInPictureModeChangedListener(modeListener)
            activity.removeOnUserLeaveHintListener(leaveHint)
            // Leaving the player: Home must no longer shrink the app into a window.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching {
                    activity.setPictureInPictureParams(
                        PictureInPictureParams.Builder().setAutoEnterEnabled(false).build(),
                    )
                }
            }
        }
    }
}

/**
 * Shrinks [activity] into a picture-in-picture window with [params]. False when the system refused
 * (e.g. PiP turned off for the app in system settings) instead of throwing.
 */
@RequiresApi(Build.VERSION_CODES.O)
internal fun enterPictureInPicture(activity: Activity, params: PictureInPictureParams): Boolean =
    runCatching { activity.enterPictureInPictureMode(params) }.getOrDefault(false)

@RequiresApi(Build.VERSION_CODES.O)
private fun buildPipParams(
    context: Context,
    aspectRatio: Rational,
    sourceRect: android.graphics.Rect?,
    isPlaying: Boolean,
    autoEnter: Boolean,
): PictureInPictureParams {
    val actions = listOf(
        remoteAction(context, PipAction.PREVIOUS, StreamBoxIcons.SkipPrevious, R.string.feature_player_channel_down),
        if (isPlaying) {
            remoteAction(context, PipAction.PLAY_PAUSE, StreamBoxIcons.Pause, R.string.feature_player_pip_pause)
        } else {
            remoteAction(context, PipAction.PLAY_PAUSE, StreamBoxIcons.Play, R.string.feature_player_pip_play)
        },
        remoteAction(context, PipAction.NEXT, StreamBoxIcons.SkipNext, R.string.feature_player_channel_up),
    )
    val builder = PictureInPictureParams.Builder()
        .setAspectRatio(aspectRatio)
        .setActions(actions)
    if (sourceRect != null) builder.setSourceRectHint(sourceRect)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        builder.setAutoEnterEnabled(autoEnter)
        // Video: no cross-fade while resizing the window.
        builder.setSeamlessResizeEnabled(false)
    }
    return builder.build()
}

@RequiresApi(Build.VERSION_CODES.O)
private fun remoteAction(
    context: Context,
    action: PipAction,
    @DrawableRes icon: Int,
    @StringRes title: Int,
): RemoteAction {
    val intent = Intent(pipControlAction(context))
        .setPackage(context.packageName)
        .putExtra(EXTRA_PIP_ACTION, action.name)
    val pendingIntent = PendingIntent.getBroadcast(
        context,
        action.ordinal,
        intent,
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
    val label = context.getString(title)
    return RemoteAction(Icon.createWithResource(context, icon), label, label, pendingIntent)
}

private fun pipControlAction(context: Context) = "${context.packageName}.action.PIP_CONTROL"

private const val EXTRA_PIP_ACTION = "pip_action"
