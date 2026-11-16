package com.streambox.player.core.model

/** Progress of one playlist import, observed by the import progress screen. */
sealed interface ImportStatus {
    data object Idle : ImportStatus

    data object Connecting : ImportStatus

    /** [totalBytes] is null when the server sends no Content-Length (chunked / gzip). */
    data class Running(val bytesRead: Long, val totalBytes: Long?, val channels: Int) : ImportStatus {
        val fraction: Float? get() = totalBytes?.takeIf { it > 0 }?.let { (bytesRead.toFloat() / it).coerceIn(0f, 1f) }
    }

    /** Parsing finished; the new data is being validated and swapped in. */
    data class Finalizing(val channels: Int) : ImportStatus

    data class Success(val channels: Int, val groups: Int, val skippedLines: Long) : ImportStatus

    data class Failed(val reason: ImportFailure) : ImportStatus

    data object Cancelled : ImportStatus
}

/** Model-level failure; mapped to localized text in the UI. Kept independent of core:common. */
sealed interface ImportFailure {
    data class Http(val code: Int) : ImportFailure

    data object Timeout : ImportFailure

    data object NoNetwork : ImportFailure

    data object CleartextNotAllowed : ImportFailure

    data object EmptyPlaylist : ImportFailure

    data object FileNotReadable : ImportFailure

    data class Other(val detail: String) : ImportFailure
}
