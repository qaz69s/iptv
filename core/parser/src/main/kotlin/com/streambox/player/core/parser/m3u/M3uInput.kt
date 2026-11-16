package com.streambox.player.core.parser.m3u

import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream

/**
 * Turns a raw byte stream into a line reader for [M3uParser]:
 * transparently gunzips `.gz` bodies (detected by magic bytes, not by name or header)
 * and decodes UTF-8. The BOM is stripped by the parser.
 */
object M3uInput {
    private const val GZIP_MAGIC_1 = 0x1f
    private const val GZIP_MAGIC_2 = 0x8b
    private const val BUFFER_SIZE = 64 * 1024

    fun reader(input: InputStream): BufferedReader {
        val buffered = input as? BufferedInputStream ?: BufferedInputStream(input, BUFFER_SIZE)
        buffered.mark(2)
        val first = buffered.read()
        val second = buffered.read()
        buffered.reset()
        val decoded: InputStream = if (first == GZIP_MAGIC_1 && second == GZIP_MAGIC_2) {
            GZIPInputStream(buffered, BUFFER_SIZE)
        } else {
            buffered
        }
        return BufferedReader(InputStreamReader(decoded, Charsets.UTF_8), BUFFER_SIZE)
    }
}
