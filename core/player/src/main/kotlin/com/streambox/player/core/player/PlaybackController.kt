package com.streambox.player.core.player

import androidx.media3.common.Player
import com.streambox.player.core.model.PlaybackError
import com.streambox.player.core.model.PlaybackItem
import com.streambox.player.core.model.PlaybackSettings
import com.streambox.player.core.model.ZapDirection
import com.streambox.player.core.player.reconnect.ReconnectStatus
import kotlinx.coroutines.flow.StateFlow

data class PlaybackState(
    val item: PlaybackItem? = null,
    val isBuffering: Boolean = false,
    val isPlaying: Boolean = false,
    val error: PlaybackError? = null,
    /** Non-null while an automatic reconnect is pending or in progress. */
    val reconnect: ReconnectStatus? = null,
    /** True for on-demand content where seeking makes sense (not live). */
    val isSeekable: Boolean = false,
    /** True after a decoder failure switched this stream to software decoders. */
    val softwareDecoderFallback: Boolean = false,
    val tracks: PlaybackTracks = PlaybackTracks(),
    /** On-demand content (not live) played to its end. A live stream that ends reconnects instead. */
    val ended: Boolean = false,
)

/**
 * App-wide player (requirements §8.8: not tied to an Activity, survives configuration changes).
 * Must be called on the main thread. P2a adds reconnects, buffering profiles, decoder fallback and
 * tracks; P2b adds background playback (the MediaSession in [com.streambox.player.core.player.session.PlaybackService]
 * follows [player], including rebuilds).
 */
@Suppress("TooManyFunctions")
interface PlaybackController {
    /** The Media3 player to render, or null when released. */
    val player: StateFlow<Player?>

    val state: StateFlow<PlaybackState>

    fun play(item: PlaybackItem)

    /** Manual retry: resets the reconnect counter and starts the current item again. */
    fun retry()

    /** Play / pause toggle (e.g. the PiP action); with no player, idle or failed it [retry]s instead. */
    fun togglePlayPause()

    /** Pauses (keeps the player and position); no-op without a player. */
    fun pause()

    /**
     * Player output volume 0..1 (not the device volume), e.g. the sleep timer's fade-out. Kept across
     * player rebuilds; [release] resets it to 1.
     */
    fun setVolume(volume: Float)

    /** Stops playback and frees decoders / network; the next [play] creates a fresh player. */
    fun release()

    /** Buffer profile / decoder mode apply from the next [play]; reconnect attempts immediately. */
    fun applySettings(settings: PlaybackSettings)

    /** Null = automatic quality. */
    fun selectVideoTrack(id: TrackId?)

    fun selectAudioTrack(id: TrackId)

    /** Null = subtitles off. */
    fun selectSubtitleTrack(id: TrackId?)

    /** Adds an SRT / VTT file (any readable uri, e.g. from SAF) to the current item and selects it. */
    fun addExternalSubtitle(uri: String, mimeType: String, label: String)

    /** Seeks relative to the current position when the content is seekable. */
    fun seekBy(offsetMs: Long)

    /** Raises / lowers the device media volume, showing the system volume UI. */
    fun adjustVolume(raise: Boolean)

    /**
     * Background mode: the video track is disabled (nothing is decoded for a hidden surface) while the
     * audio keeps playing; it also applies to players rebuilt meanwhile. Leaving it re-enables video
     * and resumes playback if it was paused (e.g. from the notification or a headset). Reset by [release].
     */
    fun setInBackground(background: Boolean)

    /** Handles "next / previous" from the media notification, headset and Bluetooth; null = no-op. */
    var channelNavigator: ChannelNavigator?
}

/** Moves to the adjacent channel; registered by the screen that owns the channel list. */
fun interface ChannelNavigator {
    fun zap(direction: ZapDirection)
}
