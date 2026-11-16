package com.streambox.player.feature.playlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streambox.player.core.domain.usecase.AddPlaylistUseCase
import com.streambox.player.core.domain.usecase.AddPlaylistUseCase.DraftProblem
import com.streambox.player.core.model.PlaylistDraft
import com.streambox.player.core.model.PlaylistType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SourceKind { URL, FILE }

data class AddPlaylistUiState(
    val name: String = "",
    val kind: SourceKind = SourceKind.URL,
    val url: String = "",
    val fileUri: String? = null,
    val fileName: String? = null,
    val userAgent: String = "",
    val allowCleartext: Boolean = false,
    val problems: Set<DraftProblem> = emptySet(),
    val submitting: Boolean = false,
    val pickerUnavailable: Boolean = false,
    /**
     * Set once the playlist is stored; the screen navigates to the progress page and then calls
     * [AddPlaylistViewModel.onNavigated].
     */
    val createdPlaylistId: Long? = null,
) {
    val isPlainHttp: Boolean get() = kind == SourceKind.URL && url.trim().startsWith("http://", ignoreCase = true)
}

@HiltViewModel
class AddPlaylistViewModel @Inject constructor(private val addPlaylist: AddPlaylistUseCase) : ViewModel() {
    private val _uiState = MutableStateFlow(AddPlaylistUiState())
    val uiState: StateFlow<AddPlaylistUiState> = _uiState.asStateFlow()

    fun onNameChange(value: String) = _uiState.update {
        it.copy(
            name = value,
            problems =
                it.problems - DraftProblem.NAME_MISSING,
        )
    }

    fun onKindChange(kind: SourceKind) = _uiState.update { it.copy(kind = kind, problems = emptySet()) }

    fun onUrlChange(value: String) = _uiState.update {
        it.copy(
            url = value,
            problems =
                it.problems -
                    setOf(DraftProblem.SOURCE_MISSING, DraftProblem.URL_INVALID, DraftProblem.CLEARTEXT_NOT_ALLOWED),
        )
    }

    fun onFilePicked(uri: String, displayName: String?) = _uiState.update {
        val suggestedName = it.name.ifBlank { displayName?.substringBeforeLast('.').orEmpty() }
        it.copy(
            fileUri = uri,
            fileName = displayName ?: uri,
            name = suggestedName,
            pickerUnavailable = false,
            problems = it.problems - setOf(DraftProblem.SOURCE_MISSING, DraftProblem.NAME_MISSING),
        )
    }

    fun onPickerUnavailable() = _uiState.update { it.copy(pickerUnavailable = true) }

    fun onUserAgentChange(value: String) = _uiState.update { it.copy(userAgent = value) }

    fun onAllowCleartextChange(allowed: Boolean) = _uiState.update {
        it.copy(allowCleartext = allowed, problems = it.problems - DraftProblem.CLEARTEXT_NOT_ALLOWED)
    }

    fun submit() {
        val state = _uiState.value
        if (state.submitting) return
        val draft = PlaylistDraft(
            name = state.name,
            type = if (state.kind == SourceKind.URL) PlaylistType.M3U_URL else PlaylistType.M3U_FILE,
            source = if (state.kind == SourceKind.URL) state.url else state.fileUri.orEmpty(),
            userAgent = state.userAgent,
            allowCleartext = state.allowCleartext,
        )
        _uiState.update { it.copy(submitting = true) }
        viewModelScope.launch {
            when (val result = addPlaylist(draft)) {
                is AddPlaylistUseCase.Result.Added ->
                    _uiState.update { it.copy(submitting = false, createdPlaylistId = result.playlistId) }

                is AddPlaylistUseCase.Result.Invalid ->
                    _uiState.update { it.copy(submitting = false, problems = result.problems) }
            }
        }
    }

    /** Resets the form after navigating away, so Back does not re-trigger navigation. */
    fun onNavigated() = _uiState.update { AddPlaylistUiState() }
}
