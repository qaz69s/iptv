package com.streambox.player.feature.playlist

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.streambox.player.core.designsystem.component.focusRing
import com.streambox.player.core.designsystem.icon.StreamBoxIcons
import com.streambox.player.core.designsystem.theme.StreamBoxTheme
import com.streambox.player.core.domain.usecase.AddPlaylistUseCase.DraftProblem

@Composable
internal fun AddPlaylistEntry(
    onBack: () -> Unit,
    onPlaylistAdded: (Long) -> Unit,
    viewModel: AddPlaylistViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) viewModel.onFilePicked(uri.toString(), context.displayName(uri))
    }
    LaunchedEffect(state.createdPlaylistId) {
        state.createdPlaylistId?.let {
            onPlaylistAdded(it)
            viewModel.onNavigated()
        }
    }
    AddPlaylistScreen(
        state = state,
        onBack = onBack,
        onNameChange = viewModel::onNameChange,
        onKindChange = viewModel::onKindChange,
        onUrlChange = viewModel::onUrlChange,
        onChooseFile = {
            try {
                // M3U MIME types are inconsistent across providers and file managers, so accept any file.
                picker.launch(arrayOf("*/*"))
            } catch (_: ActivityNotFoundException) {
                viewModel.onPickerUnavailable() // common on Android TV
            }
        },
        onUserAgentChange = viewModel::onUserAgentChange,
        onAllowCleartextChange = viewModel::onAllowCleartextChange,
        onSubmit = viewModel::submit,
    )
}

private fun Context.displayName(uri: Uri): String? = runCatching {
    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }
}.getOrNull()

@Composable
internal fun AddPlaylistScreen(
    state: AddPlaylistUiState,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onKindChange: (SourceKind) -> Unit,
    onUrlChange: (String) -> Unit,
    onChooseFile: () -> Unit,
    onUserAgentChange: (String) -> Unit,
    onAllowCleartextChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
) {
    Scaffold(topBar = { PlaylistTopBar(stringResource(R.string.feature_playlist_add_title), onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SourceSelector(state.kind, onKindChange)
                Text(
                    stringResource(R.string.feature_playlist_xtream_soon),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = state.name,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(R.string.feature_playlist_name_label)) },
                    singleLine = true,
                    isError = DraftProblem.NAME_MISSING in state.problems,
                    supportingText = errorText(
                        DraftProblem.NAME_MISSING in state.problems,
                        R.string.feature_playlist_name_missing,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth().focusRing(),
                )
                if (state.kind == SourceKind.URL) UrlFields(state, onUrlChange) else FileFields(state, onChooseFile)
                OutlinedTextField(
                    value = state.userAgent,
                    onValueChange = onUserAgentChange,
                    label = { Text(stringResource(R.string.feature_playlist_user_agent_label)) },
                    supportingText = { Text(stringResource(R.string.feature_playlist_user_agent_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRing(),
                )
                CleartextSwitch(state, onAllowCleartextChange)
                if (DraftProblem.TYPE_UNSUPPORTED in state.problems) {
                    Text(stringResource(R.string.feature_playlist_unsupported), color = MaterialTheme.colorScheme.error)
                }
                Button(
                    onClick = onSubmit,
                    enabled = !state.submitting,
                    modifier = Modifier.fillMaxWidth().focusRing(),
                ) {
                    if (state.submitting) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(8.dp))
                    }
                    Text(stringResource(R.string.feature_playlist_add_button))
                }
            }
        }
    }
}

@Composable
private fun SourceSelector(kind: SourceKind, onKindChange: (SourceKind) -> Unit) {
    val options = listOf(
        Triple(SourceKind.URL, R.string.feature_playlist_source_url, StreamBoxIcons.Link),
        Triple(SourceKind.FILE, R.string.feature_playlist_source_file, StreamBoxIcons.Folder),
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (option, label, icon) ->
            SegmentedButton(
                selected = kind == option,
                onClick = { onKindChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size + 1),
                icon = { Icon(painterResource(icon), contentDescription = null, Modifier.size(18.dp)) },
                modifier = Modifier.focusRing(),
            ) { Text(stringResource(label)) }
        }
        // Xtream Codes arrives in P6; shown disabled so users know it is planned.
        SegmentedButton(
            selected = false,
            onClick = {},
            enabled = false,
            shape = SegmentedButtonDefaults.itemShape(options.size, options.size + 1),
        ) { Text(stringResource(R.string.feature_playlist_source_xtream)) }
    }
}

@Composable
private fun UrlFields(state: AddPlaylistUiState, onUrlChange: (String) -> Unit) {
    val problem = when {
        DraftProblem.SOURCE_MISSING in state.problems -> R.string.feature_playlist_url_missing
        DraftProblem.URL_INVALID in state.problems -> R.string.feature_playlist_url_invalid
        DraftProblem.CLEARTEXT_NOT_ALLOWED in state.problems -> R.string.feature_playlist_http_required
        else -> null
    }
    OutlinedTextField(
        value = state.url,
        onValueChange = onUrlChange,
        label = { Text(stringResource(R.string.feature_playlist_url_label)) },
        placeholder = { Text("https://") },
        singleLine = true,
        isError = problem != null,
        supportingText = when {
            problem != null -> errorText(true, problem)

            state.isPlainHttp && !state.allowCleartext -> {
                { Text(stringResource(R.string.feature_playlist_http_required)) }
            }

            else -> null
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth().focusRing(),
    )
}

@Composable
private fun FileFields(state: AddPlaylistUiState, onChooseFile: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onChooseFile, modifier = Modifier.focusRing()) {
            Icon(painterResource(StreamBoxIcons.Folder), contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.feature_playlist_choose_file))
        }
        state.fileName?.let { Text(stringResource(R.string.feature_playlist_file_selected, it)) }
        if (state.pickerUnavailable) {
            Text(stringResource(R.string.feature_playlist_picker_unavailable), color = MaterialTheme.colorScheme.error)
        }
        if (DraftProblem.SOURCE_MISSING in state.problems) {
            Text(stringResource(R.string.feature_playlist_file_missing), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun CleartextSwitch(state: AddPlaylistUiState, onAllowCleartextChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .focusRing()
            .toggleable(value = state.allowCleartext, role = Role.Switch, onValueChange = onAllowCleartextChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.feature_playlist_allow_http), style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(R.string.feature_playlist_allow_http_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = state.allowCleartext, onCheckedChange = null)
    }
}

private fun errorText(show: Boolean, text: Int): (@Composable () -> Unit)? = if (show) {
    { Text(stringResource(text)) }
} else {
    null
}

@Preview
@Composable
private fun AddPlaylistScreenPreview() {
    StreamBoxTheme {
        AddPlaylistScreen(
            state = AddPlaylistUiState(url = "http://example.com/list.m3u"),
            onBack = {},
            onNameChange = {},
            onKindChange = {},
            onUrlChange = {},
            onChooseFile = {},
            onUserAgentChange = {},
            onAllowCleartextChange = {},
            onSubmit = {},
        )
    }
}
