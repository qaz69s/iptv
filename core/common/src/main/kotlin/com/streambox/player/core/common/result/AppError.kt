package com.streambox.player.core.common.result

/** Typed failure reasons. Presentation maps each case to a localized string resource. */
sealed interface AppError {
    data class Http(val code: Int) : AppError

    data object Timeout : AppError

    data object NoNetwork : AppError

    data object CleartextNotAllowed : AppError

    data class Parse(val line: Long?, val detail: String) : AppError

    data class Io(val detail: String) : AppError

    data class Unknown(val detail: String) : AppError
}
