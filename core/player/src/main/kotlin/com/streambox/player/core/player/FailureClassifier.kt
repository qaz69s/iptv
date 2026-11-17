package com.streambox.player.core.player

import androidx.media3.common.PlaybackException
import com.streambox.player.core.player.reconnect.FailureKind

/** Maps Media3 error codes (plus the HTTP status / cleartext flag found in the cause chain) to a [FailureKind]. */
object FailureClassifier {
    @Suppress("CyclomaticComplexMethod")
    fun classify(errorCode: Int, httpStatus: Int?, cleartextBlocked: Boolean): FailureKind = when {
        cleartextBlocked -> FailureKind.CLEARTEXT_BLOCKED

        httpStatus != null -> FailureKind.fromHttpStatus(httpStatus)

        errorCode == PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED -> FailureKind.CLEARTEXT_BLOCKED

        errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> FailureKind.BEHIND_LIVE_WINDOW

        errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> FailureKind.NETWORK

        errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> FailureKind.TIMEOUT

        errorCode == PlaybackException.ERROR_CODE_TIMEOUT -> FailureKind.TIMEOUT

        // Generic I/O: typically the connection dropped / the server closed the stream mid-read.
        errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> FailureKind.SOURCE_ENDED

        errorCode in DECODER_ERRORS -> FailureKind.DECODER

        errorCode in FORMAT_ERRORS -> FailureKind.UNSUPPORTED_FORMAT

        errorCode in CLIENT_ERRORS -> FailureKind.HTTP_CLIENT_ERROR

        else -> FailureKind.OTHER
    }

    private val DECODER_ERRORS = setOf(
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
    )

    private val FORMAT_ERRORS = setOf(
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
    )

    private val CLIENT_ERRORS = setOf(
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
        PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
    )
}
