package com.streambox.player.core.data.repository

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SearchQueryTest {
    @Test
    fun `blank text is no query`() {
        assertNull(SearchQuery.parse(""))
        assertNull(SearchQuery.parse("   "))
    }

    @Test
    fun `each word becomes a quoted prefix term`() {
        val query = checkNotNull(SearchQuery.parse("  CCTV-1 news "))
        assertEquals("\"CCTV\"* \"1\"* \"news\"*", query.ftsMatch)
        assertEquals("%CCTV-1 news%", query.likePattern)
        assertFalse(query.substring)
    }

    @Test
    fun `fts operators and quotes typed by the user cannot break the expression`() {
        val query = checkNotNull(SearchQuery.parse("\"a\" OR b* NEAR(c)"))
        assertEquals("\"a\"* \"OR\"* \"b\"* \"NEAR\"* \"c\"*", query.ftsMatch)
    }

    @Test
    fun `like wildcards and the escape character are escaped`() {
        assertEquals("%100\\% \\_x\\\\%", checkNotNull(SearchQuery.parse("100% _x\\")).likePattern)
    }

    @Test
    fun `text without any word has no fts term`() {
        val query = checkNotNull(SearchQuery.parse("+-"))
        assertNull(query.ftsMatch)
        assertEquals("%+-%", query.likePattern)
    }

    @Test
    fun `chinese text also searches names by substring`() {
        val query = checkNotNull(SearchQuery.parse("新闻"))
        assertTrue(query.substring)
        assertEquals("\"新闻\"*", query.ftsMatch)
        assertEquals("%新闻%", query.likePattern)
    }
}
