package com.streamdeck.player.feature.playerui

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.view.WindowManager
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import com.streamdeck.player.core.player.PlayerController
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class DragMode { NONE, SEEK, BRIGHTNESS, VOLUME }

private const val DOUBLE_TAP_SEEK_MS = 10_000L
private const val FULL_SWIPE_SEEK_MS = 120_000L
private const val SLOP_PX = 24f

class HintFormatter(private val ctx: Context) {
    fun brightness(pct: Int) = ctx.getString(R.string.hint_brightness, pct)
    fun volume(pct: Int) = ctx.getString(R.string.hint_volume, pct)
    fun seek(deltaMs: Long) = ctx.getString(R.string.hint_seek, (if (deltaMs >= 0) "+" else "-") + formatDuration(abs(deltaMs)))
}

/**
 * Phone gestures (spec §4.3): horizontal drag = seek (VOD only), left vertical = brightness, right vertical = volume,
 * double tap left/right = ∓10 s (VOD), double tap centre = play/pause, single tap = toggle overlay.
 */
fun Modifier.playerGestures(
    activity: Activity?,
    controller: PlayerController,
    onTap: () -> Unit,
    onHint: (String?) -> Unit,
    hintFormatter: HintFormatter,
    /** Time-shift for live channels with catch-up; null when unsupported. */
    onLiveSeek: ((Long) -> Unit)? = null,
): Modifier = this
    .pointerInput(controller, onLiveSeek != null) {
        detectTapGestures(
            onTap = { onTap() },
            onDoubleTap = { o ->
                val third = size.width / 3f
                val p = controller.playerFlow.value
                val seekable = p != null && p.isCurrentMediaItemSeekable && !p.isCurrentMediaItemLive
                when {
                    o.x < third && seekable -> { controller.seekBy(-DOUBLE_TAP_SEEK_MS); onHint(hintFormatter.seek(-DOUBLE_TAP_SEEK_MS)) }
                    o.x > third * 2 && seekable -> { controller.seekBy(DOUBLE_TAP_SEEK_MS); onHint(hintFormatter.seek(DOUBLE_TAP_SEEK_MS)) }
                    o.x < third && onLiveSeek != null -> { onLiveSeek(-DOUBLE_TAP_SEEK_MS); onHint(hintFormatter.seek(-DOUBLE_TAP_SEEK_MS)) }
                    else -> controller.togglePause()
                }
            },
        )
    }
    .pointerInput(controller, onLiveSeek != null) {
        val audio = activity?.getSystemService(AudioManager::class.java)
        var mode = DragMode.NONE
        var start = Offset.Zero
        var total = Offset.Zero
        var startBrightness = 0.5f
        var startVolume = 0
        var seekDelta = 0L
        detectDragGestures(
            onDragStart = { o ->
                start = o
                total = Offset.Zero
                mode = DragMode.NONE
                seekDelta = 0
                startBrightness = activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0 } ?: 0.5f
                startVolume = audio?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0
            },
            onDragEnd = {
                if (mode == DragMode.SEEK && seekDelta != 0L) {
                    val p = controller.playerFlow.value
                    val seekable = p != null && p.isCurrentMediaItemSeekable && !p.isCurrentMediaItemLive
                    if (seekable) controller.seekBy(seekDelta) else onLiveSeek?.invoke(seekDelta)
                }
                mode = DragMode.NONE
                onHint(null)
            },
            onDragCancel = { mode = DragMode.NONE; onHint(null) },
            onDrag = { change, drag ->
                change.consume()
                total += drag
                if (mode == DragMode.NONE && total.getDistance() > SLOP_PX) {
                    val p = controller.playerFlow.value
                    val seekable = p != null && p.isCurrentMediaItemSeekable && !p.isCurrentMediaItemLive
                    mode = when {
                        abs(total.x) > abs(total.y) -> if (seekable || onLiveSeek != null) DragMode.SEEK else DragMode.NONE
                        start.x < size.width / 2f -> DragMode.BRIGHTNESS
                        else -> DragMode.VOLUME
                    }
                }
                when (mode) {
                    DragMode.SEEK -> {
                        seekDelta = (total.x / size.width * FULL_SWIPE_SEEK_MS).toLong()
                        onHint(hintFormatter.seek(seekDelta))
                    }
                    DragMode.BRIGHTNESS -> activity?.window?.let { w ->
                        val v = (startBrightness - total.y / size.height).coerceIn(0.01f, 1f)
                        val lp: WindowManager.LayoutParams = w.attributes
                        lp.screenBrightness = v
                        w.attributes = lp
                        onHint(hintFormatter.brightness((v * 100).roundToInt()))
                    }
                    DragMode.VOLUME -> audio?.let { a ->
                        val max = a.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        val v = (startVolume - total.y / size.height * max).roundToInt().coerceIn(0, max)
                        a.setStreamVolume(AudioManager.STREAM_MUSIC, v, 0)
                        onHint(hintFormatter.volume(if (max == 0) 0 else v * 100 / max))
                    }
                    DragMode.NONE -> Unit
                }
            },
        )
    }

internal fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
