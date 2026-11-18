package com.streambox.player.feature.player

import com.streambox.player.core.model.ZapDirection
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Third of the player width a touch falls in. */
enum class TouchZone { LEFT, CENTER, RIGHT }

/** What a drag on the player does, decided once it has moved past the touch slop. */
enum class DragKind {
    /** Horizontal: seek preview, applied on release. */
    SEEK,

    /** Vertical on the left third: window brightness. */
    BRIGHTNESS,

    /** Vertical on the right third: media volume. */
    VOLUME,

    /** Vertical in the middle third (or anywhere with gestures off): change channel on release. */
    ZAP,

    /** Ignored until the finger lifts (e.g. horizontal swipe on live content that cannot seek). */
    NONE,
}

/** What a double-tap did, shown briefly as feedback. */
enum class DoubleTapAction { REWIND, PLAY_PAUSE, FORWARD }

/**
 * Pure touch-gesture rules of the player (phones / tablets): zones, direction lock and the conversion of
 * finger movement into seek offsets, brightness and volume. Kept free of Android types so it is JVM-tested.
 *
 * Layout: left third = brightness, middle third = channel zap, right third = volume for vertical drags;
 * horizontal drags seek anywhere. Double-tap: left third −10 s, middle pause / play, right third +10 s.
 */
@Suppress("TooManyFunctions")
object PlayerGestures {
    /** A swipe across the full player width moves this far (a fixed scale keeps it predictable for live DVR). */
    const val SEEK_FULL_WIDTH_MS = 120_000L

    /** A swipe over the full player height changes brightness / volume by this fraction (0..1). */
    const val LEVEL_FULL_HEIGHT = 1f

    /** Horizontal movement must beat vertical by this factor to lock as a seek (and vice versa). */
    private const val DIRECTION_BIAS = 1f

    fun zoneOf(x: Float, width: Float): TouchZone {
        if (width <= 0f) return TouchZone.CENTER
        val fraction = x / width
        return when {
            fraction < LEFT_ZONE_END -> TouchZone.LEFT
            fraction > RIGHT_ZONE_START -> TouchZone.RIGHT
            else -> TouchZone.CENTER
        }
    }

    /**
     * Locks the drag direction once the total movement ([dx], [dy] since the finger went down) exceeds
     * [slop]. Returns null while undecided. [startX] / [width] pick the zone for vertical drags.
     * With [gesturesEnabled] off only the legacy behavior remains: vertical anywhere zaps.
     */
    @Suppress("LongParameterList")
    fun lockDrag(
        dx: Float,
        dy: Float,
        slop: Float,
        startX: Float,
        width: Float,
        canSeek: Boolean,
        gesturesEnabled: Boolean,
    ): DragKind? {
        val ax = abs(dx)
        val ay = abs(dy)
        if (ax <= slop && ay <= slop) return null
        val horizontal = ax > ay * DIRECTION_BIAS
        return when {
            horizontal -> if (gesturesEnabled && canSeek) DragKind.SEEK else DragKind.NONE

            !gesturesEnabled -> DragKind.ZAP

            else -> when (zoneOf(startX, width)) {
                TouchZone.LEFT -> DragKind.BRIGHTNESS
                TouchZone.RIGHT -> DragKind.VOLUME
                TouchZone.CENTER -> DragKind.ZAP
            }
        }
    }

    /** Seek offset for a horizontal drag of [dx] pixels on a player [width] pixels wide. */
    fun seekOffsetMs(dx: Float, width: Float): Long =
        if (width <= 0f) 0L else (dx / width * SEEK_FULL_WIDTH_MS).toLong()

    /**
     * Target position for [offsetMs] from [positionMs], kept inside 0..[durationMs] (no upper bound when
     * the duration is unknown, i.e. negative).
     */
    fun seekTarget(positionMs: Long, offsetMs: Long, durationMs: Long): Long {
        val target = (positionMs + offsetMs).coerceAtLeast(0L)
        return if (durationMs > 0L) target.coerceAtMost(durationMs) else target
    }

    /**
     * New level (0..1) after a vertical drag of [dy] pixels (negative = up = more) on a player [height]
     * pixels tall, starting from [start].
     */
    fun adjustLevel(start: Float, dy: Float, height: Float): Float {
        if (height <= 0f) return start.coerceIn(0f, 1f)
        return (start - dy / height * LEVEL_FULL_HEIGHT).coerceIn(0f, 1f)
    }

    /** Volume step for [level] (0..1) between [minIndex] and [maxIndex] of the stream. */
    fun volumeIndex(level: Float, maxIndex: Int, minIndex: Int = 0): Int {
        if (maxIndex <= minIndex) return minIndex
        return (minIndex + level.coerceIn(0f, 1f) * (maxIndex - minIndex)).roundToInt()
    }

    /** Level (0..1) of volume step [index]. */
    fun volumeLevel(index: Int, maxIndex: Int, minIndex: Int = 0): Float {
        if (maxIndex <= minIndex) return 0f
        return ((index - minIndex).toFloat() / (maxIndex - minIndex)).coerceIn(0f, 1f)
    }

    fun percent(level: Float): Int = (level.coerceIn(0f, 1f) * PERCENT).roundToInt()

    /** Channel change for a vertical swipe of [totalDy] pixels: up = next, down = previous. */
    fun zapDirection(totalDy: Float, threshold: Float): ZapDirection? = when {
        totalDy <= -threshold -> ZapDirection.NEXT
        totalDy >= threshold -> ZapDirection.PREVIOUS
        else -> null
    }

    /**
     * Double-tap in [zone]: the sides skip only on seekable content (null = ignored), the middle always
     * toggles play / pause.
     */
    fun doubleTapAction(zone: TouchZone, canSeek: Boolean): DoubleTapAction? = when (zone) {
        TouchZone.LEFT -> DoubleTapAction.REWIND.takeIf { canSeek }
        TouchZone.CENTER -> DoubleTapAction.PLAY_PAUSE
        TouchZone.RIGHT -> DoubleTapAction.FORWARD.takeIf { canSeek }
    }

    /** "m:ss" or "h:mm:ss". */
    fun formatTime(ms: Long): String {
        val totalSeconds = ms.coerceAtLeast(0L) / MS_PER_SECOND
        val hours = totalSeconds / SECONDS_PER_HOUR
        val minutes = totalSeconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
        val seconds = totalSeconds % SECONDS_PER_MINUTE
        return if (hours > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
        }
    }

    /** Signed offset, e.g. "+0:15" / "−1:05". */
    fun formatOffset(ms: Long): String = (if (ms < 0) "\u2212" else "+") + formatTime(abs(ms))

    private const val LEFT_ZONE_END = 1f / 3f
    private const val RIGHT_ZONE_START = 2f / 3f
    private const val PERCENT = 100
    private const val MS_PER_SECOND = 1_000L
    private const val SECONDS_PER_MINUTE = 60L
    private const val SECONDS_PER_HOUR = 3_600L
}
