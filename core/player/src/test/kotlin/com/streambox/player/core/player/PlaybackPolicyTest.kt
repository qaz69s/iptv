package com.streambox.player.core.player

import androidx.media3.common.PlaybackException
import com.streambox.player.core.model.BufferProfile
import com.streambox.player.core.player.reconnect.FailureKind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

/** Failure classification, buffer profiles and decoder fallback: the pure decisions behind the controller. */
class PlaybackPolicyTest {
    @Test
    fun `network timeout and behind-live-window are retryable kinds`() {
        assertEquals(
            FailureKind.NETWORK,
            FailureClassifier.classify(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, null, false),
        )
        assertEquals(
            FailureKind.TIMEOUT,
            FailureClassifier.classify(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT, null, false),
        )
        assertEquals(
            FailureKind.BEHIND_LIVE_WINDOW,
            FailureClassifier.classify(PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW, null, false),
        )
        assertEquals(
            FailureKind.SOURCE_ENDED,
            FailureClassifier.classify(PlaybackException.ERROR_CODE_IO_UNSPECIFIED, null, false),
        )
    }

    @Test
    fun `http status in the cause chain decides over the error code`() {
        val bad = PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS
        assertEquals(FailureKind.HTTP_RETRYABLE, FailureClassifier.classify(bad, 503, false))
        assertEquals(FailureKind.HTTP_RETRYABLE, FailureClassifier.classify(bad, 429, false))
        assertEquals(FailureKind.HTTP_CLIENT_ERROR, FailureClassifier.classify(bad, 403, false))
        assertEquals(FailureKind.HTTP_CLIENT_ERROR, FailureClassifier.classify(bad, null, false))
    }

    @Test
    fun `cleartext format and decoder failures are permanent`() {
        assertEquals(
            FailureKind.CLEARTEXT_BLOCKED,
            FailureClassifier.classify(PlaybackException.ERROR_CODE_IO_UNSPECIFIED, null, cleartextBlocked = true),
        )
        assertEquals(
            FailureKind.CLEARTEXT_BLOCKED,
            FailureClassifier.classify(PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED, null, false),
        )
        assertEquals(
            FailureKind.UNSUPPORTED_FORMAT,
            FailureClassifier.classify(PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED, null, false),
        )
        assertEquals(
            FailureKind.DECODER,
            FailureClassifier.classify(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, null, false),
        )
        assertEquals(FailureKind.OTHER, FailureClassifier.classify(9999, null, false))
    }

    @ParameterizedTest
    @EnumSource(BufferProfile::class)
    fun `every buffer profile is a valid DefaultLoadControl configuration`(profile: BufferProfile) {
        val config = BufferConfig.of(profile)
        assertTrue(config.bufferForPlaybackMs <= config.minBufferMs)
        assertTrue(config.bufferForPlaybackAfterRebufferMs <= config.minBufferMs)
        assertTrue(config.minBufferMs <= config.maxBufferMs)
        assertTrue(config.bufferForPlaybackMs <= config.bufferForPlaybackAfterRebufferMs)
    }

    @Test
    fun `profiles are ordered from low latency to stable`() {
        val low = BufferConfig.of(BufferProfile.LowLatency)
        val balanced = BufferConfig.of(BufferProfile.Balanced)
        val stable = BufferConfig.of(BufferProfile.Stable)
        assertTrue(low.minBufferMs < balanced.minBufferMs && balanced.minBufferMs < stable.minBufferMs)
        assertTrue(low.maxBufferMs < balanced.maxBufferMs && balanced.maxBufferMs < stable.maxBufferMs)
        assertTrue(low.bufferForPlaybackMs < balanced.bufferForPlaybackMs)
        assertTrue(balanced.bufferForPlaybackMs < stable.bufferForPlaybackMs)
        assertEquals(3_000L, low.liveTargetOffsetMs)
        assertNull(balanced.liveTargetOffsetMs, "balanced keeps the manifest's live offset")
        assertEquals(30_000L, stable.liveTargetOffsetMs)
    }

    @Test
    fun `balanced matches the media3 default start thresholds`() {
        val balanced = BufferConfig.of(BufferProfile.Balanced)
        assertEquals(2_500, balanced.bufferForPlaybackMs)
        assertEquals(5_000, balanced.bufferForPlaybackAfterRebufferMs)
    }

    @Test
    fun `decoder failure on hardware falls back to software once`() {
        assertEquals(
            DecoderMode.SOFTWARE_FIRST,
            DecoderFallback.next(FailureKind.DECODER, DecoderMode.HARDWARE_FIRST, alreadyFellBack = false),
        )
        assertNull(DecoderFallback.next(FailureKind.DECODER, DecoderMode.SOFTWARE_FIRST, alreadyFellBack = true))
        assertNull(DecoderFallback.next(FailureKind.DECODER, DecoderMode.HARDWARE_FIRST, alreadyFellBack = true))
    }

    @Test
    fun `software only mode has nothing to fall back to and other errors never switch decoders`() {
        assertEquals(DecoderMode.SOFTWARE_FIRST, DecoderFallback.initialMode(softwareDecodingOnly = true))
        assertEquals(DecoderMode.HARDWARE_FIRST, DecoderFallback.initialMode(softwareDecodingOnly = false))
        assertNull(DecoderFallback.next(FailureKind.DECODER, DecoderMode.SOFTWARE_FIRST, alreadyFellBack = false))
        assertNull(DecoderFallback.next(FailureKind.NETWORK, DecoderMode.HARDWARE_FIRST, alreadyFellBack = false))
    }
}
