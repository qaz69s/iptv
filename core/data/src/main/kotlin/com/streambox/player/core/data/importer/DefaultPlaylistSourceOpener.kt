package com.streambox.player.core.data.importer

import android.content.Context
import android.provider.OpenableColumns
import androidx.core.net.toUri
import com.streambox.player.core.common.result.AppError
import com.streambox.player.core.common.result.AppException
import com.streambox.player.core.model.PlaylistType
import com.streambox.player.core.network.PlaylistDownloader
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.FileNotFoundException
import javax.inject.Inject

/** HTTP(S) via OkHttp (custom User-Agent, gzip, redirects, per-playlist cleartext); files via SAF. */
internal class DefaultPlaylistSourceOpener @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloader: PlaylistDownloader,
) : PlaylistSourceOpener {
    override fun open(source: PlaylistSource): OpenedSource = when (source.type) {
        PlaylistType.M3U_URL -> downloader.open(source.location, source.userAgent, source.allowCleartext).let {
            OpenedSource(it.stream, it.contentLength)
        }

        PlaylistType.M3U_FILE -> openDocument(source.location)

        PlaylistType.XTREAM -> throw AppException(AppError.Unknown("Xtream import arrives in P6"))
    }

    @Suppress("ThrowsCount") // Each SAF failure is translated to a typed AppError.
    private fun openDocument(location: String): OpenedSource {
        val uri = location.toUri()
        val resolver = context.contentResolver
        val stream = try {
            resolver.openInputStream(uri)
        } catch (e: SecurityException) {
            throw AppException(AppError.Io("permission revoked"), e)
        } catch (e: FileNotFoundException) {
            throw AppException(AppError.Io("file not found"), e)
        } ?: throw AppException(AppError.Io("file not found"))
        val size = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else null
            }
        }.getOrNull()
        return OpenedSource(stream, size?.takeIf { it > 0 })
    }
}
