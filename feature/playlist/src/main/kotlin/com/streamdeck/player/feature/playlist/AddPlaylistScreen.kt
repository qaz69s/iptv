package com.streamdeck.player.feature.playlist

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.streamdeck.player.core.data.PlaylistRepository
import com.streamdeck.player.core.database.PlaylistEntity
import com.streamdeck.player.core.database.PlaylistType
import com.streamdeck.player.core.designsystem.ErrorState
import com.streamdeck.player.core.designsystem.FocusSurface
import com.streamdeck.player.core.designsystem.OptionCard
import com.streamdeck.player.core.designsystem.ProgressState
import com.streamdeck.player.core.designsystem.SdButton
import com.streamdeck.player.core.designsystem.SdCard
import com.streamdeck.player.core.designsystem.SdChip
import com.streamdeck.player.core.designsystem.SdTextField
import com.streamdeck.player.core.designsystem.SdTheme
import com.streamdeck.player.core.designsystem.SdTopBar
import com.streamdeck.player.core.designsystem.SwitchRow
import com.streamdeck.player.core.designsystem.rememberIsTv
import com.streamdeck.player.core.designsystem.R as DR

/** All inputs of the add form, hoisted so they survive the importing / failed states and configuration changes. */
@Stable
internal class AddForm(type: PlaylistType) {
    var type by mutableStateOf(type)
    var name by mutableStateOf("")
    var url by mutableStateOf("")
    var fileUri by mutableStateOf<String?>(null)
    var server by mutableStateOf("")
    var user by mutableStateOf("")
    var cleartext by mutableStateOf(false)
    var advanced by mutableStateOf(false)
    var userAgent by mutableStateOf("")
    var headers by mutableStateOf("")
    var epg by mutableStateOf("")
    var mode by mutableStateOf(PlaylistEntity.UPDATE_MANUAL)
    var hours by mutableStateOf("24")
    var showErrors by mutableStateOf(false)

    val source: String get() = when (type) {
        PlaylistType.M3U_URL -> url.trim()
        PlaylistType.M3U_FILE -> fileUri.orEmpty()
        PlaylistType.XTREAM -> server.trim()
    }

    /** http:// source while plain HTTP is still off — shown as a hint next to the switch. */
    val needsCleartext: Boolean get() = type != PlaylistType.M3U_FILE && !cleartext && source.startsWith("http://", ignoreCase = true)

    companion object {
        /** The password is deliberately not saved into the instance state. */
        val Saver = listSaver<AddForm, Any?>(
            save = { listOf(it.type.name, it.name, it.url, it.fileUri, it.server, it.user, it.cleartext, it.advanced, it.userAgent, it.headers, it.epg,
                it.mode, it.hours) },
            restore = { v ->
                AddForm(PlaylistType.valueOf(v[0] as String)).apply {
                    name = v[1] as String; url = v[2] as String; fileUri = v[3] as String?; server = v[4] as String; user = v[5] as String
                    cleartext = v[6] as Boolean; advanced = v[7] as Boolean; userAgent = v[8] as String; headers = v[9] as String
                    epg = v[10] as String; mode = v[11] as String; hours = v[12] as String
                }
            },
        )
    }
}

/**
 * Add a playlist (spec §4.1): method cards (M3U URL / local file / Xtream), grouped form cards, per-playlist
 * cleartext switch, collapsible advanced options and a progress page with the number of parsed entries.
 * Phone: single column under a top bar. TV: method list on the left, large form on the right (D-pad friendly).
 */
@Composable
fun AddPlaylistScreen(
    onImported: () -> Unit,
    onBack: () -> Unit,
    initialType: PlaylistType = PlaylistType.M3U_URL,
    vm: AddPlaylistViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsState()
    LaunchedEffect(state) { if (state is ImportUiState.Done) onImported() }
    val form = rememberSaveable(saver = AddForm.Saver) { AddForm(initialType) }
    var password by remember { mutableStateOf("") }
    val tv = rememberIsTv()

    Column(Modifier.fillMaxSize()) {
        if (!tv) SdTopBar(stringResource(R.string.add_playlist), onBack = onBack, subtitle = stringResource(R.string.add_subtitle))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val s = state) {
                is ImportUiState.Importing -> ProgressState(
                    title = stringResource(R.string.importing_title, form.name.ifBlank { stringResource(R.string.add_playlist) }),
                    value = s.count.toString(),
                    detail = stringResource(R.string.parsed_count, s.count),
                    cancelLabel = stringResource(DR.string.ds_cancel),
                    onCancel = vm::cancel,
                )
                is ImportUiState.Failed -> ErrorState(
                    message = failureText(s.reason),
                    onRetry = vm::retry,
                    title = stringResource(R.string.import_failed_title),
                    secondaryLabel = stringResource(R.string.edit_source),
                    onSecondary = vm::cancel,
                )
                else -> if (tv) {
                    TvAddForm(form, password, { password = it }, vm)
                } else {
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
                            .windowInsetsPadding(WindowInsets.navigationBars),
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                    ) {
                        MethodCards(form, large = false)
                        FormCards(form, password, { password = it }, large = false)
                        SubmitButton(form, password, vm, large = false)
                        Box(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TvAddForm(form: AddForm, password: String, onPassword: (String) -> Unit, vm: AddPlaylistViewModel) {
    val p = SdTheme.palette
    val first = remember { FocusRequester() }
    Row(Modifier.fillMaxSize().padding(start = 56.dp, end = 56.dp, top = 44.dp)) {
        Column(Modifier.width(400.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(stringResource(R.string.add_playlist), style = MaterialTheme.typography.displaySmall, color = p.textPrimary)
            Text(stringResource(R.string.add_subtitle), style = MaterialTheme.typography.titleMedium, color = p.textSecondary,
                modifier = Modifier.padding(bottom = 12.dp))
            MethodCards(form, large = true, firstFocus = first)
        }
        Column(
            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(start = 40.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            FormCards(form, password, onPassword, large = true)
            SubmitButton(form, password, vm, large = true)
        }
    }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
}

@Composable
private fun MethodCards(form: AddForm, large: Boolean, firstFocus: FocusRequester? = null) {
    val p = SdTheme.palette
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!large) {
            Text(stringResource(R.string.add_method), color = p.textTertiary, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 6.dp))
        }
        listOf(
            Triple(PlaylistType.M3U_URL, R.string.method_url_desc, Icons.Outlined.Link) to p.hueGreen,
            Triple(PlaylistType.M3U_FILE, R.string.method_file_desc, Icons.Outlined.FolderOpen) to p.hueAmber,
            Triple(PlaylistType.XTREAM, R.string.method_xtream_desc, Icons.Outlined.Dns) to p.hueBlue,
        ).forEachIndexed { i, (t, tint) ->
            OptionCard(
                title = stringResource(t.first.labelRes()), description = stringResource(t.second), icon = t.third, tint = tint,
                selected = form.type == t.first, onClick = { form.type = t.first; form.showErrors = false }, large = large,
                modifier = if (i == 0 && firstFocus != null) Modifier.focusRequester(firstFocus) else Modifier,
            )
        }
    }
}

@Composable
private fun FormCards(form: AddForm, password: String, onPassword: (String) -> Unit, large: Boolean) {
    val ctx = LocalContext.current
    val p = SdTheme.palette
    var noPicker by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            form.fileUri = uri.toString()
            if (form.name.isBlank()) form.name = uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.').orEmpty()
        }
    }
    val required = stringResource(R.string.field_required)
    val err = form.showErrors

    SdCard(title = stringResource(R.string.section_basic)) {
        SdTextField(
            form.name, { form.name = it }, stringResource(R.string.playlist_name), placeholder = stringResource(R.string.name_placeholder),
            large = large, isError = err && form.name.isBlank(), supporting = if (err && form.name.isBlank()) required else null,
        )
        when (form.type) {
            PlaylistType.M3U_URL -> SdTextField(
                form.url, { form.url = it }, stringResource(R.string.playlist_url), placeholder = stringResource(R.string.url_placeholder),
                keyboardType = KeyboardType.Uri, large = large, isError = err && form.url.isBlank(),
                supporting = if (err && form.url.isBlank()) required else null,
            )
            PlaylistType.M3U_FILE -> {
                val picked = form.fileUri?.let { Uri.parse(it).lastPathSegment ?: it }
                FocusSurface(
                    onClick = {
                        try {
                            picker.launch(arrayOf("*/*"))
                        } catch (e: ActivityNotFoundException) {
                            noPicker = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), container = p.cardHigh,
                    border = if (err && picked == null) p.live else p.stroke, focusFill = true, contentDescription = stringResource(R.string.pick_file),
                ) { focused ->
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.FolderOpen, null, tint = if (focused) p.onFocusFill else p.accent, modifier = Modifier.size(24.dp))
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(stringResource(R.string.pick_file), color = if (focused) p.onFocusFill else p.textPrimary,
                                style = MaterialTheme.typography.titleSmall)
                            Text(
                                picked?.let { stringResource(R.string.file_selected, it) } ?: stringResource(R.string.file_none),
                                color = if (focused) p.onFocusFill.copy(alpha = 0.7f) else p.textTertiary,
                                style = MaterialTheme.typography.bodySmall, maxLines = 1,
                            )
                        }
                    }
                }
                if (noPicker) Text(stringResource(R.string.no_file_picker), color = p.live, style = MaterialTheme.typography.bodySmall)
            }
            PlaylistType.XTREAM -> {
                SdTextField(
                    form.server, { form.server = it }, stringResource(R.string.xtream_server), placeholder = stringResource(R.string.server_placeholder),
                    keyboardType = KeyboardType.Uri, large = large, isError = err && form.server.isBlank(),
                    supporting = if (err && form.server.isBlank()) required else null,
                )
                SdTextField(form.user, { form.user = it }, stringResource(R.string.xtream_user), large = large, isError = err && form.user.isBlank(),
                    supporting = if (err && form.user.isBlank()) required else null)
                SdTextField(
                    password, onPassword, stringResource(R.string.xtream_pass), password = true, large = large,
                    isError = err && password.isEmpty(), supporting = if (err && password.isEmpty()) required else stringResource(R.string.xtream_note),
                )
            }
        }
    }

    if (form.type != PlaylistType.M3U_FILE) {
        SdCard(title = stringResource(R.string.section_security)) {
            SwitchRow(
                stringResource(R.string.allow_cleartext), form.cleartext, { form.cleartext = it },
                hint = stringResource(R.string.allow_cleartext_hint), icon = Icons.Outlined.LockOpen, tint = p.hueAmber,
            )
            if (form.needsCleartext) Text(stringResource(R.string.cleartext_needed_warning), color = p.hueAmber, style = MaterialTheme.typography.bodySmall)
        }
    }

    FocusSurface(
        onClick = { form.advanced = !form.advanced }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
        container = Color.Transparent, focusFill = true, contentDescription = stringResource(R.string.section_advanced),
    ) { focused ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.section_advanced), color = if (focused) p.onFocusFill else p.textPrimary,
                    style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.section_advanced_hint), color = if (focused) p.onFocusFill.copy(alpha = 0.7f) else p.textTertiary,
                    style = MaterialTheme.typography.bodySmall)
            }
            Icon(if (form.advanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = if (focused) p.onFocusFill else p.textSecondary)
        }
    }
    AnimatedVisibility(form.advanced) {
        SdCard {
            SdTextField(form.userAgent, { form.userAgent = it }, stringResource(R.string.user_agent), large = large)
            if (form.type != PlaylistType.XTREAM) {
                val bad = HeaderLines.parse(form.headers).badLines
                SdTextField(
                    form.headers, { form.headers = it }, stringResource(R.string.headers_label), placeholder = stringResource(R.string.headers_placeholder),
                    singleLine = false, minLines = 3, large = large, isError = bad.isNotEmpty(),
                    supporting = if (bad.isNotEmpty()) {
                        stringResource(R.string.headers_invalid,
                            bad.joinToString(", "))
                    } else {
                        stringResource(R.string.headers_hint)
                    },
                )
                SdTextField(form.epg, { form.epg = it }, stringResource(R.string.epg_url), keyboardType = KeyboardType.Uri, large = large)
            }
            Text(stringResource(R.string.update_policy), color = p.textSecondary, style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    PlaylistEntity.UPDATE_MANUAL to R.string.update_manual,
                    PlaylistEntity.UPDATE_ON_START to R.string.update_on_start,
                    PlaylistEntity.UPDATE_INTERVAL to R.string.update_interval,
                ).forEach { (m, label) -> SdChip(stringResource(label), selected = form.mode == m, onClick = { form.mode = m }, compact = !large) }
            }
            if (form.mode == PlaylistEntity.UPDATE_INTERVAL) {
                SdTextField(
                    form.hours, { v -> form.hours = v.filter(Char::isDigit).take(3) }, stringResource(R.string.interval_hours),
                    keyboardType = KeyboardType.Number, large = large, modifier = Modifier.widthIn(max = 240.dp),
                )
            }
        }
    }
}

@Composable
private fun SubmitButton(form: AddForm, password: String, vm: AddPlaylistViewModel, large: Boolean) {
    val p = SdTheme.palette
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (form.showErrors) Text(stringResource(R.string.name_required), color = p.live, style = MaterialTheme.typography.bodyMedium)
        SdButton(
            stringResource(R.string.import_), onClick = {
                val headers = HeaderLines.parse(form.headers)
                val missing = form.name.isBlank() || form.source.isBlank() || (form.type == PlaylistType.XTREAM && (form.user.isBlank() || password.isEmpty()))
                form.showErrors = missing
                if (missing || headers.badLines.isNotEmpty()) return@SdButton
                val extras = PlaylistExtras(
                    userAgent = form.userAgent.trim().ifBlank { null },
                    headersJson = headers.json,
                    epgUrl = form.epg.trim().ifBlank { null },
                    updateMode = form.mode,
                    updateIntervalHours = form.hours.toIntOrNull() ?: 24,
                )
                if (form.type == PlaylistType.XTREAM) {
                    vm.submitXtream(form.name, form.server, form.user, password, form.cleartext, extras)
                } else {
                    vm.submit(form.name, form.type, form.source, form.userAgent, form.cleartext, extras)
                }
            },
            modifier = Modifier.fillMaxWidth(), icon = Icons.Default.Download, large = true,
        )
        if (!large) Text(stringResource(R.string.import_note), color = p.textTertiary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun failureText(reason: String): String = when {
    reason == PlaylistRepository.EMPTY -> stringResource(R.string.import_empty)
    reason.startsWith("XTREAM_AUTH") -> stringResource(R.string.xtream_auth_failed)
    reason.startsWith("CLEARTEXT_BLOCKED") -> stringResource(R.string.cleartext_blocked)
    else -> reason
}
