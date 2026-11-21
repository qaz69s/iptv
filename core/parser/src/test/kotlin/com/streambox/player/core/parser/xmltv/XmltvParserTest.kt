package com.streambox.player.core.parser.xmltv

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.StringReader
import java.util.zip.GZIPOutputStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class XmltvParserTest {
    private class Parsed(val items: List<XmltvItem>, val stats: XmltvParseStats) {
        val channels get() = items.filterIsInstance<XmltvChannel>()
        val programmes get() = items.filterIsInstance<XmltvProgramme>()
    }

    private fun parse(xml: String, language: String? = null): Parsed {
        val items = mutableListOf<XmltvItem>()
        val stats = XmltvParser.parse(StringReader(xml), language) { items += it }
        return Parsed(items, stats)
    }

    private fun tv(body: String) =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<tv generator-info-name=\"t\">\n$body\n</tv>"

    private fun programme(channel: String, start: String, stop: String?, title: String = "Show", extra: String = "") =
        "<programme start=\"$start\"${stop?.let { " stop=\"$it\"" }.orEmpty()} channel=\"$channel\">" +
            "<title>$title</title>$extra</programme>"

    private val t0 = XmltvTime.parse("20261121120000 +0000")!!
    private val hour = 3_600_000L

    @Nested
    inner class Channels {
        @Test
        fun `channel with several display names and an icon`() {
            val parsed = parse(
                tv(
                    """<channel id="cctv1.cn">
                         <display-name lang="zh">CCTV-1 综合</display-name>
                         <display-name lang="en">CCTV 1</display-name>
                         <display-name>1</display-name>
                         <icon src="http://logo/cctv1.png" width="100"/>
                         <url>http://cctv.com</url>
                       </channel>""",
                ),
            )
            val channel = parsed.channels.single()
            assertEquals("cctv1.cn", channel.id)
            assertEquals(listOf("CCTV-1 综合", "CCTV 1", "1"), channel.displayNames)
            assertEquals("http://logo/cctv1.png", channel.iconUrl)
            assertEquals(1L, parsed.stats.channels)
        }

        @Test
        fun `display name in the preferred language comes first`() {
            val xml = tv(
                """<channel id="a"><display-name lang="en">News</display-name>
                   <display-name lang="zh-CN">新闻</display-name></channel>""",
            )
            assertEquals(listOf("新闻", "News"), parse(xml, language = "zh").channels.single().displayNames)
            assertEquals(listOf("News", "新闻"), parse(xml).channels.single().displayNames)
        }

        @Test
        fun `whitespace inside display names is collapsed and duplicates dropped`() {
            val xml = tv(
                "<channel id=\"a\"><display-name>  BBC \n  One </display-name>" +
                    "<display-name>BBC One</display-name></channel>",
            )
            assertEquals(listOf("BBC One"), parse(xml).channels.single().displayNames)
        }

        @Test
        fun `channel without id is skipped and counted`() {
            val parsed = parse(tv("<channel><display-name>X</display-name></channel><channel id=\"b\"/>"))
            assertEquals(listOf("b"), parsed.channels.map { it.id })
            assertEquals(1L, parsed.stats.skippedChannels)
            assertEquals(emptyList<String>(), parsed.channels.single().displayNames)
            assertNull(parsed.channels.single().iconUrl)
        }
    }

    @Nested
    inner class Programmes {
        @Test
        fun `programme fields and UTC times`() {
            val parsed = parse(
                tv(
                    """<programme start="20261121200000 +0800" stop="20261121210000 +0800" channel="cctv1.cn">
                         <title lang="zh">新闻联播</title>
                         <sub-title>Episode 1</sub-title>
                         <desc lang="zh">今日要闻。
第二段。</desc>
                         <category lang="zh">新闻</category>
                         <category lang="en">News</category>
                         <icon src="http://img/p.jpg"/>
                         <rating system="x"><value>G</value></rating>
                       </programme>""",
                ),
            )
            val p = parsed.programmes.single()
            assertEquals("cctv1.cn", p.channelId)
            assertEquals(t0, p.start)
            assertEquals(t0 + hour, p.stop)
            assertEquals("新闻联播", p.title)
            assertEquals("今日要闻。\n第二段。", p.description)
            assertEquals("新闻", p.category)
            assertEquals("http://img/p.jpg", p.iconUrl)
        }

        @Test
        fun `preferred language wins for title description and category`() {
            val xml = tv(
                "<programme start=\"20261121120000\" stop=\"20261121130000\" channel=\"a\">" +
                    "<title lang=\"en\">News</title><title lang=\"de\">Nachrichten</title>" +
                    "<desc lang=\"en\">English</desc><desc lang=\"de\">Deutsch</desc>" +
                    "<category lang=\"en\">Info</category><category lang=\"de\">Information</category></programme>",
            )
            val german = parse(xml, language = "de-AT").programmes.single()
            assertEquals(
                listOf("Nachrichten", "Deutsch", "Information"),
                listOf(german.title, german.description, german.category),
            )
            val first = parse(xml, language = "fr").programmes.single()
            assertEquals(listOf("News", "English", "Info"), listOf(first.title, first.description, first.category))
        }

        @Test
        fun `optional fields may be missing`() {
            val p = parse(tv(programme("a", "20261121120000", "20261121130000"))).programmes.single()
            assertNull(p.description)
            assertNull(p.category)
            assertNull(p.iconUrl)
        }

        @Test
        fun `entities and CDATA in titles and descriptions`() {
            val p = parse(
                tv(
                    programme(
                        "a",
                        "20261121120000",
                        "20261121130000",
                        title = "Tom &amp; Jerry &#8211; Part&#x20;2",
                        extra = "<desc><![CDATA[<b>Bold</b> & fun]]> &lt;end&gt;</desc>",
                    ),
                ),
            ).programmes.single()
            assertEquals("Tom & Jerry \u2013 Part 2", p.title)
            assertEquals("<b>Bold</b> & fun <end>", p.description)
        }

        @Test
        fun `missing offset is read as UTC and missing seconds as zero`() {
            val p = parse(tv(programme("a", "202611211200", "20261121130000"))).programmes.single()
            assertEquals(t0, p.start)
            assertEquals(t0 + hour, p.stop)
        }

        @Test
        fun `negative and positive offsets`() {
            val parsed = parse(
                tv(
                    programme("a", "20261121070000 -0500", "20261121080000 -0500") +
                        programme("b", "20261121203000 +0830", "20261121213000 +0830"),
                ),
            )
            assertEquals(listOf(t0, t0), parsed.programmes.map { it.start })
        }

        @Test
        fun `programme crossing midnight keeps its duration`() {
            val p = parse(tv(programme("a", "20261121233000 +0000", "20261122010000 +0000"))).programmes.single()
            assertEquals(90 * 60_000L, p.stop - p.start)
            assertEquals(XmltvTime.parse("20261122000000")!!, p.start + 30 * 60_000L)
        }

        @Test
        fun `programme crossing the local midnight of its offset lands on the previous UTC day`() {
            val p = parse(tv(programme("a", "20261122003000 +0800", "20261122013000 +0800"))).programmes.single()
            assertEquals(XmltvTime.parse("20261121163000"), p.start)
        }

        @Test
        fun `missing stop takes the next programme start of the same channel`() {
            val parsed = parse(
                tv(
                    programme("a", "20261121120000", null, title = "A1") +
                        programme("b", "20261121120000", "20261121123000", title = "B1") +
                        programme("a", "20261121130000", "20261121140000", title = "A2"),
                ),
            )
            assertEquals(listOf("B1", "A1", "A2"), parsed.programmes.map { it.title })
            val a1 = parsed.programmes.first { it.title == "A1" }
            assertEquals(t0 + hour, a1.stop)
            assertEquals(0L, parsed.stats.skippedProgrammes)
        }

        @Test
        fun `chained programmes without stop and a last one that is skipped`() {
            val parsed = parse(
                tv(
                    programme("a", "20261121120000", null, title = "1") +
                        programme("a", "20261121130000", null, title = "2") +
                        programme("a", "20261121140000", null, title = "3"),
                ),
            )
            assertEquals(listOf("1", "2"), parsed.programmes.map { it.title })
            assertEquals(listOf(t0 + hour, t0 + 2 * hour), parsed.programmes.map { it.stop })
            assertEquals(1L, parsed.stats.skippedProgrammes)
        }

        @Test
        fun `invalid stop is treated like a missing one`() {
            val parsed = parse(
                tv(
                    programme("a", "20261121120000", "nonsense", title = "1") +
                        programme("a", "20261121130000", "20261121140000"),
                ),
            )
            assertEquals(t0 + hour, parsed.programmes.first().stop)
        }

        @Test
        fun `a missing-stop programme followed by an earlier one is skipped`() {
            val parsed = parse(
                tv(
                    programme("a", "20261121130000", null, title = "late") +
                        programme("a", "20261121120000", "20261121123000"),
                ),
            )
            assertEquals(listOf("Show"), parsed.programmes.map { it.title })
            assertEquals(1L, parsed.stats.skippedProgrammes)
        }

        @Test
        fun `malformed programmes are skipped and counted`() {
            val untitled = "<programme start=\"20261121120000\" stop=\"20261121130000\" channel=\"a\">"
            val parsed = parse(
                tv(
                    programme("", "20261121120000", "20261121130000") + // no channel
                        programme("a", "bad", "20261121130000") + // bad start
                        programme("a", "20261121130000", "20261121120000") + // stop before start
                        programme("a", "20261121130000", "20261121130000") + // zero length
                        "$untitled</programme>" + // no title
                        "$untitled<title> </title></programme>" + // blank title
                        programme("ok", "20261121120000", "20261121130000"),
                ),
            )
            assertEquals(listOf("ok"), parsed.programmes.map { it.channelId })
            assertEquals(6L, parsed.stats.skippedProgrammes)
            assertEquals(1L, parsed.stats.programmes)
        }

        @Test
        fun `unclosed inline tags inside a description do not swallow the next programme`() {
            val parsed = parse(
                tv(
                    programme("a", "20261121120000", "20261121130000", extra = "<desc>line<br>next</desc>") +
                        programme("a", "20261121130000", "20261121140000", title = "Second"),
                ),
            )
            assertEquals(listOf("Show", "Second"), parsed.programmes.map { it.title })
            assertEquals("linenext", parsed.programmes.first().description)
        }

        @Test
        fun `whitespace in titles is collapsed`() {
            val p = parse(
                tv(programme("a", "20261121120000", "20261121130000", title = "\n  The   Late\tShow  ")),
            ).programmes.single()
            assertEquals("The Late Show", p.title)
        }
    }

    @Nested
    inner class Documents {
        @Test
        fun `channels and programmes come in document order`() {
            val parsed = parse(
                tv(
                    "<channel id=\"a\"><display-name>A</display-name></channel>" +
                        programme("a", "20261121120000", "20261121130000") +
                        "<channel id=\"b\"><display-name>B</display-name></channel>",
                ),
            )
            assertEquals(
                listOf("XmltvChannel", "XmltvProgramme", "XmltvChannel"),
                parsed.items.map {
                    it.javaClass.simpleName
                },
            )
            assertEquals(XmltvParseStats(2, 1, 0, 0), parsed.stats)
        }

        @Test
        fun `doctype, comments and unknown elements are ignored`() {
            val xml = """<?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE tv SYSTEM "xmltv.dtd">
                <!-- generated -->
                <tv><unknown><channel-ish/></unknown>
                <channel id="a"><!-- c --><display-name>A</display-name></channel>
                </tv>"""
            assertEquals(listOf("a"), parse(xml).channels.map { it.id })
        }

        @Test
        fun `a truncated document keeps what was complete`() {
            val xml = "<tv><channel id=\"a\"/>" + programme("a", "20261121120000", "20261121130000") +
                "<programme start=\"20261121130000\" stop=\"20261121140000\" channel=\"a\"><title>cut"
            val parsed = parse(xml)
            assertEquals(1, parsed.channels.size)
            assertEquals(1, parsed.programmes.size)
            assertEquals(1L, parsed.stats.skippedProgrammes)
        }

        @Test
        fun `empty and non XMLTV input produce nothing`() {
            assertEquals(XmltvParseStats(0, 0, 0, 0), parse("").stats)
            assertEquals(XmltvParseStats(0, 0, 0, 0), parse("#EXTM3U\n#EXTINF:-1,x\nhttp://a").stats)
            assertEquals(XmltvParseStats(0, 0, 0, 0), parse("<html><body>404</body></html>").stats)
        }

        @Test
        fun `pull api and sequence give the same items`() {
            val xml = tv("<channel id=\"a\"/>" + programme("a", "20261121120000", "20261121130000"))
            val pulled = XmltvParser(StringReader(xml)).items().toList()
            assertEquals(parse(xml).items, pulled)
        }

        @Test
        fun `gzip input is detected by magic bytes`() {
            val xml = tv("<channel id=\"gz\"><display-name>Zipped</display-name></channel>")
            val bytes = ByteArrayOutputStream().also { out ->
                GZIPOutputStream(out).use { it.write(xml.toByteArray()) }
            }
            val items = XmltvParser(XmltvInput.reader(ByteArrayInputStream(bytes.toByteArray()))).items().toList()
            assertEquals(listOf(XmltvChannel("gz", listOf("Zipped"), null)), items)
        }

        @Test
        fun `plain input whose name says gz is still read`() {
            val xml = tv("<channel id=\"plain\"/>")
            val items = XmltvParser(XmltvInput.reader(ByteArrayInputStream(xml.toByteArray()))).items().toList()
            assertEquals(listOf("plain"), items.filterIsInstance<XmltvChannel>().map { it.id })
        }

        @Test
        fun `declared ISO-8859-1 encoding and UTF-8 with BOM are decoded`() {
            val latin = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?>" +
                "<tv><channel id=\"a\"><display-name>Télé</display-name></channel></tv>"
            val latinItems = XmltvParser(
                XmltvInput.reader(ByteArrayInputStream(latin.toByteArray(Charsets.ISO_8859_1))),
            ).items()
            assertEquals(listOf("Télé"), latinItems.filterIsInstance<XmltvChannel>().single().displayNames)

            val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
                "<tv><channel id=\"b\"><display-name>電視</display-name></channel></tv>".toByteArray()
            val bomItems = XmltvParser(XmltvInput.reader(ByteArrayInputStream(bom))).items()
            assertEquals(listOf("電視"), bomItems.filterIsInstance<XmltvChannel>().single().displayNames)
        }

        @Test
        fun `UTF-16 with byte order mark is decoded`() {
            val xml = "<tv><channel id=\"u\"><display-name>Ü</display-name></channel></tv>"
            val bytes = byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + xml.toByteArray(Charsets.UTF_16BE)
            val items = XmltvParser(XmltvInput.reader(ByteArrayInputStream(bytes))).items()
            assertEquals(listOf("Ü"), items.filterIsInstance<XmltvChannel>().single().displayNames)
        }

        @Test
        fun `unknown declared encoding falls back to UTF-8`() {
            val head = "<?xml version=\"1.0\" encoding=\"no-such-charset\"?>".toByteArray()
            assertEquals(Charsets.UTF_8, XmltvInput.detectCharset(head, head.size))
            assertEquals(Charsets.UTF_8, XmltvInput.detectCharset(ByteArray(0), 0))
        }

        @Test
        fun `stats count everything`() {
            val parsed = parse(
                tv(
                    "<channel id=\"a\"/><channel/>" + programme("a", "20261121120000", "20261121130000") +
                        programme("a", "x", "y"),
                ),
            )
            assertEquals(
                XmltvParseStats(channels = 1, programmes = 1, skippedChannels = 1, skippedProgrammes = 1),
                parsed.stats,
            )
            assertEquals(2L, parsed.stats.skipped)
            assertTrue(parsed.items.isNotEmpty())
        }
    }
}
