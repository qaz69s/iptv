package com.streambox.player.feature.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.streambox.player.core.domain.repository.ChannelRepository
import com.streambox.player.core.domain.repository.FavoritesRepository
import com.streambox.player.core.domain.repository.SettingsRepository
import com.streambox.player.core.domain.repository.WatchHistoryRepository
import com.streambox.player.core.domain.usecase.RecordPlaybackStartUseCase
import com.streambox.player.core.domain.usecase.ToggleFavoriteUseCase
import com.streambox.player.core.model.AspectRatioMode
import com.streambox.player.core.model.ChannelKey
import com.streambox.player.core.model.ChannelSortOrder
import com.streambox.player.core.model.ChannelSource
import com.streambox.player.core.model.PlaybackError
import com.streambox.player.core.model.PlaybackItem
import com.streambox.player.core.model.PlaybackSettings
import com.streambox.player.core.model.SubtitleStyle
import com.streambox.player.core.model.ZapDirection
import com.streambox.player.core.player.ChannelNavigator
import com.streambox.player.core.player.PlaybackController
import com.streambox.player.core.player.PlaybackState
import com.streambox.player.core.player.PlaybackTracks
import com.streambox.player.core.player.StreamInfo
import com.streambox.player.core.player.TrackId
import com.streambox.player.core.player.audio.AudioStatus
import com.streambox.player.core.player.reconnect.ReconnectStatus
import com.streambox.player.core.player.sleep.MonotonicClock
import com.streambox.player.core.player.sleep.SleepTimer
import com.streambox.player.core.player.sleep.SleepTimerOutput
import com.streambox.player.core.player.sleep.SleepTimerState
import com.streambox.player.feature.player.navigation.PlayerRoute
import com.streambox.player.feature.player.navigation.PlayerSource
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class PlayerUiState(
    /** The channel being shown; null until the first lookup finishes or when it no longer exists. */
    val channel: PlaybackItem? = null,
    val isBuffering: Boolean = true,
    val isPlaying: Boolean = false,
    val error: PlaybackError? = null,
    /** Channel info / controls. Always shown while there is an error so the reason stays readable. */
    val overlayVisible: Boolean = true,
    /** Non-null while an automatic reconnect runs: "Reconnecting (n/N)". */
    val reconnect: ReconnectStatus? = null,
    /** On-demand content: left / right seek instead of changing the volume. */
    val isSeekable: Boolean = false,
    val softwareDecoderFallback: Boolean = false,
    val tracks: PlaybackTracks = PlaybackTracks(),
    val aspectRatio: AspectRatioMode = AspectRatioMode.Fit,
    val subtitleStyle: SubtitleStyle = SubtitleStyle(),
    /** Tracks / aspect / subtitle panel (bottom sheet on touch, side panel on TV). */
    val panelOpen: Boolean = false,
    /** Digits typed on the remote, shown until the jump happens. */
    val numberInput: String? = null,
    /** A typed number that matched no channel; shown briefly. */
    val numberNotFound: Int? = null,
    /** Setting: keep the audio playing (media notification) when the app goes to the background. */
    val backgroundPlayback: Boolean = true,
    /** The activity is in picture-in-picture: only the video is shown, without overlay or panel. */
    val pictureInPicture: Boolean = false,
    /** Setting: shrink into a picture-in-picture window when leaving the player with Home. */
    val pictureInPictureEnabled: Boolean = true,
    /** Setting: touch gestures (seek / brightness / volume swipes, double-tap). Ignored on TV. */
    val gesturesEnabled: Boolean = true,
    /** Running sleep timer (remaining time shown in the panel); null while off. */
    val sleepTimer: SleepTimerState? = null,
    /** The channel is one of the favorites (panel toggle, P3b). */
    val isFavorite: Boolean = false,
    /** Codecs / decoders in use, shown in the panel's stream info (K26). */
    val streamInfo: StreamInfo = StreamInfo(),
) {
    /** K26: the stream has audio but this device can decode none of it (non-fatal notice). */
    val unsupportedAudio: AudioStatus.Unsupported? get() = streamInfo.audioStatus as? AudioStatus.Unsupported
}

/** Buttons of the picture-in-picture window. */
enum class PipAction { PREVIOUS, PLAY_PAUSE, NEXT }

/** Channel lookup plus the sleep timer, combined first to stay within combine's typed arity. */
private data class Lookup(
    val item: PlaybackItem?,
    val isLoading: Boolean,
    val error: PlaybackError?,
    val sleepTimer: SleepTimerState?,
    val isFavorite: Boolean,
)

/** Screen-local state that is not owned by the controller or the repositories. */
private data class ScreenLocal(
    val overlay: Boolean = true,
    val panelOpen: Boolean = false,
    val numberInput: String? = null,
    val numberNotFound: Int? = null,
    val pictureInPicture: Boolean = false,
)

/**
 * Drives the app-wide [PlaybackController] for one screen: resolves the channel, zaps inside the
 * group the user came from, jumps by typed channel number, forwards track / aspect / subtitle
 * choices and owns the overlay's auto-hide timer. Playback runs between [onStart] and [onStop];
 * leaving the screen releases the player. When the app goes to the background with background playback
 * on, the player keeps running audio-only (MediaSession service) and next / previous from the
 * notification or a headset zap through [zap]. In picture-in-picture the screen keeps playing even
 * while stopped (screen off); closing the window falls back to background playback or a release.
 *
 * P2b: the sleep timer lives here (it keeps running during background playback and PiP, because this
 * ViewModel stays alive as long as the player screen is on the back stack, and dies with the player);
 * every channel that starts playing is remembered as the last channel (P3a: and added to the watch
 * history); finished on-demand content
 * moves on to the next channel of the group when "auto play next" is on.
 */
@Suppress("TooManyFunctions", "LongParameterList") // One screen, many collaborators; all injected.
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val channelRepository: ChannelRepository,
    private val playbackController: PlaybackController,
    private val settingsRepository: SettingsRepository,
    private val recordPlaybackStart: RecordPlaybackStartUseCase,
    private val favoritesRepository: FavoritesRepository,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase,
    private val watchHistoryRepository: WatchHistoryRepository,
    clock: MonotonicClock,
) : ViewModel() {
    /** Route arguments of [com.streambox.player.feature.player.navigation.PlayerRoute]. */
    private val groupName: String? = savedStateHandle.get<String>(KEY_GROUP)
    private var channelId: Long =
        savedStateHandle.get<Long>(KEY_CURRENT) ?: checkNotNull(savedStateHandle.get<Long>(KEY_CHANNEL))

    /** The list the channel was picked from; null = opened without one (continue last channel). */
    private val routeSource: ChannelSource? =
        PlayerSource.decode(savedStateHandle.get<String>(KEY_SOURCE), groupName)

    /** Zapping, numbers and auto-play next follow the source list ([ChannelZapper]). */
    private val zapper = ChannelZapper(
        channelRepository,
        routeSource ?: groupName?.let { ChannelSource.Group(it) } ?: ChannelSource.All,
    )

    /** The browser's sort order: zapping walks the list in the order the user saw it. */
    private var sortOrder: ChannelSortOrder = ChannelSortOrder.Default

    private val current = MutableStateFlow<PlaybackItem?>(null)
    private val loading = MutableStateFlow(true)
    private val loadError = MutableStateFlow<PlaybackError?>(null)
    private val local = MutableStateFlow(ScreenLocal())
    private val playbackSettings = MutableStateFlow(PlaybackSettings())

    private var started = false

    /** Playing audio-only while the app is in the background (between [onStop] and the next [onStart]). */
    private var inBackground = false

    /** Stopped (e.g. screen off) while in picture-in-picture: playback keeps running, zaps still play. */
    private var stoppedInPip = false
    private val navigator = ChannelNavigator { direction -> zap(direction) }
    private var hideJob: Job? = null
    private var notFoundJob: Job? = null
    private val zapMutex = Mutex()

    /** Channel id last recorded by [recordPlaybackStart], so a channel is recorded once per start. */
    private var savedChannelId: Long? = null

    private val sleepTimer = SleepTimer(
        scope = viewModelScope,
        clock = clock,
        output = object : SleepTimerOutput {
            override fun setVolume(volume: Float) = playbackController.setVolume(volume)

            override fun onExpired() = playbackController.pause()
        },
    )

    private val numberInput = ChannelNumberInput(viewModelScope) { number -> jumpToNumber(number) }

    val player: StateFlow<Player?> = playbackController.player

    private val pipRequests = Channel<Unit>(Channel.CONFLATED)

    /** One-shot requests to enter picture-in-picture now (panel action); consumed by the PiP effect. */
    val pictureInPictureRequests: Flow<Unit> = pipRequests.receiveAsFlow()

    val uiState: StateFlow<PlayerUiState> = combine(
        combine(current, loading, loadError, sleepTimer.state, favoriteFlow(), ::Lookup),
        playbackController.state,
        local,
        playbackSettings,
        numberInput.digits,
    ) { lookup, playback, screen, prefs, digits ->
        val item = lookup.item
        // Ignore controller state that still belongs to the previous channel during a zap.
        val sameItem = item != null && playback.item?.channelId == item.channelId
        val error = lookup.error ?: playback.error.takeIf { sameItem }
        PlayerUiState(
            channel = item,
            isBuffering = error == null && (lookup.isLoading || (sameItem && playback.isBuffering)),
            isPlaying = sameItem && playback.isPlaying,
            error = error,
            overlayVisible = !screen.pictureInPicture && (screen.overlay || error != null || digits != null),
            reconnect = playback.reconnect.takeIf { sameItem && error == null },
            isSeekable = sameItem && playback.isSeekable,
            softwareDecoderFallback = sameItem && playback.softwareDecoderFallback,
            tracks = if (sameItem) playback.tracks else PlaybackTracks(),
            aspectRatio = prefs.defaultAspectRatio,
            subtitleStyle = prefs.subtitleStyle,
            panelOpen = screen.panelOpen,
            numberInput = digits,
            numberNotFound = screen.numberNotFound,
            backgroundPlayback = prefs.backgroundPlayback,
            pictureInPicture = screen.pictureInPicture,
            pictureInPictureEnabled = prefs.pictureInPicture,
            gesturesEnabled = prefs.playbackGestures,
            sleepTimer = lookup.sleepTimer,
            isFavorite = lookup.isFavorite,
            streamInfo = if (sameItem) playback.streamInfo else StreamInfo(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PlayerUiState())

    init {
        playbackController.channelNavigator = navigator
        viewModelScope.launch {
            settingsRepository.settings.map { it.browse.sortOrder }.distinctUntilChanged().collect { sortOrder = it }
        }
        viewModelScope.launch {
            settingsRepository.settings.map { it.playback }.distinctUntilChanged().collect { settings ->
                playbackSettings.value = settings
                playbackController.applySettings(settings)
            }
        }
        viewModelScope.launch {
            zapMutex.withLock {
                // Before the first channel plays (and moves to the top of the history).
                if (zapper.needsSnapshot) zapper.freeze(watchHistoryRepository.history.first().map { it.channel.key })
                load(initialChannelId())
            }
        }
        viewModelScope.launch { playbackController.state.collect(::rememberPlayingChannel) }
        viewModelScope.launch {
            playbackController.state
                .map { state -> state.item?.channelId?.takeIf { state.ended } }
                .distinctUntilChanged()
                .collect { endedId -> if (endedId != null && endedId == current.value?.channelId) onContentEnded() }
        }
    }

    /**
     * The first time a channel reports playing (a successful start), once per channel: remember it as
     * the last channel and add it to the watch history (both by playlist + uniqueKey, P3a).
     */
    private suspend fun rememberPlayingChannel(state: PlaybackState) {
        val item = state.item ?: return
        if (!state.isPlaying || item.channelId != current.value?.channelId || item.channelId == savedChannelId) return
        savedChannelId = item.channelId
        recordPlaybackStart(item)
    }

    /**
     * On-demand content finished (live streams never get here; they reconnect). With "auto play next"
     * on, plays the following channel of the list the user came from (P3b: also favorites / recently
     * watched, in display order); opened without a list, of the channel's own group. Stops after the
     * last one (no wrap-around).
     */
    private fun onContentEnded() {
        if (!playbackSettings.value.autoPlayNext) return
        viewModelScope.launch {
            zapMutex.withLock {
                val next = if (routeSource == null) {
                    val ownGroup = groupName ?: current.value?.groupName
                    channelRepository.nextChannel(
                        channelId,
                        ownGroup?.let { ChannelSource.Group(it) } ?: ChannelSource.All,
                        sortOrder,
                    )
                } else {
                    zapper.next(channelId, current.value?.key, sortOrder)
                }
                if (next != null && next != channelId) load(next)
            }
        }
    }

    /**
     * K20: the route's channel id may belong to a generation a refresh has just replaced. When the id
     * no longer resolves to the channel's stable key, look the key up again (current id first, so a
     * restored screen keeps the channel the user zapped to).
     */
    private suspend fun initialChannelId(): Long {
        val key = savedKey(KEY_CURRENT_PLAYLIST, KEY_CURRENT_UNIQUE) ?: savedKey(KEY_PLAYLIST, KEY_UNIQUE_KEY)
        return when {
            key == null -> channelId
            channelRepository.playbackItem(channelId)?.key == key -> channelId
            else -> channelRepository.channelIdByKey(key) ?: channelId
        }
    }

    private fun savedKey(playlistKey: String, uniqueKey: String): ChannelKey? {
        val playlistId = savedStateHandle.get<Long>(playlistKey)?.takeIf { it != PlayerRoute.NO_PLAYLIST }
        val unique = savedStateHandle.get<String>(uniqueKey)
        return if (playlistId != null && unique != null) ChannelKey(playlistId, unique) else null
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun favoriteFlow(): Flow<Boolean> = current
        .map { it?.key }
        .distinctUntilChanged()
        .flatMapLatest { key -> key?.let(favoritesRepository::isFavorite) ?: flowOf(false) }

    /** Panel action: adds the current channel to the favorites or removes it. */
    fun toggleFavorite() {
        val key = current.value?.key ?: return
        viewModelScope.launch { toggleFavoriteUseCase(key) }
    }

    // --- Sleep timer ----------------------------------------------------------------------------

    /**
     * Starts (or restarts) the sleep timer for [minutes]; null or 0 turns it off. Custom values are
     * clamped to [SleepTimer.MIN_MINUTES]..[SleepTimer.MAX_MINUTES].
     */
    fun setSleepTimer(minutes: Int?) {
        if (minutes == null || minutes <= 0) sleepTimer.cancel() else sleepTimer.start(minutes)
    }

    /**
     * Screen became visible (lifecycle ON_START): (re)start playback of the current channel, or bring the
     * picture back after background playback.
     */
    fun onStart() {
        started = true
        stoppedInPip = false
        if (inBackground) {
            inBackground = false
            playbackController.setInBackground(false)
        }
        val item = current.value ?: return
        if (playbackController.state.value.item?.channelId != item.channelId) playbackController.play(item)
        showOverlay()
    }

    /**
     * Screen hidden (ON_STOP) or left. [appInBackground] = the whole app went to the background (home,
     * screen off, another app), as opposed to navigating away inside the app. With background playback
     * on, a channel that is playing (or reconnecting) keeps its audio running; otherwise decoders and
     * network are freed and live TV restarts on return. [inPictureInPicture] = stopped while the
     * picture-in-picture window is still showing (screen off): nothing changes, playback continues.
     */
    fun onStop(appInBackground: Boolean = false, inPictureInPicture: Boolean = false) {
        started = false
        numberInput.cancel()
        if (inPictureInPicture) {
            stoppedInPip = true
            return
        }
        stoppedInPip = false
        goToBackgroundOrRelease(appInBackground)
    }

    /**
     * Picture-in-picture entered or left. [stopped] = the window was left while the activity is stopped,
     * i.e. the user closed it rather than expanding it back to full screen.
     */
    fun onPictureInPictureModeChanged(inPip: Boolean, stopped: Boolean) {
        if (inPip) {
            hideJob?.cancel()
            notFoundJob?.cancel()
            numberInput.cancel()
            local.update {
                it.copy(pictureInPicture = true, panelOpen = false, overlay = false, numberNotFound = null)
            }
            return
        }
        local.update { it.copy(pictureInPicture = false) }
        if (stopped) {
            started = false
            stoppedInPip = false
            goToBackgroundOrRelease(appInBackground = true)
        } else {
            showOverlay()
        }
    }

    /**
     * The panel's picture-in-picture action: closes the panel first, then asks the screen to enter PiP.
     * Ignored while PiP is turned off in settings or already active.
     */
    fun requestPictureInPicture() {
        if (!playbackSettings.value.pictureInPicture || local.value.pictureInPicture) return
        local.update { it.copy(panelOpen = false) }
        pipRequests.trySend(Unit)
    }

    /** Buttons of the picture-in-picture window. */
    fun onPipAction(action: PipAction) {
        when (action) {
            PipAction.PREVIOUS -> zap(ZapDirection.PREVIOUS)
            PipAction.PLAY_PAUSE -> playbackController.togglePlayPause()
            PipAction.NEXT -> zap(ZapDirection.NEXT)
        }
    }

    private fun goToBackgroundOrRelease(appInBackground: Boolean) {
        val item = current.value
        val playback = playbackController.state.value
        val keepPlaying = appInBackground &&
            playbackSettings.value.backgroundPlayback &&
            item != null &&
            playback.item?.channelId == item.channelId &&
            playback.error == null
        if (keepPlaying) {
            inBackground = true
            playbackController.setInBackground(true)
        } else {
            inBackground = false
            playbackController.release()
        }
    }

    fun zap(direction: ZapDirection) {
        showOverlay()
        viewModelScope.launch {
            // Serialized so quick repeated presses each move one channel from the latest one.
            zapMutex.withLock {
                val next = zapper.adjacent(channelId, current.value?.key, sortOrder, direction)
                if (next != null && next != channelId) load(next)
            }
        }
    }

    fun retry() {
        showOverlay()
        if (loadError.value != null || current.value == null) {
            viewModelScope.launch { zapMutex.withLock { load(channelId) } }
        } else {
            playbackController.retry()
        }
    }

    fun showOverlay() {
        // Nothing to show in the picture-in-picture window; the overlay comes back on expanding.
        if (local.value.pictureInPicture) return
        local.update { it.copy(overlay = true) }
        hideJob?.cancel()
        hideJob = viewModelScope.launch {
            delay(OVERLAY_TIMEOUT_MS)
            local.update { it.copy(overlay = false) }
        }
    }

    fun toggleOverlay() {
        if (local.value.overlay) {
            hideJob?.cancel()
            local.update { it.copy(overlay = false) }
        } else {
            showOverlay()
        }
    }

    // --- Remote control -------------------------------------------------------------------------

    /** Number keys: typed digits jump to that channel number after a short pause. */
    fun onDigit(digit: Int) {
        notFoundJob?.cancel()
        local.update { it.copy(numberNotFound = null) }
        numberInput.onDigit(digit)
    }

    /** OK while typing a number jumps right away. @return true when a number was being typed. */
    fun commitNumberInput(): Boolean {
        if (!numberInput.isActive) return false
        numberInput.commitNow()
        return true
    }

    /** Back / Del while typing a number removes the last digit. @return true when a number was being typed. */
    fun onNumberBackspace(): Boolean = numberInput.backspace()

    /** Remote / keyboard media keys while the player is in the foreground (K14). */
    fun onMediaKey(action: MediaKeyAction) {
        when (action) {
            MediaKeyAction.TOGGLE -> playbackController.togglePlayPause()
            MediaKeyAction.PLAY -> playbackController.resume()
            MediaKeyAction.PAUSE -> playbackController.pause()
        }
        showOverlay()
    }

    /** Left / right: seek ±10 s on seekable content, otherwise change the volume (live TV). */
    fun onHorizontalKey(forward: Boolean) {
        if (playbackController.state.value.isSeekable) {
            playbackController.seekBy(if (forward) SEEK_STEP_MS else -SEEK_STEP_MS)
            showOverlay()
        } else {
            playbackController.adjustVolume(raise = forward)
        }
    }

    // --- Touch gestures (phones / tablets) -------------------------------------------------------

    /**
     * End of a horizontal seek swipe: seeks by [offsetMs] on seekable content. Ignored for live streams
     * that cannot seek, in picture-in-picture and with gestures turned off. @return true when it seeked.
     */
    fun seekByGesture(offsetMs: Long): Boolean {
        if (!gesturesActive() || !playbackController.state.value.isSeekable || offsetMs == 0L) return false
        playbackController.seekBy(offsetMs)
        return true
    }

    /**
     * Double-tap in [zone]: left −10 s, right +10 s (seekable content only), middle play / pause.
     * @return what was done, or null when ignored (live sides, picture-in-picture, gestures off).
     */
    fun onDoubleTap(zone: TouchZone): DoubleTapAction? {
        val action = PlayerGestures.doubleTapAction(zone, playbackController.state.value.isSeekable)
            ?.takeIf { gesturesActive() }
        when (action) {
            DoubleTapAction.REWIND -> playbackController.seekBy(-SEEK_STEP_MS)
            DoubleTapAction.PLAY_PAUSE -> playbackController.togglePlayPause()
            DoubleTapAction.FORWARD -> playbackController.seekBy(SEEK_STEP_MS)
            null -> Unit
        }
        return action
    }

    private fun gesturesActive(): Boolean = playbackSettings.value.playbackGestures && !local.value.pictureInPicture

    // --- Panel: tracks, aspect ratio, subtitles -------------------------------------------------

    fun openPanel() {
        hideJob?.cancel()
        local.update { it.copy(panelOpen = true, overlay = true) }
    }

    fun closePanel() {
        local.update { it.copy(panelOpen = false) }
        showOverlay()
    }

    /** Menu key toggles the panel. */
    fun togglePanel() = if (local.value.panelOpen) closePanel() else openPanel()

    fun selectVideoTrack(id: TrackId?) = playbackController.selectVideoTrack(id)

    fun selectAudioTrack(id: TrackId) = playbackController.selectAudioTrack(id)

    fun selectSubtitleTrack(id: TrackId?) = playbackController.selectSubtitleTrack(id)

    /**
     * A file picked through SAF; the format comes from the file name, else the provider's MIME type.
     * @return false when it is not a supported subtitle file (nothing is loaded).
     */
    fun loadExternalSubtitle(uri: String, displayName: String?, providerMimeType: String?): Boolean {
        val mimeType = SubtitleFiles.mimeTypeFor(displayName, providerMimeType) ?: return false
        playbackController.addExternalSubtitle(uri, mimeType, displayName ?: uri.substringAfterLast('/'))
        return true
    }

    /** Applies now and becomes the default for every channel. */
    fun setAspectRatio(mode: AspectRatioMode) {
        playbackSettings.update { it.copy(defaultAspectRatio = mode) }
        viewModelScope.launch { settingsRepository.setDefaultAspectRatio(mode) }
    }

    /** Overlay shortcut: next aspect ratio in the list, wrapping around. */
    fun cycleAspectRatio() {
        val modes = AspectRatioMode.entries
        setAspectRatio(modes[(modes.indexOf(playbackSettings.value.defaultAspectRatio) + 1) % modes.size])
        showOverlay()
    }

    fun setSubtitleStyle(style: SubtitleStyle) {
        playbackSettings.update { it.copy(subtitleStyle = style) }
        viewModelScope.launch { settingsRepository.setSubtitleStyle(style) }
    }

    private fun jumpToNumber(number: Int) {
        viewModelScope.launch {
            zapMutex.withLock {
                val id = zapper.byNumber(number, sortOrder)
                when {
                    id == null -> showNotFound(number)
                    id != channelId -> load(id)
                    else -> showOverlay()
                }
            }
        }
    }

    private fun showNotFound(number: Int) {
        local.update { it.copy(numberNotFound = number) }
        showOverlay()
        notFoundJob?.cancel()
        notFoundJob = viewModelScope.launch {
            delay(NOT_FOUND_VISIBLE_MS)
            local.update { it.copy(numberNotFound = null) }
        }
    }

    private suspend fun load(id: Long) {
        loading.value = true
        loadError.value = null
        channelId = id
        savedStateHandle[KEY_CURRENT] = id
        val item = channelRepository.playbackItem(id)
        item?.let {
            savedStateHandle[KEY_CURRENT_PLAYLIST] = it.playlistId
            savedStateHandle[KEY_CURRENT_UNIQUE] = it.uniqueKey
        }
        if (item == null) {
            playbackController.release()
            loadError.value = PlaybackError.ChannelNotFound
        } else {
            current.value = item
            if (started || inBackground || stoppedInPip) playbackController.play(item)
        }
        loading.value = false
        showOverlay()
    }

    override fun onCleared() {
        sleepTimer.cancel()
        if (playbackController.channelNavigator === navigator) playbackController.channelNavigator = null
        playbackController.release()
    }

    companion object {
        internal const val KEY_CHANNEL = "channelId"
        internal const val KEY_GROUP = "groupName"
        internal const val KEY_SOURCE = "source"
        internal const val KEY_PLAYLIST = "playlistId"
        internal const val KEY_UNIQUE_KEY = "uniqueKey"
        private const val KEY_CURRENT = "currentChannelId"
        private const val KEY_CURRENT_PLAYLIST = "currentPlaylistId"
        private const val KEY_CURRENT_UNIQUE = "currentUniqueKey"
        const val OVERLAY_TIMEOUT_MS = 4_000L
        const val NOT_FOUND_VISIBLE_MS = 2_000L
        const val SEEK_STEP_MS = 10_000L
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
