package com.streambox.player.feature.playlist

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streambox.player.core.domain.repository.CustomGroupsRepository
import com.streambox.player.core.domain.repository.SettingsRepository
import com.streambox.player.core.model.Channel
import com.streambox.player.core.model.ChannelSortOrder
import com.streambox.player.core.model.CustomGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Null until loaded. */
data class CustomGroupsUiState(val groups: List<CustomGroup>? = null)

/** The user's custom groups (P3c): create, rename, delete, move up / down. */
@HiltViewModel
class CustomGroupsViewModel @Inject constructor(private val repository: CustomGroupsRepository) : ViewModel() {
    val uiState: StateFlow<CustomGroupsUiState> = repository.groups
        .map { CustomGroupsUiState(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CustomGroupsUiState())

    fun create(name: String) {
        viewModelScope.launch { repository.create(name) }
    }

    fun rename(group: CustomGroup, name: String) {
        viewModelScope.launch { repository.rename(group.id, name) }
    }

    fun delete(group: CustomGroup) {
        viewModelScope.launch { repository.delete(group.id) }
    }

    fun move(group: CustomGroup, up: Boolean) {
        val groups = uiState.value.groups ?: return
        val moved = groups.movedOrNull(groups.indexOfFirst { it.id == group.id }, up) ?: return
        viewModelScope.launch { repository.reorderGroups(moved.map { it.id }) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

data class CustomGroupEditorUiState(
    /** Null while loading or after the group was deleted. */
    val group: CustomGroup? = null,
    val channels: List<Channel> = emptyList(),
    val loaded: Boolean = false,
)

/**
 * The channels of one custom group: remove, move up / down. Moving switches the browse sort to
 * "Custom" (user decision 3), because the custom groups keep their own order only in that sort.
 */
@HiltViewModel
class CustomGroupEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CustomGroupsRepository,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val groupId: Long = checkNotNull(savedStateHandle.get<Long>("groupId"))

    val uiState: StateFlow<CustomGroupEditorUiState> = combine(
        repository.groups,
        repository.channels(groupId),
    ) { groups, channels ->
        CustomGroupEditorUiState(groups.firstOrNull { it.id == groupId }, channels, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CustomGroupEditorUiState())

    fun remove(channel: Channel) {
        viewModelScope.launch { repository.removeChannel(groupId, channel.key) }
    }

    fun move(channel: Channel, up: Boolean) {
        val channels = uiState.value.channels
        val moved = channels.movedOrNull(channels.indexOfFirst { it.key == channel.key }, up) ?: return
        viewModelScope.launch {
            repository.reorderChannels(groupId, moved.map { it.key })
            settings.setChannelSortOrder(ChannelSortOrder.Custom)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
