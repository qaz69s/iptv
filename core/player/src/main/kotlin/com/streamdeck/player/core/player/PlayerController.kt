@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.streamdeck.player.core.player

import android.content.Context
import android.net.Uri
import android.net.wifi.WifiManager
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.streamdeck.player.core.common.ReconnectPolicy
import com.streamdeck.player.core.common.ReconnectSession
import com.streamdeck.player.core.datastore.BufferProfile
import com.streamdeck.player.core.datastore.DecoderMode
import com.streamdeck.player.core.datastore.SettingsRepository
import com.streamdeck.player.core.network.HttpDefaults
import com.streamdeck.player.core.network.allowingCleartext
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

internal data class PlayerConfig(
    val decoder: DecoderMode = DecoderMode.HARDWARE_PREFERRED,
    val buffer: BufferProfile = BufferProfile.BALANCED,
)

/**
 * App-scoped player owner (spec §8.8): the ExoPlayer instance survives rotation and Activity recreation and is shared
 * with [PlaybackService] for background audio. Hardware decoders are preferred, with FFmpeg as automatic fallback;
 * “software only” mode restricts MediaCodec to software codecs and prefers the FFmpeg extension.
 */
@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttp: OkHttpClient,
    settings: SettingsRepository,
    private val network: NetworkMonitor,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _player = MutableStateFlow<ExoPlayer?>(null)

    /** Current player instance; may change when decoder or buffer settings change. */
    val playerFlow: StateFlow<ExoPlayer?> = _player.asStateFlow()

    private val _current = MutableStateFlow<StreamRequest?>(null)
    val current: StateFlow<StreamRequest?> = _current.asStateFlow()

    private var config = PlayerConfig()
    private var builtWith: PlayerConfig? = null
    private var allowMetered = true
    private var reconnect = ReconnectSession(ReconnectPolicy())
    private var retryJob: Job? = null
    private var httpFactory: OkHttpDataSource.Factory = OkHttpDataSource.Factory(okHttp).setUserAgent(HttpDefaults.USER_AGENT)
    private var multicastLock: WifiManager.MulticastLock? = null
    private val cleartextClient: OkHttpClient by lazy { okHttp.allowingCleartext() }

    init {
        scope.launch {
            settings.settings.map { Triple(PlayerConfig(it.decoder, it.buffer), it.reconnectMax, it.allowMetered) }
                .distinctUntilChanged()
                .collect { (cfg, max, metered) ->
                    config = cfg
                    allowMetered = metered
                    if (reconnect.policy.maxAttempts != max) reconnect = ReconnectSession(ReconnectPolicy(maxAttempts = max))
                }
        }
        scope.launch {
            network.online.collect { online -> if (online && _state.value is PlaybackState.Reconnecting) retryNow() }
        }
    }

    /** Returns the player, (re)building it if settings that require a new instance have changed. */
    fun obtainPlayer(): ExoPlayer {
        val existing = _player.value
        if (existing != null && builtWith == config) return existing
        val position = existing?.currentPosition
        val item = existing?.currentMediaItem
        existing?.release()
        val created = build(config)
        builtWith = config
        _player.value = created
        if (item != null) created.setMediaItem(item, position ?: C.TIME_UNSET)
        return created
    }

    private fun build(cfg: PlayerConfig): ExoPlayer {
        val renderers = DefaultRenderersFactory(context).setEnableDecoderFallback(true).apply {
            if (cfg.decoder == DecoderMode.SOFTWARE_ONLY) {
                setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
                setMediaCodecSelector(SoftwareOnlyCodecSelector)
            } else {
                setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            }
        }
        // Low-RAM devices (1 GB boxes) never use the large “stable” buffer (spec §5 降级策略).
        val lowRam = context.getSystemService(android.app.ActivityManager::class.java)?.isLowRamDevice == true
        val b = (if (lowRam && cfg.buffer == BufferProfile.STABLE) BufferProfile.BALANCED else cfg.buffer).durations()
        val load = DefaultLoadControl.Builder()
            .setBufferDurationsMs(b.minMs, b.maxMs, b.startMs, b.rebufferMs)
            .build()
        val upstream = DataSource.Factory { httpFactory.createDataSource() }
        val dataSource = DefaultDataSource.Factory(context, upstream)
        val audio = AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build()
        return ExoPlayer.Builder(context, renderers)
            .setLoadControl(load)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setAudioAttributes(audio, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
            .apply { addListener(listener) }
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) reconnect.reset()
            publish()
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) = publish()
        override fun onPlayerError(error: PlaybackException) = handleError(error)
    }

    private fun publish() {
        val p = _player.value ?: return
        if (_state.value is PlaybackState.Reconnecting && p.playbackState == Player.STATE_IDLE) return
        _state.value = when {
            p.playerError != null -> _state.value
            p.playbackState == Player.STATE_BUFFERING -> PlaybackState.Buffering
            p.playbackState == Player.STATE_ENDED -> PlaybackState.Ended
            p.isPlaying -> PlaybackState.Playing
            p.playbackState == Player.STATE_IDLE -> PlaybackState.Idle
            else -> PlaybackState.Paused
        }
    }

    fun play(req: StreamRequest) {
        retryJob?.cancel()
        reconnect.reset()
        _current.value = req
        if (!allowMetered && network.isMetered()) {
            _player.value?.stop()
            _state.value = PlaybackState.Error(PlaybackError.MeteredBlocked)
            return
        }
        if (!req.allowCleartext && req.url.startsWith("http://", ignoreCase = true)) {
            _player.value?.stop()
            _state.value = PlaybackState.Error(PlaybackError.CleartextBlocked)
            return
        }
        updateMulticastLock(req.url)
        val headers = buildMap {
            putAll(req.headers)
            req.referrer?.let { put("Referer", it) }
        }
        // Per-channel User-Agent / Referer / headers (spec §8.4).
        httpFactory = OkHttpDataSource.Factory(if (req.allowCleartext) cleartextClient else okHttp)
            .setUserAgent(req.userAgent ?: HttpDefaults.USER_AGENT)
            .setDefaultRequestProperties(headers)
        val p = obtainPlayer()
        p.setMediaItem(req.toMediaItem(), if (req.startPositionMs > 0) req.startPositionMs else C.TIME_UNSET)
        p.prepare()
        p.playWhenReady = true
    }

    /** Re-opens the current stream with an extra side-loaded subtitle (SRT/VTT/SSA), keeping the position. */
    fun addExternalSubtitle(sub: ExternalSubtitle) {
        val req = _current.value ?: return
        val p = _player.value ?: return
        val pos = if (req.isLive) 0L else p.currentPosition
        play(req.copy(externalSubtitles = req.externalSubtitles + sub, startPositionMs = pos))
    }

    fun retry() { _current.value?.let(::play) }

    /** Lets callers surface failures that happen before a stream can be opened (e.g. catch-up URL errors). */
    fun reportError(error: PlaybackError) {
        _player.value?.stop()
        _state.value = PlaybackState.Error(error)
    }
    fun togglePause() { _player.value?.let { it.playWhenReady = !it.playWhenReady } }
    fun pause() { _player.value?.pause() }
    fun resume() { _player.value?.play() }

    fun seekBy(deltaMs: Long) {
        val p = _player.value ?: return
        if (!p.isCurrentMediaItemSeekable) return
        val dur = p.duration
        val target = (p.currentPosition + deltaMs).coerceAtLeast(0)
        p.seekTo(if (dur != C.TIME_UNSET) target.coerceAtMost(dur) else target)
    }

    fun seekTo(positionMs: Long) { _player.value?.seekTo(positionMs.coerceAtLeast(0)) }

    fun stop() {
        retryJob?.cancel()
        _player.value?.stop()
        _player.value?.clearMediaItems()
        _current.value = null
        _state.value = PlaybackState.Idle
        updateMulticastLock(null)
    }

    private fun handleError(e: PlaybackException) {
        val p = _player.value ?: return
        if (e.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
            p.seekToDefaultPosition()
            p.prepare()
            return
        }
        val err = classify(e)
        if (err.isRecoverable()) {
            val wait = reconnect.next()
            if (wait != null) {
                _state.value = PlaybackState.Reconnecting(reconnect.attempts, reconnect.policy.maxAttempts, wait)
                retryJob?.cancel()
                retryJob = scope.launch {
                    delay(wait)
                    retryNow()
                }
                return
            }
        }
        _state.value = PlaybackState.Error(err)
    }

    private fun retryNow() {
        retryJob?.cancel()
        val p = _player.value ?: return
        p.prepare()
        p.playWhenReady = true
    }

    private fun updateMulticastLock(url: String?) {
        val needs = url != null && (url.startsWith("udp://", true) || url.startsWith("rtp://", true))
        if (needs && multicastLock == null) {
            val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
            multicastLock = wifi?.createMulticastLock("streamdeck-multicast")?.apply { setReferenceCounted(false); acquire() }
        } else if (!needs) {
            multicastLock?.release()
            multicastLock = null
        }
    }

    private fun StreamRequest.toMediaItem(): MediaItem {
        val meta = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(subtitle)
            .setArtworkUri(artworkUrl?.let(Uri::parse))
            .build()
        val subs = externalSubtitles.map {
            MediaItem.SubtitleConfiguration.Builder(Uri.parse(it.uri))
                .setMimeType(it.mimeType)
                .setLabel(it.label)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
        }
        val lower = url.lowercase()
        val mime = when {
            ".m3u8" in lower -> MimeTypes.APPLICATION_M3U8
            ".mpd" in lower -> MimeTypes.APPLICATION_MPD
            else -> null
        }
        return MediaItem.Builder()
            .setUri(Uri.parse(url))
            .setMimeType(mime)
            .setMediaMetadata(meta)
            .setSubtitleConfigurations(subs)
            .build()
    }

    companion object {
        internal fun classify(e: PlaybackException): PlaybackError {
            var cause: Throwable? = e.cause
            while (cause != null) {
                if (cause is HttpDataSource.InvalidResponseCodeException) return PlaybackError.Http(cause.responseCode)
                if (cause.message?.contains(CLEARTEXT_MARKER) == true) return PlaybackError.CleartextBlocked
                cause = cause.cause
            }
            return when (e.errorCode) {
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> PlaybackError.Timeout
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> PlaybackError.Network
                PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED -> PlaybackError.CleartextBlocked
                in DECODER_ERRORS -> PlaybackError.Decoder(e.errorCodeName)
                else -> PlaybackError.Other(e.errorCodeName)
            }
        }

        /** Message used by the network layer when a cleartext request is refused. */
        const val CLEARTEXT_MARKER = "CLEARTEXT_BLOCKED"

        private val DECODER_ERRORS = setOf(
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        )
    }
}

internal fun PlaybackError.isRecoverable(): Boolean = when (this) {
    PlaybackError.Network, PlaybackError.Timeout -> true
    is PlaybackError.Http -> code >= 500 || code == 408 || code == 429
    is PlaybackError.Other -> detail.startsWith("ERROR_CODE_IO")
    else -> false
}

internal data class BufferDurations(val minMs: Int, val maxMs: Int, val startMs: Int, val rebufferMs: Int)

internal fun BufferProfile.durations(): BufferDurations = when (this) {
    BufferProfile.LOW_LATENCY -> BufferDurations(2_000, 10_000, 500, 1_000)
    BufferProfile.BALANCED -> BufferDurations(15_000, 50_000, 2_500, 5_000)
    BufferProfile.STABLE -> BufferDurations(30_000, 120_000, 5_000, 10_000)
}

/** Restricts MediaCodec to software implementations for the “仅软件解码” switch. */
internal val SoftwareOnlyCodecSelector = MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
    MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder).filter { it.softwareOnly }
}
