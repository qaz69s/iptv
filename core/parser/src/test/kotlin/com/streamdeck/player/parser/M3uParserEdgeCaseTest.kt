package com.streamdeck.player.parser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.io.StringReader

class M3uParserEdgeCaseTest {
    private fun parse(s: String): Pair<M3uHeader?, List<M3uEntry>> {
        var h: M3uHeader? = null
        val list = mutableListOf<M3uEntry>()
        M3uParser().parse(StringReader(s.trimIndent()), { h = it }) { list += it }
        return h to list
    }

    private fun one(s: String): M3uEntry = parse(s).second.single()

    @Test fun `lowercase directives accepted`() {
        assertEquals("A", one("#extm3u\n#extinf:-1,A\nhttp://a").name)
    }

    @Test fun `attribute keys are case insensitive`() {
        assertEquals("x", one("#EXTINF:-1 TVG-ID=\"x\",A\nhttp://a").tvgId)
    }

    @Test fun `zero duration accepted`() {
        assertEquals("Z", one("#EXTINF:0,Z\nhttp://z").name)
    }

    @Test fun `title is trimmed`() {
        assertEquals("Spaced", one("#EXTINF:-1,   Spaced   \nhttp://s").name)
    }

    @Test fun `title may contain commas`() {
        assertEquals("News, Live", one("#EXTINF:-1 tvg-id=\"n\",News, Live\nhttp://n").name)
    }

    @Test fun `group-title wins over EXTGRP`() {
        assertEquals("A", one("#EXTINF:-1 group-title=\"A\",C\n#EXTGRP:B\nhttp://c").group)
    }

    @Test fun `unknown vlc options ignored`() {
        val e = one("#EXTINF:-1,C\n#EXTVLCOPT:network-caching=1000\n#EXTVLCOPT:http-referer=http://r\nhttp://c")
        assertEquals("http://r", e.referrer)
        assertNull(e.userAgent)
    }

    @Test fun `user-agent attribute on EXTINF`() {
        assertEquals("UA1", one("#EXTINF:-1 user-agent=\"UA1\",C\nhttp://c").userAgent)
    }

    @Test fun `vlcopt user agent overrides attribute`() {
        assertEquals("UA2", one("#EXTINF:-1 user-agent=\"UA1\",C\n#EXTVLCOPT:http-user-agent=UA2\nhttp://c").userAgent)
    }

    @Test fun `comments between EXTINF and url ignored`() {
        assertEquals("http://c", one("#EXTINF:-1,C\n# a comment\n#EXTSIZE: 1\nhttp://c").url)
    }

    @Test fun `missing header still parses entries`() {
        val (h, e) = parse("#EXTINF:-1,A\nhttp://a")
        assertNull(h)
        assertEquals(1, e.size)
    }

    @Test fun `x-tvg-url header fallback`() {
        assertEquals("http://e.xml", parse("#EXTM3U x-tvg-url=\"http://e.xml\"\n#EXTINF:-1,A\nhttp://a").first?.epgUrl)
    }

    @Test fun `only the first header is used`() {
        val (h, _) = parse("#EXTM3U url-tvg=\"first\"\n#EXTM3U url-tvg=\"second\"\n#EXTINF:-1,A\nhttp://a")
        assertEquals("first", h?.epgUrl)
    }

    @Test fun `channel catchup overrides header`() {
        val e = one("#EXTM3U catchup=\"shift\" catchup-source=\"?h\"\n#EXTINF:-1 catchup=\"append\",A\nhttp://a")
        assertEquals("append", e.catchup)
        assertEquals("?h", e.catchupSource)
    }

    @Test fun `non numeric catchup days becomes null`() {
        assertNull(one("#EXTINF:-1 catchup-days=\"seven\",A\nhttp://a").catchupDays)
    }

    @Test fun `channel numbers with leading zeros`() {
        assertEquals(7, one("#EXTINF:-1 tvg-chno=\"007\",A\nhttp://a").number)
    }

    @Test fun `udp rtp and rtsp urls`() {
        val (_, e) = parse("#EXTINF:-1,U\nudp://@239.0.0.1:1234\n#EXTINF:-1,R\nrtp://239.0.0.2:5000\n#EXTINF:-1,S\nrtsp://cam/stream")
        assertEquals(listOf("udp://@239.0.0.1:1234", "rtp://239.0.0.2:5000", "rtsp://cam/stream"), e.map { it.url })
    }

    @Test fun `second EXTINF replaces dangling first`() {
        assertEquals("B", one("#EXTINF:-1,A\n#EXTINF:-1,B\nhttp://b").name)
    }

    @Test fun `trailing EXTINF without url ignored`() {
        assertEquals(1, parse("#EXTINF:-1,A\nhttp://a\n#EXTINF:-1,B").second.size)
    }

    @Test fun `name falls back to tvg-name`() {
        assertEquals("TvgName", one("#EXTINF:-1 tvg-name=\"TvgName\",\nhttp://a").name)
    }

    @Test fun `unicode names`() {
        val e = one("#EXTINF:-1 group-title=\"央视\",CCTV-1 综合\nhttp://a")
        assertEquals("CCTV-1 综合", e.name)
        assertEquals("央视", e.group)
    }

    @Test fun `returns entry count`() {
        var n = 0
        val c = M3uParser().parse(StringReader("#EXTINF:-1,A\nhttp://a\n#EXTINF:-1,B\nhttp://b")) { n++ }
        assertEquals(2, c)
        assertEquals(2, n)
    }

    @Test fun `empty input`() {
        val (h, e) = parse("")
        assertNull(h)
        assertEquals(0, e.size)
    }

    @Test fun `spaces around equals and equals in values`() {
        val e = one("#EXTINF:-1 tvg-id = \"a\" catchup-source=\"?utc={utc}&lutc={lutc}\",A\nhttp://a")
        assertEquals("a", e.tvgId)
        assertEquals("?utc={utc}&lutc={lutc}", e.catchupSource)
    }

    @Test fun `CR only line endings`() {
        assertEquals(2, parse("#EXTM3U\r#EXTINF:-1,A\rhttp://a\r#EXTINF:-1,B\rhttp://b\r").second.size)
    }

    @Test fun `url hash keys differ per url`() {
        assertNotEquals(ChannelKey.of(M3uEntry("a", "http://1")), ChannelKey.of(M3uEntry("a", "http://2")))
    }
}
