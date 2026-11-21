package com.streambox.player.core.parser.xmltv

import java.io.StringReader
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class XmlTokenizerTest {
    /** Flattens the events into strings like `<a x=1>`, `"text"`, `</a>`; whitespace-only text dropped. */
    private fun tokens(xml: String): List<String> {
        val tokenizer = XmlTokenizer(StringReader(xml))
        val out = mutableListOf<String>()
        while (true) {
            when (tokenizer.next()) {
                XmlTokenizer.Event.START_TAG -> out += "<${tokenizer.name}>"
                XmlTokenizer.Event.END_TAG -> out += "</${tokenizer.name}>"
                XmlTokenizer.Event.TEXT -> if (tokenizer.text.isNotBlank()) out += "\"${tokenizer.text}\""
                XmlTokenizer.Event.END_DOCUMENT -> return out
            }
        }
    }

    private fun shorten(token: String) = if (token.length > 100) "<long text>" else token

    private fun firstStartTag(xml: String): XmlTokenizer {
        val tokenizer = XmlTokenizer(StringReader(xml))
        var event = tokenizer.next()
        while (event != XmlTokenizer.Event.START_TAG) event = tokenizer.next()
        return tokenizer
    }

    @Test
    fun `elements text and end tags`() {
        assertEquals(listOf("<a>", "<b>", "\"hi\"", "</b>", "</a>"), tokens("<a><b>hi</b></a>"))
    }

    @Test
    fun `self closing tags produce a start and an end tag`() {
        assertEquals(listOf("<a>", "<icon>", "</icon>", "</a>"), tokens("<a><icon src=\"x\"/></a>"))
    }

    @Test
    fun `xml declaration doctype with internal subset and comments are skipped`() {
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE tv SYSTEM "xmltv.dtd" [ <!ENTITY foo "bar>"> <!ELEMENT tv (channel*)> ]>
            <!-- a comment with <tags> and -- dashes -->
            <tv><?pi data?><x/></tv>"""
        assertEquals(listOf("<tv>", "<x>", "</x>", "</tv>"), tokens(xml))
    }

    @Test
    fun `attributes with double, single and no quotes and without value`() {
        val tag = firstStartTag("<p a=\"1\" b='two words' c=3 d e = \"5\">")
        assertEquals("1", tag.attribute("a"))
        assertEquals("two words", tag.attribute("b"))
        assertEquals("3", tag.attribute("c"))
        assertEquals("", tag.attribute("d"))
        assertEquals("5", tag.attribute("e"))
        assertNull(tag.attribute("missing"))
    }

    @Test
    fun `named and numeric entities are decoded in text and attributes`() {
        assertEquals(
            listOf("<t>", "\"A & B < C > D \" E ' F\u00A0G é 中 \uD83D\uDE00\"", "</t>"),
            tokens("<t>A &amp; B &lt; C &gt; D &quot; E &apos; F&nbsp;G &#233; &#x4E2D; &#x1F600;</t>"),
        )
        assertEquals("Tom & Jerry", firstStartTag("<p title=\"Tom &amp; Jerry\">").attribute("title"))
    }

    @Test
    fun `unknown, unterminated and invalid entities stay as written`() {
        assertEquals(
            listOf("<t>", "\"&copy; & x &amp &#xZZ; &#0;\"", "</t>"),
            tokens("<t>&copy; & x &amp &#xZZ; &#0;</t>"),
        )
    }

    @Test
    fun `cdata is text and markup inside it is not interpreted`() {
        assertEquals(
            listOf("<d>", "\"before \"", "\"<b>bold</b> & ]] more\"", "\" after\"", "</d>"),
            tokens("<d>before <![CDATA[<b>bold</b> & ]] more]]> after</d>"),
        )
    }

    @Test
    fun `a stray less-than sign is kept as text`() {
        assertEquals(listOf("<t>", "\"a \"", "\"<\"", "\" b\"", "</t>"), tokens("<t>a < b</t>"))
    }

    @Test
    fun `a truncated document ends without an exception`() {
        assertEquals(listOf("<tv>", "<programme>", "\"half\""), tokens("<tv><programme start=\"1\">half"))
        assertEquals(listOf("<tv>", "<programme>"), tokens("<tv><programme start=\"1"))
        assertEquals(listOf("<tv>"), tokens("<tv><!-- never closed"))
    }

    @Test
    fun `byte order mark is ignored`() {
        assertEquals(listOf("<tv>", "</tv>"), tokens("\uFEFF<tv></tv>"))
    }

    @Test
    fun `markers split across buffer refills are still recognised`() {
        // Pushes "<![CDATA[" and "-->" across the 64 KiB buffer boundary at every offset nearby.
        for (shift in 0 until 12) {
            val padding = "x".repeat(64 * 1024 - 5 + shift)
            val cdataAtBoundary = "<a>$padding<![CDATA[q]]></a><b/>"
            val commentAtBoundary = "<a>$padding<!-- c --></a><b/>"
            val expected = listOf("<a>", "<long text>", "\"q\"", "</a>", "<b>", "</b>")
            assertEquals(expected, tokens(cdataAtBoundary).map(::shorten), "CDATA, shift $shift")
            assertEquals(expected - "\"q\"", tokens(commentAtBoundary).map(::shorten), "comment, shift $shift")
        }
    }

    @Test
    fun `entity decoding helper`() {
        assertEquals("&", XmlTokenizer.decodeEntity("amp"))
        assertEquals("A", XmlTokenizer.decodeEntity("#65"))
        assertEquals("A", XmlTokenizer.decodeEntity("#x41"))
        assertNull(XmlTokenizer.decodeEntity("#xD800"))
        assertNull(XmlTokenizer.decodeEntity("#1114112"))
        assertNull(XmlTokenizer.decodeEntity(""))
    }
}
