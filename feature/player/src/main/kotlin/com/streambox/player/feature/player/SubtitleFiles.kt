package com.streambox.player.feature.player

import androidx.media3.common.MimeTypes
import java.util.Locale

/** External subtitle files the player can side-load, recognized by extension or provider MIME type. */
internal object SubtitleFiles {
    /** MIME types offered to the SAF picker; many providers report SRT as text/plain or octet-stream. */
    val PICKER_MIME_TYPES = arrayOf(
        MimeTypes.APPLICATION_SUBRIP,
        MimeTypes.TEXT_VTT,
        MimeTypes.TEXT_SSA,
        "text/plain",
        "application/octet-stream",
        "*/*",
    )

    private val BY_EXTENSION = mapOf(
        "srt" to MimeTypes.APPLICATION_SUBRIP,
        "vtt" to MimeTypes.TEXT_VTT,
        "webvtt" to MimeTypes.TEXT_VTT,
        "ass" to MimeTypes.TEXT_SSA,
        "ssa" to MimeTypes.TEXT_SSA,
    )

    private val KNOWN_MIME = BY_EXTENSION.values.toSet()

    /** The subtitle MIME type for a picked file, or null when it is not a supported subtitle format. */
    fun mimeTypeFor(displayName: String?, providerMimeType: String?): String? {
        val extension = displayName?.substringAfterLast('.', "")?.lowercase(Locale.ROOT).orEmpty()
        return BY_EXTENSION[extension]
            ?: providerMimeType?.lowercase(Locale.ROOT)?.takeIf { it in KNOWN_MIME }
            ?: MimeTypes.APPLICATION_SUBRIP.takeIf { providerMimeType.equals("text/srt", ignoreCase = true) }
    }
}
