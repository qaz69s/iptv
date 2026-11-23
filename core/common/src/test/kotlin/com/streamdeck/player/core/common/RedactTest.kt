package com.streamdeck.player.core.common

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RedactTest {
    @Test fun `query secrets removed`() {
        assertEquals("http://h/p?username=***&password=***&x=1", "http://h/p?username=bob&password=secret&x=1".redactSecrets())
        assertEquals("http://h/a.m3u8?token=***", "http://h/a.m3u8?token=abc123".redactSecrets())
    }

    @Test fun `user info removed`() {
        assertEquals("http://***@host/x", "http://bob:pw@host/x".redactSecrets())
    }

    @Test fun `xtream path credentials removed`() {
        assertEquals("http://h:80/live/***/***/123.ts", "http://h:80/live/bob/pw/123.ts".redactSecrets())
    }

    @Test fun `plain url untouched`() {
        assertEquals("https://example.com/list.m3u", "https://example.com/list.m3u".redactSecrets())
    }
}
