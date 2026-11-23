package com.streamdeck.player.core.player

data class ExternalSubtitle(val uri: String, val mimeType: String, val label: String)

data class StreamRequest(
    val url: String,
    val title: String,
    val subtitle: String? = null,
    val artworkUrl: String? = null,
    val userAgent: String? = null,
    val referrer: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val isLive: Boolean = true,
    val startPositionMs: Long = 0,
    val externalSubtitles: List<ExternalSubtitle> = emptyList(),
    val allowCleartext: Boolean = true,
)

/** Typed failure reasons; the UI maps them to localized strings (spec §4.3 “明确原因”). */
sealed interface PlaybackError {
    data class Http(val code: Int) : PlaybackError
    data object Timeout : PlaybackError
    data object Network : PlaybackError
    data class Decoder(val detail: String) : PlaybackError
    data object CleartextBlocked : PlaybackError
    data object MeteredBlocked : PlaybackError
    data class Other(val detail: String) : PlaybackError
}

sealed interface PlaybackState {
    data object Idle : PlaybackState
    data object Buffering : PlaybackState
    data object Playing : PlaybackState
    data object Paused : PlaybackState
    data object Ended : PlaybackState
    data class Reconnecting(val attempt: Int, val maxAttempts: Int, val delayMs: Long) : PlaybackState
    data class Error(val error: PlaybackError) : PlaybackState
}
