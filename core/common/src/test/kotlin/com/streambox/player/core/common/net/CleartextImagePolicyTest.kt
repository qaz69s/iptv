package com.streambox.player.core.common.net

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CleartextImagePolicyTest {
    @Test
    fun `an http logo is not requested when its playlist does not allow cleartext (K1)`() {
        assertFalse(CleartextImagePolicy.isAllowed("http://logos.example/a.png", allowCleartext = false))
        assertFalse(CleartextImagePolicy.isAllowed(" HTTP://logos.example/a.png", allowCleartext = false))
    }

    @Test
    fun `an http logo is requested when its playlist allows cleartext`() {
        assertTrue(CleartextImagePolicy.isAllowed("http://logos.example/a.png", allowCleartext = true))
    }

    @Test
    fun `https logos are always requested and blank ones never`() {
        assertTrue(CleartextImagePolicy.isAllowed("https://logos.example/a.png", allowCleartext = false))
        assertFalse(CleartextImagePolicy.isAllowed(null, allowCleartext = true))
        assertFalse(CleartextImagePolicy.isAllowed("  ", allowCleartext = true))
    }

    @Test
    fun `header values`() {
        assertEquals("1", CleartextImagePolicy.headerValue(true))
        assertEquals("0", CleartextImagePolicy.headerValue(false))
    }
}
