package com.streamdeck.player.feature.cast

import android.content.Context
import android.net.Uri
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.images.WebImage
import com.streamdeck.player.core.network.HttpDefaults
import com.streamdeck.player.core.player.PlayerController
import com.streamdeck.player.core.player.StreamRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

sealed interface CastState {
    data object Unavailable : CastState
    data object Idle : CastState
    data class Connected(val deviceName: String, val playing: Boolean) : CastState
    /** The stream needs custom headers the Default Media Receiver cannot send (spec §4.8). */
    data object NotCastable : CastState
}

/**
 * Chromecast integration (spec §4.8): loads the current stream on the receiver when a session starts or resumes,
 * pauses the local player while casting and resumes it when the session ends.
 */
@Singleton
class CastController @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val player: PlayerController,
) {
    private val _state = MutableStateFlow<CastState>(CastState.Unavailable)
    val state: StateFlow<CastState> = _state.asStateFlow()

    private val castContext: CastContext? by lazy {
        val ok = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(ctx) == ConnectionResult.SUCCESS
        if (!ok) null else runCatching { CastContext.getSharedInstance(ctx) }.getOrNull()?.also { c ->
            c.sessionManager.addSessionManagerListener(listener, CastSession::class.java)
            _state.value = c.sessionManager.currentCastSession?.let { connected(it) } ?: CastState.Idle
        }
    }

    val isAvailable: Boolean get() = castContext != null

    private val session: CastSession? get() = castContext?.sessionManager?.currentCastSession

    private val listener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(s: CastSession, sessionId: String) = loadCurrent(s)
        override fun onSessionResumed(s: CastSession, wasSuspended: Boolean) {
            if (s.remoteMediaClient?.hasMediaSession() == true) _state.value = connected(s) else loadCurrent(s)
        }
        override fun onSessionEnded(s: CastSession, error: Int) = onDisconnected()
        override fun onSessionSuspended(s: CastSession, reason: Int) { _state.value = CastState.Idle }
        override fun onSessionStarting(s: CastSession) = Unit
        override fun onSessionStartFailed(s: CastSession, error: Int) { _state.value = CastState.Idle }
        override fun onSessionEnding(s: CastSession) = Unit
        override fun onSessionResuming(s: CastSession, sessionId: String) = Unit
        override fun onSessionResumeFailed(s: CastSession, error: Int) { _state.value = CastState.Idle }
    }

    /** Call once from the UI so the lazy CastContext is created on the main thread. */
    fun ensureInitialized() { castContext }

    private fun loadCurrent(s: CastSession) {
        val req = player.current.value
        if (req == null) { _state.value = connected(s); return }
        if (!isCastable(req)) {
            _state.value = CastState.NotCastable
            return
        }
        val meta = MediaMetadata(MediaMetadata.MEDIA_TYPE_GENERIC).apply {
            putString(MediaMetadata.KEY_TITLE, req.title)
            req.subtitle?.let { putString(MediaMetadata.KEY_SUBTITLE, it) }
            req.artworkUrl?.let { addImage(WebImage(Uri.parse(it))) }
        }
        val info = MediaInfo.Builder(req.url)
            .setStreamType(if (req.isLive) MediaInfo.STREAM_TYPE_LIVE else MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(mimeFor(req.url))
            .setMetadata(meta)
            .build()
        val position = if (req.isLive) 0L else player.playerFlow.value?.currentPosition ?: 0L
        s.remoteMediaClient?.load(MediaLoadRequestData.Builder().setMediaInfo(info).setAutoplay(true).setCurrentTime(position).build())
        player.pause()
        _state.value = connected(s)
    }

    private fun onDisconnected() {
        _state.value = if (castContext != null) CastState.Idle else CastState.Unavailable
        if (player.current.value != null) player.resume()
    }

    private fun connected(s: CastSession) = CastState.Connected(s.castDevice?.friendlyName.orEmpty(), s.remoteMediaClient?.isPlaying == true)

    fun togglePause() {
        val c = session?.remoteMediaClient ?: return
        if (c.isPlaying) c.pause() else c.play()
        session?.let { _state.value = CastState.Connected(it.castDevice?.friendlyName.orEmpty(), !c.isPlaying) }
    }

    fun adjustVolume(delta: Double) {
        val s = session ?: return
        runCatching { s.volume = (s.volume + delta).coerceIn(0.0, 1.0) }
    }

    fun stop() { castContext?.sessionManager?.endCurrentSession(true) }

    fun dismissNotCastable() { _state.value = CastState.Idle }

    companion object {
        /** Default Media Receiver cannot send per-request headers, so such streams are refused with an explanation. */
        fun isCastable(r: StreamRequest): Boolean =
            r.headers.isEmpty() && r.referrer == null && (r.userAgent == null || r.userAgent == HttpDefaults.USER_AGENT) && r.url.startsWith("http", true)

        fun mimeFor(url: String): String {
            val u = url.lowercase()
            return when {
                ".m3u8" in u -> "application/x-mpegURL"
                ".mpd" in u -> "application/dash+xml"
                ".mp4" in u -> "video/mp4"
                ".mkv" in u -> "video/x-matroska"
                else -> "video/mp2t"
            }
        }
    }
}
