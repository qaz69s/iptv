package com.streambox.player.core.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import com.streambox.player.core.common.result.AppError
import com.streambox.player.core.common.result.AppException
import com.streambox.player.core.model.PlaybackError
import com.streambox.player.core.model.PlaybackItem
import com.streambox.player.core.network.CleartextGuardInterceptor
import com.streambox.player.core.network.PlaylistDownloader
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import okhttp3.OkHttpClient

@Singleton
internal class Media3PlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
) : PlaybackController {
    private val _player = MutableStateFlow<ExoPlayer?>(null)
    override val player: StateFlow<Player?> = _player.asStateFlow()

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var currentType: StreamType = StreamType.PROGRESSIVE
    private var triedHlsFallback = false

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.update { it.copy(isBuffering = playbackState == Player.STATE_BUFFERING) }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying, error = if (isPlaying) null else it.error) }
        }

        override fun onPlayerError(error: PlaybackException) = handleError(error)
    }

    override fun play(item: PlaybackItem) {
        triedHlsFallback = false
        _state.value = PlaybackState(item = item, isBuffering = true)
        if (item.streamUrl.startsWith("http://", ignoreCase = true) && !item.allowCleartext) {
            obtainPlayer().stop()
            _state.value = PlaybackState(item = item, error = PlaybackError.CleartextNotAllowed)
            return
        }
        start(item, StreamTypeResolver.resolve(item.streamUrl))
    }

    override fun retry() {
        val item = _state.value.item ?: return
        play(item)
    }

    override fun release() {
        _player.value?.let {
            it.removeListener(listener)
            it.release()
        }
        _player.value = null
        _state.value = PlaybackState()
    }

    private fun start(item: PlaybackItem, type: StreamType) {
        currentType = type
        val player = obtainPlayer()
        player.setMediaSource(buildMediaSource(item, type))
        player.prepare()
        player.playWhenReady = true
    }

    private fun obtainPlayer(): ExoPlayer = _player.value ?: ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
            true,
        )
        .setHandleAudioBecomingNoisy(true)
        .build()
        .also {
            it.addListener(listener)
            _player.value = it
        }

    private fun buildMediaSource(item: PlaybackItem, type: StreamType): MediaSource {
        // Per-playlist cleartext guard on every hop (redirects included); certificates stay validated.
        val client = okHttpClient.newBuilder()
            .addNetworkInterceptor(CleartextGuardInterceptor(defaultAllowed = item.allowCleartext))
            .build()
        val userAgent = item.userAgent ?: PlaylistDownloader.DEFAULT_USER_AGENT
        val httpFactory = OkHttpDataSource.Factory(client)
            .setUserAgent(userAgent)
            .setDefaultRequestProperties(item.referrer?.let { mapOf("Referer" to it) } ?: emptyMap())
        // DefaultDataSource adds udp://, file://, content:// and asset support around the HTTP factory.
        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
        val mediaItem = MediaItem.Builder()
            .setUri(item.streamUrl)
            .setMimeType(
                when (type) {
                    StreamType.HLS -> MimeTypes.APPLICATION_M3U8
                    StreamType.DASH -> MimeTypes.APPLICATION_MPD
                    else -> null
                },
            )
            .build()
        return when (type) {
            StreamType.HLS -> HlsMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)

            StreamType.DASH -> DashMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)

            StreamType.RTSP -> RtspMediaSource.Factory().setUserAgent(userAgent).createMediaSource(mediaItem)

            StreamType.PROGRESSIVE -> ProgressiveMediaSource.Factory(
                dataSourceFactory,
                DefaultExtractorsFactory().setTsExtractorFlags(
                    DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                        DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS,
                ),
            ).createMediaSource(mediaItem)
        }
    }

    private fun handleError(error: PlaybackException) {
        val item = _state.value.item
        val player = _player.value
        when {
            error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW && player != null -> {
                player.seekToDefaultPosition()
                player.prepare()
            }

            item != null && currentType == StreamType.PROGRESSIVE && !triedHlsFallback &&
                error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> {
                // Extension-less HLS URL: retry once as HLS.
                triedHlsFallback = true
                start(item, StreamType.HLS)
            }

            else -> _state.update {
                it.copy(
                    isBuffering = false,
                    isPlaying = false,
                    error = PlaybackErrorMapper.map(
                        errorCode = error.errorCode,
                        httpStatus = error.httpStatus(),
                        cleartextBlocked = error.isCleartextBlocked(),
                        detail = error.errorCodeName,
                    ),
                )
            }
        }
    }

    private fun Throwable.causes(): Sequence<Throwable> = generateSequence(this) { it.cause }

    private fun PlaybackException.httpStatus(): Int? =
        causes().filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()?.responseCode

    private fun PlaybackException.isCleartextBlocked(): Boolean =
        causes().any { it is AppException && it.error == AppError.CleartextNotAllowed }
}
