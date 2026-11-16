package com.streambox.player.core.data.repository

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Keeps read access to SAF documents across reboots so "refresh" works for local files. */
interface UriPermissions {
    fun persistRead(uri: String)

    fun releaseRead(uri: String)
}

internal class AndroidUriPermissions @Inject constructor(@ApplicationContext private val context: Context) :
    UriPermissions {
    override fun persistRead(uri: String) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    override fun releaseRead(uri: String) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(uri.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
