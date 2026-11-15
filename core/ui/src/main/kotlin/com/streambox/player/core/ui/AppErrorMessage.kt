package com.streambox.player.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.streambox.player.core.common.result.AppError

/** Human-readable, localized reason for an [AppError]. */
@Composable
fun AppError.message(): String = when (this) {
    is AppError.Http -> stringResource(R.string.core_ui_error_http, code)

    AppError.Timeout -> stringResource(R.string.core_ui_error_timeout)

    AppError.NoNetwork -> stringResource(R.string.core_ui_error_no_network)

    AppError.CleartextNotAllowed -> stringResource(R.string.core_ui_error_cleartext)

    is AppError.Parse -> line?.let { stringResource(R.string.core_ui_error_parse_line, it, detail) }
        ?: stringResource(R.string.core_ui_error_parse, detail)

    is AppError.Io -> stringResource(R.string.core_ui_error_io, detail)

    is AppError.Unknown -> stringResource(R.string.core_ui_error_unknown, detail)
}
