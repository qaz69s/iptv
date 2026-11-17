package com.streambox.player.core.player.reconnect

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource

class ReconnectPolicyTest {
    private val noJitter = ReconnectConfig(initialDelayMs = 1_000, maxDelayMs = 30_000, jitterRatio = 0.0)

    @ParameterizedTest
    @CsvSource("1, 1000", "2, 2000", "3, 4000", "4, 8000", "5, 16000", "6, 30000", "12, 30000", "200, 30000")
    fun `delay grows exponentially and is capped`(attempt: Int, expected: Long) {
        assertEquals(expected, ReconnectPolicy(noJitter).delayBefore(attempt))
    }

    @Test
    fun `jitter spreads the delay symmetrically`() {
        val config = noJitter.copy(jitterRatio = 0.2)
        assertEquals(1_600L, ReconnectPolicy(config) { 0.0 }.delayBefore(2)) // 2000 - 20 %
        assertEquals(2_000L, ReconnectPolicy(config) { 0.5 }.delayBefore(2))
        assertEquals(2_200L, ReconnectPolicy(config) { 0.75 }.delayBefore(2)) // +10 %
    }

    @Test
    fun `jitter never exceeds the cap nor goes negative`() {
        val config = noJitter.copy(jitterRatio = 1.0)
        assertEquals(30_000L, ReconnectPolicy(config) { 0.9999 }.delayBefore(10))
        assertEquals(0L, ReconnectPolicy(config) { 0.0 }.delayBefore(1))
    }

    @Test
    fun `random jitter stays inside the band over many draws`() {
        val policy = ReconnectPolicy(noJitter.copy(jitterRatio = 0.25))
        repeat(500) {
            val delay = policy.delayBefore(3)
            assertTrue(delay in 3_000L..5_000L, "delay $delay outside 4000 ± 25 %")
        }
    }

    @Test
    fun `retries only retryable failures and only up to the limit`() {
        val policy = ReconnectPolicy(ReconnectConfig(maxAttempts = 3))
        assertTrue(policy.shouldRetry(FailureKind.NETWORK, 0))
        assertTrue(policy.shouldRetry(FailureKind.NETWORK, 2))
        assertFalse(policy.shouldRetry(FailureKind.NETWORK, 3))
        assertFalse(policy.shouldRetry(FailureKind.HTTP_CLIENT_ERROR, 0))
    }

    @Test
    fun `zero max attempts disables reconnecting`() {
        val policy = ReconnectPolicy(ReconnectConfig(maxAttempts = 0))
        FailureKind.entries.forEach { assertFalse(policy.shouldRetry(it, 0), "$it") }
    }

    @ParameterizedTest
    @EnumSource(
        FailureKind::class,
        names = ["NETWORK", "TIMEOUT", "HTTP_RETRYABLE", "BEHIND_LIVE_WINDOW", "SOURCE_ENDED"],
    )
    fun `transient failures are retryable`(kind: FailureKind) {
        assertTrue(kind.retryable)
    }

    @ParameterizedTest
    @EnumSource(
        FailureKind::class,
        names = ["HTTP_CLIENT_ERROR", "CLEARTEXT_BLOCKED", "UNSUPPORTED_FORMAT", "DECODER", "OTHER"],
    )
    fun `permanent failures are not retryable`(kind: FailureKind) {
        assertFalse(kind.retryable)
    }

    @ParameterizedTest
    @CsvSource(
        "408, HTTP_RETRYABLE",
        "429, HTTP_RETRYABLE",
        "500, HTTP_RETRYABLE",
        "502, HTTP_RETRYABLE",
        "503, HTTP_RETRYABLE",
        "599, HTTP_RETRYABLE",
        "400, HTTP_CLIENT_ERROR",
        "401, HTTP_CLIENT_ERROR",
        "403, HTTP_CLIENT_ERROR",
        "404, HTTP_CLIENT_ERROR",
        "410, HTTP_CLIENT_ERROR",
        "451, HTTP_CLIENT_ERROR",
    )
    fun `http status classification`(status: Int, expected: FailureKind) {
        assertEquals(expected, FailureKind.fromHttpStatus(status))
    }

    @Test
    fun `invalid configs are rejected`() {
        assertThrows<IllegalArgumentException> { ReconnectConfig(maxAttempts = -1) }
        assertThrows<IllegalArgumentException> { ReconnectConfig(initialDelayMs = 5_000, maxDelayMs = 1_000) }
        assertThrows<IllegalArgumentException> { ReconnectConfig(multiplier = 0.5) }
        assertThrows<IllegalArgumentException> { ReconnectConfig(jitterRatio = 1.5) }
        assertThrows<IllegalArgumentException> { ReconnectPolicy().delayBefore(0) }
    }
}
