package com.streamdeck.player.feature.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.streamdeck.player.core.data.EditorRepository
import com.streamdeck.player.core.database.ChannelEntity
import com.streamdeck.player.core.database.ChannelGroupEntity
import com.streamdeck.player.core.designsystem.ActionPanel
import com.streamdeck.player.core.designsystem.ChannelLogo
import com.streamdeck.player.core.designsystem.EmptyState
import com.streamdeck.player.core.designsystem.FocusSurface
import com.streamdeck.player.core.designsystem.IconTile
import com.streamdeck.player.core.designsystem.InputDialog
import com.streamdeck.player.core.designsystem.KeyHint
import com.streamdeck.player.core.designsystem.OutlineTag
import com.streamdeck.player.core.designsystem.PanelAction
import com.streamdeck.player.core.designsystem.SdButton
import com.streamdeck.player.core.designsystem.SdButtonStyle
import com.streamdeck.player.core.designsystem.SdIconButton
import com.streamdeck.player.core.designsystem.SdTheme
import com.streamdeck.player.core.designsystem.SdTopBar
import com.streamdeck.player.core.designsystem.rememberIsTv
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.streamdeck.player.core.designsystem.R as DR

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlaylistEditorViewModel @Inject constructor(savedState: SavedStateHandle, private val repo: EditorRepository) : ViewModel() {
    val playlistId: Long = checkNotNull(savedState[ARG_PLAYLIST_ID])
    val groups: StateFlow<List<ChannelGroupEntity>> = repo.groups(playlistId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val selectedGroup = MutableStateFlow<ChannelGroupEntity?>(null)
    val channels: StateFlow<List<ChannelEntity>> = selectedGroup.flatMapLatest { g ->
        if (g == null) flowOf(emptyList()) else repo.groupChannels(playlistId, g.id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun launch(block: suspend () -> Unit) { viewModelScope.launch { block() } }

    fun toggleGroupHidden(g: ChannelGroupEntity) = launch { repo.setGroupHidden(g, !g.hidden) }
    fun toggleGroupLocked(g: ChannelGroupEntity) = launch { repo.setGroupLocked(g, !g.locked) }
    fun renameGroup(g: ChannelGroupEntity, name: String) = launch { repo.renameGroup(g, name) }
    fun moveGroup(g: ChannelGroupEntity, delta: Int) = launch { repo.moveGroup(groups.value, g, delta) }
    fun createGroup(name: String) = launch { repo.createCustomGroup(playlistId, name) }
    fun toggleChannelHidden(c: ChannelEntity) = launch { repo.setChannelHidden(c, !c.hidden) }
    fun toggleChannelLocked(c: ChannelEntity) = launch { repo.setChannelLocked(c, !c.locked) }
    fun renameChannel(c: ChannelEntity, name: String) = launch { repo.renameChannel(c, name) }
    fun moveChannel(c: ChannelEntity, delta: Int) = launch { repo.moveChannel(channels.value, c, delta) }
    fun moveToGroup(c: ChannelEntity, g: ChannelGroupEntity) = launch { repo.moveChannelToGroup(c, g) }

    companion object { const val ARG_PLAYLIST_ID = "playlistId" }
}

private sealed interface EditorDialog {
    data class GroupActions(val g: ChannelGroupEntity) : EditorDialog
    data class ChannelActions(val c: ChannelEntity) : EditorDialog
    data class PickGroup(val c: ChannelEntity) : EditorDialog
    data class RenameGroup(val g: ChannelGroupEntity) : EditorDialog
    data class RenameChannel(val c: ChannelEntity) : EditorDialog
    data object NewGroup : EditorDialog
}

/**
 * Two-level editor (spec §4.1 列表编辑器): groups → channels. Hide / rename / lock / reorder / move live in an action
 * panel — a bottom sheet on phones, a right-hand side panel on TV.
 */
@Composable
fun PlaylistEditorScreen(onBack: () -> Unit, vm: PlaylistEditorViewModel = hiltViewModel()) {
    val groups by vm.groups.collectAsStateWithLifecycle()
    val selected by vm.selectedGroup.collectAsStateWithLifecycle()
    val channels by vm.channels.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<EditorDialog?>(null) }
    val tv = rememberIsTv()
    // Phone: BACK in the channel level returns to the groups first. TV shows both levels side by side.
    BackHandler(enabled = !tv && selected != null) { vm.selectedGroup.value = null }

    if (tv) {
        TvEditor(groups, selected, channels, onSelect = { vm.selectedGroup.value = it }, onDialog = { dialog = it })
    } else {
        PhoneEditor(groups, selected, channels, onBack = { if (selected != null) vm.selectedGroup.value = null else onBack() },
            onSelect = { vm.selectedGroup.value = it }, onDialog = { dialog = it })
    }
    EditorDialogs(dialog, groups, channels, vm) { dialog = it }
}

// ------------------------------------------------------------------------------------------- phone

@Composable
private fun PhoneEditor(
    groups: List<ChannelGroupEntity>,
    selected: ChannelGroupEntity?,
    channels: List<ChannelEntity>,
    onBack: () -> Unit,
    onSelect: (ChannelGroupEntity?) -> Unit,
    onDialog: (EditorDialog) -> Unit,
) {
    val p = SdTheme.palette
    Column(Modifier.fillMaxSize()) {
        SdTopBar(
            title = selected?.name ?: stringResource(R.string.editor_title),
            onBack = onBack,
            subtitle = if (selected == null) stringResource(R.string.group_count, groups.size) else stringResource(R.string.channel_count, channels.size),
        ) {
            if (selected == null) {
                SdIconButton(Icons.Default.CreateNewFolder, stringResource(R.string.new_group), { onDialog(EditorDialog.NewGroup) }, container = p.accent,
                    tint = p.onAccent)
            }
        }
        Text(
            stringResource(if (selected == null) R.string.editor_hint_groups else R.string.editor_hint_channels),
            color = p.textTertiary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (selected == null) {
                if (groups.isEmpty()) {
                    EmptyState(stringResource(R.string.editor_empty), icon = Icons.Outlined.Folder)
                } else {
                    EditorList {
                        items(groups, key = { it.id }) { g ->
                            EditorRow(
                                title = g.name, hidden = g.hidden, locked = g.locked, leading = { IconTile(Icons.Outlined.Folder, p.hueBlue) },
                                onClick = { onSelect(g) }, onMore = { onDialog(EditorDialog.GroupActions(g)) }, chevron = true,
                            )
                        }
                    }
                }
            } else if (channels.isEmpty()) {
                EmptyState(stringResource(R.string.group_empty), icon = Icons.Outlined.Tv)
            } else {
                EditorList {
                    items(channels, key = { it.id }) { c ->
                        EditorRow(
                            title = c.name, number = c.number, hidden = c.hidden, locked = c.locked,
                            leading = { ChannelLogo(c.logoUrl, c.name, 40.dp) },
                            onClick = { onDialog(EditorDialog.ChannelActions(c)) }, onMore = { onDialog(EditorDialog.ChannelActions(c)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorList(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    val p = SdTheme.palette
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp).background(p.card, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
        contentPadding = PaddingValues(vertical = 6.dp),
        content = content,
    )
}

@Composable
private fun EditorRow(
    title: String,
    hidden: Boolean,
    locked: Boolean,
    leading: @Composable () -> Unit,
    onClick: () -> Unit,
    onMore: () -> Unit,
    number: Int? = null,
    chevron: Boolean = false,
) {
    val p = SdTheme.palette
    FocusSurface(onClick = onClick, onLongClick = onMore, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(0.dp), container = Color.Transparent,
        contentDescription = title) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (number != null) Text(number.toString(), color = p.textTertiary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(40.dp))
            Box(Modifier.alpha(if (hidden) 0.45f else 1f)) { leading() }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(title, color = if (hidden) p.textTertiary else p.textPrimary, style = MaterialTheme.typography.bodyLarge, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                StateTags(hidden, locked, Modifier.padding(top = 4.dp))
            }
            SdIconButton(Icons.Default.MoreHoriz, stringResource(DR.string.ds_more), onMore, size = 40.dp, container = Color.Transparent,
                tint = p.textSecondary)
            if (chevron) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = p.textTertiary, modifier = Modifier.padding(end = 6.dp).size(20.dp))
        }
    }
}

@Composable
private fun StateTags(hidden: Boolean, locked: Boolean, modifier: Modifier = Modifier, onFocus: Boolean = false) {
    if (!hidden && !locked) return
    val p = SdTheme.palette
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (hidden) OutlineTag(stringResource(R.string.tag_hidden), color = if (onFocus) p.onFocusFill else p.textTertiary)
        if (locked) OutlineTag(stringResource(R.string.tag_locked), color = if (onFocus) p.onFocusFill else p.hueAmber)
    }
}

// ------------------------------------------------------------------------------------------- TV

@Composable
private fun TvEditor(
    groups: List<ChannelGroupEntity>,
    selected: ChannelGroupEntity?,
    channels: List<ChannelEntity>,
    onSelect: (ChannelGroupEntity?) -> Unit,
    onDialog: (EditorDialog) -> Unit,
) {
    val p = SdTheme.palette
    Row(Modifier.fillMaxSize().padding(start = 56.dp, end = 48.dp, top = 40.dp, bottom = 28.dp)) {
        Column(Modifier.width(460.dp).fillMaxHeight()) {
            Text(stringResource(R.string.editor_title), style = MaterialTheme.typography.displaySmall, color = p.textPrimary)
            Text(stringResource(R.string.group_count, groups.size), style = MaterialTheme.typography.titleMedium, color = p.textSecondary)
            SdButton(
                stringResource(R.string.new_group), { onDialog(EditorDialog.NewGroup) }, Modifier.padding(top = 18.dp),
                icon = Icons.Default.CreateNewFolder, style = SdButtonStyle.Secondary, large = true,
            )
            if (groups.isEmpty()) {
                EmptyState(stringResource(R.string.editor_empty), Modifier.weight(1f), icon = Icons.Outlined.Folder)
            } else {
                LazyColumn(Modifier.weight(1f).padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(4.dp)) {
                    items(groups, key = { it.id }) { g ->
                        TvEditorItem(
                            title = g.name, hidden = g.hidden, locked = g.locked, selected = selected?.id == g.id,
                            leading = { IconTile(Icons.Outlined.Folder, p.hueBlue) },
                            onClick = { onSelect(g) }, onLongClick = { onDialog(EditorDialog.GroupActions(g)) },
                        )
                    }
                }
            }
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                KeyHint(stringResource(R.string.key_ok), stringResource(R.string.hint_open_group))
                KeyHint(stringResource(R.string.key_hold_ok), stringResource(R.string.hint_more_actions))
            }
        }
        Column(
            Modifier.padding(start = 32.dp).weight(1f).fillMaxHeight().background(p.card, RoundedCornerShape(28.dp))
                .border(1.dp, p.stroke, RoundedCornerShape(28.dp)).padding(24.dp),
        ) {
            if (selected == null) {
                EmptyState(stringResource(R.string.editor_pick_group), icon = Icons.Outlined.Tv)
            } else {
                Text(selected.name, style = MaterialTheme.typography.headlineSmall, color = p.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.channel_count, channels.size), style = MaterialTheme.typography.bodyLarge, color = p.textSecondary)
                if (channels.isEmpty()) {
                    EmptyState(stringResource(R.string.group_empty), Modifier.weight(1f), icon = Icons.Outlined.Tv)
                } else {
                    LazyColumn(Modifier.weight(1f).padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(4.dp)) {
                        items(channels, key = { it.id }) { c ->
                            TvEditorItem(
                                title = listOfNotNull(c.number?.toString(), c.name).joinToString("  "), hidden = c.hidden, locked = c.locked, selected = false,
                                leading = { ChannelLogo(c.logoUrl, c.name, 40.dp) },
                                onClick = { onDialog(EditorDialog.ChannelActions(c)) }, onLongClick = { onDialog(EditorDialog.ChannelActions(c)) },
                            )
                        }
                    }
                    Row(Modifier.padding(top = 10.dp)) { KeyHint(stringResource(R.string.key_ok), stringResource(R.string.hint_edit_channel)) }
                }
            }
        }
    }
}

@Composable
private fun TvEditorItem(
    title: String,
    hidden: Boolean,
    locked: Boolean,
    selected: Boolean,
    leading: @Composable () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val p = SdTheme.palette
    FocusSurface(
        onClick = onClick, onLongClick = onLongClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        container = if (selected) p.accent.copy(alpha = 0.14f) else p.card, border = if (selected) p.accent else null,
        focusFill = true, focusScale = 1.02f, contentDescription = title,
    ) { focused ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.alpha(if (hidden) 0.45f else 1f)) { leading() }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(
                    title, color = when {
                        focused -> p.onFocusFill
                        hidden -> p.textTertiary
                        else -> p.textPrimary
                    },
                    style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                StateTags(hidden, locked, Modifier.padding(top = 4.dp), onFocus = focused)
            }
        }
    }
}

// ------------------------------------------------------------------------------------------- dialogs

@Composable
private fun EditorDialogs(
    dialog: EditorDialog?,
    groups: List<ChannelGroupEntity>,
    channels: List<ChannelEntity>,
    vm: PlaylistEditorViewModel,
    set: (EditorDialog?) -> Unit,
) {
    when (dialog) {
        is EditorDialog.GroupActions -> {
            val g = dialog.g
            val i = groups.indexOfFirst { it.id == g.id }
            ActionPanel(
                title = g.name, subtitle = stringResource(R.string.tab_groups), onDismiss = { set(null) },
                actions = listOfNotNull(
                    PanelAction(stringResource(R.string.rename), Icons.Default.DriveFileRenameOutline) { set(EditorDialog.RenameGroup(g)) },
                    PanelAction(stringResource(if (g.hidden) R.string.show else R.string.hide),
                        if (g.hidden) Icons.Default.Visibility else Icons.Default.VisibilityOff) { vm.toggleGroupHidden(g) },
                    PanelAction(stringResource(if (g.locked) R.string.unlock else R.string.lock),
                        if (g.locked) Icons.Default.LockOpen else Icons.Default.Lock, hint = stringResource(R.string.lock_hint)) { vm.toggleGroupLocked(g) },
                    PanelAction(stringResource(R.string.move_up), Icons.Default.ArrowUpward) { vm.moveGroup(g, -1) }.takeIf { i > 0 },
                    PanelAction(stringResource(R.string.move_down), Icons.Default.ArrowDownward) { vm.moveGroup(g,
                        1) }.takeIf { i in 0 until groups.lastIndex },
                ),
            )
        }
        is EditorDialog.ChannelActions -> {
            val c = dialog.c
            val i = channels.indexOfFirst { it.id == c.id }
            ActionPanel(
                title = c.name, subtitle = stringResource(R.string.tab_channels), onDismiss = { set(null) },
                actions = listOfNotNull(
                    PanelAction(stringResource(R.string.rename), Icons.Default.DriveFileRenameOutline) { set(EditorDialog.RenameChannel(c)) },
                    PanelAction(stringResource(if (c.hidden) R.string.show else R.string.hide),
                        if (c.hidden) Icons.Default.Visibility else Icons.Default.VisibilityOff) { vm.toggleChannelHidden(c) },
                    PanelAction(stringResource(if (c.locked) R.string.unlock else R.string.lock),
                        if (c.locked) Icons.Default.LockOpen else Icons.Default.Lock, hint = stringResource(R.string.lock_hint)) { vm.toggleChannelLocked(c) },
                    PanelAction(stringResource(R.string.move_to_group), Icons.Default.DriveFileMove) { set(EditorDialog.PickGroup(c)) },
                    PanelAction(stringResource(R.string.move_up), Icons.Default.ArrowUpward) { vm.moveChannel(c, -1) }.takeIf { i > 0 },
                    PanelAction(stringResource(R.string.move_down), Icons.Default.ArrowDownward) { vm.moveChannel(c,
                        1) }.takeIf { i in 0 until channels.lastIndex },
                ),
            )
        }
        is EditorDialog.PickGroup -> ActionPanel(
            title = stringResource(R.string.move_to_group), subtitle = dialog.c.name, onDismiss = { set(null) },
            actions = groups.map { g -> PanelAction(g.name, Icons.Outlined.Folder, selected = g.id == dialog.c.groupId) { vm.moveToGroup(dialog.c, g) } },
        )
        is EditorDialog.RenameGroup -> InputDialog(stringResource(R.string.rename), dialog.g.name, { set(null) }, { vm.renameGroup(dialog.g, it); set(null) },
            label = stringResource(R.string.playlist_name))
        is EditorDialog.RenameChannel -> InputDialog(stringResource(R.string.rename), dialog.c.name, { set(null) }, { vm.renameChannel(dialog.c,
            it); set(null) }, label = stringResource(R.string.playlist_name))
        EditorDialog.NewGroup -> InputDialog(stringResource(R.string.new_group), "", { set(null) }, { vm.createGroup(it); set(null) },
            label = stringResource(R.string.playlist_name))
        null -> Unit
    }
}
