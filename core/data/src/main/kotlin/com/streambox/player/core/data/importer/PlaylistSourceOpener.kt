package com.streambox.player.core.data.importer

import java.io.InputStream

class OpenedSource(val stream: InputStream, val totalBytes: Long?) : AutoCloseable {
    override fun close() = stream.close()
}

/** Opens the raw bytes of a playlist (HTTP download or SAF document). Blocking; call on IO. */
fun interface PlaylistSourceOpener {
    fun open(source: PlaylistSource): OpenedSource
}
