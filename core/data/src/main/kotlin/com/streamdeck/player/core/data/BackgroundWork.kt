package com.streamdeck.player.core.data

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.streamdeck.player.core.database.PlaylistDao
import com.streamdeck.player.core.database.PlaylistEntity
import com.streamdeck.player.core.datastore.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Refreshes the XMLTV data of every enabled playlist that has an EPG source (spec §4.4). */
@HiltWorker
class EpgUpdateWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val playlists: PlaylistDao,
    private val epg: EpgRepository,
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        var transientFailure = false
        playlists.all().filter { it.enabled && !it.epgUrl.isNullOrBlank() }.forEach { p ->
            try {
                epg.refresh(p.id)
            } catch (e: IOException) {
                transientFailure = true
            }
        }
        epg.cleanup()
        return if (transientFailure && runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
    }

    companion object { const val MAX_RETRIES = 3 }
}

/**
 * Re-imports one playlist (and its EPG) in the background (spec §4.1 自动更新). Failures are retried with
 * exponential back-off; user favorites, edits and order survive because they are re-applied after import.
 */
@HiltWorker
class PlaylistUpdateWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val playlists: PlaylistRepository,
    private val epg: EpgRepository,
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_ID, -1)
        val p = playlists.get(id) ?: return Result.success()
        if (!p.enabled) return Result.success()
        return try {
            playlists.import(id) { }
            if (!p.epgUrl.isNullOrBlank()) runCatching { epg.refresh(id) }
            Result.success()
        } catch (e: IOException) {
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val KEY_ID = "playlistId"
        const val MAX_RETRIES = 5
    }
}

@Singleton
class BackgroundWork @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val settings: SettingsRepository,
) {
    private val wm get() = WorkManager.getInstance(ctx)

    private suspend fun unmeteredOnly(): Boolean = !settings.current().allowMetered

    fun scheduleEpg(hours: Int, unmeteredOnly: Boolean) {
        val req = PeriodicWorkRequestBuilder<EpgUpdateWorker>(hours.coerceAtLeast(1).toLong(), TimeUnit.HOURS)
            .setConstraints(constraints(unmeteredOnly))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MIN, TimeUnit.MINUTES)
            .build()
        wm.enqueueUniquePeriodicWork(EPG_WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
    }

    /** Applies the playlist's update policy: periodic work for INTERVAL, nothing otherwise. */
    suspend fun schedulePlaylist(p: PlaylistEntity) {
        if (p.updateMode == PlaylistEntity.UPDATE_INTERVAL && p.enabled) {
            val req = PeriodicWorkRequestBuilder<PlaylistUpdateWorker>(p.updateIntervalHours.coerceIn(1, 168).toLong(), TimeUnit.HOURS)
                .setInputData(workDataOf(PlaylistUpdateWorker.KEY_ID to p.id))
                .setConstraints(constraints(unmeteredOnly()))
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MIN, TimeUnit.MINUTES)
                .build()
            wm.enqueueUniquePeriodicWork(periodicName(p.id), ExistingPeriodicWorkPolicy.UPDATE, req)
        } else {
            wm.cancelUniqueWork(periodicName(p.id))
        }
    }

    /** “每次启动” policy: one immediate refresh per app start. */
    suspend fun refreshOnStart(p: PlaylistEntity) {
        val req = OneTimeWorkRequestBuilder<PlaylistUpdateWorker>()
            .setInputData(workDataOf(PlaylistUpdateWorker.KEY_ID to p.id))
            .setConstraints(constraints(unmeteredOnly()))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_MIN, TimeUnit.MINUTES)
            .build()
        wm.enqueueUniqueWork("playlist-start-${p.id}", ExistingWorkPolicy.KEEP, req)
    }

    fun cancelPlaylist(id: Long) {
        wm.cancelUniqueWork(periodicName(id))
        wm.cancelUniqueWork("playlist-start-$id")
    }

    private fun periodicName(id: Long) = "playlist-periodic-$id"

    internal fun constraints(unmeteredOnly: Boolean) = Constraints.Builder()
        .setRequiredNetworkType(if (unmeteredOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .build()

    companion object {
        private const val EPG_WORK = "epg-update"
        internal const val BACKOFF_MIN = 10L
    }
}
