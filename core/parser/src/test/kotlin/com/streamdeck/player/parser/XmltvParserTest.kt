package com.streamdeck.player.parser

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.time.Instant

class XmltvParserTest {
    private fun parser(xml: String): XmlPullParser = KXmlParser().apply {
        setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        setInput(StringReader(xml.trimIndent()))
    }

    private fun run(xml: String, from: Long = Long.MIN_VALUE, to: Long = Long.MAX_VALUE): Pair<List<XmltvChannel>, List<XmltvProgramme>> {
        val ch = mutableListOf<XmltvChannel>()
        val pr = mutableListOf<XmltvProgramme>()
        XmltvParser(from, to).parse(parser(xml), { ch += it }, { pr += it })
        return ch to pr
    }

    private val sample = """
        <?xml version="1.0" encoding="UTF-8"?>
        <tv>
          <channel id="bbc1.uk"><display-name lang="en">BBC One</display-name><display-name>BBC 1</display-name><icon src="http://i/bbc.png"/></channel>
          <channel id="cctv1"><display-name>CCTV-1 综合</display-name></channel>
          <programme start="20240310120000 +0000" stop="20240310130000 +0000" channel="bbc1.uk">
            <title lang="en">News</title><title lang="cy">Newyddion</title>
            <desc>Headlines &amp; weather</desc><category>News</category><icon src="http://i/p.jpg"/>
          </programme>
          <programme start="20240310200000 +0800" stop="20240310210000 +0800" channel="cctv1">
            <title><![CDATA[新闻联播]]></title>
          </programme>
        </tv>
    """

    @Test fun `parses channels with names and icon`() {
        val (ch, _) = run(sample)
        assertEquals(2, ch.size)
        assertEquals(listOf("BBC One", "BBC 1"), ch[0].displayNames)
        assertEquals("http://i/bbc.png", ch[0].icon)
    }

    @Test fun `parses programmes in UTC with first title`() {
        val (_, pr) = run(sample)
        assertEquals(2, pr.size)
        with(pr[0]) {
            assertEquals("News", title)
            assertEquals("Headlines & weather", description)
            assertEquals("News", category)
            assertEquals(Instant.parse("2024-03-10T12:00:00Z").toEpochMilli(), startUtcMs)
            assertEquals(Instant.parse("2024-03-10T13:00:00Z").toEpochMilli(), endUtcMs)
        }
        assertEquals("新闻联播", pr[1].title)
        assertEquals(Instant.parse("2024-03-10T12:00:00Z").toEpochMilli(), pr[1].startUtcMs)
    }

    @Test fun `window filter skips programmes`() {
        val from = Instant.parse("2024-03-10T12:30:00Z").toEpochMilli()
        val to = Instant.parse("2024-03-10T12:45:00Z").toEpochMilli()
        val (_, pr) = run(sample, from, to)
        assertEquals(1, pr.size)
        assertEquals("bbc1.uk", pr[0].channelId)
    }

    @Test fun `missing stop gets default duration and broken programmes skipped`() {
        val (_, pr) = run(
            """
            <tv>
              <programme start="20240101000000" channel="a"><title>A</title></programme>
              <programme channel="a"><title>NoStart</title></programme>
              <programme start="20240101010000"><title>NoChannel</title></programme>
            </tv>
            """,
        )
        assertEquals(1, pr.size)
        assertEquals(3_600_000L, pr[0].endUtcMs - pr[0].startUtcMs)
    }

    @Test fun `nested markup inside desc is flattened`() {
        val (_, pr) = run("""<tv><programme start="20240101000000" stop="20240101010000" channel="a"><title>T</title><desc>Hello <b>bold</b> world</desc></programme></tv>""")
        assertEquals("Hello bold world", pr[0].description)
    }

    @Test fun `empty document`() {
        val (ch, pr) = run("<tv/>")
        assertEquals(0, ch.size)
        assertEquals(0, pr.size)
    }
}

class XmltvTimeTest {
    private fun utc(s: String) = Instant.parse(s).toEpochMilli()

    @Test fun `with offset`() { assertEquals(utc("2024-03-10T10:30:00Z"), XmltvTime.parse("20240310123000 +0200")) }

    @Test fun `negative offset`() { assertEquals(utc("2024-03-10T17:30:00Z"), XmltvTime.parse("20240310123000 -0500")) }

    @Test fun `offset with colon`() { assertEquals(utc("2024-03-10T10:30:00Z"), XmltvTime.parse("20240310123000 +02:00")) }

    @Test fun `no offset means UTC`() { assertEquals(utc("2024-03-10T12:30:00Z"), XmltvTime.parse("20240310123000")) }

    @Test fun `minutes precision`() { assertEquals(utc("2024-03-10T12:30:00Z"), XmltvTime.parse("202403101230")) }

    @Test fun `date only`() { assertEquals(utc("2024-03-10T00:00:00Z"), XmltvTime.parse("20240310")) }

    @Test fun `malformed`() {
        assertNull(XmltvTime.parse("yesterday"))
        assertNull(XmltvTime.parse("20241340000000 +0000"))
    }

    @Test fun `half hour offsets`() { assertEquals(utc("2024-03-10T07:00:00Z"), XmltvTime.parse("20240310123000 +0530")) }
}

class EpgMatcherTest {
    private val epg = listOf(
        XmltvChannel("bbc1.uk", listOf("BBC One", "BBC 1"), null),
        XmltvChannel("CNN.us", listOf("CNN International"), null),
        XmltvChannel("film4", listOf("Film4"), null),
        XmltvChannel("film4plus1", listOf("Film4 +1"), null),
        XmltvChannel("discovery", listOf("Discovery Channel"), null),
    )
    private val m = EpgMatcher(epg)

    @Test fun `tvg id first`() { assertEquals("bbc1.uk", m.match(MatchChannel("k", "bbc1.uk", null, "whatever"))) }

    @Test fun `tvg id case insensitive`() { assertEquals("CNN.us", m.match(MatchChannel("k", "cnn.US", null, "x"))) }

    @Test fun `tvg name against display names`() { assertEquals("bbc1.uk", m.match(MatchChannel("k", null, "BBC 1", "x"))) }

    @Test fun `normalized name strips prefix and quality`() { assertEquals("bbc1.uk", m.match(MatchChannel("k", null, null, "UK: BBC One HD"))) }

    @Test fun `plus one is a different channel`() { assertEquals("film4plus1", m.match(MatchChannel("k", null, null, "Film4 +1 HD"))) }

    @Test fun `fuzzy one edit`() { assertEquals("discovery", m.match(MatchChannel("k", null, null, "Discovery Chanel"))) }

    @Test fun `unknown returns null`() { assertNull(m.match(MatchChannel("k", "nope", null, "Totally Different"))) }

    @Test fun `edit distance helper`() {
        assertEquals(true, EpgMatcher.withinOneEdit("abcdef", "abcdeg"))
        assertEquals(true, EpgMatcher.withinOneEdit("abcdef", "abcdf"))
        assertEquals(false, EpgMatcher.withinOneEdit("abcdef", "abcxyz"))
    }
}
