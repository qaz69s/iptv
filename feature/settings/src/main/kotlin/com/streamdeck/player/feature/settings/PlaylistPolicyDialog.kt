package com.streamdeck.player.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamdeck.player.core.data.PlaylistRepository
import com.streamdeck.player.core.database.PlaylistEntity
import com.streamdeck.player.core.database.PlaylistType
import com.streamdeck.player.core.designsystem.focusRing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.streamdeck.player.core.designsystem.R as DR

@HiltViewModel
class PlaylistPolicyViewModel @Inject constructor(private val repo: PlaylistRepository) : ViewModel() {
    fun save(p: PlaylistEntity, newPassword: String?, done: (PlaylistEntity) -> Unit) {
        viewModelScope.launch {
            if (!newPassword.isNullOrEmpty() && p.type == PlaylistType.XTREAM) repo.updateXtreamPassword(p, newPassword)
            done(repo.get(p.id)?.let { stored -> p.copy(passwordEncrypted = stored.passwordEncrypted) } ?: p)
        }
    }
}

/**
 * Playlist edit dialog with update policy (manual / on start / every N hours), per-playlist cleartext switch
 * and Xtream password (spec §4.1, §4.9, §5).
 */
@Composable
fun PlaylistEditDialogWithPolicy(
    p: PlaylistEntity,
    onDismiss: () -> Unit,
    onSave: (PlaylistEntity) -> Unit,
    vm: PlaylistPolicyViewModel = hiltViewModel(),
) {
    var name by remember { mutableStateOf(p.name) }
    var url by remember { mutableStateOf(p.url) }
    var ua by remember { mutableStateOf(p.userAgent.orEmpty()) }
    var epg by remember { mutableStateOf(if (p.epgUrl == XTREAM_EPG) "" else p.epgUrl.orEmpty()) }
    var mode by remember { mutableStateOf(p.updateMode) }
    var hours by remember { mutableStateOf(p.updateIntervalHours.toString()) }
    var cleartext by remember { mutableStateOf(p.allowCleartext) }
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.edit_playlist)) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.playlist_name)) }, singleLine = true)
                if (p.type != PlaylistType.M3U_FILE) {
                    OutlinedTextField(url, { url = it }, label = { Text(stringResource(R.string.playlist_url)) }, singleLine = true)
                }
                if (p.type == PlaylistType.XTREAM) {
                    OutlinedTextField(
                        password, { password = it }, label = { Text(stringResource(R.string.new_password)) }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    )
                } else {
                    OutlinedTextField(epg, { epg = it }, label = { Text(stringResource(R.string.epg_url)) }, singleLine = true)
                }
                OutlinedTextField(ua, { ua = it }, label = { Text(stringResource(R.string.user_agent)) }, singleLine = true)
                Text(stringResource(R.string.update_policy), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        PlaylistEntity.UPDATE_MANUAL to R.string.update_manual,
                        PlaylistEntity.UPDATE_ON_START to R.string.update_on_start,
                        PlaylistEntity.UPDATE_INTERVAL to R.string.update_interval,
                    ).forEach { (m, label) -> FilterChip(mode == m, { mode = m }, { Text(stringResource(label)) }) }
                }
                if (mode == PlaylistEntity.UPDATE_INTERVAL) {
                    OutlinedTextField(
                        hours, { v -> hours = v.filter(Char::isDigit).take(3) }, label = { Text(stringResource(R.string.interval_hours)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                Row(Modifier.fillMaxWidth().focusRing().clickable { cleartext = !cleartext }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.allow_cleartext))
                        Text(stringResource(R.string.allow_cleartext_hint), style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(cleartext, null)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank() || url.isBlank()) return@TextButton
                val updated = p.copy(
                    name = name.trim(), url = url.trim(), userAgent = ua.trim().ifBlank { null },
                    epgUrl = if (p.type == PlaylistType.XTREAM) p.epgUrl else epg.trim().ifBlank { null },
                    updateMode = mode, updateIntervalHours = hours.toIntOrNull()?.coerceIn(1, 168) ?: 24, allowCleartext = cleartext,
                )
                vm.save(updated, password.ifEmpty { null }, onSave)
            }) { Text(stringResource(DR.string.ds_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(DR.string.ds_cancel)) } },
    )
}

private const val XTREAM_EPG = "xtream:xmltv"
