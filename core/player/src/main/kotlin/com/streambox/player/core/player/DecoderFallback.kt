package com.streambox.player.core.player

import com.streambox.player.core.player.reconnect.FailureKind

/** Which decoders MediaCodecSelector offers first. */
enum class DecoderMode { HARDWARE_FIRST, SOFTWARE_FIRST }

/**
 * Hardware decoding is preferred; when a decoder fails to initialize or decode, the player is rebuilt
 * once with software decoders first. With "software decoding only" there is nothing to fall back to.
 */
object DecoderFallback {
    fun initialMode(softwareDecodingOnly: Boolean): DecoderMode =
        if (softwareDecodingOnly) DecoderMode.SOFTWARE_FIRST else DecoderMode.HARDWARE_FIRST

    /** The mode to rebuild with, or null when the error must be shown. */
    fun next(kind: FailureKind, current: DecoderMode, alreadyFellBack: Boolean): DecoderMode? =
        DecoderMode.SOFTWARE_FIRST.takeIf {
            kind == FailureKind.DECODER && current == DecoderMode.HARDWARE_FIRST && !alreadyFellBack
        }
}
