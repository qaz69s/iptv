package com.streamdeck.player.core.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FtsQueryTest {
    @Test fun `words become quoted prefix terms`() { assertEquals("\"cc\"* \"tv\"*", FtsQuery.of("  cc   tv ")) }

    @Test fun `quotes are stripped`() { assertEquals("\"ab\"*", FtsQuery.of("a\"b")) }

    @Test fun `blank query`() { assertEquals("", FtsQuery.of("   ")) }
}
