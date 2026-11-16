package com.streambox.player.feature.playlist

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.streambox.player.core.designsystem.component.focusRing
import com.streambox.player.core.designsystem.icon.StreamBoxIcons
import com.streambox.player.core.designsystem.theme.StreamBoxTheme
import com.streambox.player.core.model.ImportFailure
import com.streambox.player.core.model.ImportStatus
import com.streambox.player.core.model.Playlist
import com.streambox.player.core.model.PlaylistType
import com.streambox.player.core.ui.FormFactor
import com.streambox.player.core.ui.LocalFormFactor

@Composable
internal fun ImportProgressEntry(
    onBack: () -> Unit,
    onGoToChannels: () -> Unit,
    viewModel: ImportProgressViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ImportProgressScreen(
        state = state,
        onBack = onBack,
        onCancel = viewModel::cancel,
        onRetry = viewModel::retry,
        onGoToChannels = onGoToChannels,
    )
}

@Composable
internal fun ImportProgressScreen(
    state: ImportProgressUiState,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onGoToChannels: () -> Unit,
) {
    val title = state.playlist?.name ?: stringResource(R.string.feature_playlist_import_title)
    Scaffold(topBar = { PlaylistTopBar(title, onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (val status = state.status) {
                    ImportStatus.Connecting -> InProgress(
                        text = stringResource(R.string.feature_playlist_import_connecting),
                        detail = null,
                        fraction = null,
                        onCancel = onCancel,
                    )

                    is ImportStatus.Running -> InProgress(
                        text = stringResource(R.string.feature_playlist_import_running, status.channels),
                        detail = bytesText(status.bytesRead, status.totalBytes),
                        fraction = status.fraction,
                        onCancel = onCancel,
                    )

                    is ImportStatus.Finalizing -> InProgress(
                        text = stringResource(R.string.feature_playlist_import_finalizing, status.channels),
                        detail = null,
                        fraction = null,
                        onCancel = null,
                    )

                    is ImportStatus.Success -> Finished(
                        icon = StreamBoxIcons.Success,
                        isError = false,
                        title = stringResource(
                            R.string.feature_playlist_import_success,
                            status.channels,
                            status.groups,
                        ),
                        message = if (status.skippedLines > 0) {
                            stringResource(R.string.feature_playlist_import_skipped, status.skippedLines.toInt())
                        } else {
                            null
                        },
                        onRetry = null,
                        onGoToChannels = onGoToChannels,
                    )

                    is ImportStatus.Failed -> Finished(
                        icon = StreamBoxIcons.Error,
                        isError = true,
                        title = stringResource(R.string.feature_playlist_import_failed),
                        message = status.reason.message() + oldChannelsNote(state.playlist),
                        onRetry = onRetry,
                        onGoToChannels = onGoToChannels,
                    )

                    ImportStatus.Cancelled -> Finished(
                        icon = StreamBoxIcons.Error,
                        isError = false,
                        title = stringResource(R.string.feature_playlist_import_cancelled),
                        message = null,
                        onRetry = onRetry,
                        onGoToChannels = onGoToChannels,
                    )

                    ImportStatus.Idle -> Finished(
                        icon = StreamBoxIcons.Playlists,
                        isError = false,
                        title = stringResource(R.string.feature_playlist_import_idle),
                        message = state.playlist?.lastError,
                        onRetry = onRetry,
                        onGoToChannels = onGoToChannels,
                        retryLabel = stringResource(R.string.feature_playlist_refresh_now),
                    )
                }
            }
        }
    }
}

@Composable
private fun oldChannelsNote(playlist: Playlist?): String = if ((playlist?.channelCount ?: 0) > 0) {
    "\n" + stringResource(R.string.feature_playlist_import_old_kept)
} else {
    ""
}

@Composable
private fun bytesText(read: Long, total: Long?): String {
    val context = LocalContext.current
    val readText = Formatter.formatShortFileSize(context, read)
    return if (total != null && total > 0) {
        stringResource(R.string.feature_playlist_import_bytes, readText, Formatter.formatShortFileSize(context, total))
    } else {
        stringResource(R.string.feature_playlist_import_bytes_unknown, readText)
    }
}

@Composable
private fun InProgress(text: String, detail: String?, fraction: Float?, onCancel: (() -> Unit)?) {
    Text(text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    if (fraction != null) {
        LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
    } else {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
    detail?.let {
        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (onCancel != null) {
        OutlinedButton(onClick = onCancel, modifier = Modifier.focusRing().autoFocusOnTv()) {
            Text(stringResource(R.string.feature_playlist_cancel))
        }
    }
}

@Composable
@Suppress("LongParameterList")
private fun Finished(
    icon: Int,
    isError: Boolean,
    title: String,
    message: String?,
    onRetry: (() -> Unit)?,
    onGoToChannels: () -> Unit,
    retryLabel: String = stringResource(R.string.feature_playlist_retry),
) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        modifier = Modifier.size(56.dp),
        tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
    )
    Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
    if (!message.isNullOrBlank()) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
    // Success: "Go to channels" is the primary action. Failure: "Retry" is.
    if (onRetry != null && isError) {
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth().focusRing().autoFocusOnTv()) {
            Text(retryLabel)
        }
        OutlinedButton(onClick = onGoToChannels, modifier = Modifier.fillMaxWidth().focusRing()) {
            Text(stringResource(R.string.feature_playlist_go_to_channels))
        }
    } else {
        Button(onClick = onGoToChannels, modifier = Modifier.fillMaxWidth().focusRing().autoFocusOnTv()) {
            Text(stringResource(R.string.feature_playlist_go_to_channels))
        }
        if (onRetry != null) {
            OutlinedButton(onClick = onRetry, modifier = Modifier.fillMaxWidth().focusRing()) {
                Text(retryLabel)
            }
        }
    }
}

/** On TV the primary button takes focus when it appears, so D-pad users can act immediately. */
@Composable
internal fun Modifier.autoFocusOnTv(): Modifier {
    if (LocalFormFactor.current != FormFactor.Tv) return this
    val requester = remember { FocusRequester() }
    LaunchedEffect(requester) { runCatching { requester.requestFocus() } }
    return this.focusRequester(requester)
}

private val previewPlaylist = Playlist(id = 1, name = "My IPTV", type = PlaylistType.M3U_URL, source = "https://x")

@Preview
@Composable
private fun ImportRunningPreview() {
    StreamBoxTheme {
        ImportProgressScreen(
            state = ImportProgressUiState(previewPlaylist, ImportStatus.Running(1_200_000, 4_000_000, 812)),
            onBack = {},
            onCancel = {},
            onRetry = {},
            onGoToChannels = {},
        )
    }
}

@Preview
@Composable
private fun ImportFailedPreview() {
    StreamBoxTheme {
        ImportProgressScreen(
            state = ImportProgressUiState(previewPlaylist, ImportStatus.Failed(ImportFailure.Http(403))),
            onBack = {},
            onCancel = {},
            onRetry = {},
            onGoToChannels = {},
        )
    }
}
