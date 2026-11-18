package com.streambox.player.core.player.audio

import androidx.media3.exoplayer.DefaultRenderersFactory
import com.streambox.player.core.player.DecoderMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** K26 "video but no audio": unsupported-audio detection, FFmpeg renderer placement, selector settings. */
class AudioSupportTest {
    private val ac3 = MediaFormatInfo("audio/ac3", null, 6, 48_000)
    private val mp2 = MediaFormatInfo("audio/mpeg-L2", null, 2, 48_000)
    private val aac = MediaFormatInfo("audio/mp4a-latm", "mp4a.40.2", 2, 48_000)

    @Test
    fun `a stream without audio tracks is not reported as unsupported`() {
        assertEquals(AudioStatus.NoAudio, AudioSupport.status(emptyList()))
    }

    @Test
    fun `audio tracks that no renderer can decode are reported with their formats`() {
        val status = AudioSupport.status(
            listOf(
                AudioTrackFormat(ac3, playable = false, selected = false),
                AudioTrackFormat(mp2, playable = false, selected = false),
                AudioTrackFormat(ac3, playable = false, selected = false),
            ),
        )

        assertEquals(AudioStatus.Unsupported(listOf(ac3, mp2)), status)
    }

    @Test
    fun `one playable track is enough`() {
        val status = AudioSupport.status(
            listOf(
                AudioTrackFormat(ac3, playable = false, selected = false),
                AudioTrackFormat(aac, playable = true, selected = true),
            ),
        )

        assertEquals(AudioStatus.Playable, status)
    }

    @Test
    fun `a selected unplayable track falls back when another one is playable`() {
        assertTrue(
            AudioSupport.needsFallback(
                listOf(
                    AudioTrackFormat(ac3, playable = false, selected = true),
                    AudioTrackFormat(aac, playable = true, selected = false),
                ),
            ),
        )
        assertFalse(
            AudioSupport.needsFallback(listOf(AudioTrackFormat(ac3, playable = false, selected = true))),
        )
        assertFalse(
            AudioSupport.needsFallback(
                listOf(
                    AudioTrackFormat(ac3, playable = false, selected = false),
                    AudioTrackFormat(aac, playable = true, selected = true),
                ),
            ),
        )
    }

    @Test
    fun `formats are described for logcat and the stream info`() {
        assertEquals("audio/ac3, 6 ch, 48000 Hz", ac3.describe())
        assertEquals("audio/mp4a-latm (mp4a.40.2), 2 ch, 48000 Hz", aac.describe())
        val video = MediaFormatInfo("video/avc", "avc1.64001f", width = 1920, height = 1080)
        assertEquals("video/avc (avc1.64001f), 1920×1080", video.describe())
        assertEquals("unknown", MediaFormatInfo().describe())
    }

    @Test
    fun `the FFmpeg extension is on behind MediaCodec by default and preferred for software decoding`() {
        assertEquals(
            DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON,
            AudioRendererPolicy.extensionMode(DecoderMode.HARDWARE_FIRST),
        )
        assertEquals(
            DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER,
            AudioRendererPolicy.extensionMode(DecoderMode.SOFTWARE_FIRST),
        )
    }

    @Test
    fun `the FFmpeg audio renderer goes after MediaCodec when on and before it when preferred`() {
        assertEquals(3, AudioRendererPolicy.ffmpegIndex(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON, 2))
        assertEquals(2, AudioRendererPolicy.ffmpegIndex(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER, 2))
        assertNull(AudioRendererPolicy.ffmpegIndex(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF, 2))
    }

    @Test
    fun `the track selector rather picks an over-capability audio track than none, without tunneling or offload`() {
        val policy = AudioSelectionPolicy.DEFAULT

        assertTrue(policy.exceedRendererCapabilitiesIfNecessary)
        assertTrue(policy.exceedAudioConstraintsIfNecessary)
        assertTrue(policy.allowMixedMimeTypeAdaptiveness)
        assertFalse(policy.tunneling)
        assertFalse(policy.audioOffload)
    }
}
