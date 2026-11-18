package com.streambox.player.core.player

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.common.Tracks
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleExtractor
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import com.streambox.player.core.common.net.CleartextImagePolicy
import com.streambox.player.core.common.result.AppError
import com.streambox.player.core.common.result.AppException
import com.streambox.player.core.model.BufferProfile
import com.streambox.player.core.model.PlaybackError
import com.streambox.player.core.model.PlaybackItem
import com.streambox.player.core.model.PlaybackSettings
import com.streambox.player.core.network.CleartextGuardInterceptor
import com.streambox.player.core.network.PlaylistDownloader
import com.streambox.player.core.player.audio.AudioSupport
import com.streambox.player.core.player.audio.AudioTrackFormat
import com.streambox.player.core.player.audio.StreamBoxRenderersFactory
import com.streambox.player.core.player.audio.audioFriendlyTrackSelector
import com.streambox.player.core.player.audio.audioTrackFormats
import com.streambox.player.core.player.audio.toInfo
import com.streambox.player.core.player.reconnect.FailureKind
import com.streambox.player.core.player.reconnect.ReconnectConfig
import com.streambox.player.core.player.reconnect.ReconnectPolicy
import com.streambox.player.core.player.reconnect.Reconnector
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/**
 * Media3 implementation. Recoverable failures go through [Reconnector] (exponential backoff, immediate
 * retry when the network returns); decoder failures rebuild the player once with software decoders
 * first; buffer profile and decoder mode are fixed per ExoPlayer instance, so a change rebuilds it.
 * Audio MediaCodec cannot decode goes to the FFmpeg extension (K26); the codecs and decoders in use are
 * published as [StreamInfo] and logged (tag `StreamBoxPlayer`).
 */
@Singleton
@Suppress("TooManyFunctions")
internal class Media3PlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val networkMonitor: NetworkMonitor,
) : PlaybackController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _player = MutableStateFlow<ExoPlayer?>(null)
    override val player: StateFlow<Player?> = _player.asStateFlow()

    private val _state = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private var settings = PlaybackSettings()

    /** Buffer profile and decoder mode the current ExoPlayer was built with. */
    private var playerConfig: PlayerConfig? = null
    private var currentType: StreamType = StreamType.PROGRESSIVE
    private var triedHlsFallback = false
    private var decoderMode = DecoderMode.HARDWARE_FIRST
    private var fellBackToSoftware = false
    private val externalSubtitles = mutableListOf<MediaItem.SubtitleConfiguration>()
    private var networkJob: Job? = null
    private var inBackground = false
    private var volume = 1f

    /** Decoders the current player initialized (K26 stream info); reset per item and per rebuild. */
    private var videoDecoderName: String? = null
    private var audioDecoderName: String? = null

    override var channelNavigator: ChannelNavigator? = null

    private val reconnector = Reconnector(scope, policyFor(settings)) { reconnectNow() }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.update {
                it.copy(
                    isBuffering = playbackState == Player.STATE_BUFFERING || reconnector.status.value != null,
                    ended = false,
                )
            }
            if (playbackState == Player.STATE_ENDED) onEnded()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) reconnector.onPlaying()
            _state.update {
                it.copy(
                    isPlaying = isPlaying,
                    error = if (isPlaying) null else it.error,
                    reconnect = reconnector.status.value,
                )
            }
        }

        override fun onPlayerError(error: PlaybackException) = handleError(error)

        override fun onTracksChanged(tracks: Tracks) = publishTracks()

        override fun onTrackSelectionParametersChanged(parameters: TrackSelectionParameters) = publishTracks()

        override fun onEvents(player: Player, events: Player.Events) {
            val seekable = player.isCurrentMediaItemSeekable && !player.isCurrentMediaItemLive
            if (seekable != _state.value.isSeekable) _state.update { it.copy(isSeekable = seekable) }
        }
    }

    private val analyticsListener = object : AnalyticsListener {
        override fun onVideoDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            videoDecoderName = decoderName
            publishStreamInfo()
        }

        override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            audioDecoderName = decoderName
            publishStreamInfo()
        }
    }

    override fun play(item: PlaybackItem) {
        reconnector.reset()
        videoDecoderName = null
        audioDecoderName = null
        triedHlsFallback = false
        fellBackToSoftware = false
        decoderMode = DecoderFallback.initialMode(settings.softwareDecodingOnly)
        externalSubtitles.clear()
        _player.value?.let { it.trackSelectionParameters = it.trackSelectionParameters.defaultsForNewItem() }
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

    override fun togglePlayPause() {
        val player = _player.value
        if (player == null || player.playbackState == Player.STATE_IDLE || _state.value.error != null) {
            retry()
            return
        }
        if (player.isPlaying) player.pause() else player.play()
    }

    override fun pause() {
        _player.value?.pause()
    }

    override fun resume() {
        val player = _player.value
        if (player == null || player.playbackState == Player.STATE_IDLE || _state.value.error != null) {
            retry()
            return
        }
        player.play()
    }

    override fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
        _player.value?.volume = this.volume
    }

    override fun release() {
        networkJob?.cancel()
        networkJob = null
        reconnector.reset()
        _player.value?.let {
            it.removeListener(listener)
            it.removeAnalyticsListener(analyticsListener)
            it.release()
        }
        videoDecoderName = null
        audioDecoderName = null
        _player.value = null
        playerConfig = null
        inBackground = false
        volume = 1f
        _state.value = PlaybackState()
    }

    override fun setInBackground(background: Boolean) {
        if (inBackground == background) return
        inBackground = background
        val player = _player.value ?: return
        player.applyBackgroundMode()
        if (!background && !player.playWhenReady && player.playbackState != Player.STATE_IDLE) player.play()
    }

    private fun ExoPlayer.applyBackgroundMode() {
        trackSelectionParameters = trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, inBackground)
            .build()
    }

    override fun applySettings(settings: PlaybackSettings) {
        this.settings = settings
        reconnector.updatePolicy(policyFor(settings))
    }

    override fun selectVideoTrack(id: TrackId?) = updateParameters { builder ->
        if (id == null) {
            builder.clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        } else {
            overrideFor(id)?.let(builder::setOverrideForType)
        }
    }

    override fun selectAudioTrack(id: TrackId) = updateParameters { builder ->
        overrideFor(id)?.let { builder.setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, false).setOverrideForType(it) }
    }

    override fun selectSubtitleTrack(id: TrackId?) = updateParameters { builder ->
        if (id == null) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            overrideFor(id)?.let { builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).setOverrideForType(it) }
        }
    }

    override fun addExternalSubtitle(uri: String, mimeType: String, label: String) {
        val item = _state.value.item ?: return
        externalSubtitles += MediaItem.SubtitleConfiguration.Builder(Uri.parse(uri))
            .setMimeType(mimeType)
            .setLabel(label)
            .setId("$EXTERNAL_SUBTITLE_PREFIX${externalSubtitles.size}")
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()
        val player = _player.value ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .build()
        start(item, currentType, startPositionMs = player.currentPosition.takeIf { _state.value.isSeekable })
    }

    override fun seekBy(offsetMs: Long) {
        val player = _player.value ?: return
        if (!player.isCurrentMediaItemSeekable) return
        val target = (player.currentPosition + offsetMs).coerceAtLeast(0)
        val duration = player.duration
        player.seekTo(if (duration != C.TIME_UNSET) target.coerceAtMost(duration) else target)
    }

    override fun adjustVolume(raise: Boolean) {
        val player = _player.value ?: return
        if (raise) {
            player.increaseDeviceVolume(C.VOLUME_FLAG_SHOW_UI)
        } else {
            player.decreaseDeviceVolume(C.VOLUME_FLAG_SHOW_UI)
        }
    }

    private fun start(item: PlaybackItem, type: StreamType, startPositionMs: Long? = null) {
        currentType = type
        val player = obtainPlayer()
        val source = buildMediaSource(item, type)
        if (startPositionMs != null) player.setMediaSource(source, startPositionMs) else player.setMediaSource(source)
        player.prepare()
        player.playWhenReady = true
    }

    /** Reconnect: rebuilds the source; live rejoins at the live edge, on-demand resumes where it stopped. */
    private fun reconnectNow() {
        val item = _state.value.item ?: return
        val position = _player.value?.currentPosition?.takeIf { _state.value.isSeekable }
        _state.update { it.copy(isBuffering = true, error = null, reconnect = reconnector.status.value) }
        start(item, currentType, position)
    }

    private fun obtainPlayer(): ExoPlayer {
        val wanted = PlayerConfig(settings.bufferProfile, decoderMode)
        val existing = _player.value
        if (existing != null && playerConfig == wanted) return existing
        existing?.let {
            it.removeListener(listener)
            it.removeAnalyticsListener(analyticsListener)
            it.release()
        }
        videoDecoderName = null
        audioDecoderName = null
        val buffer = BufferConfig.of(wanted.bufferProfile)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                buffer.minBufferMs,
                buffer.maxBufferMs,
                buffer.bufferForPlaybackMs,
                buffer.bufferForPlaybackAfterRebufferMs,
            )
            .build()
        // K26: FFmpeg decodes the audio MediaCodec cannot (behind it, or in front for software-first).
        val renderersFactory = StreamBoxRenderersFactory(context, wanted.decoderMode)
            .setEnableDecoderFallback(true)
            .setMediaCodecSelector(codecSelector(wanted.decoderMode))
        return ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(audioFriendlyTrackSelector(context))
            .setLoadControl(loadControl)
            .setDeviceVolumeControlEnabled(true)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
            .also {
                it.addListener(listener)
                it.addAnalyticsListener(analyticsListener)
                it.volume = volume
                if (inBackground) it.applyBackgroundMode()
                playerConfig = wanted
                _player.value = it
                watchNetwork()
            }
    }

    private fun watchNetwork() {
        if (networkJob?.isActive == true) return
        networkJob = scope.launch {
            var wasOnline: Boolean? = null
            networkMonitor.isOnline.collect { online ->
                if (online && wasOnline == false && reconnector.onNetworkAvailable()) {
                    _state.update {
                        it.copy(isBuffering = true, error = null, reconnect = reconnector.status.value)
                    }
                }
                wasOnline = online
            }
        }
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
        val builder = MediaItem.Builder()
            .setUri(item.streamUrl)
            // Shown by the media notification / Bluetooth displays during background playback.
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(item.name)
                    .setDisplayTitle(item.name)
                    // K1: the notification's artwork obeys the playlist's cleartext switch too.
                    .setArtworkUri(
                        item.logoUrl
                            ?.takeIf { CleartextImagePolicy.isAllowed(it, item.allowCleartext) }
                            ?.let(Uri::parse),
                    )
                    .build(),
            )
            .setMimeType(
                when (type) {
                    StreamType.HLS -> MimeTypes.APPLICATION_M3U8
                    StreamType.DASH -> MimeTypes.APPLICATION_MPD
                    else -> null
                },
            )
        BufferConfig.of(settings.bufferProfile).liveTargetOffsetMs?.let { offset ->
            builder.setLiveConfiguration(MediaItem.LiveConfiguration.Builder().setTargetOffsetMs(offset).build())
        }
        val mediaItem = builder.build()
        val main = when (type) {
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
        val subtitles = externalSubtitles.mapNotNull { subtitleSource(it, dataSourceFactory) }

        // Spread is fine here: a handful of side-loaded subtitle sources, built once per item.
        @Suppress("SpreadOperator")
        val merged = if (subtitles.isEmpty()) main else MergingMediaSource(main, *subtitles.toTypedArray())
        return merged
    }

    /** Same wiring DefaultMediaSourceFactory uses for side-loaded subtitles (parsed during extraction). */
    private fun subtitleSource(config: MediaItem.SubtitleConfiguration, factory: DataSource.Factory): MediaSource? {
        val format = Format.Builder()
            .setSampleMimeType(config.mimeType)
            .setLanguage(config.language)
            .setSelectionFlags(config.selectionFlags)
            .setRoleFlags(config.roleFlags)
            .setLabel(config.label)
            .setId(config.id)
            .build()
        val parserFactory = DefaultSubtitleParserFactory()
        if (!parserFactory.supportsFormat(format)) return null
        val extractors = ExtractorsFactory { arrayOf(SubtitleExtractor(parserFactory.create(format), format)) }
        return ProgressiveMediaSource.Factory(factory, extractors)
            .createMediaSource(MediaItem.fromUri(config.uri))
    }

    private fun onEnded() {
        val player = _player.value ?: return
        val live = player.isCurrentMediaItemLive || player.duration == C.TIME_UNSET
        if (!live) {
            // On-demand content finished: the screen decides whether to play the next channel.
            _state.update { it.copy(isBuffering = false, isPlaying = false, ended = true) }
            return
        }
        // A live stream must never "finish": the source closed the connection.
        if (reconnector.onFailure(FailureKind.SOURCE_ENDED)) {
            _state.update { it.copy(isBuffering = true, isPlaying = false, reconnect = reconnector.status.value) }
        } else {
            showError(PlaybackError.StreamEnded)
        }
    }

    private fun handleError(error: PlaybackException) {
        val item = _state.value.item ?: return
        if (currentType == StreamType.PROGRESSIVE && !triedHlsFallback &&
            error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
        ) {
            // Extension-less HLS URL: retry once as HLS.
            triedHlsFallback = true
            start(item, StreamType.HLS)
            return
        }
        val httpStatus = error.httpStatus()
        val cleartextBlocked = error.isCleartextBlocked()
        val kind = FailureClassifier.classify(error.errorCode, httpStatus, cleartextBlocked)
        val fallbackMode = DecoderFallback.next(kind, decoderMode, fellBackToSoftware)
        when {
            fallbackMode != null -> {
                fellBackToSoftware = true
                decoderMode = fallbackMode
                _state.update { it.copy(isBuffering = true, error = null, softwareDecoderFallback = true) }
                // obtainPlayer() sees the new decoder mode and rebuilds the ExoPlayer.
                start(item, currentType, _player.value?.currentPosition?.takeIf { _state.value.isSeekable })
            }

            reconnector.onFailure(kind) -> _state.update {
                it.copy(isBuffering = true, isPlaying = false, error = null, reconnect = reconnector.status.value)
            }

            else -> showError(
                PlaybackErrorMapper.map(
                    errorCode = error.errorCode,
                    httpStatus = httpStatus,
                    cleartextBlocked = cleartextBlocked,
                    detail = error.errorCodeName,
                ),
            )
        }
    }

    private fun showError(error: PlaybackError) = _state.update {
        it.copy(isBuffering = false, isPlaying = false, error = error, reconnect = null)
    }

    private fun publishTracks() {
        val player = _player.value ?: return
        val parameters = player.trackSelectionParameters
        val video = mutableListOf<VideoTrackOption>()
        val audio = mutableListOf<MediaTrackOption>()
        val text = mutableListOf<MediaTrackOption>()
        player.currentTracks.groups.forEachIndexed { groupIndex, group ->
            for (trackIndex in 0 until group.length) {
                if (!group.isTrackSupported(trackIndex)) continue
                val format = group.getTrackFormat(trackIndex)
                val id = TrackId(groupIndex, trackIndex)
                val selected = group.isTrackSelected(trackIndex)
                when (group.type) {
                    C.TRACK_TYPE_VIDEO -> video += VideoTrackOption(
                        id = id,
                        width = format.width,
                        height = format.height,
                        bitrate = format.bitrate.takeIf { it != Format.NO_VALUE },
                        selected = selected,
                    )

                    C.TRACK_TYPE_AUDIO -> audio += MediaTrackOption(id, format.label, format.language, selected)

                    C.TRACK_TYPE_TEXT -> text += MediaTrackOption(
                        id = id,
                        label = format.label,
                        language = format.language,
                        selected = selected,
                        external = format.id?.contains(EXTERNAL_SUBTITLE_PREFIX) == true,
                    )
                }
            }
        }
        _state.update {
            it.copy(
                tracks = PlaybackTracks(
                    video = video.sortedWith(
                        compareByDescending<VideoTrackOption> { v -> v.height }
                            .thenByDescending { v -> v.bitrate ?: 0 },
                    ),
                    videoAuto = parameters.overrides.values.none { o -> o.type == C.TRACK_TYPE_VIDEO },
                    audio = audio,
                    subtitles = text,
                    subtitlesOff = C.TRACK_TYPE_TEXT in parameters.disabledTrackTypes || text.none { t -> t.selected },
                ),
            )
        }
        val audioFormats = publishStreamInfo()
        if (AudioSupport.needsFallback(audioFormats)) {
            // K26: a manual choice points at audio this device cannot decode; let the selector pick.
            Log.i(LOG_TAG, "Selected audio track is not decodable; falling back to automatic selection")
            player.trackSelectionParameters = parameters.buildUpon().clearOverridesOfType(C.TRACK_TYPE_AUDIO).build()
        }
    }

    /**
     * Publishes the formats / decoders in use and whether any audio track is decodable (K26), and logs
     * each change. Returns the audio tracks it judged.
     */
    private fun publishStreamInfo(): List<AudioTrackFormat> {
        val player = _player.value ?: return emptyList()
        val audioFormats = audioTrackFormats(player.currentTracks)
        val info = StreamInfo(
            video = player.videoFormat?.toInfo(),
            videoDecoder = videoDecoderName,
            audio = player.audioFormat?.toInfo(),
            audioDecoder = audioDecoderName,
            audioStatus = AudioSupport.status(audioFormats),
        )
        if (info != _state.value.streamInfo) {
            Log.i(
                LOG_TAG,
                "video=${info.video?.describe()} [${info.videoDecoder}] " +
                    "audio=${info.audio?.describe()} [${info.audioDecoder}] status=${info.audioStatus}",
            )
            _state.update { it.copy(streamInfo = info) }
        }
        return audioFormats
    }

    private fun overrideFor(id: TrackId): TrackSelectionOverride? {
        val group = _player.value?.currentTracks?.groups?.getOrNull(id.group)
        return when {
            group == null -> null
            id.track !in 0 until group.length -> null
            else -> TrackSelectionOverride(group.mediaTrackGroup, id.track)
        }
    }

    private fun updateParameters(change: (TrackSelectionParameters.Builder) -> Unit) {
        val player = _player.value ?: return
        val builder = player.trackSelectionParameters.buildUpon()
        change(builder)
        player.trackSelectionParameters = builder.build()
    }

    private fun TrackSelectionParameters.defaultsForNewItem(): TrackSelectionParameters =
        buildUpon().clearOverrides().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false).build()

    private fun Throwable.causes(): Sequence<Throwable> = generateSequence(this) { it.cause }

    private fun PlaybackException.httpStatus(): Int? =
        causes().filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()?.responseCode

    private fun PlaybackException.isCleartextBlocked(): Boolean =
        causes().any { it is AppException && it.error == AppError.CleartextNotAllowed }

    private data class PlayerConfig(val bufferProfile: BufferProfile, val decoderMode: DecoderMode)

    private companion object {
        const val EXTERNAL_SUBTITLE_PREFIX = "streambox-ext-"
        const val LOG_TAG = "StreamBoxPlayer"

        fun policyFor(settings: PlaybackSettings) =
            ReconnectPolicy(ReconnectConfig(maxAttempts = settings.maxReconnectAttempts))

        fun codecSelector(mode: DecoderMode): MediaCodecSelector = when (mode) {
            DecoderMode.HARDWARE_FIRST -> MediaCodecSelector.DEFAULT

            DecoderMode.SOFTWARE_FIRST -> MediaCodecSelector { mimeType, secure, tunneling ->
                MediaCodecUtil.getDecoderInfosSortedBySoftwareOnly(
                    MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, secure, tunneling),
                )
            }
        }
    }
}
