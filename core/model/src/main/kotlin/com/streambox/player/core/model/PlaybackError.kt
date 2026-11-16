package com.streambox.player.core.model

/** Why playback failed, precise enough to tell the user what went wrong. */
sealed interface PlaybackError {
    data class Http(val code: Int) : PlaybackError

    data object Timeout : PlaybackError

    data object Network : PlaybackError

    data object CleartextNotAllowed : PlaybackError

    data object Decoder : PlaybackError

    data object UnsupportedFormat : PlaybackError

    data object ChannelNotFound : PlaybackError

    data class Other(val code: Int, val detail: String?) : PlaybackError
}
