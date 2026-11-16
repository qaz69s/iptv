package com.streambox.player.core.player

import androidx.media3.common.PlaybackException
import com.streambox.player.core.model.PlaybackError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class PlayerLogicTest {
    @ParameterizedTest
    @CsvSource(
        "http://h/live/index.m3u8, HLS",
        "https://h/a/playlist.M3U8?token=1, HLS",
        "http://h/get.php?id=1&type=m3u8, HLS",
        "https://h/manifest.mpd, DASH",
        "rtsp://cam.local:554/stream, RTSP",
        "http://h:8080/live/user/pass/123.ts, PROGRESSIVE",
        "http://h:8080/live/user/pass/123, PROGRESSIVE",
        "https://h/movie.mp4, PROGRESSIVE",
        "udp://@239.0.0.1:1234, PROGRESSIVE",
    )
    fun `stream type is inferred from the url`(url: String, expected: StreamType) {
        assertEquals(expected, StreamTypeResolver.resolve(url))
    }

    @Test
    fun `http status wins over the generic error code`() {
        assertEquals(
            PlaybackError.Http(404),
            PlaybackErrorMapper.map(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, 404, cleartextBlocked = false),
        )
    }

    @Test
    fun `timeouts network and cleartext are distinguished`() {
        assertEquals(
            PlaybackError.Timeout,
            PlaybackErrorMapper.map(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT, null, false),
        )
        assertEquals(
            PlaybackError.Network,
            PlaybackErrorMapper.map(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED, null, false),
        )
        assertEquals(
            PlaybackError.CleartextNotAllowed,
            PlaybackErrorMapper.map(PlaybackException.ERROR_CODE_IO_UNSPECIFIED, null, cleartextBlocked = true),
        )
        assertEquals(
            PlaybackError.CleartextNotAllowed,
            PlaybackErrorMapper.map(PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED, null, false),
        )
    }

    @Test
    fun `decoder and format errors`() {
        assertEquals(
            PlaybackError.Decoder,
            PlaybackErrorMapper.map(PlaybackException.ERROR_CODE_DECODER_INIT_FAILED, null, false),
        )
        assertEquals(
            PlaybackError.Decoder,
            PlaybackErrorMapper.map(PlaybackException.ERROR_CODE_DECODING_FAILED, null, false),
        )
        assertEquals(
            PlaybackError.UnsupportedFormat,
            PlaybackErrorMapper.map(PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED, null, false),
        )
    }

    @Test
    fun `unknown errors keep their code`() {
        assertEquals(PlaybackError.Other(9999, "X"), PlaybackErrorMapper.map(9999, null, false, "X"))
    }
}
