package com.streamdeck.player.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Row of the grid EPG: a channel and (if matched) its EPG channel id. */
data class GridChannel(
    val id: Long,
    val playlistId: Long,
    val uniqueKey: String,
    val name: String,
    val logoUrl: String?,
    val number: Int?,
    val catchupType: String?,
    val catchupDays: Int?,
    val epgChannelId: String?,
)

@Dao
interface EpgDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertChannels(list: List<EpgChannelEntity>)

    @Insert suspend fun insertPrograms(list: List<ProgramEntity>)

    @Query("DELETE FROM program WHERE playlistId = :pid AND pending = 1") suspend fun dropPendingPrograms(pid: Long)

    @Query("DELETE FROM epg_channel WHERE playlistId = :pid AND pending = 1") suspend fun dropPendingChannels(pid: Long)

    @Query("DELETE FROM program WHERE playlistId = :pid AND pending = 0") suspend fun deleteLivePrograms(pid: Long)

    @Query("DELETE FROM epg_channel WHERE playlistId = :pid AND pending = 0") suspend fun deleteLiveChannels(pid: Long)

    @Query("UPDATE program SET pending = 0 WHERE playlistId = :pid") suspend fun promotePrograms(pid: Long)

    @Query("UPDATE epg_channel SET pending = 0 WHERE playlistId = :pid") suspend fun promoteChannels(pid: Long)

    @Query("DELETE FROM program WHERE `end` < :before") suspend fun deleteOlderThan(before: Long)

    @Transaction
    suspend fun abortImport(pid: Long) { dropPendingPrograms(pid); dropPendingChannels(pid) }

    @Transaction
    suspend fun commitImport(pid: Long) {
        deleteLivePrograms(pid)
        deleteLiveChannels(pid)
        promotePrograms(pid)
        promoteChannels(pid)
    }

    @Query("SELECT * FROM epg_channel WHERE playlistId = :pid AND pending = 0") suspend fun channels(pid: Long): List<EpgChannelEntity>

    @Query("SELECT * FROM epg_channel WHERE playlistId = :pid AND pending = 0 AND displayName LIKE '%' || :q || '%' ORDER BY displayName LIMIT 100")
    suspend fun searchChannels(pid: Long, q: String): List<EpgChannelEntity>

    @Query("DELETE FROM epg_mapping WHERE playlistId = :pid AND manual = 0") suspend fun clearAutoMappings(pid: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertMappings(list: List<EpgMappingEntity>)

    @Upsert suspend fun upsertMapping(m: EpgMappingEntity)

    @Query("SELECT * FROM epg_mapping WHERE playlistId = :pid AND uniqueKey = :key") suspend fun mapping(pid: Long, key: String): EpgMappingEntity?

    @Transaction
    suspend fun replaceAutoMappings(pid: Long, list: List<EpgMappingEntity>) {
        clearAutoMappings(pid)
        list.chunked(500).forEach { insertMappings(it) }
    }

    @Query(
        "SELECT p.* FROM program p JOIN epg_mapping m ON m.playlistId = p.playlistId AND m.epgChannelId = p.epgChannelId " +
            "WHERE m.playlistId = :pid AND m.uniqueKey = :key AND p.pending = 0 AND p.`end` > :from AND p.start < :to ORDER BY p.start",
    )
    fun programsFor(pid: Long, key: String, from: Long, to: Long): Flow<List<ProgramEntity>>

    @Query(
        "SELECT p.* FROM program p JOIN epg_mapping m ON m.playlistId = p.playlistId AND m.epgChannelId = p.epgChannelId " +
            "WHERE m.playlistId = :pid AND m.uniqueKey = :key AND p.pending = 0 AND p.`end` > :from AND p.start < :to ORDER BY p.start",
    )
    suspend fun programsOnce(pid: Long, key: String, from: Long, to: Long): List<ProgramEntity>

    @Query(
        "SELECT c.id, c.playlistId, c.uniqueKey, c.name, c.logoUrl, c.number, c.catchupType, c.catchupDays, m.epgChannelId " +
            "FROM channel c JOIN channel_group g ON g.id = c.groupId " +
            "LEFT JOIN epg_mapping m ON m.playlistId = c.playlistId AND m.uniqueKey = c.uniqueKey " +
            "WHERE c.playlistId = :pid AND c.pending = 0 AND c.hidden = 0 AND g.hidden = 0 AND (:groupId IS NULL OR c.groupId = :groupId) " +
            "ORDER BY c.sortOrder",
    )
    fun gridChannels(pid: Long, groupId: Long?): Flow<List<GridChannel>>

    @Query("SELECT * FROM program WHERE id = :id") suspend fun program(id: Long): ProgramEntity?

    @Insert suspend fun insertReminder(r: ReminderEntity): Long

    @Query("DELETE FROM reminder WHERE id = :id") suspend fun deleteReminder(id: Long)

    @Query("SELECT * FROM reminder WHERE id = :id") suspend fun reminder(id: Long): ReminderEntity?

    @Query("SELECT * FROM reminder WHERE startAt > :now ORDER BY startAt") fun upcomingReminders(now: Long): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminder WHERE playlistId = :pid AND uniqueKey = :key AND startAt = :start LIMIT 1")
    suspend fun findReminder(pid: Long, key: String, start: Long): ReminderEntity?

    @Query("DELETE FROM reminder WHERE startAt < :before") suspend fun deletePastReminders(before: Long)
}
