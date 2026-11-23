@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.streamdeck.player.core.player

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Exposes the shared player through a MediaSession: media notification, lock-screen, headset and Bluetooth controls,
 * and keeps audio running in the background as a foreground service (spec §4.3). The player is owned by
 * [PlayerController]; this service never releases it.
 */
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {
    @Inject lateinit var controller: PlayerController
    private var session: MediaSession? = null
    private val scope = MainScope()

    override fun onCreate() {
        super.onCreate()
        val player = controller.obtainPlayer()
        val builder = MediaSession.Builder(this, player)
        launchIntent()?.let(builder::setSessionActivity)
        session = builder.build()
        scope.launch {
            controller.playerFlow.filterNotNull().collect { p -> session?.let { if (it.player !== p) it.setPlayer(p) } }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) {
            controller.stop()
            stopSelf()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        session?.release()
        session = null
        super.onDestroy()
    }

    private fun launchIntent(): PendingIntent? {
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }
}
