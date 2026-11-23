package com.streamdeck.player.core.network

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okhttp3.OkHttpClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class XtreamParsersTest {
    private fun j(s: String): JsonElement = Json.parseToJsonElement(s)

    @Test fun `auth active`() {
        val a = XtreamParsers.auth(j("""{"user_info":{"auth":1,"status":"Active","exp_date":"1735689600","max_connections":"2"},"server_info":{"timezone":"Europe/Berlin"}}"""))
        assertTrue(a.authorized)
        assertEquals(1735689600L, a.expiresAtSec)
        assertEquals(2, a.maxConnections)
        assertEquals("Europe/Berlin", a.serverTimezone)
    }

    @Test fun `auth expired`() {
        assertFalse(XtreamParsers.auth(j("""{"user_info":{"auth":1,"status":"Expired"}}""")).authorized)
        assertFalse(XtreamParsers.auth(j("""{"user_info":{"auth":0}}""")).authorized)
    }

    @Test fun `live stream with string numbers and archive`() {
        val l = XtreamParsers.live(j("""{"num":"5","name":"News","stream_id":"101","stream_icon":"","epg_channel_id":null,"category_id":7,"tv_archive":"1","tv_archive_duration":"3"}"""))!!
        assertEquals(101L, l.streamId)
        assertEquals(5, l.number)
        assertNull(l.icon)
        assertNull(l.epgChannelId)
        assertEquals("7", l.categoryId)
        assertTrue(l.tvArchive)
        assertEquals(3, l.archiveDays)
    }

    @Test fun `live without id is skipped`() { assertNull(XtreamParsers.live(j("""{"name":"x"}"""))) }

    @Test fun `vod defaults extension`() {
        val v = XtreamParsers.vod(j("""{"stream_id":9,"name":"Film","rating":"7.5"}"""))!!
        assertEquals("mp4", v.extension)
        assertEquals(7.5f, v.rating)
    }

    @Test fun `series info with season map`() {
        val info = XtreamParsers.seriesInfo(
            j("""{"info":{"name":"Show"},"episodes":{"2":[{"id":"21","episode_num":1,"title":"S2E1","container_extension":"mkv"}],"1":[{"id":"12","episode_num":"2","title":"S1E2"},{"id":"11","episode_num":"1","title":"S1E1","info":{"duration_secs":"1500"}}]}}"""),
        )
        assertEquals(listOf(11L, 12L, 21L), info.episodes.map { it.id })
        assertEquals(1500, info.episodes[0].durationSec)
        assertEquals("mkv", info.episodes[2].extension)
        assertEquals(2, info.episodes[2].season)
    }

    @Test fun `series info with empty episodes`() {
        assertEquals(0, XtreamParsers.seriesInfo(j("""{"info":[],"episodes":[]}""")).episodes.size)
    }

    @Test fun `server normalization`() {
        assertEquals("http://host:8080", XtreamUrls.normalizeServer("host:8080/"))
        assertEquals("https://h", XtreamUrls.normalizeServer("https://h/player_api.php"))
    }

    @Test fun `stream urls`() {
        val a = XtreamAccount("http://h:80", "u", "p", true)
        assertEquals("http://h:80/live/u/p/1.ts", XtreamUrls.live(a, 1, "ts"))
        assertEquals("http://h:80/movie/u/p/2.mkv", XtreamUrls.movie(a, 2, "mkv"))
        assertEquals("http://h:80/series/u/p/3.mp4", XtreamUrls.episode(a, 3, "mp4"))
    }

    @Test fun `elements accepts array object and empty`() {
        val c = XtreamClient(OkHttpClient())
        fun count(s: String): Int { var n = 0; c.elements(s.byteInputStream()) { n++ }; return n }
        assertEquals(2, count("""  [{"a":1},{"a":2}]"""))
        assertEquals(2, count("""{"1":{"a":1},"2":{"a":2}}"""))
        assertEquals(0, count("""{}"""))
        assertEquals(0, count(""))
    }
}
