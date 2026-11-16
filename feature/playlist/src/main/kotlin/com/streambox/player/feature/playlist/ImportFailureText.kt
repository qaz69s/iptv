package com.streambox.player.feature.playlist

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.streambox.player.core.model.ImportFailure

/** Localized, concrete reason for a failed import. */
@Composable
internal fun ImportFailure.message(): String = when (this) {
    is ImportFailure.Http -> stringResource(R.string.feature_playlist_error_http, code)
    ImportFailure.Timeout -> stringResource(R.string.feature_playlist_error_timeout)
    ImportFailure.NoNetwork -> stringResource(R.string.feature_playlist_error_network)
    ImportFailure.CleartextNotAllowed -> stringResource(R.string.feature_playlist_error_cleartext)
    ImportFailure.EmptyPlaylist -> stringResource(R.string.feature_playlist_error_empty)
    ImportFailure.FileNotReadable -> stringResource(R.string.feature_playlist_error_file)
    is ImportFailure.Other -> stringResource(R.string.feature_playlist_error_other, detail)
}
