package com.streambox.player.feature.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.streambox.player.core.domain.repository.ChannelRepository
import com.streambox.player.core.domain.repository.SettingsRepository
import com.streambox.player.core.model.AspectRatioMode
import com.streambox.player.core.model.PlaybackError
import com.streambox.player.core.model.PlaybackItem
import com.streambox.player.core.model.PlaybackSettings
import com.streambox.player.core.model.SubtitleStyle
import com.streambox.player.core.model.ZapDirection
import com.streambox.player.core.player.PlaybackController
import com.streambox.player.core.player.PlaybackTracks
import com.streambox.player.core.player.TrackId
import com.streambox.player.core.player.reconnect.ReconnectStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
)

/** Screen-local state that is not owned by the controller or the repositories. */
private data class ScreenLocal(
    val overlay: Boolean = true,
    val panelOpen: Boolean = false,
    val numberInput: String? = null,
    val numberNotFound: Int? = null,
)

/**
 * Drives the app-wide [PlaybackController] for one screen: resolves the channel, zaps inside the
 * group the user came from, jumps by typed channel number, forwards track / aspect / subtitle
 * choices and owns the overlay's auto-hide timer. Playback only runs between [onStart] and
 * [onStop]; leaving the screen releases the player.
 */
@Suppress("TooManyFunctions")
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val channelRepository: ChannelRepository,
    private val playbackController: PlaybackController,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    /** Route arguments of [com.streambox.player.feature.player.navigation.PlayerRoute]. */
    private val groupName: String? = savedStateHandle.get<String>(KEY_GROUP)
    private var channelId: Long =
        savedStateHandle.get<Long>(KEY_CURRENT) ?: checkNotNull(savedStateHandle.get<Long>(KEY_CHANNEL))

    private val current = MutableStateFlow<PlaybackItem?>(null)
    private val loading = MutableStateFlow(true)
    private val loadError = MutableStateFlow<PlaybackError?>(null)
    private val local = MutableStateFlow(ScreenLocal())
    private val playbackSettings = MutableStateFlow(PlaybackSettings())

    private var started = false
    private var hideJob: Job? = null
    private var notFoundJob: Job? = null
    private val zapMutex = Mutex()

    private val numberInput = ChannelNumberInput(viewModelScope) { number -> jumpToNumber(number) }

    val player: StateFlow<Player?> = playbackController.player

    val uiState: StateFlow<PlayerUiState> = combine(
        combine(current, loading, loadError) { item, isLoading, lookupError -> Triple(item, isLoading, lookupError) },
        playbackController.state,
        local,
        playbackSettings,
        numberInput.digits,
    ) { (item, isLoading, lookupError), playback, screen, prefs, digits ->
        // Ignore controller state that still belongs to the previous channel during a zap.
        val sameItem = item != null && playback.item?.channelId == item.channelId
        val error = lookupError ?: playback.error.takeIf { sameItem }
        PlayerUiState(
            channel = item,
            isBuffering = error == null && (isLoading || (sameItem && playback.isBuffering)),
            isPlaying = sameItem && playback.isPlaying,
            error = error,
            overlayVisible = screen.overlay || error != null || digits != null,
            reconnect = playback.reconnect.takeIf { sameItem && error == null },
            isSeekable = sameItem && playback.isSeekable,
            softwareDecoderFallback = sameItem && playback.softwareDecoderFallback,
            tracks = if (sameItem) playback.tracks else PlaybackTracks(),
            aspectRatio = prefs.defaultAspectRatio,
            subtitleStyle = prefs.subtitleStyle,
            panelOpen = screen.panelOpen,
            numberInput = digits,
            numberNotFound = screen.numberNotFound,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PlayerUiState())

    init {
        viewModelScope.launch {
            settingsRepository.settings.map { it.playback }.distinctUntilChanged().collect { settings ->
                playbackSettings.value = settings
                playbackController.applySettings(settings)
            }
        }
        viewModelScope.launch { zapMutex.withLock { load(channelId) } }
    }

    /** Screen became visible (lifecycle ON_START): (re)start playback of the current channel. */
    fun onStart() {
        started = true
        val item = current.value ?: return
        if (playbackController.state.value.item?.channelId != item.channelId) playbackController.play(item)
        showOverlay()
    }

    /** Screen hidden (ON_STOP) or left: free decoders and network; live TV restarts on return. */
    fun onStop() {
        started = false
        numberInput.cancel()
        playbackController.release()
    }

    fun zap(direction: ZapDirection) {
        showOverlay()
        viewModelScope.launch {
            // Serialized so quick repeated presses each move one channel from the latest one.
            zapMutex.withLock {
                val next = channelRepository.adjacentChannel(channelId, groupName, direction)
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

    /** Left / right: seek ±10 s on seekable content, otherwise change the volume (live TV). */
    fun onHorizontalKey(forward: Boolean) {
        if (playbackController.state.value.isSeekable) {
            playbackController.seekBy(if (forward) SEEK_STEP_MS else -SEEK_STEP_MS)
            showOverlay()
        } else {
            playbackController.adjustVolume(raise = forward)
        }
    }

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
                val id = channelRepository.channelIdByNumber(number, groupName)
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
        if (item == null) {
            playbackController.release()
            loadError.value = PlaybackError.ChannelNotFound
        } else {
            current.value = item
            if (started) playbackController.play(item)
        }
        loading.value = false
        showOverlay()
    }

    override fun onCleared() {
        playbackController.release()
    }

    companion object {
        internal const val KEY_CHANNEL = "channelId"
        internal const val KEY_GROUP = "groupName"
        private const val KEY_CURRENT = "currentChannelId"
        const val OVERLAY_TIMEOUT_MS = 4_000L
        const val NOT_FOUND_VISIBLE_MS = 2_000L
        const val SEEK_STEP_MS = 10_000L
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
