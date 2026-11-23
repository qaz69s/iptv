@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.streamdeck.player.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.decodeToSequence
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.QueryMap
import retrofit2.http.Streaming
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Xtream Codes `player_api.php` (spec §4.1 / §4.6). Responses are streamed so 50k-entry lists never sit in memory at once. */
interface XtreamService {
    @Streaming
    @GET("player_api.php")
    suspend fun call(@QueryMap params: Map<String, String>): ResponseBody

    @GET("player_api.php")
    suspend fun element(@QueryMap params: Map<String, String>): JsonElement
}

data class XtreamAccount(val server: String, val username: String, val password: String, val allowCleartext: Boolean) {
    /** `http(s)://host[:port]` without trailing slash or `player_api.php`. */
    val base: String = XtreamUrls.normalizeServer(server)
}

class XtreamAuthException(val reason: String) : IOException("XTREAM_AUTH: $reason")

object XtreamUrls {
    fun normalizeServer(raw: String): String {
        var s = raw.trim().removeSuffix("/")
        s = s.removeSuffix("/player_api.php").removeSuffix("/get.php").removeSuffix("/")
        if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) s = "http://$s"
        return s
    }

    fun live(a: XtreamAccount, streamId: Long, ext: String) = "${a.base}/live/${a.username}/${a.password}/$streamId.$ext"
    fun movie(a: XtreamAccount, streamId: Long, ext: String) = "${a.base}/movie/${a.username}/${a.password}/$streamId.$ext"
    fun episode(a: XtreamAccount, episodeId: Long, ext: String) = "${a.base}/series/${a.username}/${a.password}/$episodeId.$ext"
    fun xmltv(a: XtreamAccount) = "${a.base}/xmltv.php?username=${enc(a.username)}&password=${enc(a.password)}"

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
}

@Singleton
class XtreamClient @Inject constructor(private val http: OkHttpClient) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    private fun service(a: XtreamAccount): XtreamService = Retrofit.Builder()
        .baseUrl(a.base + "/")
        .client(if (a.allowCleartext) http.allowingCleartext() else http)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(XtreamService::class.java)

    private fun params(a: XtreamAccount, action: String?, vararg extra: Pair<String, String>) = buildMap {
        put("username", a.username)
        put("password", a.password)
        action?.let { put("action", it) }
        extra.forEach { (k, v) -> put(k, v) }
    }

    suspend fun authenticate(a: XtreamAccount): XtreamAuth = withContext(Dispatchers.IO) {
        val e = wrap { service(a).element(params(a, null)) }
        val auth = XtreamParsers.auth(e)
        if (!auth.authorized) throw XtreamAuthException(auth.status ?: "unauthorized")
        auth
    }

    suspend fun liveCategories(a: XtreamAccount) = list(a, "get_live_categories") { XtreamParsers.category(it) }
    suspend fun vodCategories(a: XtreamAccount) = list(a, "get_vod_categories") { XtreamParsers.category(it) }
    suspend fun seriesCategories(a: XtreamAccount) = list(a, "get_series_categories") { XtreamParsers.category(it) }

    /** Streams `get_live_streams` element by element into [onItem]. */
    suspend fun liveStreams(a: XtreamAccount, onItem: (XtreamLiveStream) -> Unit) = each(a, "get_live_streams") { XtreamParsers.live(it)?.let(onItem) }
    suspend fun vodStreams(a: XtreamAccount, onItem: (XtreamVod) -> Unit) = each(a, "get_vod_streams") { XtreamParsers.vod(it)?.let(onItem) }
    suspend fun series(a: XtreamAccount, onItem: (XtreamSeries) -> Unit) = each(a, "get_series") { XtreamParsers.series(it)?.let(onItem) }

    suspend fun seriesInfo(a: XtreamAccount, seriesId: Long): XtreamSeriesInfo = withContext(Dispatchers.IO) {
        XtreamParsers.seriesInfo(wrap { service(a).element(params(a, "get_series_info", "series_id" to seriesId.toString())) })
    }

    suspend fun vodInfo(a: XtreamAccount, vodId: Long): XtreamVodInfo = withContext(Dispatchers.IO) {
        XtreamParsers.vodInfo(wrap { service(a).element(params(a, "get_vod_info", "vod_id" to vodId.toString())) })
    }

    private suspend fun <T> list(a: XtreamAccount, action: String, map: (JsonElement) -> T?): List<T> {
        val out = ArrayList<T>()
        each(a, action) { e -> map(e)?.let(out::add) }
        return out
    }

    private suspend fun each(a: XtreamAccount, action: String, onElement: (JsonElement) -> Unit): Int = withContext(Dispatchers.IO) {
        val body = wrap { service(a).call(params(a, action)) }
        var n = 0
        body.use { b -> elements(b.byteStream()) { onElement(it); n++ } }
        n
    }

    /** Some panels return `{}` or an object keyed by id instead of an empty array; accept all three shapes. */
    internal fun elements(input: InputStream, onElement: (JsonElement) -> Unit) {
        val s = BufferedInputStream(input)
        s.mark(64)
        var c: Int
        do { c = s.read() } while (c != -1 && Character.isWhitespace(c))
        s.reset()
        if (c == '['.code) {
            json.decodeToSequence(s, JsonElement.serializer(), DecodeSequenceMode.ARRAY_WRAPPED).forEach(onElement)
        } else if (c != -1) {
            when (val e = json.decodeFromStream(JsonElement.serializer(), s)) {
                is JsonArray -> e.forEach(onElement)
                is JsonObject -> e.values.forEach(onElement)
                else -> Unit
            }
        }
    }

    private suspend fun <T> wrap(block: suspend () -> T): T = try {
        block()
    } catch (e: HttpException) {
        throw IOException("HTTP ${e.code()}")
    }
}
