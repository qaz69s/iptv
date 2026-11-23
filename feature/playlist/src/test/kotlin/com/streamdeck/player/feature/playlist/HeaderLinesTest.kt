package com.streamdeck.player.feature.playlist

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HeaderLinesTest {
    @Test fun emptyTextHasNoHeaders() {
        val r = HeaderLines.parse("  \n\n")
        assertNull(r.json)
        assertEquals(emptyList<Int>(), r.badLines)
    }

    @Test fun parsesNameValueLines() {
        val r = HeaderLines.parse("Referer: https://a.example/\nX-Token:abc")
        assertEquals("{\"Referer\":\"https://a.example/\",\"X-Token\":\"abc\"}", r.json)
        assertEquals(emptyList<Int>(), r.badLines)
    }

    @Test fun reportsMalformedLines() {
        val r = HeaderLines.parse("Good: 1\nno colon here\n: missing name\nBad Name: x")
        assertEquals(listOf(2, 3, 4), r.badLines)
        assertEquals("{\"Good\":\"1\"}", r.json)
    }

    @Test fun escapesQuotesAndBackslashes() {
        val r = HeaderLines.parse("X-A: say \"hi\" \\ there")
        assertEquals("{\"X-A\":\"say \\\"hi\\\" \\\\ there\"}", r.json)
    }

    @Test fun formatIsTheInverseOfParse() {
        val text = "Referer: https://a.example/\nX-A: say \"hi\""
        assertEquals(text, HeaderLines.format(HeaderLines.parse(text).json))
    }

    @Test fun formatToleratesNullAndBlank() {
        assertEquals("", HeaderLines.format(null))
        assertEquals("", HeaderLines.format(" "))
    }
}
