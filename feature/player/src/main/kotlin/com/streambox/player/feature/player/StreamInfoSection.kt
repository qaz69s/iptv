package com.streambox.player.feature.player

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.streambox.player.core.designsystem.component.focusRing
import com.streambox.player.core.player.StreamInfo
import com.streambox.player.core.player.audio.AudioStatus
import com.streambox.player.core.player.audio.MediaFormatInfo

/**
 * K26: codecs and decoders of the playing channel, so the user can report a silent channel. Read-only
 * rows are focusable on TV so the remote can scroll to them.
 */
internal fun LazyListScope.streamInfoSection(info: StreamInfo) {
    val unsupported = info.audioStatus as? AudioStatus.Unsupported
    val audio = info.audio
    if (info.video == null && audio == null && unsupported == null) return
    item(key = "stream-info-header") { InfoHeader() }
    info.video?.let { video ->
        item(key = "stream-info-video") {
            InfoRow(stringResource(R.string.feature_player_stream_video, describe(video, info.videoDecoder)))
        }
    }
    when {
        unsupported != null -> item(key = "stream-info-audio") {
            InfoRow(
                stringResource(
                    R.string.feature_player_stream_audio_unsupported,
                    unsupported.formats.joinToString { it.describe() },
                ),
            )
        }

        audio != null -> item(key = "stream-info-audio") {
            InfoRow(stringResource(R.string.feature_player_stream_audio, describe(audio, info.audioDecoder)))
        }

        else -> item(key = "stream-info-audio") {
            InfoRow(stringResource(R.string.feature_player_stream_audio_none))
        }
    }
}

@Composable
private fun describe(format: MediaFormatInfo, decoder: String?): String =
    stringResource(
        R.string.feature_player_stream_format_decoder,
        format.describe(),
        decoder ?: stringResource(R.string.feature_player_stream_decoder_unknown),
    )

@Composable
private fun InfoHeader() {
    Text(
        text = stringResource(R.string.feature_player_stream_info),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

@Composable
private fun InfoRow(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .focusRing()
            .focusable()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
