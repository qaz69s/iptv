package com.streambox.player.core.player

import com.streambox.player.core.model.BufferProfile

/**
 * DefaultLoadControl durations plus the live target offset for one [BufferProfile].
 * [liveTargetOffsetMs] null keeps the offset the manifest / Media3 default asks for.
 */
data class BufferConfig(
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val bufferForPlaybackMs: Int,
    val bufferForPlaybackAfterRebufferMs: Int,
    val liveTargetOffsetMs: Long?,
) {
    companion object {
        /** Low latency starts fast and stays close to the live edge; Stable trades delay for fewer stalls. */
        fun of(profile: BufferProfile): BufferConfig = when (profile) {
            BufferProfile.LowLatency -> BufferConfig(
                minBufferMs = 4_000,
                maxBufferMs = 15_000,
                bufferForPlaybackMs = 1_000,
                bufferForPlaybackAfterRebufferMs = 2_000,
                liveTargetOffsetMs = 3_000,
            )

            BufferProfile.Balanced -> BufferConfig(
                minBufferMs = 15_000,
                maxBufferMs = 50_000,
                bufferForPlaybackMs = 2_500,
                bufferForPlaybackAfterRebufferMs = 5_000,
                liveTargetOffsetMs = null,
            )

            BufferProfile.Stable -> BufferConfig(
                minBufferMs = 30_000,
                maxBufferMs = 90_000,
                bufferForPlaybackMs = 5_000,
                bufferForPlaybackAfterRebufferMs = 10_000,
                liveTargetOffsetMs = 30_000,
            )
        }
    }
}
