package com.streambox.player.feature.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.streambox.player.core.model.ZapDirection

/** Touch-gesture callbacks of the player screen (phones / tablets). */
internal data class PlayerGestureActions(
    /** End of a seek swipe; @return true when it seeked. */
    val onSeek: (offsetMs: Long) -> Boolean = { false },
    /** @return what the double-tap did, or null when ignored. */
    val onDoubleTap: (TouchZone) -> DoubleTapAction? = { null },
)

/** Short-lived feedback shown in the middle of the player while / after a gesture. */
internal sealed interface GestureFeedback {
    /** False once the finger lifted: the badge fades out shortly after. */
    val active: Boolean

    data class Seek(val targetMs: Long, val offsetMs: Long, val durationMs: Long, override val active: Boolean) :
        GestureFeedback

    data class Brightness(val percent: Int, override val active: Boolean) : GestureFeedback

    data class Volume(val percent: Int, override val active: Boolean) : GestureFeedback

    data class DoubleTap(val action: DoubleTapAction, val nowPlaying: Boolean) : GestureFeedback {
        override val active: Boolean get() = false
    }
}

/**
 * Window brightness (this screen only: restored when it leaves) and media volume, as levels 0..1.
 * Null activity (previews) makes every call a no-op.
 */
internal class DeviceLevels(private val activity: Activity?, context: Context) {
    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val resolver = context.contentResolver
    private val originalBrightness: Float? = activity?.window?.attributes?.screenBrightness

    fun brightness(): Float {
        val current = activity?.window?.attributes?.screenBrightness ?: DEFAULT_LEVEL
        // Negative = following the system: start from the system setting (0..255 on most devices).
        return if (current >= 0f) current else systemBrightness()
    }

    private fun systemBrightness(): Float {
        val system = runCatching { Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS) }.getOrNull()
            ?: return DEFAULT_LEVEL
        return (system / SYSTEM_BRIGHTNESS_MAX.toFloat()).coerceIn(0f, 1f)
    }

    fun setBrightness(level: Float) {
        val window = activity?.window ?: return
        window.attributes = window.attributes.apply { screenBrightness = level.coerceIn(MIN_BRIGHTNESS, 1f) }
    }

    /** Back to what the window had before this screen (normally "follow the system"). */
    fun restoreBrightness() {
        val window = activity?.window ?: return
        val original = originalBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        if (window.attributes.screenBrightness != original) {
            window.attributes = window.attributes.apply { screenBrightness = original }
        }
    }

    fun volume(): Float {
        val audio = audio ?: return 0f
        return PlayerGestures.volumeLevel(audio.getStreamVolume(STREAM), audio.getStreamMaxVolume(STREAM), minVolume())
    }

    /** @return the level actually applied (volume has a few discrete steps). */
    fun setVolume(level: Float): Float {
        val audio = audio ?: return level
        val max = audio.getStreamMaxVolume(STREAM)
        val index = PlayerGestures.volumeIndex(level, max, minVolume())
        // Fixed-volume devices or Do Not Disturb policies may refuse; keep the gesture going.
        runCatching { audio.setStreamVolume(STREAM, index, 0) }
        return PlayerGestures.volumeLevel(audio.getStreamVolume(STREAM), max, minVolume())
    }

    private fun minVolume(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) audio?.getStreamMinVolume(STREAM) ?: 0 else 0

    private companion object {
        const val STREAM = AudioManager.STREAM_MUSIC
        const val SYSTEM_BRIGHTNESS_MAX = 255
        const val DEFAULT_LEVEL = 0.5f

        /** 0 can switch the backlight off on some devices. */
        const val MIN_BRIGHTNESS = 0.01f
    }
}

/** [DeviceLevels] for this screen; the brightness override is undone when the screen leaves. */
@Composable
internal fun rememberDeviceLevels(): DeviceLevels {
    val activity = LocalActivity.current
    val context = LocalContext.current
    val levels = remember(activity, context) { DeviceLevels(activity, context) }
    DisposableEffect(levels) { onDispose { levels.restoreBrightness() } }
    return levels
}

/** Callbacks of [detectPlayerDrags]; read through `rememberUpdatedState` by the caller. */
@Suppress("LongParameterList")
internal class PlayerDragHandler(
    val canSeek: () -> Boolean,
    val gesturesEnabled: () -> Boolean,
    val player: () -> Player?,
    val levels: DeviceLevels,
    val zapThresholdPx: () -> Float,
    val onZap: (ZapDirection) -> Unit,
    val onSeek: (Long) -> Boolean,
    val onFeedback: (GestureFeedback?) -> Unit,
)

/**
 * One drag at a time: waits for the touch slop, locks the direction ([PlayerGestures.lockDrag]) and from
 * then on consumes the moves (so the tap detector cancels). Horizontal = seek preview applied on release;
 * vertical = brightness (left third), zap (middle third) or volume (right third).
 */
@Suppress("CyclomaticComplexMethod", "LongMethod")
internal suspend fun PointerInputScope.detectPlayerDrags(handler: PlayerDragHandler) = awaitEachGesture {
    val down = awaitFirstDown(requireUnconsumed = false)
    val width = size.width.toFloat()
    val height = size.height.toFloat()
    var kind: DragKind? = null
    var dx = 0f
    var dy = 0f
    // Captured when the direction locks.
    var startLevel = 0f
    var startPosition = 0L
    var duration = -1L
    var seekOffset = 0L

    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == down.id }
        if (change == null || !change.pressed) break
        val delta = change.positionChange()
        dx += delta.x
        dy += delta.y
        if (kind == null) {
            kind = PlayerGestures.lockDrag(
                dx = dx,
                dy = dy,
                slop = viewConfiguration.touchSlop,
                startX = down.position.x,
                width = width,
                canSeek = handler.canSeek(),
                gesturesEnabled = handler.gesturesEnabled(),
            )
            when (kind) {
                DragKind.SEEK -> {
                    val player = handler.player()
                    startPosition = player?.currentPosition ?: 0L
                    duration = player?.duration ?: -1L
                }

                DragKind.BRIGHTNESS -> startLevel = handler.levels.brightness()

                DragKind.VOLUME -> startLevel = handler.levels.volume()

                DragKind.ZAP, DragKind.NONE, null -> Unit
            }
            // Measure from the lock so the value does not jump by the slop.
            if (kind == DragKind.BRIGHTNESS || kind == DragKind.VOLUME || kind == DragKind.SEEK) {
                dx = 0f
                dy = 0f
            }
        }
        when (kind) {
            null, DragKind.ZAP, DragKind.NONE -> Unit

            DragKind.SEEK -> {
                val target = PlayerGestures.seekTarget(startPosition, PlayerGestures.seekOffsetMs(dx, width), duration)
                seekOffset = target - startPosition
                handler.onFeedback(GestureFeedback.Seek(target, seekOffset, duration, active = true))
            }

            DragKind.BRIGHTNESS -> {
                val level = PlayerGestures.adjustLevel(startLevel, dy, height)
                handler.levels.setBrightness(level)
                handler.onFeedback(GestureFeedback.Brightness(PlayerGestures.percent(level), active = true))
            }

            DragKind.VOLUME -> {
                val applied = handler.levels.setVolume(PlayerGestures.adjustLevel(startLevel, dy, height))
                handler.onFeedback(GestureFeedback.Volume(PlayerGestures.percent(applied), active = true))
            }
        }
        if (kind != null) change.consume()
    }

    when (kind) {
        DragKind.SEEK -> {
            handler.onSeek(seekOffset)
            handler.onFeedback(GestureFeedback.Seek(startPosition + seekOffset, seekOffset, duration, active = false))
        }

        DragKind.BRIGHTNESS, DragKind.VOLUME -> handler.onFeedback(null)

        DragKind.ZAP -> PlayerGestures.zapDirection(dy, handler.zapThresholdPx())?.let(handler.onZap)

        DragKind.NONE, null -> Unit
    }
}

/** Centered badge: seek target / offset, brightness or volume percentage, double-tap result. */
@Composable
internal fun GestureFeedbackBadge(feedback: GestureFeedback, modifier: Modifier = Modifier) {
    val lines: List<String> = when (feedback) {
        is GestureFeedback.Seek -> listOf(
            if (feedback.durationMs > 0L) {
                stringResource(
                    R.string.feature_player_gesture_seek_target,
                    PlayerGestures.formatTime(feedback.targetMs),
                    PlayerGestures.formatTime(feedback.durationMs),
                )
            } else {
                PlayerGestures.formatTime(feedback.targetMs)
            },
            PlayerGestures.formatOffset(feedback.offsetMs),
        )

        is GestureFeedback.Brightness ->
            listOf(stringResource(R.string.feature_player_gesture_brightness, feedback.percent))

        is GestureFeedback.Volume -> listOf(stringResource(R.string.feature_player_gesture_volume, feedback.percent))

        is GestureFeedback.DoubleTap -> listOf(
            when (feedback.action) {
                DoubleTapAction.REWIND -> stringResource(R.string.feature_player_gesture_rewind)

                DoubleTapAction.FORWARD -> stringResource(R.string.feature_player_gesture_forward)

                DoubleTapAction.PLAY_PAUSE -> stringResource(
                    if (feedback.nowPlaying) R.string.feature_player_pip_play else R.string.feature_player_pip_pause,
                )
            },
        )
    }
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        lines.forEachIndexed { index, line ->
            val primary = index == 0
            val typography = MaterialTheme.typography
            Text(
                text = line,
                style = if (primary) typography.headlineSmall else typography.titleMedium,
                color = if (primary) Color.White else Color.White.copy(alpha = 0.8f),
            )
        }
    }
}
