package com.streambox.player.core.network

import com.streambox.player.core.common.result.AppError
import com.streambox.player.core.common.result.AppException
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers.Companion.headersOf
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PlaylistDownloaderTest {
    private lateinit var server: MockWebServer
    private val client = OkHttpClient.Builder()
        .readTimeout(1, TimeUnit.SECONDS)
        .addNetworkInterceptor(CleartextGuardInterceptor(defaultAllowed = true))
        .build()
    private val downloader = PlaylistDownloader(client)

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() = server.close()

    private fun url(path: String = "/list.m3u") = server.url(path).toString()

    @Test
    fun `streams the body and sends the custom user agent`() {
        server.enqueue(MockResponse(body = "#EXTM3U\n"))
        val text = downloader.open(url(), "MyBox/2.0", allowCleartext = true).use { it.stream.reader().readText() }
        assertEquals("#EXTM3U\n", text)
        assertEquals("MyBox/2.0", server.takeRequest().headers["User-Agent"])
    }

    @Test
    fun `default user agent when none is given`() {
        server.enqueue(MockResponse(body = "x"))
        downloader.open(url(), null, allowCleartext = true).close()
        assertEquals(PlaylistDownloader.DEFAULT_USER_AGENT, server.takeRequest().headers["User-Agent"])
    }

    @Test
    fun `http error status is reported with its code`() {
        server.enqueue(MockResponse(code = 403))
        val error = assertThrows<AppException> { downloader.open(url(), null, allowCleartext = true) }
        assertEquals(AppError.Http(403), error.error)
    }

    @Test
    fun `cleartext is refused unless the playlist allows it`() {
        val error = assertThrows<AppException> { downloader.open(url(), null, allowCleartext = false) }
        assertEquals(AppError.CleartextNotAllowed, error.error)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `redirects are followed`() {
        server.enqueue(MockResponse(code = 302, headers = headersOf("Location", "/moved.m3u")))
        server.enqueue(MockResponse(body = "moved"))
        val text = downloader.open(url(), null, allowCleartext = true).use { it.stream.reader().readText() }
        assertEquals("moved", text)
    }

    @Test
    fun `gzip content encoding is decoded transparently`() {
        val gz = ByteArrayOutputStream().also { out ->
            GZIPOutputStream(out).use { it.write("#EXTM3U gz\n".toByteArray()) }
        }
        server.enqueue(
            MockResponse.Builder()
                .addHeader("Content-Encoding", "gzip")
                .body(Buffer().write(gz.toByteArray()))
                .build(),
        )
        val text = downloader.open(url(), null, allowCleartext = true).use { it.stream.reader().readText() }
        assertEquals("#EXTM3U gz\n", text)
    }

    @Test
    fun `content length is exposed when known`() {
        server.enqueue(MockResponse(body = "12345"))
        downloader.open(url(), null, allowCleartext = true).use { assertEquals(5L, it.contentLength) }
    }

    @Test
    fun `invalid url is reported as an io error`() {
        val error = assertThrows<AppException> { downloader.open("not a url", null, allowCleartext = true) }
        assertEquals(AppError.Io("invalid URL"), error.error)
    }
}
