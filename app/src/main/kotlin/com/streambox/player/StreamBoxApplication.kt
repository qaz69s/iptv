package com.streambox.player

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.streambox.player.core.network.CleartextHeaderCallFactory
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import okhttp3.OkHttpClient

@HiltAndroidApp
class StreamBoxApplication :
    Application(),
    SingletonImageLoader.Factory {
    /** Shared client from core:network (connection pool, timeouts, cleartext guard). Lazy: built on first image. */
    @Inject
    lateinit var okHttpClient: Lazy<OkHttpClient>

    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        // K1: logo requests carry their playlist's cleartext choice (header -> guard policy, every hop).
        .components {
            add(OkHttpNetworkFetcherFactory(callFactory = { CleartextHeaderCallFactory(okHttpClient.get()) }))
        }
        .crossfade(true)
        .build()
}
