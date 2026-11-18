package com.streambox.player.core.player

import com.streambox.player.core.player.audio.AudioStatus
import com.streambox.player.core.player.audio.MediaFormatInfo

/**
 * What is actually being decoded (K26): shown in the player panel's "Stream info" so the user can
 * report a channel's codecs, and logged to logcat (tag `StreamBoxPlayer`).
 */
data class StreamInfo(
    val video: MediaFormatInfo? = null,
    /** Decoder name, e.g. `c2.qti.avc.decoder`. */
    val videoDecoder: String? = null,
    val audio: MediaFormatInfo? = null,
    /** Decoder name, e.g. `c2.android.aac.decoder`, or `ffmpeg…` for the FFmpeg extension. */
    val audioDecoder: String? = null,
    val audioStatus: AudioStatus = AudioStatus.NoAudio,
)
