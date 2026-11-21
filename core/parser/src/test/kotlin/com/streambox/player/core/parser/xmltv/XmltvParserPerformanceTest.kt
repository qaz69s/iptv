package com.streambox.player.core.parser.xmltv

import java.io.Reader
import kotlin.math.min
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

/**
 * A large guide (1 000 channels x 150 programmes = 150 000 programmes, about 50 MB of XML) streamed
 * through the parser. The document is generated on the fly by [GeneratedGuideReader], so neither the
 * text nor the parsed items are ever held whole: if the parser kept state per item, the heap measured
 * late in the run would be clearly larger than early on.
 */
@Tag("performance")
class XmltvParserPerformanceTest {
    @Test
    fun `parses a large guide quickly with flat memory`() {
        val reader = GeneratedGuideReader(CHANNELS, PROGRAMMES_PER_CHANNEL)
        val runtime = Runtime.getRuntime()
        var earlyHeap = 0L
        var lateHeap = 0L
        var programmes = 0
        var channels = 0
        var checksum = 0L
        val total = CHANNELS * PROGRAMMES_PER_CHANNEL

        val start = System.nanoTime()
        val stats = XmltvParser.parse(reader) { item ->
            when (item) {
                is XmltvChannel -> channels++

                is XmltvProgramme -> {
                    programmes++
                    checksum += item.title.length + (item.stop - item.start) / 60_000
                    if (programmes == EARLY_SAMPLE) earlyHeap = usedHeapAfterGc(runtime)
                    if (programmes == total - 1) lateHeap = usedHeapAfterGc(runtime)
                }
            }
        }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000

        assertEquals(CHANNELS, channels)
        assertEquals(total, programmes)
        assertEquals(0L, stats.skipped)
        assertTrue(checksum > 0)
        assertTrue(elapsedMs < MAX_MILLIS, "parsing took $elapsedMs ms")
        val growthMb = (lateHeap - earlyHeap) / (1024.0 * 1024.0)
        assertTrue(growthMb < MAX_GROWTH_MB, "heap grew by $growthMb MB between programme $EARLY_SAMPLE and $total")
        println(
            "XMLTV perf: $channels channels, $programmes programmes in $elapsedMs ms, heap growth %.1f MB".format(
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

    /** Streams `<channel>` elements, then every channel's programmes, without materializing the document. */
    private class GeneratedGuideReader(private val channels: Int, private val perChannel: Int) : Reader() {
        private var current = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!DOCTYPE tv SYSTEM \"xmltv.dtd\">\n<tv>\n"
        private var position = 0
        private var index = 0
        private var done = false
        private val base = XmltvTime.parse("20261121000000 +0000")!!

        override fun read(buffer: CharArray, offset: Int, length: Int): Int {
            if (position >= current.length && !advance()) return -1
            val n = min(length, current.length - position)
            current.toCharArray(buffer, offset, position, position + n)
            position += n
            return n
        }

        private fun advance(): Boolean {
            if (done) return false
            position = 0
            val channelCount = channels
            current = when {
                index < channelCount -> channelXml(index)
                index < channelCount + channels * perChannel -> programmeXml(index - channelCount)
                else -> "</tv>\n".also { done = true }
            }
            index++
            return true
        }

        private fun channelXml(i: Int) =
            "  <channel id=\"ch$i.example\">\n    <display-name lang=\"en\">Channel $i HD</display-name>\n" +
                "    <icon src=\"http://logo.example/$i.png\"/>\n  </channel>\n"

        private fun programmeXml(n: Int): String {
            val channel = n / perChannel
            val slot = n % perChannel
            val start = XmltvTime.format(base + slot * 30 * 60_000L).replace("+0000", "+0800")
            val stop = XmltvTime.format(base + (slot + 1) * 30 * 60_000L).replace("+0000", "+0800")
            return "  <programme start=\"$start\" stop=\"$stop\" channel=\"ch$channel.example\">\n" +
                "    <title lang=\"en\">Programme $slot &amp; friends</title>\n" +
                "    <desc lang=\"en\">Description of programme $slot on channel $channel, long enough to look real " +
                "with a few more words in it.</desc>\n    <category lang=\"en\">News</category>\n  </programme>\n"
        }

        override fun close() = Unit
    }

    private companion object {
        const val CHANNELS = 1_000
        const val PROGRAMMES_PER_CHANNEL = 150
        const val EARLY_SAMPLE = 10_000
        const val MAX_GROWTH_MB = 16.0

        /** Generous for CI runners; a phone parses such a guide in well under a minute. */
        const val MAX_MILLIS = 30_000L
    }
}
