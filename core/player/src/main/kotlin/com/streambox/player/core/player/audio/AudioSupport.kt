package com.streambox.player.core.player.audio

/**
 * Format of one elementary stream as the player reports it (K26): what the user reads in the panel's
 * stream info and what goes to logcat, so a silent channel can be reported with its audio codec.
 */
data class MediaFormatInfo(
    /** E.g. `audio/ac3`, `audio/mpeg-L2`, `video/avc`; null when the container did not say. */
    val sampleMimeType: String? = null,
    /** RFC 6381 codecs string when known, e.g. `mp4a.40.2`. */
    val codecs: String? = null,
    val channelCount: Int? = null,
    val sampleRate: Int? = null,
    val width: Int? = null,
    val height: Int? = null,
) {
    /** `audio/ac3 (ac-3), 6 ch, 48000 Hz` / `video/avc (avc1.64001f), 1920×1080`. */
    fun describe(): String = buildString {
        append(sampleMimeType ?: UNKNOWN)
        codecs?.takeIf { it.isNotBlank() }?.let { append(" (").append(it).append(')') }
        channelCount?.let { append(", ").append(it).append(" ch") }
        sampleRate?.let { append(", ").append(it).append(" Hz") }
        if (width != null && height != null) append(", ").append(width).append('×').append(height)
    }

    private companion object {
        const val UNKNOWN = "unknown"
    }
}

/** One audio track of the current stream and whether a renderer of this device can play it. */
data class AudioTrackFormat(
    val format: MediaFormatInfo,
    /** Some audio renderer (MediaCodec or the FFmpeg extension) can decode it. */
    val playable: Boolean,
    val selected: Boolean,
)

/** What the audio of the current stream looks like to the user. */
sealed interface AudioStatus {
    /** The stream has no audio tracks at all (or they are not known yet). */
    data object NoAudio : AudioStatus

    /** At least one audio track can be played. */
    data object Playable : AudioStatus

    /** Audio tracks exist but none can be decoded on this device: video plays silently (K26). */
    data class Unsupported(val formats: List<MediaFormatInfo>) : AudioStatus
}

/** Pure decisions about audio tracks (K26: "video but no audio"). */
object AudioSupport {
    /** [AudioStatus.Unsupported] lists each distinct format once, in track order. */
    fun status(tracks: List<AudioTrackFormat>): AudioStatus = when {
        tracks.isEmpty() -> AudioStatus.NoAudio
        tracks.any { it.playable } -> AudioStatus.Playable
        else -> AudioStatus.Unsupported(tracks.map { it.format }.distinct())
    }

    /**
     * A manual choice (an override) points at a track that cannot be played while another one can:
     * the override must be dropped so the track selector falls back to a playable track.
     */
    fun needsFallback(tracks: List<AudioTrackFormat>): Boolean =
        tracks.any { it.selected && !it.playable } && tracks.any { it.playable }
}
