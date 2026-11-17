package com.streambox.player.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.streambox.player.core.model.PlaybackError

/** Localized, specific reason for a playback failure (HTTP status, timeout, decoder...). */
@Composable
internal fun PlaybackError.message(): String = when (this) {
    is PlaybackError.Http -> stringResource(R.string.feature_player_error_http, code)
    PlaybackError.Timeout -> stringResource(R.string.feature_player_error_timeout)
    PlaybackError.Network -> stringResource(R.string.feature_player_error_network)
    PlaybackError.CleartextNotAllowed -> stringResource(R.string.feature_player_error_cleartext)
    PlaybackError.Decoder -> stringResource(R.string.feature_player_error_decoder)
    PlaybackError.StreamEnded -> stringResource(R.string.feature_player_error_stream_ended)
    PlaybackError.UnsupportedFormat -> stringResource(R.string.feature_player_error_format)
    PlaybackError.ChannelNotFound -> stringResource(R.string.feature_player_error_not_found)
    is PlaybackError.Other -> stringResource(R.string.feature_player_error_other, detail ?: code.toString())
}
