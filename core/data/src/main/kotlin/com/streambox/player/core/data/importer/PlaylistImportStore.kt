package com.streambox.player.core.data.importer

import com.streambox.player.core.model.PlaylistType

/** What the importer needs to know about a playlist to fetch it. */
data class PlaylistSource(
    val playlistId: Long,
    val type: PlaylistType,
    val location: String,
    val userAgent: String?,
    val allowCleartext: Boolean,
)

data class NewGroup(val name: String, val sortOrder: Int)

data class NewChannel(
    val groupName: String,
    val uniqueKey: String,
    val name: String,
    val logoUrl: String?,
    val streamUrl: String,
    val tvgId: String?,
    val tvgName: String?,
    val number: Int?,
    val catchupType: String?,
    val catchupSource: String?,
    val catchupDays: Int?,
    val timeshift: Int?,
    val userAgent: String?,
    val referrer: String?,
    val sortOrder: Int,
)

/** Thrown when the written data does not match what was parsed; the import is rolled back. */
class ImportValidationException(message: String) : IllegalStateException(message)

/**
 * Persistence used by the importer, abstracted so the import pipeline is unit-testable on the JVM.
 *
 * Contract (write new -> validate -> atomic swap):
 * 1. [beginImport] removes leftovers of earlier interrupted imports and returns a fresh generation.
 * 2. [writeBatch] writes rows under that generation in one transaction each; they stay invisible.
 * 3. [verifyAndCommit] checks the row count and, in one transaction, switches the playlist to the new generation.
 * 4. [deleteInactive] drops the previous generation afterwards; [discard] drops the new one on failure.
 */
interface PlaylistImportStore {
    suspend fun source(playlistId: Long): PlaylistSource?

    suspend fun beginImport(playlistId: Long): Int

    /** @return ids of the groups in [newGroups], by name. */
    suspend fun writeBatch(
        playlistId: Long,
        generation: Int,
        newGroups: List<NewGroup>,
        channels: List<NewChannel>,
        knownGroupIds: Map<String, Long>,
    ): Map<String, Long>

    /** @return number of groups of the committed generation. */
    suspend fun verifyAndCommit(
        playlistId: Long,
        generation: Int,
        expectedChannels: Int,
        epgUrl: String?,
        updatedAt: Long,
    ): Int

    suspend fun deleteInactive(playlistId: Long)

    suspend fun discard(playlistId: Long, generation: Int)

    suspend fun recordFailure(playlistId: Long, message: String)
}
