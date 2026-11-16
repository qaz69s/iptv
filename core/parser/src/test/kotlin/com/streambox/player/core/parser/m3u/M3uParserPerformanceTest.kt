package com.streambox.player.core.parser.m3u

import java.io.Reader
import kotlin.math.min
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * 100 000-line playlist on the JVM. The input is generated on the fly by [GeneratedPlaylistReader],
 * so neither the source text nor the parsed entries are ever held in memory as a whole: if the parser
 * retained state per entry, the heap measured late in the run would be clearly larger than early on.
 */
@Tag("performance")
class M3uParserPerformanceTest {
    @Test
    fun `parses 100k lines quickly with flat memory`() {
        val channels = 50_000 // 2 lines per channel = 100 000 lines (+ header)
        val reader = GeneratedPlaylistReader(channels)
        val runtime = Runtime.getRuntime()
        var earlyHeap = 0L
        var lateHeap = 0L
        var count = 0
        var checksum = 0L

        val start = System.nanoTime()
        val result = M3uParser.parse(reader) { entry ->
            count++
            checksum += entry.name.length + entry.url.length
            if (count == EARLY_SAMPLE) earlyHeap = usedHeapAfterGc(runtime)
            if (count == channels - 1) lateHeap = usedHeapAfterGc(runtime)
        }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000

        assertEquals(channels, count)
        assertEquals(100_001L, result.stats.lines)
        assertEquals(0L, result.stats.malformedLines)
        assertTrue(checksum > 0)
        // Requirement: 100k+ lines in < 10 s on a device. The JVM run excludes the GC sampling time
        // only roughly, so the bound stays at the requirement itself.
        assertTrue(elapsedMs < 10_000, "parsing took $elapsedMs ms")
        val growthMb = (lateHeap - earlyHeap) / (1024.0 * 1024.0)
        assertTrue(growthMb < MAX_GROWTH_MB, "heap grew by $growthMb MB between entry $EARLY_SAMPLE and $channels")
        println(
            "M3U perf: ${result.stats.lines} lines, $count entries in $elapsedMs ms, heap growth %.1f MB".format(
                growthMb,
            ),
        )
    }

    private fun usedHeapAfterGc(runtime: Runtime): Long {
        repeat(3) {
            System.gc()
            Thread.sleep(20)
        }
        return runtime.totalMemory() - runtime.freeMemory()
    }

    /** Streams a synthetic m3u_plus playlist line by line without materializing it. */
    private class GeneratedPlaylistReader(private val channels: Int) : Reader() {
        private var index = -1
        private var current: String = "#EXTM3U url-tvg=\"http://epg.example/guide.xml.gz\"\n"
        private var position = 0
        private var urlLinePending = false

        override fun read(buffer: CharArray, offset: Int, length: Int): Int {
            if (position >= current.length && !advance()) return -1
            val n = min(length, current.length - position)
            current.toCharArray(buffer, offset, position, position + n)
            position += n
            return n
        }

        @Suppress("ReturnCount")
        private fun advance(): Boolean {
            position = 0
            if (urlLinePending) {
                urlLinePending = false
                current = "http://stream.example:8080/live/user/pass/$index.ts\n"
                return true
            }
            index++
            if (index >= channels) return false
            urlLinePending = true
            current = "#EXTINF:-1 tvg-id=\"ch$index.example\" tvg-name=\"Channel $index\" " +
                "tvg-logo=\"http://logo.example/$index.png\" group-title=\"Group ${index % 200}\" " +
                "tvg-chno=\"$index\" catchup=\"default\" catchup-days=\"3\",Channel $index HD\n"
            return true
        }

        override fun close() = Unit
    }

    private companion object {
        const val EARLY_SAMPLE = 5_000
        const val MAX_GROWTH_MB = 16.0
    }
}
