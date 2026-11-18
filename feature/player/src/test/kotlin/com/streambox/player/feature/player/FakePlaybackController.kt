package com.streambox.player.feature.player

import androidx.media3.common.Player
import com.streambox.player.core.model.PlaybackError
import com.streambox.player.core.model.PlaybackItem
import com.streambox.player.core.model.PlaybackSettings
import com.streambox.player.core.player.ChannelNavigator
import com.streambox.player.core.player.PlaybackController
import com.streambox.player.core.player.PlaybackState
import com.streambox.player.core.player.TrackId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Records calls instead of decoding; tests push buffering / errors through [state]. */
@Suppress("TooManyFunctions")
class FakePlaybackController : PlaybackController {
    override val player: StateFlow<Player?> = MutableStateFlow(null)
    override val state = MutableStateFlow(PlaybackState())

    val played = mutableListOf<PlaybackItem>()
    var retries = 0
    var releases = 0
    var playPauseToggles = 0
    val appliedSettings = mutableListOf<PlaybackSettings>()
    val seeks = mutableListOf<Long>()
    val volumeChanges = mutableListOf<Boolean>()
    val externalSubtitles = mutableListOf<Triple<String, String, String>>()
    var videoTrack: TrackId? = null
    var subtitleTrack: TrackId? = null
    val backgroundModes = mutableListOf<Boolean>()
    override var channelNavigator: ChannelNavigator? = null

    override fun play(item: PlaybackItem) {
        played += item
        state.value = PlaybackState(item = item, isBuffering = true)
    }

    override fun retry() {
        retries++
        state.update { it.copy(error = null, isBuffering = true) }
    }

    override fun togglePlayPause() {
        playPauseToggles++
    }

    override fun release() {
        releases++
        state.value = PlaybackState()
    }

    override fun applySettings(settings: PlaybackSettings) {
        appliedSettings += settings
    }

    override fun selectVideoTrack(id: TrackId?) {
        videoTrack = id
    }

    override fun selectAudioTrack(id: TrackId) = Unit

    override fun selectSubtitleTrack(id: TrackId?) {
        subtitleTrack = id
    }

    override fun addExternalSubtitle(uri: String, mimeType: String, label: String) {
        externalSubtitles += Triple(uri, mimeType, label)
    }

    override fun seekBy(offsetMs: Long) {
        seeks += offsetMs
    }

    override fun adjustVolume(raise: Boolean) {
        volumeChanges += raise
    }

    override fun setInBackground(background: Boolean) {
        backgroundModes += background
    }

    fun startPlaying() = state.update { it.copy(isBuffering = false, isPlaying = true) }

    fun fail(error: PlaybackError) = state.update { it.copy(isBuffering = false, isPlaying = false, error = error) }
}
