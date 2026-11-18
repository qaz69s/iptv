package com.streambox.player.core.player.audio

import android.content.Context
import android.os.Handler
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import com.streambox.player.core.player.DecoderMode
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.FfmpegAudioRenderer

/**
 * The default renderers plus the FFmpeg audio decoder (K26), placed by [AudioRendererPolicy]: behind
 * MediaCodec normally, in front of it for software-first decoding. Video keeps using MediaCodec only.
 */
@OptIn(UnstableApi::class)
internal class StreamBoxRenderersFactory(context: Context, decoderMode: DecoderMode) :
    DefaultRenderersFactory(context) {
    init {
        setExtensionRendererMode(AudioRendererPolicy.extensionMode(decoderMode))
    }

    override fun buildAudioRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        audioSink: AudioSink,
        eventHandler: Handler,
        eventListener: AudioRendererEventListener,
        out: ArrayList<Renderer>,
    ) {
        val start = out.size
        super.buildAudioRenderers(
            context,
            extensionRendererMode,
            mediaCodecSelector,
            enableDecoderFallback,
            audioSink,
            eventHandler,
            eventListener,
            out,
        )
        val mediaCodecIndex = (start until out.size).firstOrNull { out[it] is MediaCodecAudioRenderer } ?: return
        val index = AudioRendererPolicy.ffmpegIndex(extensionRendererMode, mediaCodecIndex) ?: return
        // Same sink as the MediaCodec renderer: only one audio renderer is enabled at a time.
        out.add(index, FfmpegAudioRenderer(eventHandler, eventListener, audioSink))
    }
}
