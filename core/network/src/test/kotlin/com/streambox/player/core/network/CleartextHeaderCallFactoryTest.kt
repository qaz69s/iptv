package com.streambox.player.core.network

import com.streambox.player.core.common.net.CleartextImagePolicy
import com.streambox.player.core.common.result.AppError
import com.streambox.player.core.common.result.AppException
import java.util.concurrent.TimeUnit
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Headers.Companion.headersOf
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/** K1: channel logos must obey the per-playlist cleartext switch (MockWebServer serves plain http). */
class CleartextHeaderCallFactoryTest {
    private lateinit var server: MockWebServer

    // Same shape as the app's shared client: the guard lets untagged requests through.
    private val client = OkHttpClient.Builder()
        .readTimeout(1, TimeUnit.SECONDS)
        .addNetworkInterceptor(CleartextGuardInterceptor(defaultAllowed = true))
        .build()
    private val factory = CleartextHeaderCallFactory(client)

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @AfterEach
    fun tearDown() = server.close()

    private fun request(path: String = "/logo.png", allow: String? = null) = Request.Builder()
        .url(server.url(path))
        .apply { if (allow != null) header(CleartextImagePolicy.HEADER, allow) }
        .build()

    @Test
    fun `a logo of a playlist without cleartext is not loaded over http (K1)`() {
        server.enqueue(MockResponse(body = "png"))

        val error = assertThrows<AppException> {
            factory.newCall(request(allow = CleartextImagePolicy.NOT_ALLOWED)).execute().close()
        }

        assertEquals(AppError.CleartextNotAllowed, error.error)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a logo request without the header is treated as not allowed (K1)`() {
        server.enqueue(MockResponse(body = "png"))

        assertThrows<AppException> { factory.newCall(request()).execute().close() }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a logo of a playlist with cleartext loads and the marker header is not sent (K1)`() {
        server.enqueue(MockResponse(body = "png"))

        val body = factory.newCall(request(allow = CleartextImagePolicy.ALLOWED)).execute().use { it.body.string() }

        assertEquals("png", body)
        assertNull(server.takeRequest().headers[CleartextImagePolicy.HEADER])
    }

    @Test
    fun `redirect hops keep the playlist's policy (K1)`() {
        server.enqueue(MockResponse(code = 302, headers = headersOf("Location", "/moved.png")))
        server.enqueue(MockResponse(body = "moved"))

        val body = factory.newCall(request(allow = CleartextImagePolicy.ALLOWED)).execute().use { it.body.string() }

        assertEquals("moved", body)
        assertEquals(2, server.requestCount)
    }
}
