package com.streambox.player.core.ui

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.runtime.staticCompositionLocalOf

enum class FormFactor { Phone, Tablet, Tv }

/** Provided once at the root; screens read it to pick TV vs touch layouts. */
val LocalFormFactor = staticCompositionLocalOf { FormFactor.Phone }

private const val TABLET_MIN_WIDTH_DP = 600

/**
 * TV is detected from the system (leanback feature or television UI mode), never from screen size,
 * so a large tablet is still a touch device and a small TV is still a D-pad device.
 */
fun Context.detectFormFactor(): FormFactor {
    val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    val isTelevision = uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
        packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    return when {
        isTelevision -> FormFactor.Tv
        resources.configuration.smallestScreenWidthDp >= TABLET_MIN_WIDTH_DP -> FormFactor.Tablet
        else -> FormFactor.Phone
    }
}
