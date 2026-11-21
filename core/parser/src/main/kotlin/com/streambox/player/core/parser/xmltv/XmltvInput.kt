package com.streambox.player.core.parser.xmltv

import java.io.BufferedInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.Reader
import java.nio.charset.Charset
import java.util.zip.GZIPInputStream

/**
 * Turns the raw bytes of an XMLTV file (`.xml` or `.xml.gz`) into a [Reader] for [XmltvParser].
 * gzip is detected by its magic bytes `1f 8b`, never by name or header. The charset comes from a
 * byte order mark or the XML declaration's `encoding`, else UTF-8.
 */
object XmltvInput {
    private const val GZIP_MAGIC_1 = 0x1f
    private const val GZIP_MAGIC_2 = 0x8b
    private const val BUFFER_SIZE = 64 * 1024
    private const val SNIFF_BYTES = 256
    private val ENCODING = Regex("""encoding\s*=\s*["']([A-Za-z0-9._\-]+)["']""")

    fun reader(input: InputStream): Reader {
        val decoded = BufferedInputStream(gunzipIfNeeded(input), BUFFER_SIZE)
        decoded.mark(SNIFF_BYTES)
        val head = ByteArray(SNIFF_BYTES)
        var read = 0
        while (read < SNIFF_BYTES) {
            val n = decoded.read(head, read, SNIFF_BYTES - read)
            if (n < 0) break
            read += n
        }
        decoded.reset()
        return InputStreamReader(decoded, detectCharset(head, read))
    }

    private fun gunzipIfNeeded(input: InputStream): InputStream {
        val buffered = input as? BufferedInputStream ?: BufferedInputStream(input, BUFFER_SIZE)
        buffered.mark(2)
        val first = buffered.read()
        val second = buffered.read()
        buffered.reset()
        return if (first == GZIP_MAGIC_1 && second == GZIP_MAGIC_2) GZIPInputStream(buffered, BUFFER_SIZE) else buffered
    }

    /** The UTF-8 BOM is left in the stream; [XmlTokenizer] ignores it. */
    @Suppress("MagicNumber") // Byte order marks.
    internal fun detectCharset(head: ByteArray, length: Int): Charset {
        val b0 = if (length > 0) head[0].toInt() and 0xFF else -1
        val b1 = if (length > 1) head[1].toInt() and 0xFF else -1
        return when {
            b0 == 0xFE && b1 == 0xFF -> Charsets.UTF_16BE
            b0 == 0xFF && b1 == 0xFE -> Charsets.UTF_16LE
            else -> declaredCharset(String(head, 0, maxOf(length, 0), Charsets.ISO_8859_1)) ?: Charsets.UTF_8
        }
    }

    private fun declaredCharset(head: String): Charset? {
        val declaration = head.substringAfter("<?xml", missingDelimiterValue = "").substringBefore("?>")
        val name = ENCODING.find(declaration)?.groupValues?.get(1) ?: return null
        return runCatching { Charset.forName(name) }.getOrNull()
    }
}
