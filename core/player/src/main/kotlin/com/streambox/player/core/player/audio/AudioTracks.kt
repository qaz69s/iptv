package com.streambox.player.core.player.audio

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector

/** What the panel's stream info and logcat show for a Media3 [Format] (K26). */
internal fun Format.toInfo() = MediaFormatInfo(
    sampleMimeType = sampleMimeType,
    codecs = codecs,
    channelCount = channelCount.takeIf { it != Format.NO_VALUE },
    sampleRate = sampleRate.takeIf { it != Format.NO_VALUE },
    width = width.takeIf { it != Format.NO_VALUE },
    height = height.takeIf { it != Format.NO_VALUE },
)

/** Every audio track of [tracks] with whether a renderer (MediaCodec or FFmpeg) can decode it. */
internal fun audioTrackFormats(tracks: Tracks): List<AudioTrackFormat> = tracks.groups
    .filter { it.type == C.TRACK_TYPE_AUDIO }
    .flatMap { group ->
        (0 until group.length).map { index ->
            AudioTrackFormat(
                format = group.getTrackFormat(index).toInfo(),
                // Over-capability tracks still play: the selector may pick them (AudioSelectionPolicy).
                playable = group.isTrackSupported(index, true),
                selected = group.isTrackSelected(index),
            )
        }
    }

/** K26: rather an over-capability audio track than none; no tunneling, no audio offload. */
internal fun audioFriendlyTrackSelector(
    context: Context,
    policy: AudioSelectionPolicy = AudioSelectionPolicy.DEFAULT,
): DefaultTrackSelector {
    val offload = AudioOffloadPreferences.Builder()
        .setAudioOffloadMode(
            if (policy.audioOffload) {
                AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
            } else {
                AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
            },
        )
        .build()
    val parameters = DefaultTrackSelector.Parameters.Builder(context).apply {
        setExceedRendererCapabilitiesIfNecessary(policy.exceedRendererCapabilitiesIfNecessary)
        setExceedAudioConstraintsIfNecessary(policy.exceedAudioConstraintsIfNecessary)
        setAllowAudioMixedMimeTypeAdaptiveness(policy.allowMixedMimeTypeAdaptiveness)
        setTunnelingEnabled(policy.tunneling)
        setAudioOffloadPreferences(offload)
    }.build()
    return DefaultTrackSelector(context, parameters)
}
