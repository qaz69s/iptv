package com.streambox.player.core.model

/** How much the player buffers before / while playing (requirements §4.3 "缓冲档位"). */
enum class BufferProfile { LowLatency, Balanced, Stable }

/** How the video fills the screen (requirements §4.3 "画面比例"). */
enum class AspectRatioMode {
    /** Whole picture visible, letterboxed (自适应). */
    Fit,
    Ratio16x9,
    Ratio4x3,

    /** Fills the screen, cropping the overflow (填充). */
    Fill,

    /** Fills the screen, distorting the picture (拉伸). */
    Stretch,

    /** Native pixel size, centered (原始). */
    Original,
}

enum class SubtitleSize { Small, Medium, Large, ExtraLarge }

enum class SubtitleColor { White, Yellow, Green, Cyan }

enum class SubtitleBackground { None, SemiTransparent, Opaque }

data class SubtitleStyle(
    val size: SubtitleSize = SubtitleSize.Medium,
    val color: SubtitleColor = SubtitleColor.White,
    val background: SubtitleBackground = SubtitleBackground.SemiTransparent,
)

/** Player preferences persisted in settings (requirements §4.9 "播放"). */
data class PlaybackSettings(
    val bufferProfile: BufferProfile = BufferProfile.Balanced,
    /** Prefer the platform's software decoders over hardware ones for every stream. */
    val softwareDecodingOnly: Boolean = false,
    /** Automatic reconnect attempts after a recoverable failure; 0 disables reconnecting. */
    val maxReconnectAttempts: Int = DEFAULT_RECONNECT_ATTEMPTS,
    val defaultAspectRatio: AspectRatioMode = AspectRatioMode.Fit,
    val subtitleStyle: SubtitleStyle = SubtitleStyle(),
    /** Keep the audio playing (media notification, headset controls) when the app goes to the background. */
    val backgroundPlayback: Boolean = true,
    /**
     * Picture-in-picture on phones / tablets (Android 8.0+ with PiP support; never on TV): leaving the
     * player with Home shrinks it into a floating window instead of going audio-only.
     */
    val pictureInPicture: Boolean = true,
    /**
     * Touch gestures on the player (phones / tablets only, never on TV): horizontal swipe seeks, vertical
     * swipe on the left / right third changes brightness / volume, double-tap seeks ±10 s or pauses.
     * Off: only tap (overlay) and vertical swipe anywhere (zap) remain.
     */
    val playbackGestures: Boolean = true,
) {
    companion object {
        const val DEFAULT_RECONNECT_ATTEMPTS = 5
        const val MAX_RECONNECT_ATTEMPTS = 20

        /** Choices offered in settings. */
        val RECONNECT_ATTEMPT_CHOICES = listOf(0, 3, 5, 10)
    }
}
