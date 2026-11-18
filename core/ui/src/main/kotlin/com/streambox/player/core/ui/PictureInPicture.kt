package com.streambox.player.core.ui

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Picture-in-picture is offered on phones and tablets running Android 8.0+ (the PiP params API) whose
 * system supports it; never on TV, where the player is always full screen (requirements §4.3).
 */
fun Context.supportsPictureInPicture(formFactor: FormFactor): Boolean = formFactor != FormFactor.Tv &&
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
    packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
