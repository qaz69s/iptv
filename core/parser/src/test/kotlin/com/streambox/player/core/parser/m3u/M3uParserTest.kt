package com.streambox.player.core.parser.m3u

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.util.zip.GZIPOutputStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class M3uParserTest {
    private fun parse(text: String): Pair<List<M3uEntry>, M3uParseResult> {
        val entries = mutableListOf<M3uEntry>()
        val result = M3uParser.parse(StringReader(text)) { entries += it }
        return entries to result
    }

    private fun single(text: String): M3uEntry {
        val (entries, _) = parse(text)
        assertEquals(1, entries.size, "expected exactly one entry")
        return entries.single()
    }

    @Nested
    inner class Header {
        @Test
        fun `header with url-tvg is parsed`() {
            val (_, result) = parse("#EXTM3U url-tvg=\"http://epg.example/guide.xml\"\n")
            assertTrue(result.stats.hasHeader)
            assertEquals(listOf("http://epg.example/guide.xml"), result.header.epgUrls)
        }

        @Test
        fun `x-tvg-url with several comma separated urls`() {
            val (_, result) = parse("#EXTM3U x-tvg-url=\"http://a/1.xml, http://b/2.xml.gz\"\n")
            assertEquals(listOf("http://a/1.xml", "http://b/2.xml.gz"), result.header.epgUrls)
        }

        @Test
        fun `url-tvg and x-tvg-url are merged without duplicates`() {
            val (_, result) = parse("#EXTM3U url-tvg=\"http://a/1.xml\" x-tvg-url=\"http://a/1.xml,http://c/3.xml\"\n")
            assertEquals(listOf("http://a/1.xml", "http://c/3.xml"), result.header.epgUrls)
        }

        @Test
        fun `other header attributes are kept`() {
            val (_, result) = parse("#EXTM3U refresh=\"3600\" tvg-shift=2\n")
            assertEquals("3600", result.header.attributes["refresh"])
            assertEquals("2", result.header.attributes["tvg-shift"])
        }

        @Test
        fun `missing EXTM3U header is tolerated`() {
            val (entries, result) = parse("#EXTINF:-1,News\nhttp://s/1.ts\n")
            assertFalse(result.stats.hasHeader)
            assertEquals(1, entries.size)
        }

        @Test
        fun `UTF-8 BOM before header is stripped`() {
            val (entries, result) = parse("\uFEFF#EXTM3U\n#EXTINF:-1,A\nhttp://s/a\n")
            assertTrue(result.stats.hasHeader)
            assertEquals("A", entries.single().name)
        }
    }

    @Nested
    inner class ExtInf {
        @Test
        fun `full m3u_plus entry`() {
            val entry = single(
                """
                #EXTM3U
                #EXTINF:-1 tvg-id="bbc1.uk" tvg-name="BBC One" tvg-logo="http://l/bbc.png" group-title="UK",BBC One HD
                http://s/bbc1.m3u8
                """.trimIndent(),
            )
            assertEquals("bbc1.uk", entry.tvgId)
            assertEquals("BBC One", entry.tvgName)
            assertEquals("http://l/bbc.png", entry.tvgLogo)
            assertEquals("UK", entry.groupTitle)
            assertEquals("BBC One HD", entry.name)
            assertEquals("http://s/bbc1.m3u8", entry.url)
            assertEquals(-1.0, entry.durationSeconds)
        }

        @Test
        fun `positive and fractional durations`() {
            assertEquals(10.5, single("#EXTINF:10.5,Clip\nhttp://s/c.mp4").durationSeconds)
            assertEquals(0.0, single("#EXTINF:0,Clip\nhttp://s/c.mp4").durationSeconds)
        }

        @Test
        fun `non numeric duration becomes null`() {
            assertNull(single("#EXTINF:abc,Clip\nhttp://s/c.mp4").durationSeconds)
        }

        @Test
        fun `attribute value containing commas`() {
            val entry = single("#EXTINF:-1 tvg-name=\"News, Weather, Sport\" group-title=\"A,B\",Display\nhttp://s/1")
            assertEquals("News, Weather, Sport", entry.tvgName)
            assertEquals("A,B", entry.groupTitle)
            assertEquals("Display", entry.name)
        }

        @Test
        fun `name containing commas keeps everything after the first unquoted comma`() {
            assertEquals(
                "Movies, Series, Kids",
                single("#EXTINF:-1 group-title=\"X\",Movies, Series, Kids\nhttp://s/1").name,
            )
        }

        @Test
        fun `single quoted values and embedded double quotes`() {
            val entry = single("#EXTINF:-1 tvg-name='The \"Best\" Channel' group-title='Fun',Best\nhttp://s/1")
            assertEquals("The \"Best\" Channel", entry.tvgName)
            assertEquals("Fun", entry.groupTitle)
        }

        @Test
        fun `unquoted attribute values`() {
            val entry = single("#EXTINF:-1 tvg-id=cnn.us tvg-chno=42 group-title=News,CNN\nhttp://s/cnn")
            assertEquals("cnn.us", entry.tvgId)
            assertEquals(42, entry.tvgChno)
            assertEquals("News", entry.groupTitle)
            assertEquals("CNN", entry.name)
        }

        @Test
        fun `attribute values with spaces`() {
            val entry = single("#EXTINF:-1 group-title=\"Sports Premium HD\",ESPN\nhttp://s/espn")
            assertEquals("Sports Premium HD", entry.groupTitle)
        }

        @Test
        fun `attribute keys are case insensitive`() {
            val entry = single("#EXTINF:-1 TVG-ID=\"x\" Group-Title=\"G\" tvg-LOGO=\"http://l\",N\nhttp://s/1")
            assertEquals("x", entry.tvgId)
            assertEquals("G", entry.groupTitle)
            assertEquals("http://l", entry.tvgLogo)
        }

        @Test
        fun `extra whitespace around tokens`() {
            val entry = single("#EXTINF:  -1   tvg-id=\"a\"    group-title=\"G\"  ,   Spaced Name  \nhttp://s/1")
            assertEquals("a", entry.tvgId)
            assertEquals("Spaced Name", entry.name)
        }

        @Test
        fun `catchup attributes`() {
            val entry = single(
                "#EXTINF:-1 catchup=\"append\" catchup-source=\"?utc={utc}&lutc={lutc}\" " +
                    "catchup-days=\"7\" timeshift=\"3\",C\n" +
                    "http://s/c",
            )
            assertEquals("append", entry.catchup)
            assertEquals("?utc={utc}&lutc={lutc}", entry.catchupSource)
            assertEquals(7, entry.catchupDays)
            assertEquals(3, entry.timeshift)
        }

        @Test
        fun `catchup-type alias is accepted`() {
            assertEquals("flussonic", single("#EXTINF:-1 catchup-type=\"flussonic\",C\nhttp://s/c").catchup)
        }

        @Test
        fun `unknown attributes are preserved`() {
            val entry = single("#EXTINF:-1 tvg-country=\"US\" radio=\"true\",R\nhttp://s/r")
            assertEquals("US", entry.attributes["tvg-country"])
            assertEquals("true", entry.attributes["radio"])
        }

        @Test
        fun `missing name falls back to tvg-name`() {
            assertEquals("Fallback", single("#EXTINF:-1 tvg-name=\"Fallback\",\nhttp://s/1").name)
        }

        @Test
        fun `missing name and tvg-name falls back to the url file name`() {
            assertEquals("stream42.ts", single("#EXTINF:-1\nhttp://s/live/stream42.ts?token=x").name)
        }

        @Test
        fun `non numeric tvg-chno and catchup-days are ignored`() {
            val entry = single("#EXTINF:-1 tvg-chno=\"A1\" catchup-days=\"many\",X\nhttp://s/1")
            assertNull(entry.tvgChno)
            assertNull(entry.catchupDays)
        }

        @Test
        fun `unterminated quote keeps the display name after the last comma`() {
            val entry = single("#EXTINF:-1 tvg-name=\"Broken,Name\nhttp://s/1")
            assertEquals("Name", entry.name)
            assertEquals("Broken", entry.tvgName)
        }

        @Test
        fun `utf-8 names survive`() {
            val entry = single("#EXTINF:-1 group-title=\"体育\",CCTV-5 体育频道 🏀\nhttp://s/5")
            assertEquals("体育", entry.groupTitle)
            assertEquals("CCTV-5 体育频道 🏀", entry.name)
        }
    }

    @Nested
    inner class Directives {
        @Test
        fun `EXTVLCOPT user agent and referrer apply to the next entry only`() {
            val (entries, _) = parse(
                """
                #EXTINF:-1,A
                #EXTVLCOPT:http-user-agent=MyAgent/1.0 (Linux)
                #EXTVLCOPT:http-referrer=http://ref.example/
                http://s/a
                #EXTINF:-1,B
                http://s/b
                """.trimIndent(),
            )
            assertEquals("MyAgent/1.0 (Linux)", entries[0].userAgent)
            assertEquals("http://ref.example/", entries[0].referrer)
            assertNull(entries[1].userAgent)
            assertNull(entries[1].referrer)
        }

        @Test
        fun `EXTVLCOPT http-referer spelling and options before EXTINF`() {
            val entry = single("#EXTVLCOPT:http-referer=http://r/\n#EXTINF:-1,A\nhttp://s/a")
            assertEquals("http://r/", entry.referrer)
        }

        @Test
        fun `EXTGRP sets the group when group-title is absent`() {
            assertEquals("Music", single("#EXTINF:-1,A\n#EXTGRP:Music\nhttp://s/a").groupTitle)
        }

        @Test
        fun `group-title wins over EXTGRP`() {
            assertEquals("News", single("#EXTGRP:Music\n#EXTINF:-1 group-title=\"News\",A\nhttp://s/a").groupTitle)
        }

        @Test
        fun `comments and unknown directives are skipped and counted`() {
            val (entries, result) = parse("#EXTM3U\n# a comment\n#KODIPROP:inputstream=x\n#EXTINF:-1,A\nhttp://s/a\n")
            assertEquals(1, entries.size)
            assertEquals(2, result.stats.commentLines)
            assertEquals(0, result.stats.malformedLines)
        }
    }

    @Nested
    inner class LineHandling {
        @Test
        fun `CRLF line endings`() {
            val (entries, _) = parse(
                "#EXTM3U\r\n#EXTINF:-1 tvg-id=\"a\",A\r\nhttp://s/a\r\n#EXTINF:-1,B\r\nhttp://s/b\r\n",
            )
            assertEquals(listOf("A", "B"), entries.map { it.name })
            assertEquals("http://s/a", entries[0].url)
        }

        @Test
        fun `old mac CR line endings`() {
            val (entries, _) = parse("#EXTM3U\r#EXTINF:-1,A\rhttp://s/a\r")
            assertEquals("http://s/a", entries.single().url)
        }

        @Test
        fun `blank lines and indentation between EXTINF and URL`() {
            assertEquals("http://s/a", single("#EXTM3U\n\n   #EXTINF:-1,A\n\n\n   http://s/a   \n\n").url)
        }

        @Test
        fun `bare URLs without EXTINF become entries`() {
            val (entries, _) = parse("http://s/one.m3u8\nrtmp://s/live/two\nudp://@239.0.0.1:1234\n")
            assertEquals(3, entries.size)
            assertEquals("one.m3u8", entries[0].name)
            assertNull(entries[0].groupTitle)
        }

        @ParameterizedTest
        @ValueSource(
            strings = [
                "http://h/a", "https://h/a.m3u8", "rtsp://h:554/s", "rtmp://h/app/s", "udp://@239.1.1.1:5000",
                "rtp://239.1.1.1:5000", "mms://h/s", "file:///sdcard/a.ts",
            ],
        )
        fun `supported url schemes are accepted`(url: String) {
            assertEquals(url, single("#EXTINF:-1,X\n$url").url)
        }

        @Test
        fun `empty input yields no entries`() {
            val (entries, result) = parse("")
            assertTrue(entries.isEmpty())
            assertEquals(0, result.stats.lines)
        }

        @Test
        fun `header only playlist`() {
            val (entries, result) = parse("#EXTM3U\n")
            assertTrue(entries.isEmpty())
            assertTrue(result.stats.hasHeader)
        }
    }

    @Nested
    inner class Malformed {
        @Test
        fun `text line that is not a url is skipped and counted`() {
            val (entries, result) = parse("#EXTM3U\n#EXTINF:-1,A\nthis is not a url\nhttp://s/a\n")
            assertEquals(1, entries.size)
            assertEquals("A", entries.single().name)
            assertEquals(1, result.stats.malformedLines)
        }

        @Test
        fun `EXTINF followed by another EXTINF drops the first and counts it`() {
            val (entries, result) = parse("#EXTINF:-1,Lost\n#EXTINF:-1,Kept\nhttp://s/k\n")
            assertEquals(listOf("Kept"), entries.map { it.name })
            assertEquals(1, result.stats.malformedLines)
        }

        @Test
        fun `dangling EXTINF at end of file is counted`() {
            val (entries, result) = parse("#EXTINF:-1,A\nhttp://s/a\n#EXTINF:-1,Dangling\n")
            assertEquals(1, entries.size)
            assertEquals(1, result.stats.malformedLines)
        }

        @Test
        fun `EXTINF without colon is malformed`() {
            val (entries, result) = parse("#EXTINF -1,A\nhttp://s/a\n")
            assertEquals(1, entries.size)
            assertEquals("a", entries.single().name) // bare URL fallback
            assertEquals(1, result.stats.malformedLines)
        }

        @Test
        fun `EXTVLCOPT without value is malformed`() {
            val (_, result) = parse("#EXTVLCOPT:garbage\n#EXTINF:-1,A\nhttp://s/a\n")
            assertEquals(1, result.stats.malformedLines)
        }

        @Test
        fun `url containing spaces or relative paths is rejected`() {
            val (entries, result) = parse(
                "#EXTINF:-1,A\nhttp://s/a b\n#EXTINF:-1,B\nsegment001.ts\n#EXTINF:-1,C\nhttp://s/c\n",
            )
            assertEquals(listOf("C"), entries.map { it.name })
            assertEquals(4, result.stats.malformedLines) // 2 bad urls + 2 orphaned EXTINF
        }

        @Test
        fun `binary garbage does not crash the parser`() {
            val garbage = String(CharArray(500) { (it * 31 % 256).toChar() })
            val (entries, result) = parse("#EXTM3U\n$garbage\n#EXTINF:-1,A\nhttp://s/a\n")
            assertEquals(1, entries.size)
            assertTrue(result.stats.malformedLines >= 1)
        }

        @Test
        fun `a malformed line between EXTINF and its URL does not lose metadata`() {
            val entry = single("#EXTINF:-1 group-title=\"G\",A\n???\nhttp://s/a")
            assertEquals("G", entry.groupTitle)
        }
    }

    @Nested
    inner class Identity {
        @Test
        fun `unique key uses tvg-id when present`() {
            assertEquals("tvg:bbc1.uk", single("#EXTINF:-1 tvg-id=\"bbc1.uk\",A\nhttp://s/a").uniqueKey)
        }

        @Test
        fun `unique key falls back to a stable url hash`() {
            val first = single("#EXTINF:-1,A\nhttp://s/a").uniqueKey
            val again = single("#EXTINF:-1,Renamed\nhttp://s/a").uniqueKey
            val other = single("#EXTINF:-1,A\nhttp://s/b").uniqueKey
            assertTrue(first.startsWith("url:"))
            assertEquals(20, first.length)
            assertEquals(first, again)
            assertNotEquals(first, other)
        }

        @Test
        fun `blank tvg-id is treated as absent`() {
            assertTrue(single("#EXTINF:-1 tvg-id=\"  \",A\nhttp://s/a").uniqueKey.startsWith("url:"))
        }

        @Test
        fun `line numbers point at the url line`() {
            assertEquals(3L, single("#EXTM3U\n#EXTINF:-1,A\nhttp://s/a").lineNumber)
        }
    }

    @Nested
    inner class Input {
        @Test
        fun `gzip compressed body is detected by magic bytes`() {
            val bytes = ByteArrayOutputStream().also { out ->
                GZIPOutputStream(out).use { it.write("#EXTM3U\n#EXTINF:-1,Zipped\nhttp://s/z\n".toByteArray()) }
            }.toByteArray()
            val entries = M3uParser(M3uInput.reader(ByteArrayInputStream(bytes))).entries().toList()
            assertEquals("Zipped", entries.single().name)
        }

        @Test
        fun `plain utf-8 bytes with BOM`() {
            val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
                "#EXTM3U\n#EXTINF:-1,Ünïcødé\nhttp://s/u\n".toByteArray()
            val parser = M3uParser(M3uInput.reader(ByteArrayInputStream(bytes)))
            assertEquals("Ünïcødé", parser.entries().single().name)
            assertTrue(parser.stats.hasHeader)
        }

        @Test
        fun `entries sequence is lazy`() {
            val parser = M3uParser(StringReader("#EXTINF:-1,A\nhttp://s/a\n#EXTINF:-1,B\nhttp://s/b\n"))
            val first = parser.entries().first()
            assertEquals("A", first.name)
            assertEquals(2, parser.stats.lines)
        }

        @Test
        fun `stats count lines and entries`() {
            val (_, result) = parse("#EXTM3U\n#EXTINF:-1,A\nhttp://s/a\n\n#EXTINF:-1,B\nhttp://s/b\n")
            assertEquals(6, result.stats.lines)
            assertEquals(2, result.stats.entries)
        }
    }
}
