package com.streambox.player.core.player.audio

import androidx.media3.exoplayer.DefaultRenderersFactory
import com.streambox.player.core.player.DecoderMode

/**
 * Where the FFmpeg audio decoder sits among the audio renderers (K26).
 *
 * - Hardware first (default): `EXTENSION_RENDERER_MODE_ON`. The device's MediaCodec renderer comes
 *   first, so AAC / MP3 and TV passthrough (AC-3 / E-AC-3 to an AV receiver) keep using the platform;
 *   the FFmpeg renderer only takes formats MediaCodec cannot handle (typically AC-3, E-AC-3, DTS,
 *   TrueHD on phones).
 * - Software first ("software decoding only", or after a decoder failure):
 *   `EXTENSION_RENDERER_MODE_PREFER`. FFmpeg is offered first for every audio format it knows.
 */
@Suppress("UnusedParameter", "FunctionOnlyReturningConstant") // K26 failing-test stubs
object AudioRendererPolicy {
    // K26 failing-test stub.
    fun extensionMode(mode: DecoderMode): Int = DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF

    /**
     * Index at which to insert the FFmpeg audio renderer, given the index of the MediaCodec audio
     * renderer the default factory added; null = do not add it.
     */
    fun ffmpegIndex(extensionMode: Int, mediaCodecIndex: Int): Int? = null
}

/**
 * Track-selector settings that keep a channel audible (K26). Explicit so a Media3 default change
 * cannot silently bring back "video without audio".
 */
data class AudioSelectionPolicy(
    /** Pick a track even if the renderer says it exceeds its capabilities, rather than none. */
    val exceedRendererCapabilitiesIfNecessary: Boolean = true,
    /** Pick a track even if it exceeds channel-count / bitrate constraints, rather than none. */
    val exceedAudioConstraintsIfNecessary: Boolean = true,
    /** Adaptive audio may switch between e.g. AAC and AC-3 renditions of an HLS stream. */
    val allowMixedMimeTypeAdaptiveness: Boolean = true,
    /**
     * Tunneled playback binds audio to the video decoder's hardware path; many IPTV boxes fail
     * silently with it, and it cannot be used with the FFmpeg decoder.
     */
    val tunneling: Boolean = true,
    /** Audio offload hands the stream to the DSP; some devices go silent with TS / HLS audio. */
    val audioOffload: Boolean = false,
) {
    companion object {
        val DEFAULT = AudioSelectionPolicy()
    }
}
