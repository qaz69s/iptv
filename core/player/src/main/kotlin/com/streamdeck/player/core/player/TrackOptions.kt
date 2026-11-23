@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.streamdeck.player.core.player

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks

enum class TrackKind(val type: Int) { VIDEO(C.TRACK_TYPE_VIDEO), AUDIO(C.TRACK_TYPE_AUDIO), TEXT(C.TRACK_TYPE_TEXT) }

data class TrackOption(val kind: TrackKind, val format: Format, val selected: Boolean, internal val group: Tracks.Group, internal val index: Int)

data class TrackMenu(val kind: TrackKind, val options: List<TrackOption>, val isAuto: Boolean, val isDisabled: Boolean)

/** Quality / audio / subtitle selection on top of Media3 track overrides (spec §4.3 轨道选择). */
object TrackOptions {
    fun menu(player: Player, kind: TrackKind): TrackMenu {
        val params = player.trackSelectionParameters
        val options = player.currentTracks.groups
            .filter { it.type == kind.type }
            .flatMap { g -> (0 until g.length).filter { g.isTrackSupported(it) }.map { i -> TrackOption(kind, g.getTrackFormat(i), g.isTrackSelected(i), g, i) } }
        val hasOverride = params.overrides.keys.any { it.type == kind.type }
        return TrackMenu(kind, options, isAuto = !hasOverride, isDisabled = kind.type in params.disabledTrackTypes)
    }

    fun select(player: Player, option: TrackOption) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(option.kind.type, false)
            .setOverrideForType(TrackSelectionOverride(option.group.mediaTrackGroup, option.index))
            .build()
    }

    fun auto(player: Player, kind: TrackKind) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(kind.type)
            .setTrackTypeDisabled(kind.type, false)
            .build()
    }

    fun disable(player: Player, kind: TrackKind) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(kind.type)
            .setTrackTypeDisabled(kind.type, true)
            .build()
    }

    /** Human-readable label without localized words, e.g. "1080p · 4.2 Mbps" or "eng · 5.1". */
    fun label(f: Format): String = when {
        f.height > 0 -> buildString {
            append("${f.height}p")
            if (f.bitrate > 0) append(" · %.1f Mbps".format(f.bitrate / 1_000_000f))
        }
        else -> listOfNotNull(
            f.label,
            f.language?.takeIf { it != C.LANGUAGE_UNDETERMINED },
            f.channelCount.takeIf { it > 0 }?.let { if (it == 6) "5.1" else if (it == 8) "7.1" else "${it}ch" },
            f.codecs,
        ).distinct().joinToString(" · ")
    }
}
