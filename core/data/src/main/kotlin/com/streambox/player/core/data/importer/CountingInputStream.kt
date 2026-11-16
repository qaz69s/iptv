package com.streambox.player.core.data.importer

import java.io.FilterInputStream
import java.io.InputStream

/** Counts bytes read for determinate progress when the total size is known. */
internal class CountingInputStream(input: InputStream) : FilterInputStream(input) {
    @Volatile
    var count: Long = 0
        private set

    override fun read(): Int = super.read().also { if (it >= 0) count++ }

    override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) count += it }

    override fun skip(n: Long): Long = super.skip(n).also { count += it }

    override fun markSupported(): Boolean = false
}
