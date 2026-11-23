package com.streamdeck.player.feature.catchup

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class CatchupUrlBuilderTest {
    private val start: Instant = Instant.parse("2024-05-01T10:00:00Z") // 1714557600
    private val end: Instant = Instant.parse("2024-05-01T11:00:00Z") // 1714561200
    private val now: Instant = Instant.parse("2024-05-01T12:00:00Z") // 1714564800

    private fun req(
        url: String = "http://h/ch/index.m3u8",
        type: CatchupType? = null,
        source: String? = null,
        s: Instant = start,
        e: Instant = end,
        n: Instant = now,
        zone: ZoneId = ZoneOffset.UTC,
        xc: XtreamCatchup? = null,
        xcZone: ZoneId = ZoneOffset.UTC,
    ) = CatchupRequest(url, type, source, s, e, n, zone, xc, xcZone)

    @Test fun `shift appends utc and lutc`() {
        assertEquals("http://h/ch/index.m3u8?utc=1714557600&lutc=1714564800", CatchupUrlBuilder.build(req(type = CatchupType.SHIFT)))
    }

    @Test fun `shift uses ampersand when query exists`() {
        assertEquals("http://h/a.m3u8?token=x&utc=1714557600&lutc=1714564800", CatchupUrlBuilder.build(req(url = "http://h/a.m3u8?token=x", type = CatchupType.SHIFT)))
    }

    @Test fun `append adds source to stream url`() {
        assertEquals("http://h/a.ts?start=1714557600&end=1714561200", CatchupUrlBuilder.build(req(url = "http://h/a.ts", type = CatchupType.APPEND, source = "?start=\${start}&end=\${end}")))
    }

    @Test fun `append without source behaves like shift`() {
        assertEquals("http://h/a.ts?utc=1714557600&lutc=1714564800", CatchupUrlBuilder.build(req(url = "http://h/a.ts", type = CatchupType.APPEND)))
    }

    @Test fun `default replaces url with template`() {
        assertEquals(
            "http://arch/ch1/1714557600-3600.ts",
            CatchupUrlBuilder.build(req(type = CatchupType.DEFAULT, source = "http://arch/ch1/{utc}-{duration}.ts")),
        )
    }

    @Test fun `default with query-only source appends`() {
        assertEquals("http://h/ch/index.m3u8?archive=1714557600", CatchupUrlBuilder.build(req(type = CatchupType.DEFAULT, source = "?archive={utc}")))
    }

    @Test fun `no type no source means shift`() {
        assertEquals("http://h/ch/index.m3u8?utc=1714557600&lutc=1714564800", CatchupUrlBuilder.build(req()))
    }

    @Test fun `no type with source means default`() {
        assertEquals("http://x/1714557600", CatchupUrlBuilder.build(req(source = "http://x/\${start}")))
    }

    @Test fun `vod uses source template`() {
        assertEquals("http://v/1714557600/1714561200.mp4", CatchupUrlBuilder.build(req(type = CatchupType.VOD, source = "http://v/{utc}/{utcend}.mp4")))
    }

    @Test fun `flussonic hls`() {
        assertEquals("http://h/ch/index-1714557600-3600.m3u8", CatchupUrlBuilder.build(req(type = CatchupType.FLUSSONIC)))
    }

    @Test fun `flussonic hls keeps query`() {
        assertEquals(
            "http://h/ch/video-1714557600-3600.m3u8?token=t",
            CatchupUrlBuilder.build(req(url = "http://h/ch/video.m3u8?token=t", type = CatchupType.FLUSSONIC)),
        )
    }

    @Test fun `flussonic mpegts`() {
        assertEquals("http://h/ch/timeshift_abs-1714557600.ts", CatchupUrlBuilder.build(req(url = "http://h/ch/mpegts", type = CatchupType.FLUSSONIC)))
    }

    @Test fun `flussonic rejects unknown file`() {
        assertThrows<CatchupException> { CatchupUrlBuilder.build(req(url = "http://h/ch/stream.flv", type = CatchupType.FLUSSONIC)) }
    }

    @Test fun `xtream from explicit credentials`() {
        val x = XtreamCatchup("http://srv:8080/", "u", "p", "42")
        assertEquals("http://srv:8080/timeshift/u/p/60/2024-05-01:10-00/42.ts", CatchupUrlBuilder.build(req(type = CatchupType.XC, xc = x)))
    }

    @Test fun `xtream parsed from live url`() {
        assertEquals(
            "http://srv:8080/timeshift/u/p/60/2024-05-01:10-00/42.ts",
            CatchupUrlBuilder.build(req(url = "http://srv:8080/live/u/p/42.ts", type = CatchupType.XC)),
        )
    }

    @Test fun `xtream rounds partial minutes up`() {
        val e = start.plusSeconds(61)
        assertEquals("http://s/timeshift/u/p/2/2024-05-01:10-00/1.ts", CatchupUrlBuilder.build(req(url = "http://s/u/p/1", type = CatchupType.XC, e = e)))
    }

    @Test fun `xtream uses server zone`() {
        val url = CatchupUrlBuilder.build(req(url = "http://s/live/u/p/1.ts", type = CatchupType.XC, xcZone = ZoneId.of("Europe/Berlin")))
        assertEquals("http://s/timeshift/u/p/60/2024-05-01:12-00/1.ts", url)
    }

    @Test fun `xtream without credentials fails`() {
        assertThrows<CatchupException> { CatchupUrlBuilder.build(req(url = "http://s/index.m3u8", type = CatchupType.XC)) }
    }

    @Test fun `divided duration and offset`() {
        assertEquals("d=60&o=120", CatchupUrlBuilder.expand("d={duration:60}&o=\${offset:60}", req()))
    }

    @Test fun `offset and timestamp placeholders`() {
        assertEquals("7200/1714564800/1714564800", CatchupUrlBuilder.expand("\${offset}/\${timestamp}/{lutc}", req()))
    }

    @Test fun `java date pattern in local zone`() {
        assertEquals("20240501180000", CatchupUrlBuilder.expand("\${start:yyyyMMddHHmmss}", req(zone = ZoneId.of("Asia/Shanghai"))))
    }

    @Test fun `strftime letters`() {
        assertEquals("2024-05-01 10:00:00", CatchupUrlBuilder.expand("{utc:Y-m-d H:M:S}", req()))
    }

    @Test fun `component placeholders cross midnight in local zone`() {
        val s = Instant.parse("2024-05-01T23:30:00Z")
        val out = CatchupUrlBuilder.expand("{Y}/{m}/{d}/{H}{M}", req(s = s, e = s.plusSeconds(1800), zone = ZoneId.of("Asia/Shanghai")))
        assertEquals("2024/05/02/0730", out)
    }

    @Test fun `dst spring forward london`() {
        val zone = ZoneId.of("Europe/London")
        val before = Instant.parse("2024-03-31T00:30:00Z")
        val after = Instant.parse("2024-03-31T01:30:00Z")
        assertEquals("00:30", CatchupUrlBuilder.expand("{H}:{M}", req(s = before, e = before, zone = zone)))
        assertEquals("02:30", CatchupUrlBuilder.expand("{H}:{M}", req(s = after, e = after, zone = zone)))
    }

    @Test fun `dst does not change unix placeholders`() {
        val zone = ZoneId.of("America/New_York")
        val s = Instant.parse("2024-11-03T05:30:00Z") // 01:30 EDT, ambiguous local hour
        val e = Instant.parse("2024-11-03T06:30:00Z") // 01:30 EST
        assertEquals("3600", CatchupUrlBuilder.expand("{duration}", req(s = s, e = e, zone = zone)))
        assertEquals("01-01", CatchupUrlBuilder.expand("\${start:HH}-\${end:HH}", req(s = s, e = e, zone = zone)))
    }

    @Test fun `end before start rejected`() {
        assertThrows<IllegalArgumentException> { CatchupUrlBuilder.build(req(s = end, e = start)) }
    }

    @ParameterizedTest
    @CsvSource("default,DEFAULT", "append,APPEND", "shift,SHIFT", "timeshift,SHIFT", "flussonic,FLUSSONIC", "fs,FLUSSONIC", "xc,XC", "vod,VOD", "weird,DEFAULT")
    fun `type parsing`(raw: String, expected: CatchupType) {
        assertEquals(expected, CatchupType.from(raw))
    }

    @Test fun `blank type is null`() { assertNull(CatchupType.from(" ")) }

    @Test fun `negative epg offset shifts real time earlier`() {
        val source = Instant.parse("2024-05-01T12:00:00Z").toEpochMilli()
        val real = EpgOffset.toReal(source, -90)
        assertEquals(Instant.parse("2024-05-01T10:30:00Z").toEpochMilli(), real)
        assertEquals(source, EpgOffset.toSource(real, -90))
        val url = CatchupUrlBuilder.build(req(type = CatchupType.SHIFT, s = Instant.ofEpochMilli(real), e = Instant.ofEpochMilli(real + 3_600_000)))
        assertEquals("http://h/ch/index.m3u8?utc=1714559400&lutc=1714564800", url)
    }

    @Test fun `positive half hour offset across midnight`() {
        val source = Instant.parse("2024-05-01T23:45:00Z").toEpochMilli()
        val real = Instant.ofEpochMilli(EpgOffset.toReal(source, 30))
        assertEquals("2024-05-02 00:15", CatchupUrlBuilder.expand("{utc:Y-m-d H:M}", req(s = real, e = real)))
    }

    @Test fun `append query helper`() {
        assertEquals("u?a=1", CatchupUrlBuilder.appendQuery("u", "?a=1"))
        assertEquals("u?x=0&a=1", CatchupUrlBuilder.appendQuery("u?x=0", "&a=1"))
        assertEquals("u", CatchupUrlBuilder.appendQuery("u", "?"))
    }
}
