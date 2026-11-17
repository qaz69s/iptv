package com.streambox.player.core.player

/** Position of a track in the player's current track groups; valid until the tracks change. */
data class TrackId(val group: Int, val track: Int)

data class VideoTrackOption(
    val id: TrackId,
    val width: Int,
    val height: Int,
    /** Bits per second, or null when unknown. */
    val bitrate: Int?,
    val selected: Boolean,
)

/** An audio or subtitle track. [label] / [language] come from the stream and may be missing. */
data class MediaTrackOption(
    val id: TrackId,
    val label: String?,
    val language: String?,
    val selected: Boolean,
    /** Loaded from a user-picked SRT / VTT file. */
    val external: Boolean = false,
)

data class PlaybackTracks(
    val video: List<VideoTrackOption> = emptyList(),
    /** No manual quality chosen: adaptive selection decides. */
    val videoAuto: Boolean = true,
    val audio: List<MediaTrackOption> = emptyList(),
    val subtitles: List<MediaTrackOption> = emptyList(),
    val subtitlesOff: Boolean = false,
)
