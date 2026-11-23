package com.streamdeck.player.core.network

import android.util.Log
import com.streamdeck.player.core.common.redactSecrets
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

object HttpDefaults {
    const val USER_AGENT = "StreamDeckPlayer/1.0"
}

/** Request tag: the owning playlist allows plain HTTP. */
object CleartextAllowed

class CleartextBlockedException(url: String) : IOException("CLEARTEXT_BLOCKED: ${url.redactSecrets()}")

/**
 * HTTPS by default (spec §5): plain-HTTP requests are refused unless tagged with [CleartextAllowed].
 * The OS-level network security config permits cleartext so that this per-playlist switch can work;
 * certificate validation is never relaxed.
 */
class CleartextGuardInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val req = chain.request()
        if (!req.url.isHttps && req.tag(CleartextAllowed::class.java) == null) throw CleartextBlockedException(req.url.toString())
        return chain.proceed(req)
    }
}

/** Adds the [CleartextAllowed] tag to every request of a client (used for player data sources of opted-in playlists). */
class AllowCleartextInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response =
        chain.proceed(chain.request().newBuilder().tag(CleartextAllowed::class.java, CleartextAllowed).build())
}

/** Logs method, redacted URL and status only — never credentials (spec §5). Enable with `adb shell setprop log.tag.SDHttp DEBUG`. */
internal class RedactingLogInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val req = chain.request()
        val resp = chain.proceed(req)
        if (Log.isLoggable(TAG, Log.DEBUG)) Log.d(TAG, "${req.method} ${req.url.toString().redactSecrets()} -> ${resp.code}")
        return resp
    }

    private companion object { const val TAG = "SDHttp" }
}

/** Builds a client that may use plain HTTP: the tagging interceptor must run before the guard. */
fun OkHttpClient.allowingCleartext(): OkHttpClient = newBuilder().apply { interceptors().add(0, AllowCleartextInterceptor()) }.build()

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    /** gzip and redirects are handled by OkHttp. Certificate validation is never disabled. */
    @Provides
    @Singleton
    fun okHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor(CleartextGuardInterceptor())
        .addInterceptor(RedactingLogInterceptor())
        .build()
}
