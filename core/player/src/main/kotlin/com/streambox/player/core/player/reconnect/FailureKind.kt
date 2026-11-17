package com.streambox.player.core.player.reconnect

/**
 * Why a stream stopped, reduced to what decides between reconnecting and giving up. Pure Kotlin so
 * the reconnect logic is testable without Media3; [com.streambox.player.core.player.FailureClassifier]
 * maps player errors onto it.
 */
enum class FailureKind(val retryable: Boolean) {
    /** Connection refused / reset / DNS / no route. */
    NETWORK(retryable = true),

    /** Connect or read timeout. */
    TIMEOUT(retryable = true),

    /** HTTP 5xx, 408 Request Timeout or 429 Too Many Requests. */
    HTTP_RETRYABLE(retryable = true),

    /** The player fell behind the live window (HLS / DASH): rejoin at the live edge. */
    BEHIND_LIVE_WINDOW(retryable = true),

    /** A live source closed the stream or the read was cut off mid-way. */
    SOURCE_ENDED(retryable = true),

    /** Any other HTTP 4xx (401, 403, 404...): retrying gives the same answer. */
    HTTP_CLIENT_ERROR(retryable = false),

    /** Plain http refused by the playlist's cleartext setting. */
    CLEARTEXT_BLOCKED(retryable = false),

    /** Container / manifest not supported or malformed, or file missing. */
    UNSUPPORTED_FORMAT(retryable = false),

    /** Decoder init / decode failure; handled by the software-decoder fallback, not by reconnecting. */
    DECODER(retryable = false),

    /** Anything unrecognized: surfaced to the user rather than retried blindly. */
    OTHER(retryable = false),
    ;

    companion object {
        /** Classifies an HTTP status: 408 / 429 / 5xx are worth retrying, other codes are not. */
        fun fromHttpStatus(status: Int): FailureKind = when {
            status == HTTP_REQUEST_TIMEOUT || status == HTTP_TOO_MANY_REQUESTS -> HTTP_RETRYABLE
            status in HTTP_SERVER_ERRORS -> HTTP_RETRYABLE
            else -> HTTP_CLIENT_ERROR
        }

        private const val HTTP_REQUEST_TIMEOUT = 408
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private val HTTP_SERVER_ERRORS = 500..599
    }
}
