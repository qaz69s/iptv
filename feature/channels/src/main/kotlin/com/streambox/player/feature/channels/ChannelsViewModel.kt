package com.streambox.player.feature.channels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.streambox.player.core.domain.channel.ChannelSorting
import com.streambox.player.core.domain.repository.ChannelOverridesRepository
import com.streambox.player.core.domain.repository.ChannelRepository
import com.streambox.player.core.domain.repository.CustomGroupsRepository
import com.streambox.player.core.domain.repository.FavoritesRepository
import com.streambox.player.core.domain.repository.PlaylistRepository
import com.streambox.player.core.domain.repository.SettingsRepository
import com.streambox.player.core.domain.repository.WatchHistoryRepository
import com.streambox.player.core.domain.usecase.ToggleFavoriteUseCase
import com.streambox.player.core.model.BrowseSettings
import com.streambox.player.core.model.Channel
import com.streambox.player.core.model.ChannelGroupSummary
import com.streambox.player.core.model.ChannelKey
import com.streambox.player.core.model.ChannelSortOrder
import com.streambox.player.core.model.ChannelSource
import com.streambox.player.core.model.ChannelViewMode
import com.streambox.player.core.model.CustomGroup
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface ChannelsUiState {
    data object Loading : ChannelsUiState

    /** Fresh install: nothing is bundled, the user must add a playlist first. */
    data object NoPlaylists : ChannelsUiState

    /** Playlists exist but none has channels yet (first import running or failed). */
    data object NoChannels : ChannelsUiState

    data class Content(
        val groups: List<ChannelGroupSummary>,
        val totalChannels: Int,
        /** The group being browsed; null = all channels or a virtual group (see [source]). */
        val selectedGroup: String?,
        /** Playlists that allow plain HTTP; their channels' logos may load over http (K1). */
        val cleartextPlaylists: Set<Long> = emptySet(),
        /** What the list shows (requirements §4.2: all, a group, or the virtual favorites / recently watched). */
        val source: ChannelSource = selectedGroup?.let { ChannelSource.Group(it) } ?: ChannelSource.All,
        val favoriteCount: Int = 0,
        val recentCount: Int = 0,
        /** Stable keys of every favorite, for the star on each channel. */
        val favoriteKeys: Set<ChannelKey> = emptySet(),
        val viewMode: ChannelViewMode = ChannelViewMode.List,
        val sortOrder: ChannelSortOrder = ChannelSortOrder.Default,
        /** The user's own groups (P3c), listed after the virtual groups. */
        val customGroups: List<CustomGroup> = emptyList(),
    ) : ChannelsUiState
}

/** User data shown next to the channel data; combined first to stay within combine's typed arity. */
private data class Library(
    val favorites: List<Channel>,
    val favoriteKeys: Set<ChannelKey>,
    val recent: List<Channel>,
    val browse: BrowseSettings,
    val customGroups: List<CustomGroup>,
)

/**
 * Channel browser: groups, the virtual groups "favorites" / "recently watched", view mode and sort order
 * (persisted in DataStore). The selection lives in [SavedStateHandle] (`selectedGroup` for a group,
 * `selectedVirtual` for a virtual group), so it survives process death and search can set it.
 */
@HiltViewModel
@Suppress("LongParameterList") // Injected repositories of one screen.
class ChannelsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    playlistRepository: PlaylistRepository,
    private val channelRepository: ChannelRepository,
    private val favoritesRepository: FavoritesRepository,
    private val watchHistoryRepository: WatchHistoryRepository,
    private val customGroupsRepository: CustomGroupsRepository,
    private val settingsRepository: SettingsRepository,
    private val toggleFavorite: ToggleFavoriteUseCase,
    private val channelOverrides: ChannelOverridesRepository,
) : ViewModel() {
    private val selectedGroup: StateFlow<String?> = savedStateHandle.getStateFlow(KEY_GROUP, null)
    private val selectedVirtual: StateFlow<String?> = savedStateHandle.getStateFlow(KEY_VIRTUAL, null)

    private val browse: Flow<BrowseSettings> = settingsRepository.settings.map { it.browse }.distinctUntilChanged()

    private val library: Flow<Library> = combine(
        favoritesRepository.favorites,
        favoritesRepository.favoriteKeys,
        watchHistoryRepository.history.map { entries -> entries.map { it.channel } },
        browse,
        customGroupsRepository.groups,
        ::Library,
    )

    /**
     * The source actually shown: a saved group that vanished after a refresh falls back to "all",
     * matching the chips (the saved choice comes back if the group reappears).
     */
    private val effectiveSource: Flow<ChannelSource> = combine(
        selectedGroup,
        selectedVirtual,
        channelRepository.groups,
        customGroupsRepository.groups,
    ) { group, virtual, groups, customGroups ->
        val customId = virtual
            ?.takeIf { it.startsWith(VIRTUAL_CUSTOM_PREFIX) }
            ?.removePrefix(VIRTUAL_CUSTOM_PREFIX)
            ?.toLongOrNull()
        when {
            virtual == VIRTUAL_FAVORITES -> ChannelSource.Favorites
            virtual == VIRTUAL_RECENT -> ChannelSource.Recent
            customId != null && customGroups.any { it.id == customId } -> ChannelSource.Custom(customId)
            group != null && groups.any { it.name == group } -> ChannelSource.Group(group)
            else -> ChannelSource.All
        }
    }.distinctUntilChanged()

    val uiState: StateFlow<ChannelsUiState> = combine(
        playlistRepository.playlists,
        channelRepository.groups,
        channelRepository.channelCount,
        effectiveSource,
        library,
    ) { playlists, groups, count, source, library ->
        when {
            playlists.isEmpty() -> ChannelsUiState.NoPlaylists

            count == 0 -> ChannelsUiState.NoChannels

            else -> ChannelsUiState.Content(
                groups = groups,
                totalChannels = count,
                selectedGroup = (source as? ChannelSource.Group)?.name,
                cleartextPlaylists = playlists.filter { it.allowCleartext }.mapTo(mutableSetOf()) { it.id },
                source = source,
                favoriteCount = library.favorites.size,
                recentCount = library.recent.size,
                favoriteKeys = library.favoriteKeys,
                viewMode = library.browse.viewMode,
                sortOrder = library.browse.sortOrder,
                customGroups = library.customGroups,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ChannelsUiState.Loading)

    /** The list of the selected source in the chosen order (recently watched: always newest first). */
    @OptIn(ExperimentalCoroutinesApi::class)
    val channels: Flow<PagingData<Channel>> = combine(effectiveSource, browse.map { it.sortOrder }) { source, sort ->
        source to sort
    }
        .distinctUntilChanged()
        .flatMapLatest { (source, sort) ->
            when (source) {
                ChannelSource.All -> channelRepository.pagedChannels(null, sort)

                is ChannelSource.Group -> channelRepository.pagedChannels(source.name, sort)

                ChannelSource.Favorites -> favoritesRepository.favorites.map { list ->
                    PagingData.from(ChannelSorting.sort(list, sort))
                }

                ChannelSource.Recent -> watchHistoryRepository.history.map { entries ->
                    PagingData.from(entries.map { it.channel })
                }

                is ChannelSource.Custom -> customGroupsRepository.channels(source.groupId).map { list ->
                    PagingData.from(ChannelSorting.sort(list, sort))
                }
            }
        }
        .cachedIn(viewModelScope)

    fun onSourceSelected(source: ChannelSource) {
        savedStateHandle[KEY_GROUP] = (source as? ChannelSource.Group)?.name
        savedStateHandle[KEY_VIRTUAL] = when (source) {
            ChannelSource.Favorites -> VIRTUAL_FAVORITES
            ChannelSource.Recent -> VIRTUAL_RECENT
            is ChannelSource.Custom -> VIRTUAL_CUSTOM_PREFIX + source.groupId
            else -> null
        }
    }

    /** Kept for callers that pick a group by name; null = all channels. */
    fun onGroupSelected(groupName: String?) = onSourceSelected(
        groupName?.let {
            ChannelSource.Group(it)
        } ?: ChannelSource.All,
    )

    fun onViewModeSelected(mode: ChannelViewMode) {
        viewModelScope.launch { settingsRepository.setChannelViewMode(mode) }
    }

    fun onSortOrderSelected(order: ChannelSortOrder) {
        viewModelScope.launch { settingsRepository.setChannelSortOrder(order) }
    }

    fun onToggleFavorite(channel: Channel) {
        viewModelScope.launch { toggleFavorite(channel.key) }
    }

    /** Quick hide from the channel menu (P3c); the list editor shows it again. */
    fun onHideChannel(channel: Channel) {
        viewModelScope.launch { channelOverrides.setChannelHidden(channel.key, true) }
    }

    /** Quick rename from the channel menu; a blank name restores the playlist's name. */
    fun onRenameChannel(channel: Channel, name: String) {
        viewModelScope.launch { channelOverrides.renameChannel(channel.key, name) }
    }

    /** Ids of the custom groups the channel is in (for the "Add to group" dialog). */
    fun customGroupsOf(channel: Channel): Flow<Set<Long>> = customGroupsRepository.groupsOf(channel.key)

    fun onSetInCustomGroup(groupId: Long, channel: Channel, member: Boolean) {
        viewModelScope.launch {
            if (member) {
                customGroupsRepository.addChannel(groupId, channel.key)
            } else {
                customGroupsRepository.removeChannel(groupId, channel.key)
            }
        }
    }

    /** Creates a custom group (or reuses one of that name) and adds the channel to it. */
    fun onCreateCustomGroup(name: String, channel: Channel) {
        viewModelScope.launch {
            customGroupsRepository.create(name)?.let { customGroupsRepository.addChannel(it, channel.key) }
        }
    }

    internal companion object {
        const val KEY_GROUP = "selectedGroup"
        const val KEY_VIRTUAL = "selectedVirtual"
        const val VIRTUAL_FAVORITES = "favorites"
        const val VIRTUAL_RECENT = "recent"

        /** A custom group is saved as this prefix plus its id. */
        const val VIRTUAL_CUSTOM_PREFIX = "custom:"
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
