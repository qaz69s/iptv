package com.streambox.player.core.network

import com.streambox.player.core.common.result.AppError
import com.streambox.player.core.common.result.AppException
import java.io.InputStream
import javax.inject.Inject
import okhttp3.OkHttpClient
import okhttp3.Request

/** An open response body. Close it when done (closes the HTTP connection). */
class DownloadStream(val stream: InputStream, val contentLength: Long?) : AutoCloseable {
    override fun close() = stream.close()
}

/**
 * Streams a playlist over HTTP(S). The body is never buffered whole: the caller reads it line by line.
 * gzip `Content-Encoding` is decoded transparently by OkHttp; redirects (incl. https -> http, subject to
 * [CleartextGuardInterceptor]) are followed.
 */
class PlaylistDownloader @Inject constructor(private val client: OkHttpClient) {
    /** Blocking; call from an IO dispatcher. Throws [AppException] with a typed reason on failure. */
    fun open(url: String, userAgent: String?, allowCleartext: Boolean): DownloadStream {
        val request = try {
            Request.Builder()
                .url(url)
                .header("User-Agent", userAgent?.takeIf { it.isNotBlank() } ?: DEFAULT_USER_AGENT)
                .header("Accept", "*/*")
                .tag(CleartextPolicy::class.java, CleartextPolicy(allowCleartext))
                .build()
        } catch (e: IllegalArgumentException) {
            throw AppException(AppError.Io("invalid URL"), e)
        }
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val code = response.code
            response.close()
            throw AppException(AppError.Http(code))
        }
        val body = response.body
        val length = body.contentLength().takeIf { it >= 0 }
        return DownloadStream(body.byteStream(), length)
    }

    companion object {
        const val DEFAULT_USER_AGENT = "StreamBoxPlayer/1.0 (Linux; Android)"
    }
}
