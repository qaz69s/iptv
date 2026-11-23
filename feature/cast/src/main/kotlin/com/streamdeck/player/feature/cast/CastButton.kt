package com.streamdeck.player.feature.cast

import android.view.ContextThemeWrapper
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.streamdeck.player.core.designsystem.R as DR

@HiltViewModel
class CastViewModel @Inject constructor(val cast: CastController) : ViewModel()

/**
 * Cast icon plus remote controls while connected (pause / volume / stop). Renders nothing when Google Play
 * services are missing (e.g. many TV boxes), so callers can always place it.
 */
@Composable
fun CastButton(vm: CastViewModel = hiltViewModel()) {
    LaunchedEffect(Unit) { vm.cast.ensureInitialized() }
    if (!vm.cast.isAvailable) return
    val state by vm.cast.state.collectAsStateWithLifecycle()
    Row(verticalAlignment = Alignment.CenterVertically) {
        (state as? CastState.Connected)?.let { c ->
            IconButton(onClick = vm.cast::togglePause) {
                Icon(if (c.playing) Icons.Default.Pause else Icons.Default.PlayArrow, stringResource(R.string.cast_toggle), tint = Color.White)
            }
            IconButton(onClick = { vm.cast.adjustVolume(-VOLUME_STEP) }) { Icon(Icons.AutoMirrored.Filled.VolumeDown, stringResource(R.string.cast_volume_down), tint = Color.White) }
            IconButton(onClick = { vm.cast.adjustVolume(VOLUME_STEP) }) { Icon(Icons.AutoMirrored.Filled.VolumeUp, stringResource(R.string.cast_volume_up), tint = Color.White) }
            IconButton(onClick = vm.cast::stop) { Icon(Icons.Default.Stop, stringResource(R.string.cast_stop), tint = Color.White) }
        }
        AndroidView(
            factory = { c ->
                // MediaRouteButton needs an AppCompat theme; the host Activity must be a FragmentActivity.
                MediaRouteButton(ContextThemeWrapper(c, androidx.appcompat.R.style.Theme_AppCompat_NoActionBar)).also {
                    CastButtonFactory.setUpMediaRouteButton(c.applicationContext, it)
                    it.contentDescription = c.getString(R.string.cast)
                }
            },
            modifier = Modifier.size(48.dp),
        )
    }
    if (state == CastState.NotCastable) {
        AlertDialog(
            onDismissRequest = vm.cast::dismissNotCastable,
            text = { Text(stringResource(R.string.cast_not_castable)) },
            confirmButton = { TextButton(onClick = vm.cast::dismissNotCastable) { Text(stringResource(DR.string.ds_ok)) } },
        )
    }
}

private const val VOLUME_STEP = 0.05
