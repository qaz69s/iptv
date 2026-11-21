package com.streambox.player.core.parser.xmltv

import java.time.LocalDateTime
import java.time.ZoneOffset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class XmltvTimeTest {
    private fun utc(y: Int, mo: Int, d: Int, h: Int = 0, mi: Int = 0, s: Int = 0): Long =
        LocalDateTime.of(y, mo, d, h, mi, s).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun `full timestamp with a positive offset is converted to UTC`() {
        assertEquals(utc(2026, 11, 21, 12, 0), XmltvTime.parse("20261121200000 +0800"))
    }

    @Test
    fun `negative offset moves the instant forward`() {
        assertEquals(utc(2026, 11, 21, 17, 30), XmltvTime.parse("20261121123000 -0500"))
    }

    @Test
    fun `half hour and quarter hour offsets`() {
        assertEquals(utc(2026, 1, 1, 6, 30), XmltvTime.parse("20260101120000 +0530"))
        assertEquals(utc(2026, 1, 1, 6, 15), XmltvTime.parse("20260101120000 +0545"))
        assertEquals(utc(2026, 1, 1, 12, 30), XmltvTime.parse("20260101090000 -0330"))
    }

    @Test
    fun `missing offset means UTC`() {
        assertEquals(utc(2026, 11, 21, 20, 0), XmltvTime.parse("20261121200000"))
    }

    @Test
    fun `missing seconds and minutes count as zero`() {
        assertEquals(utc(2026, 11, 21, 20, 15), XmltvTime.parse("202611212015 +0000"))
        assertEquals(utc(2026, 11, 21, 20), XmltvTime.parse("2026112120"))
        assertEquals(utc(2026, 11, 21), XmltvTime.parse("20261121"))
    }

    @Test
    fun `offset glued to the digits, with a colon, hours only, Z and UTC`() {
        val expected = utc(2026, 11, 21, 12)
        assertEquals(expected, XmltvTime.parse("20261121200000+0800"))
        assertEquals(expected, XmltvTime.parse("20261121200000 +08:00"))
        assertEquals(expected, XmltvTime.parse("20261121200000 +08"))
        assertEquals(utc(2026, 11, 21, 20), XmltvTime.parse("20261121200000 Z"))
        assertEquals(utc(2026, 11, 21, 20), XmltvTime.parse("20261121200000 UTC"))
        assertEquals(utc(2026, 11, 21, 20), XmltvTime.parse("  20261121200000 GMT  "))
    }

    @Test
    fun `positive offset crossing midnight lands on the previous UTC day`() {
        assertEquals(utc(2026, 11, 20, 17, 30), XmltvTime.parse("20261121013000 +0800"))
    }

    @Test
    fun `negative offset crossing midnight lands on the next UTC day`() {
        assertEquals(utc(2027, 1, 1, 3), XmltvTime.parse("20261231220000 -0500"))
    }

    @Test
    fun `the conversion only uses the written offset, never daylight saving rules`() {
        // 2026-03-29 01:30 in London does not exist (clocks jump); XMLTV states the offset explicitly.
        assertEquals(utc(2026, 3, 29, 1, 30), XmltvTime.parse("20260329013000 +0000"))
        assertEquals(utc(2026, 3, 29, 0, 30), XmltvTime.parse("20260329013000 +0100"))
        // Same wall clock, summer vs winter offsets of New York.
        assertEquals(utc(2026, 7, 1, 16), XmltvTime.parse("20260701120000 -0400"))
        assertEquals(utc(2026, 1, 1, 17), XmltvTime.parse("20260101120000 -0500"))
    }

    @Test
    fun `leap day is valid and the 29th of February of a common year is not`() {
        assertEquals(utc(2028, 2, 29, 10), XmltvTime.parse("20280229100000 +0000"))
        assertNull(XmltvTime.parse("20270229100000 +0000"))
        assertEquals(utc(2000, 2, 29), XmltvTime.parse("20000229000000"))
        assertNull(XmltvTime.parse("19000229000000"))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "",
            "garbage",
            "2026112120000", // 13 digits
            "202611212000001", // 15 digits
            "20261321200000 +0000", // month 13
            "20261100200000", // day 0
            "20261121250000", // hour 25
            "20261121206000", // minute 60
            "20261121200000 +2000", // offset beyond 18 h
            "20261121200000 +0860", // offset minutes 60
            "20261121200000 0800", // no sign
            "20261121200000 +08x0",
            "20261121200000 CET",
        ],
    )
    fun `malformed timestamps are rejected`(value: String) {
        assertNull(XmltvTime.parse(value))
    }

    @Test
    fun `civil date arithmetic matches java time across centuries`() {
        val dates = listOf(
            Triple(1970, 1, 1),
            Triple(1969, 12, 31),
            Triple(1900, 3, 1),
            Triple(2000, 3, 1),
            Triple(2026, 11, 21),
            Triple(2099, 12, 31),
            Triple(2400, 2, 29),
        )
        for ((y, m, d) in dates) {
            assertEquals(LocalDateTime.of(y, m, d, 0, 0).toLocalDate().toEpochDay(), XmltvTime.daysFromCivil(y, m, d))
        }
    }

    @Test
    fun `format is the inverse of parse`() {
        val instant = utc(2026, 2, 3, 4, 5, 6)
        assertEquals("20260203040506 +0000", XmltvTime.format(instant))
        assertEquals(instant, XmltvTime.parse(XmltvTime.format(instant)))
        assertEquals("19691231235959 +0000", XmltvTime.format(-1000))
    }
}
