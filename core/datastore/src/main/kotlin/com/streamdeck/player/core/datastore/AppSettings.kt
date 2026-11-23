package com.streamdeck.player.core.datastore

import kotlinx.serialization.Serializable

enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }
enum class DecoderMode { HARDWARE_PREFERRED, SOFTWARE_ONLY }
enum class BufferProfile { LOW_LATENCY, BALANCED, STABLE }
enum class AspectMode { FIT, RATIO_16_9, RATIO_4_3, FILL, STRETCH, ORIGINAL }
enum class ChannelViewMode { LIST, GRID, COMPACT }
enum class ChannelSort { DEFAULT, NAME, NUMBER, CUSTOM }

@Serializable
data class SubtitleStyle(
    val scale: Float = 1f,
    val textColor: Int = 0xFFFFFFFF.toInt(),
    val backgroundColor: Int = 0x99000000.toInt(),
)

/** All user-visible settings. Serializable so it can be part of a backup (spec §4.9). */
@Serializable
data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val accentIndex: Int = 0,
    val fontScale: Float = 1f,
    val decoder: DecoderMode = DecoderMode.HARDWARE_PREFERRED,
    val buffer: BufferProfile = BufferProfile.BALANCED,
    val aspect: AspectMode = AspectMode.FIT,
    val autoResume: Boolean = false,
    val backgroundPlayback: Boolean = true,
    val reconnectMax: Int = 5,
    val allowMetered: Boolean = true,
    val autoplayNext: Boolean = false,
    val subtitle: SubtitleStyle = SubtitleStyle(),
    val epgUpdateHours: Int = 24,
    val epgKeepDays: Int = 3,
    val epgOffsetMinutes: Int = 0,
    val channelView: ChannelViewMode = ChannelViewMode.LIST,
    val channelSort: ChannelSort = ChannelSort.DEFAULT,
    val lockSettings: Boolean = false,
    val lockEditor: Boolean = false,
)

data class LastChannel(val playlistId: Long, val uniqueKey: String)

/** Persisted PIN material: PBKDF2 hash + salt, never the PIN itself. */
data class PinRecord(val hash: String, val salt: String, val failedAttempts: Int, val lockoutUntil: Long)
