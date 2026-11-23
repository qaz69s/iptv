package com.streamdeck.player

import android.app.ActivityManager
import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.streamdeck.player.core.data.BackgroundWork
import com.streamdeck.player.core.data.PlaylistRepository
import com.streamdeck.player.core.datastore.SettingsRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class StreamDeckApp : Application(), Configuration.Provider, ImageLoaderFactory {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var work: BackgroundWork
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var playlists: PlaylistRepository

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        appScope.launch { playlists.applyUpdatePolicies() }
        appScope.launch {
            settings.settings.map { it.epgUpdateHours to !it.allowMetered }.distinctUntilChanged().collect { (hours, unmetered) ->
                work.scheduleEpg(hours, unmetered)
            }
        }
    }

    /** Channel logos: bounded memory + disk cache, smaller on low-RAM TV boxes (spec §5 1GB 降级). */
    override fun newImageLoader(): ImageLoader {
        val lowRam = getSystemService(ActivityManager::class.java)?.isLowRamDevice == true
        return ImageLoader.Builder(this)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(if (lowRam) 0.08 else 0.15).build() }
            .diskCache { DiskCache.Builder().directory(cacheDir.resolve("logos")).maxSizeBytes(if (lowRam) 32L shl 20 else 128L shl 20).build() }
            .crossfade(!lowRam)
            .respectCacheHeaders(false)
            .build()
    }
}
