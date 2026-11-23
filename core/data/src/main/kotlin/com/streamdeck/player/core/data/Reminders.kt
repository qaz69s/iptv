package com.streamdeck.player.core.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.streamdeck.player.core.database.EpgDao
import com.streamdeck.player.core.database.ReminderEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** A request to open a channel coming from outside the navigation graph (reminder, notification). */
data class ChannelTarget(val playlistId: Long, val uniqueKey: String)

@Singleton
class DeepLinkBus @Inject constructor() {
    private val _targets = MutableSharedFlow<ChannelTarget>(extraBufferCapacity = 4)
    val targets: SharedFlow<ChannelTarget> = _targets.asSharedFlow()
    fun emit(t: ChannelTarget) { _targets.tryEmit(t) }

    companion object {
        const val EXTRA_PLAYLIST = "sd.extra.playlist"
        const val EXTRA_KEY = "sd.extra.key"

        fun fromIntent(intent: Intent?): ChannelTarget? {
            val pid = intent?.getLongExtra(EXTRA_PLAYLIST, -1L) ?: return null
            val key = intent.getStringExtra(EXTRA_KEY) ?: return null
            return if (pid >= 0) ChannelTarget(pid, key) else null
        }
    }
}

/** Programme reminders (spec §4.4): local notification at start time, optional automatic channel switch. */
@Singleton
class ReminderRepository @Inject constructor(@ApplicationContext private val ctx: Context, private val dao: EpgDao) {
    fun upcoming(): Flow<List<ReminderEntity>> = dao.upcomingReminders(System.currentTimeMillis())

    suspend fun find(playlistId: Long, uniqueKey: String, startAt: Long) = dao.findReminder(playlistId, uniqueKey, startAt)

    suspend fun add(playlistId: Long, uniqueKey: String, channelName: String, program: Program, autoSwitch: Boolean): Long {
        dao.findReminder(playlistId, uniqueKey, program.start)?.let { return it.id }
        val id = dao.insertReminder(ReminderEntity(0, playlistId, uniqueKey, channelName, program.title, program.start, autoSwitch))
        val delay = (program.start - System.currentTimeMillis()).coerceAtLeast(0)
        val req = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf(ReminderWorker.KEY_ID to id))
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(workName(id), ExistingWorkPolicy.REPLACE, req)
        return id
    }

    suspend fun remove(id: Long) {
        WorkManager.getInstance(ctx).cancelUniqueWork(workName(id))
        dao.deleteReminder(id)
    }

    private fun workName(id: Long) = "reminder-$id"
}

@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val dao: EpgDao,
    private val bus: DeepLinkBus,
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_ID, -1)
        val r = dao.reminder(id) ?: return Result.success()
        dao.deleteReminder(id)
        val target = ChannelTarget(r.playlistId, r.uniqueKey)
        val foreground = withContext(Dispatchers.Main) {
            ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }
        if (r.autoSwitch && foreground) bus.emit(target)
        notify(r)
        return Result.success()
    }

    private fun notify(r: ReminderEntity) {
        val ctx = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL, ctx.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_HIGH))
        }
        val launch = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.apply {
            putExtra(DeepLinkBus.EXTRA_PLAYLIST, r.playlistId)
            putExtra(DeepLinkBus.EXTRA_KEY, r.uniqueKey)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        } ?: return
        val pi = PendingIntent.getActivity(ctx, r.id.toInt(), launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(r.title)
            .setContentText(ctx.getString(R.string.reminder_text, r.channelName))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            NotificationManagerCompat.from(ctx).notify(NOTIFICATION_BASE + r.id.toInt(), n)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call; nothing else to do.
        }
    }

    companion object {
        const val KEY_ID = "id"
        private const val CHANNEL = "reminders"
        private const val NOTIFICATION_BASE = 10_000
    }
}
