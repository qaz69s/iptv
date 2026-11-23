package com.streamdeck.player.core.designsystem

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * TV detection used to pick the TV or the phone UI at runtime: the UI mode reported by [UiModeManager]
 * (television) or, for boxes that report “normal”, the leanback feature.
 */
fun Context.isTv(): Boolean {
    val ui = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    if (ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) return true
    return packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
}

/** Remembered [isTv] for composables. */
@Composable
fun rememberIsTv(): Boolean {
    val ctx = LocalContext.current
    return remember(ctx) { ctx.isTv() }
}

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
