package com.streambox.player.core.player.session

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.streambox.player.core.player.PlaybackController
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Background playback (requirements §4.3): a foreground service with Media3's media notification
 * (play / pause, previous / next channel), headset and Bluetooth buttons. Media3 promotes the service to
 * the foreground while playing; the player's own audio focus handling and "becoming noisy" pause apply.
 *
 * The session follows [PlaybackController.player]: a rebuilt ExoPlayer (buffer profile / decoder change,
 * software-decoder fallback) is swapped in with [MediaSession.setPlayer]; when the controller releases its
 * player the session is released and the service stops. Started by [start] from the player screen while
 * background playback is enabled.
 */
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {
    @Inject
    lateinit var playbackController: PlaybackController

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        scope.launch {
            playbackController.player.collect { player ->
                if (player == null) stopPlaybackService() else attach(player)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Swiping the task away ends playback: zapping needs the player screen, which is gone with the task. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        playbackController.release()
        stopPlaybackService()
    }

    override fun onDestroy() {
        scope.cancel()
        releaseSession()
        super.onDestroy()
    }

    private fun attach(player: Player) {
        val wrapped = ChannelNavigationPlayer(
            player = player,
            onZap = { direction -> playbackController.channelNavigator?.zap(direction) },
            onRelease = playbackController::release,
        )
        val current = session
        if (current != null) {
            current.player = wrapped
            return
        }
        val builder = MediaSession.Builder(this, wrapped).setId(SESSION_ID)
        sessionActivity()?.let(builder::setSessionActivity)
        session = builder.build().also(::addSession)
    }

    private fun stopPlaybackService() {
        releaseSession()
        stopSelf()
    }

    private fun releaseSession() {
        session?.let {
            removeSession(it)
            it.release()
        }
        session = null
    }

    /** Tapping the notification brings the app (singleTask, so the player screen) back. */
    private fun sessionActivity(): PendingIntent? {
        val launch = packageManager.getLaunchIntentForPackage(packageName)
            ?: packageManager.getLeanbackLaunchIntentForPackage(packageName)
            ?: return null
        return PendingIntent.getActivity(
            this,
            0,
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        private const val TAG = "PlaybackService"
        private const val SESSION_ID = "streambox-playback"

        /**
         * Starts the service while the app is in the foreground; Media3 moves it to the foreground with
         * the media notification once playback runs. Stops itself when the player is released.
         */
        fun start(context: Context) {
            try {
                context.startService(Intent(context, PlaybackService::class.java))
            } catch (e: IllegalStateException) {
                // Background start restrictions: only reachable if called while the app is not visible.
                Log.w(TAG, "Could not start the playback service", e)
            }
        }
    }
}
