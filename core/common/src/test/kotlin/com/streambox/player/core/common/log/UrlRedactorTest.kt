package com.streambox.player.core.common.log

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class UrlRedactorTest {
    @ParameterizedTest(name = "{0}")
    @CsvSource(
        delimiter = '|',
        value = [
            "http://example.com/list.m3u | http://example.com/list.m3u",
            "http://user:secret@example.com/a.m3u | http://***@example.com/a.m3u",
            "http://host/get.php?username=bob&password=pw&type=m3u_plus | " +
                "http://host/get.php?username=***&password=***&type=m3u_plus",
            "http://host/s.m3u8?TOKEN=abc123 | http://host/s.m3u8?TOKEN=***",
            "http://host:8080/live/bob/pw/123.ts | http://host:8080/live/***/***/123.ts",
            "http://host/movie/bob/pw/9.mkv | http://host/movie/***/***/9.mkv",
            "http://host/series/bob/pw/77.mp4 | http://host/series/***/***/77.mp4",
            "http://host/timeshift/bob/pw/60/2026-01-01:10-00/1.ts | " +
                "http://host/timeshift/***/***/60/2026-01-01:10-00/1.ts",
            "http://host/p?a=1&key=zz#frag | http://host/p?a=1&key=***#frag",
            "udp://@239.0.0.1:1234 | udp://@239.0.0.1:1234",
        ],
    )
    fun `redacts credentials`(input: String, expected: String) {
        assertEquals(expected, UrlRedactor.redact(input))
    }
}
