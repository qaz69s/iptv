package com.streambox.player.feature.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streambox.player.core.domain.repository.ChannelOverridesRepository
import com.streambox.player.core.domain.repository.SettingsRepository
import com.streambox.player.core.model.ChannelSortOrder
import com.streambox.player.core.model.EditorChannel
import com.streambox.player.core.model.EditorGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface GroupEditorUiState {
    data object Loading : GroupEditorUiState

    data class Loaded(
        /** Null when the group is gone from the playlist's current data. */
        val group: EditorGroup?,
        val channels: List<EditorChannel>,
    ) : GroupEditorUiState
}

/**
 * List editor, level 2 (P3c): the channels of one group of one playlist, hidden ones included (user
 * decision 2: a channel hidden itself and one hidden through its group look different). Moving a
 * channel switches the browse sort to "Custom" (user decision 3), so the new order shows at once.
 */
@HiltViewModel
class GroupEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val overrides: ChannelOverridesRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val playlistId: Long = checkNotNull(savedStateHandle.get<Long>("playlistId"))
    private val groupName: String = savedStateHandle.get<String>("groupName").orEmpty()

    val uiState: StateFlow<GroupEditorUiState> = combine(
        overrides.editorGroups(playlistId),
        overrides.editorChannels(playlistId, groupName),
    ) { groups, channels ->
        GroupEditorUiState.Loaded(groups.firstOrNull { it.name == groupName }, channels)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), GroupEditorUiState.Loading)

    /** Shows / hides the channel itself; a channel of a hidden group stays hidden until the group is shown. */
    fun setHidden(channel: EditorChannel, hidden: Boolean) {
        viewModelScope.launch { overrides.setChannelHidden(channel.key, hidden) }
    }

    fun setGroupHidden(hidden: Boolean) {
        viewModelScope.launch { overrides.setGroupHidden(playlistId, groupName, hidden) }
    }

    /** A blank name restores the playlist's name. */
    fun rename(channel: EditorChannel, name: String) {
        viewModelScope.launch { overrides.renameChannel(channel.key, name) }
    }

    fun move(channel: EditorChannel, up: Boolean) {
        val channels = (uiState.value as? GroupEditorUiState.Loaded)?.channels ?: return
        val moved = channels.movedOrNull(channels.indexOfFirst { it.key == channel.key }, up) ?: return
        viewModelScope.launch {
            overrides.reorderChannels(playlistId, moved.map { it.key.uniqueKey })
            settings.setChannelSortOrder(ChannelSortOrder.Custom)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
