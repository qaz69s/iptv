package com.streamdeck.player.feature.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.streamdeck.player.core.data.ChannelRepository
import com.streamdeck.player.core.data.PlaylistRepository
import com.streamdeck.player.core.database.PlaylistEntity
import com.streamdeck.player.core.database.PlaylistType
import com.streamdeck.player.core.designsystem.ActionPanel
import com.streamdeck.player.core.designsystem.ConfirmDialog
import com.streamdeck.player.core.designsystem.EmptyState
import com.streamdeck.player.core.designsystem.FocusSurface
import com.streamdeck.player.core.designsystem.IconTile
import com.streamdeck.player.core.designsystem.LoadingState
import com.streamdeck.player.core.designsystem.PanelAction
import com.streamdeck.player.core.designsystem.SdButton
import com.streamdeck.player.core.designsystem.SdButtonStyle
import com.streamdeck.player.core.designsystem.SdIconButton
import com.streamdeck.player.core.designsystem.SdSwitch
import com.streamdeck.player.core.designsystem.SdTheme
import com.streamdeck.player.core.designsystem.SdTopBar
import com.streamdeck.player.core.designsystem.SwitchRow
import com.streamdeck.player.core.designsystem.onFocusGained
import com.streamdeck.player.core.designsystem.rememberIsTv
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject
import com.streamdeck.player.core.designsystem.R as DR

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlaylistManagerViewModel @Inject constructor(private val repo: PlaylistRepository, channels: ChannelRepository) : ViewModel() {
    val playlists: StateFlow<List<PlaylistEntity>?> = repo.observePlaylists().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Visible channel count per playlist for the cards. */
    val counts: StateFlow<Map<Long, Int>> = repo.observePlaylists().flatMapLatest { list ->
        if (list.isEmpty()) flowOf(emptyMap()) else combine(list.map { pl -> channels.visibleCount(pl.id).map { pl.id to it } }) { it.toMap() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** playlistId -> progress / error message of a running refresh. */
    private val _status = MutableStateFlow<Map<Long, String>>(emptyMap())
    val status: StateFlow<Map<Long, String>> = _status

    fun setEnabled(p: PlaylistEntity, enabled: Boolean) { viewModelScope.launch { repo.setEnabled(p.id, enabled) } }
    fun move(p: PlaylistEntity, delta: Int) { viewModelScope.launch { repo.move(p.id, delta) } }
    fun delete(p: PlaylistEntity) { viewModelScope.launch { repo.delete(p.id) } }
    fun save(p: PlaylistEntity) { viewModelScope.launch { repo.update(p) } }

    fun refresh(p: PlaylistEntity, progress: (Int) -> String, failed: (String) -> String) {
        viewModelScope.launch {
            val result = runCatching { repo.import(p.id) { n -> _status.value = _status.value + (p.id to progress(n)) } }
            _status.value = if (result.isSuccess) _status.value - p.id else _status.value + (p.id to failed(result.exceptionOrNull()?.message.orEmpty()))
        }
    }
}

/** Callbacks of one playlist card / panel, built once per item. */
private class PlaylistActions(
    val refresh: () -> Unit,
    val edit: () -> Unit,
    val editor: () -> Unit,
    val toggle: (Boolean) -> Unit,
    val up: () -> Unit,
    val down: () -> Unit,
    val delete: () -> Unit,
)

/**
 * Add / edit / delete / reorder / enable playlists (spec §4.1). Phone: cards with a drag handle (or 上移/下移 in the
 * “更多” sheet). TV: focusable list on the left, details and large action buttons of the focused playlist on the right.
 */
@Composable
fun PlaylistManagerScreen(
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpenEditor: (Long) -> Unit,
    editDialog: @Composable (PlaylistEntity, onDismiss: () -> Unit, onSave: (PlaylistEntity) -> Unit) -> Unit = { p, dismiss, save -> PlaylistEditDialog(p,
        dismiss, save) },
    vm: PlaylistManagerViewModel = hiltViewModel(),
) {
    val playlists by vm.playlists.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val counts by vm.counts.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<PlaylistEntity?>(null) }
    var deleting by remember { mutableStateOf<PlaylistEntity?>(null) }
    var more by remember { mutableStateOf<PlaylistEntity?>(null) }
    val progressFmt = stringResource(R.string.importing)
    val failedFmt = stringResource(R.string.import_failed)
    val tv = rememberIsTv()

    fun actionsFor(p: PlaylistEntity) = PlaylistActions(
        refresh = { vm.refresh(p, { progressFmt.format(it) }, { failedFmt.format(it) }) },
        edit = { editing = p },
        editor = { onOpenEditor(p.id) },
        toggle = { vm.setEnabled(p, it) },
        up = { vm.move(p, -1) },
        down = { vm.move(p, 1) },
        delete = { deleting = p },
    )

    val list = playlists
    Column(Modifier.fillMaxSize()) {
        if (!tv) {
            SdTopBar(
                stringResource(R.string.manage_title), onBack = onBack,
                subtitle = list?.let { stringResource(R.string.playlist_count, it.size) },
            ) { SdIconButton(Icons.Default.Add, stringResource(R.string.add_playlist), onAdd, container = SdTheme.palette.accent,
                tint = SdTheme.palette.onAccent) }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                list == null -> LoadingState()
                list.isEmpty() -> EmptyState(
                    stringResource(R.string.no_playlists_hint), actionLabel = stringResource(R.string.add_playlist), onAction = onAdd,
                    title = stringResource(R.string.no_playlists_yet), icon = Icons.AutoMirrored.Outlined.PlaylistPlay,
                )
                tv -> TvManager(list, counts, status, onAdd, ::actionsFor)
                else -> LazyColumn(
                    Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(list, key = { it.id }) { p -> PhonePlaylistCard(p, counts[p.id], status[p.id], actionsFor(p)) { more = p } }
                }
            }
        }
    }

    more?.let { p ->
        val a = actionsFor(p)
        val i = list.orEmpty().indexOfFirst { it.id == p.id }
        ActionPanel(
            title = p.name,
            subtitle = stringResource(p.type.labelRes()),
            onDismiss = { more = null },
            actions = listOfNotNull(
                PanelAction(stringResource(R.string.move_up), Icons.Default.ArrowUpward, onClick = a.up).takeIf { i > 0 },
                PanelAction(stringResource(R.string.move_down), Icons.Default.ArrowDownward, onClick = a.down).takeIf { i in 0 until list.orEmpty().lastIndex },
                PanelAction(stringResource(R.string.edit_playlist), Icons.Default.Edit, onClick = a.edit),
                PanelAction(stringResource(DR.string.ds_delete), Icons.Default.Delete, destructive = true, onClick = a.delete),
            ),
        )
    }
    editing?.let { p -> editDialog(p, { editing = null }) { vm.save(it); editing = null } }
    deleting?.let { p ->
        ConfirmDialog(
            title = stringResource(R.string.delete_confirm_title),
            message = stringResource(R.string.delete_confirm_body, p.name),
            confirmLabel = stringResource(DR.string.ds_delete),
            onConfirm = { vm.delete(p); deleting = null },
            onDismiss = { deleting = null },
        )
    }
}

// ------------------------------------------------------------------------------------------- phone

@Composable
private fun PhonePlaylistCard(p: PlaylistEntity, count: Int?, status: String?, a: PlaylistActions, onMore: () -> Unit) {
    val pal = SdTheme.palette
    val density = LocalDensity.current
    val step = with(density) { 72.dp.toPx() }
    var dragging by remember { mutableStateOf(false) }
    val up by rememberUpdatedState(a.up)
    val down by rememberUpdatedState(a.down)
    val dragDesc = stringResource(R.string.drag_to_reorder)
    Column(
        Modifier.fillMaxWidth().background(pal.card, RoundedCornerShape(20.dp))
            .border(if (dragging) 2.dp else 1.dp, if (dragging) pal.accent else pal.stroke.copy(alpha = 0.6f), RoundedCornerShape(20.dp)),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(width = 32.dp, height = 48.dp).semantics { contentDescription = dragDesc }.pointerInput(p.id) {
                    var acc = 0f
                    detectVerticalDragGestures(
                        onDragStart = { dragging = true; acc = 0f },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                    ) { change, amount ->
                        change.consume()
                        acc += amount
                        if (acc > step) { acc -= step; down() } else if (acc < -step) { acc += step; up() }
                    }
                },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.DragIndicator, null, tint = pal.textTertiary) }
            Box(Modifier.alpha(if (p.enabled) 1f else 0.5f)) { IconTile(p.type.icon(), p.type.tint(), size = 48.dp) }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp).alpha(if (p.enabled) 1f else 0.6f)) {
                Text(p.name, color = pal.textPrimary, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(summaryLine(p, count), color = pal.textSecondary, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                    overflow = TextOverflow.Ellipsis)
                Text(updatedLine(p), color = pal.textTertiary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            EnabledSwitch(p.enabled, a.toggle)
        }
        if (status != null) {
            Text(status, color = pal.accent, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 98.dp, end = 16.dp, top = 6.dp))
        }
        Box(Modifier.padding(top = 12.dp).fillMaxWidth().height(0.5.dp).background(pal.stroke))
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            CardAction(Icons.Default.Refresh, stringResource(R.string.refresh_short), a.refresh)
            CardAction(Icons.AutoMirrored.Filled.List, stringResource(R.string.editor_short), a.editor)
            CardAction(Icons.Default.Edit, stringResource(R.string.edit_short), a.edit)
            Box(Modifier.weight(1f))
            SdIconButton(Icons.Default.MoreHoriz, stringResource(DR.string.ds_more), onMore, size = 40.dp, container = Color.Transparent,
                tint = pal.textSecondary)
        }
    }
}

@Composable
private fun CardAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    val p = SdTheme.palette
    FocusSurface(onClick = onClick, shape = RoundedCornerShape(12.dp), container = Color.Transparent, focusFill = true, contentDescription = label) { focused ->
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (focused) p.onFocusFill else p.accent, modifier = Modifier.size(18.dp))
            Text(label, color = if (focused) p.onFocusFill else p.textPrimary, style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 6.dp))
        }
    }
}

@Composable
private fun EnabledSwitch(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    FocusSurface(
        onClick = { onToggle(!enabled) }, shape = RoundedCornerShape(50), container = Color.Transparent,
        contentDescription = stringResource(if (enabled) R.string.playlist_enabled else R.string.playlist_disabled),
    ) { Box(Modifier.padding(2.dp)) { SdSwitch(enabled) } }
}

// ------------------------------------------------------------------------------------------- TV

@Composable
private fun TvManager(
    list: List<PlaylistEntity>,
    counts: Map<Long, Int>,
    status: Map<Long, String>,
    onAdd: () -> Unit,
    actionsFor: (PlaylistEntity) -> PlaylistActions,
) {
    val pal = SdTheme.palette
    var selectedId by remember { mutableStateOf(list.first().id) }
    val selected = list.firstOrNull { it.id == selectedId } ?: list.first()
    Row(Modifier.fillMaxSize().padding(start = 56.dp, end = 48.dp, top = 40.dp, bottom = 32.dp)) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.manage_title), style = MaterialTheme.typography.displaySmall, color = pal.textPrimary)
                    Text(stringResource(R.string.playlist_count, list.size), style = MaterialTheme.typography.titleMedium, color = pal.textSecondary)
                }
                SdButton(stringResource(R.string.add_playlist), onAdd, icon = Icons.Default.Add, large = true)
            }
            LazyColumn(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 6.dp,
                horizontal = 4.dp)) {
                items(list, key = { it.id }) { p ->
                    FocusSurface(
                        onClick = { selectedId = p.id }, modifier = Modifier.fillMaxWidth().onFocusGained { selectedId = p.id },
                        shape = RoundedCornerShape(20.dp), container = if (p.id == selected.id) pal.cardHigh else pal.card,
                        focusFill = true, focusScale = 1.02f,
                        contentDescription = p.name,
                    ) { focused ->
                        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconTile(p.type.icon(), p.type.tint(), size = 52.dp)
                            Column(Modifier.weight(1f).padding(horizontal = 18.dp)) {
                                Text(p.name, color = if (focused) pal.onFocusFill else pal.textPrimary, style = MaterialTheme.typography.titleLarge,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    summaryLine(p, counts[p.id]) + " · " + updatedLine(p), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    color = if (focused) pal.onFocusFill.copy(alpha = 0.7f) else pal.textSecondary, style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            if (!p.enabled) {
                                Text(stringResource(R.string.playlist_disabled), color = if (focused) pal.onFocusFill else pal.textTertiary,
                                    style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        }
        TvPlaylistPanel(selected, counts[selected.id], status[selected.id], list.indexOfFirst { it.id == selected.id }, list.size, actionsFor(selected))
    }
}

@Composable
private fun TvPlaylistPanel(p: PlaylistEntity, count: Int?, status: String?, index: Int, total: Int, a: PlaylistActions) {
    val pal = SdTheme.palette
    Column(
        Modifier.padding(start = 32.dp).width(420.dp).fillMaxHeight().background(pal.card, RoundedCornerShape(28.dp))
            .border(1.dp, pal.stroke, RoundedCornerShape(28.dp)).verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconTile(p.type.icon(), p.type.tint(), size = 64.dp)
        Text(p.name, color = pal.textPrimary, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(summaryLine(p, count), color = pal.textSecondary, style = MaterialTheme.typography.bodyLarge)
        Text(updatedLine(p), color = pal.textTertiary, style = MaterialTheme.typography.bodyMedium)
        if (status != null) Text(status, color = pal.accent, style = MaterialTheme.typography.bodyMedium)
        Box(Modifier.height(6.dp))
        SwitchRow(stringResource(R.string.playlist_enabled), p.enabled, a.toggle, icon = Icons.Outlined.PowerSettingsNew, tint = pal.hueGreen)
        SdButton(stringResource(R.string.refresh_now), a.refresh, Modifier.fillMaxWidth(), icon = Icons.Default.Refresh, large = true)
        SdButton(stringResource(R.string.open_editor), a.editor, Modifier.fillMaxWidth(), icon = Icons.AutoMirrored.Filled.List,
            style = SdButtonStyle.Secondary, large = true)
        SdButton(stringResource(R.string.edit_playlist), a.edit, Modifier.fillMaxWidth(), icon = Icons.Default.Edit, style = SdButtonStyle.Secondary,
            large = true)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SdButton(stringResource(R.string.move_up), a.up, Modifier.weight(1f), icon = Icons.Default.ArrowUpward, style = SdButtonStyle.Secondary,
                enabled = index > 0)
            SdButton(stringResource(R.string.move_down), a.down, Modifier.weight(1f), icon = Icons.Default.ArrowDownward, style = SdButtonStyle.Secondary,
                enabled = index < total - 1)
        }
        SdButton(stringResource(DR.string.ds_delete), a.delete, Modifier.fillMaxWidth(), icon = Icons.Default.Delete, style = SdButtonStyle.Danger)
    }
}

// ------------------------------------------------------------------------------------------- shared

@Composable
private fun summaryLine(p: PlaylistEntity, count: Int?): String =
    listOfNotNull(stringResource(p.type.labelRes()), count?.let { stringResource(R.string.channel_count, it) }).joinToString(" · ")

@Composable
private fun updatedLine(p: PlaylistEntity): String = if (p.lastUpdated > 0) {
    stringResource(R.string.updated_at, DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(p.lastUpdated)))
} else {
    stringResource(R.string.never_updated)
}

internal fun PlaylistType.icon(): ImageVector = when (this) {
    PlaylistType.M3U_URL -> Icons.Outlined.Link
    PlaylistType.M3U_FILE -> Icons.Outlined.FolderOpen
    PlaylistType.XTREAM -> Icons.Outlined.Dns
}

@Composable
internal fun PlaylistType.tint(): Color {
    val p = SdTheme.palette
    return when (this) {
        PlaylistType.M3U_URL -> p.hueGreen
        PlaylistType.M3U_FILE -> p.hueAmber
        PlaylistType.XTREAM -> p.hueBlue
    }
}

internal fun PlaylistType.labelRes() = when (this) {
    PlaylistType.M3U_URL -> R.string.source_url
    PlaylistType.M3U_FILE -> R.string.source_file
    PlaylistType.XTREAM -> R.string.source_xtream
}

/** Fallback edit dialog when the app does not supply the policy-aware one from `feature:settings`. */
@Composable
fun PlaylistEditDialog(p: PlaylistEntity, onDismiss: () -> Unit, onSave: (PlaylistEntity) -> Unit) {
    var name by remember { mutableStateOf(p.name) }
    var url by remember { mutableStateOf(p.url) }
    var ua by remember { mutableStateOf(p.userAgent.orEmpty()) }
    var epg by remember { mutableStateOf(p.epgUrl.orEmpty()) }
    com.streamdeck.player.core.designsystem.SdDialog(onDismiss = onDismiss, title = stringResource(R.string.edit_playlist)) {
        com.streamdeck.player.core.designsystem.SdTextField(name, { name = it }, stringResource(R.string.playlist_name))
        if (p.type != PlaylistType.M3U_FILE) com.streamdeck.player.core.designsystem.SdTextField(url, { url = it }, stringResource(R.string.playlist_url))
        com.streamdeck.player.core.designsystem.SdTextField(ua, { ua = it }, stringResource(R.string.user_agent))
        com.streamdeck.player.core.designsystem.SdTextField(epg, { epg = it }, stringResource(R.string.epg_url))
        com.streamdeck.player.core.designsystem.DialogButtons(
            confirm = stringResource(DR.string.ds_save),
            onConfirm = { onSave(p.copy(name = name.trim(), url = url.trim(), userAgent = ua.trim().ifBlank { null }, epgUrl = epg.trim().ifBlank { null })) },
            onDismiss = onDismiss,
            confirmEnabled = name.isNotBlank() && url.isNotBlank(),
        )
    }
}
