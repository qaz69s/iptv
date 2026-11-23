package com.streamdeck.player.core.data

import com.streamdeck.player.core.datastore.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Converts between three clocks (spec §4.5 重点):
 * - real UTC now ([System.currentTimeMillis]),
 * - “source time” as written in the EPG (real time − user offset),
 * - display time (UTC rendered in the device time zone by the UI).
 * Programme rows store source time; everything user-facing is shifted by [offsetMs].
 */
@Singleton
class EpgClock @Inject constructor(settings: SettingsRepository) {
    @Volatile var offsetMs: Long = 0
        private set

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            settings.settings.map { it.epgOffsetMinutes }.collect { offsetMs = it * 60_000L }
        }
    }

    fun now(): Long = System.currentTimeMillis()

    /** Real time → EPG source time, for queries against stored programme times. */
    fun sourceNow(): Long = now() - offsetMs
    fun toSource(realMs: Long): Long = realMs - offsetMs

    /** EPG source time → real time, for display and catch-up URLs. */
    fun toReal(sourceMs: Long): Long = sourceMs + offsetMs
}
