package com.streambox.player.feature.player

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.streambox.player.core.player.session.PlaybackService

/**
 * While background playback is on and a player exists, keeps the MediaSession service running (it stops
 * itself when the player is released). On Android 13+ asks once per process for the notification
 * permission the media notification needs; a refusal does not block playback.
 */
@Composable
internal fun BackgroundPlaybackEffect(enabled: Boolean, hasPlayer: Boolean) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(enabled) {
        if (enabled && needsNotificationPermission(context) && !NotificationPermissionPrompt.asked) {
            NotificationPermissionPrompt.asked = true
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(enabled, hasPlayer) {
        if (enabled && hasPlayer) PlaybackService.start(context)
    }
}

private fun needsNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED

/** The system already limits repeated prompts; this keeps it to the first player visit per process. */
private object NotificationPermissionPrompt {
    var asked = false
}

/**
 * True when the screen stops because the whole app is going to the background (home, recents, screen
 * off), not because of navigation inside the app, a finishing activity or a configuration change. The
 * activity's own lifecycle has already left STARTED when its ON_STOP reaches the screen.
 */
internal fun Activity?.isMovingToBackground(): Boolean {
    if (this == null || isFinishing || isChangingConfigurations) return false
    val state = (this as? LifecycleOwner)?.lifecycle?.currentState
    return state != null && !state.isAtLeast(Lifecycle.State.STARTED)
}
