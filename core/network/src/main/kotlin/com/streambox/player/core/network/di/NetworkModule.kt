package com.streambox.player.core.network.di

import com.streambox.player.core.network.CleartextGuardInterceptor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    private const val CONNECT_TIMEOUT_S = 15L
    private const val READ_TIMEOUT_S = 30L

    /**
     * Shared client (connection pool, dispatcher). Requests without a [com.streambox.player.core.network.CleartextPolicy]
     * tag are allowed through the guard; per-playlist callers tag their requests or derive a client
     * with their own guard via [OkHttpClient.newBuilder].
     */
    @Provides
    @Singleton
    fun providesOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addNetworkInterceptor(CleartextGuardInterceptor(defaultAllowed = true))
        .build()
}
