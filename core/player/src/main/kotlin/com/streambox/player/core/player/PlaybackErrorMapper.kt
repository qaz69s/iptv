package com.streambox.player.core.player

import androidx.media3.common.PlaybackException
import com.streambox.player.core.model.PlaybackError

/** Translates Media3 error codes into reasons the UI can explain (HTTP status, timeout, decoder...). */
object PlaybackErrorMapper {
    @Suppress("CyclomaticComplexMethod")
    fun map(errorCode: Int, httpStatus: Int?, cleartextBlocked: Boolean, detail: String? = null): PlaybackError = when {
        cleartextBlocked -> PlaybackError.CleartextNotAllowed
        httpStatus != null -> PlaybackError.Http(httpStatus)
        errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> PlaybackError.Other(errorCode, detail)
        errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> PlaybackError.Timeout
        errorCode == PlaybackException.ERROR_CODE_TIMEOUT -> PlaybackError.Timeout
        errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> PlaybackError.Network
        errorCode == PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED -> PlaybackError.CleartextNotAllowed
        errorCode in DECODER_ERRORS -> PlaybackError.Decoder
        errorCode in FORMAT_ERRORS -> PlaybackError.UnsupportedFormat
        else -> PlaybackError.Other(errorCode, detail)
    }

    private val DECODER_ERRORS = setOf(
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
    )

    private val FORMAT_ERRORS = setOf(
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
    )
}
