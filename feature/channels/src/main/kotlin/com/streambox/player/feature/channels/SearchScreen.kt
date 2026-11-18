package com.streambox.player.feature.channels

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.streambox.player.core.designsystem.component.focusRing
import com.streambox.player.core.designsystem.icon.StreamBoxIcons
import com.streambox.player.core.model.Channel
import com.streambox.player.core.model.ChannelSource
import com.streambox.player.core.model.ChannelViewMode

@Composable
internal fun SearchEntry(
    onBack: () -> Unit,
    onChannelClick: (Channel, ChannelSource) -> Unit,
    onGroupClick: (String) -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SearchScreen(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onBack = onBack,
        onChannelClick = onChannelClick,
        onGroupClick = onGroupClick,
        onToggleFavorite = viewModel::onToggleFavorite,
    )
}

/**
 * Search field (focused on entry, keyboard shown) and the results: matching groups (OK opens the group
 * in the channel browser) and channels (OK plays; the player then zaps through all channels).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SearchScreen(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onChannelClick: (Channel, ChannelSource) -> Unit,
    onGroupClick: (String) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val fieldFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(fieldFocus) { runCatching { fieldFocus.requestFocus() } }
    Column(modifier.fillMaxSize()) {
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = onBack, modifier = Modifier.focusRing()) {
                    Icon(
                        painterResource(StreamBoxIcons.Back),
                        contentDescription = stringResource(R.string.feature_channels_back),
                    )
                }
            },
            title = {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.feature_channels_search_hint)) },
                    leadingIcon = { Icon(painterResource(StreamBoxIcons.Search), contentDescription = null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }, modifier = Modifier.focusRing()) {
                                Icon(
                                    painterResource(StreamBoxIcons.Close),
                                    contentDescription = stringResource(R.string.feature_channels_search_clear),
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 16.dp)
                        .focusRequester(fieldFocus),
                )
            },
        )
        if (state.isSearching) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        } else {
            // Same height as the indicator, so the list does not jump.
            Spacer(Modifier.height(4.dp))
        }
        SearchResultsList(state, onChannelClick, onGroupClick, onToggleFavorite)
    }
}

@Composable
private fun SearchResultsList(
    state: SearchUiState,
    onChannelClick: (Channel, ChannelSource) -> Unit,
    onGroupClick: (String) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
) {
    val results = state.results
    when {
        state.query.isBlank() -> Hint(stringResource(R.string.feature_channels_search_prompt))

        results.isEmpty && !state.isSearching -> Hint(
            stringResource(R.string.feature_channels_search_empty, state.searchedQuery),
        )

        else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            if (results.groups.isNotEmpty()) {
                item(key = "groups-header") { Header(stringResource(R.string.feature_channels_search_groups)) }
                items(results.groups, key = { "g:" + it.name }) { group ->
                    val shape = RoundedCornerShape(8.dp)
                    Text(
                        text = stringResource(
                            R.string.feature_channels_group_label,
                            group.name.ifBlank { stringResource(R.string.feature_channels_group_ungrouped) },
                            group.channelCount,
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                            .focusRing(shape = shape)
                            .clip(shape)
                            .clickable(role = Role.Button, onClick = { onGroupClick(group.name) })
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    )
                }
            }
            if (results.channels.isNotEmpty()) {
                item(key = "channels-header") { Header(stringResource(R.string.feature_channels_search_channels)) }
                items(results.channels, key = { "c:" + it.id }) { channel ->
                    ChannelItem(
                        channel = channel,
                        mode = ChannelViewMode.List,
                        state = ChannelItemState(
                            isFavorite = channel.key in state.favoriteKeys,
                            allowCleartext = channel.playlistId in state.cleartextPlaylists,
                        ),
                        onClick = { onChannelClick(channel, ChannelSource.All) },
                        onToggleFavorite = { onToggleFavorite(channel) },
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(24.dp),
    )
}
