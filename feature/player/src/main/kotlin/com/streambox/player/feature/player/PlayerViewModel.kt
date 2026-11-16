package com.streambox.player.feature.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.streambox.player.core.domain.repository.ChannelRepository
import com.streambox.player.core.model.PlaybackError
import com.streambox.player.core.model.PlaybackItem
import com.streambox.player.core.model.ZapDirection
import com.streambox.player.core.player.PlaybackController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
)

/**
 * Drives the app-wide [PlaybackController] for one screen: resolves the channel, zaps inside the
 * group the user came from, and owns the overlay's auto-hide timer. Playback only runs between
 * [onStart] and [onStop]; leaving the screen releases the player.
 */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val channelRepository: ChannelRepository,
    private val playbackController: PlaybackController,
) : ViewModel() {
    /** Route arguments of [com.streambox.player.feature.player.navigation.PlayerRoute]. */
    private val groupName: String? = savedStateHandle.get<String>(KEY_GROUP)
    private var channelId: Long =
        savedStateHandle.get<Long>(KEY_CURRENT) ?: checkNotNull(savedStateHandle.get<Long>(KEY_CHANNEL))

    private val current = MutableStateFlow<PlaybackItem?>(null)
    private val loading = MutableStateFlow(true)
    private val loadError = MutableStateFlow<PlaybackError?>(null)
    private val overlay = MutableStateFlow(true)

    private var started = false
    private var hideJob: Job? = null
    private val zapMutex = Mutex()

    val player: StateFlow<Player?> = playbackController.player

    val uiState: StateFlow<PlayerUiState> = combine(
        current,
        loading,
        loadError,
        playbackController.state,
        overlay,
    ) { item, isLoading, lookupError, playback, overlayShown ->
        // Ignore controller state that still belongs to the previous channel during a zap.
        val sameItem = item != null && playback.item?.channelId == item.channelId
        val error = lookupError ?: playback.error.takeIf { sameItem }
        PlayerUiState(
            channel = item,
            isBuffering = error == null && (isLoading || (sameItem && playback.isBuffering)),
            isPlaying = sameItem && playback.isPlaying,
            error = error,
            overlayVisible = overlayShown || error != null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PlayerUiState())

    init {
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
        overlay.value = true
        hideJob?.cancel()
        hideJob = viewModelScope.launch {
            delay(OVERLAY_TIMEOUT_MS)
            overlay.value = false
        }
    }

    fun toggleOverlay() {
        if (overlay.value) {
            hideJob?.cancel()
            overlay.value = false
        } else {
            showOverlay()
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
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
