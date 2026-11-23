package com.streamdeck.player.parser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.StringReader

class M3uParserTest {
    private fun parse(s: String): Pair<M3uHeader?, List<M3uEntry>> {
        var h: M3uHeader? = null
        val list = mutableListOf<M3uEntry>()
        M3uParser().parse(StringReader(s.trimIndent()), { h = it }) { list += it }
        return h to list
    }

    @Test fun `parses basic attributes`() {
        val (_, e) = parse(
            """
            #EXTM3U
            #EXTINF:-1 tvg-id="cctv1" tvg-name="CCTV1" tvg-logo="http://l/1.png" group-title="News" tvg-chno="1",CCTV-1
            http://s/1.m3u8
            """,
        )
        assertEquals(1, e.size)
        with(e[0]) {
            assertEquals("CCTV-1", name); assertEquals("cctv1", tvgId); assertEquals("News", group)
            assertEquals(1, number); assertEquals("http://l/1.png", logo); assertEquals("http://s/1.m3u8", url)
        }
    }

    @Test fun `header epg and catchup inherited`() {
        val (h, e) = parse(
            """
            #EXTM3U url-tvg="http://epg.xml" catchup="shift"
            #EXTINF:-1,A
            http://a
            """,
        )
        assertEquals("http://epg.xml", h?.epgUrl)
        assertEquals("shift", e[0].catchup)
    }

    @Test fun `comma inside quoted attribute does not split title`() {
        val (_, e) = parse(
            """
            #EXTINF:-1 group-title="Movies, HD",Film One
            http://f
            """,
        )
        assertEquals("Film One", e[0].name); assertEquals("Movies, HD", e[0].group)
    }

    @Test fun `extgrp and vlcopt`() {
        val (_, e) = parse(
            """
            #EXTINF:-1,B
            #EXTGRP:Sports
            #EXTVLCOPT:http-user-agent=MyUA/1.0
            #EXTVLCOPT:http-referrer=http://ref
            http://b
            """,
        )
        assertEquals("Sports", e[0].group); assertEquals("MyUA/1.0", e[0].userAgent); assertEquals("http://ref", e[0].referrer)
    }

    @Test fun `catchup attributes`() {
        val (_, e) = parse(
            """
            #EXTINF:-1 catchup="append" catchup-source="?utc={utc}" catchup-days="7" timeshift="3",C
            http://c
            """,
        )
        with(e[0]) { assertEquals("append", catchup); assertEquals("?utc={utc}", catchupSource); assertEquals(7, catchupDays); assertEquals(3, timeshift) }
    }

    @Test fun `url without extinf ignored and malformed lines skipped`() {
        val (_, e) = parse(
            """
            #EXTM3U
            http://orphan
            #EXTINF garbage
            http://g
            #EXTINF:-1,
            http://noname
            """,
        )
        assertEquals(2, e.size)
        assertEquals("http://noname", e[1].name)
    }

    @Test fun `bom crlf and blank lines`() {
        val (_, e) = parse("\uFEFF#EXTM3U\r\n\r\n#EXTINF:-1,D\r\nhttp://d\r\n")
        assertEquals(1, e.size); assertEquals("http://d", e[0].url)
    }

    @Test fun `empty attributes become null`() {
        val (_, e) = parse(
            """
            #EXTINF:-1 tvg-id="" group-title="" tvg-chno="x",E
            http://e
            """,
        )
        assertNull(e[0].tvgId); assertNull(e[0].group); assertNull(e[0].number)
    }

    @Test fun `large playlist streams`() {
        val sb = StringBuilder("#EXTM3U\n")
        repeat(50_000) { sb.append("#EXTINF:-1 group-title=\"G${it % 50}\",Ch $it\nhttp://h/$it\n") }
        var n = 0
        val start = System.nanoTime()
        M3uParser().parse(StringReader(sb.toString())) { n++ }
        assertEquals(50_000, n)
        assertTrue((System.nanoTime() - start) / 1_000_000 < 5_000)
    }

    @Test fun `channel key prefers tvg id`() {
        assertEquals("id:x", ChannelKey.of(M3uEntry("a", "u", tvgId = "x")))
        assertTrue(ChannelKey.of(M3uEntry("a", "u")).startsWith("url:"))
        assertEquals(ChannelKey.of(M3uEntry("a", "u")), ChannelKey.of(M3uEntry("b", "u")))
    }
}
