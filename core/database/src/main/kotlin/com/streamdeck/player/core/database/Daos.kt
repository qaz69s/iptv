package com.streamdeck.player.core.database

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** A channel row plus per-row flags computed in SQL so the list never does N+1 lookups. */
data class ChannelListItem(
    @Embedded val channel: ChannelEntity,
    val isFavorite: Boolean,
    val groupLocked: Boolean,
    /** Title of the programme airing at query time (null without EPG). */
    val nowTitle: String?,
    /** Start / end of the airing programme. Source time in SQL; [com.streamdeck.player.core.data] shifts it to real time. */
    val nowStart: Long? = null,
    val nowEnd: Long? = null,
    /** Next programme after the airing one. */
    val nextTitle: String? = null,
    val nextStart: Long? = null,
)

/** Visible channel count of one group (chips show “新闻 33”). */
data class GroupCount(val groupId: Long, val count: Int)

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlist ORDER BY sortOrder, id")
    fun observeAll(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlist ORDER BY sortOrder, id")
    suspend fun all(): List<PlaylistEntity>

    @Query("SELECT * FROM playlist WHERE id = :id")
    suspend fun get(id: Long): PlaylistEntity?

    @Insert suspend fun insert(p: PlaylistEntity): Long

    @Update suspend fun update(p: PlaylistEntity)

    @Update suspend fun updateAll(list: List<PlaylistEntity>)

    @Query("DELETE FROM playlist WHERE id = :id") suspend fun delete(id: Long)
}

private const val PROGRAM_JOIN =
    "FROM epg_mapping m JOIN program p ON p.playlistId = m.playlistId AND p.epgChannelId = m.epgChannelId " +
        "WHERE m.playlistId = c.playlistId AND m.uniqueKey = c.uniqueKey AND p.pending = 0"

private const val NOW_FROM = "$PROGRAM_JOIN AND p.start <= :now AND p.`end` > :now ORDER BY p.start DESC LIMIT 1"
private const val NEXT_FROM = "$PROGRAM_JOIN AND p.start >= :now ORDER BY p.start LIMIT 1"

private const val LIST_COLUMNS =
    "c.*, EXISTS(SELECT 1 FROM favorite f WHERE f.playlistId = c.playlistId AND f.uniqueKey = c.uniqueKey) AS isFavorite, " +
        "g.locked AS groupLocked, " +
        "(SELECT p.title $NOW_FROM) AS nowTitle, (SELECT p.start $NOW_FROM) AS nowStart, (SELECT p.`end` $NOW_FROM) AS nowEnd, " +
        "(SELECT p.title $NEXT_FROM) AS nextTitle, (SELECT p.start $NEXT_FROM) AS nextStart"

private const val SORT =
    "ORDER BY CASE WHEN :sort = 'NAME' THEN c.name END COLLATE NOCASE, " +
        "CASE WHEN :sort = 'NUMBER' THEN IFNULL(c.number, 2147483647) END, " +
        "CASE WHEN :sort = 'CUSTOM' THEN c.sortOrder END, c.importOrder"

@Dao
interface ChannelDao {
    @Insert suspend fun insertGroup(g: ChannelGroupEntity): Long

    @Insert suspend fun insertChannels(list: List<ChannelEntity>)

    @Query("SELECT * FROM channel_group WHERE playlistId = :pid AND pending = 0 AND hidden = 0 ORDER BY sortOrder")
    fun observeGroups(pid: Long): Flow<List<ChannelGroupEntity>>

    @Query("SELECT * FROM channel_group WHERE playlistId = :pid AND pending = 0 ORDER BY sortOrder")
    fun observeAllGroups(pid: Long): Flow<List<ChannelGroupEntity>>

    @Query("SELECT * FROM channel_group WHERE id = :id") suspend fun group(id: Long): ChannelGroupEntity?

    @Query(
        "SELECT $LIST_COLUMNS FROM channel c JOIN channel_group g ON g.id = c.groupId " +
            "WHERE c.playlistId = :pid AND c.pending = 0 AND c.hidden = 0 AND g.hidden = 0 " +
            "AND (:groupId IS NULL OR c.groupId = :groupId) $SORT",
    )
    fun pageChannels(pid: Long, groupId: Long?, sort: String, now: Long): PagingSource<Int, ChannelListItem>

    @Query(
        "SELECT $LIST_COLUMNS FROM favorite fav JOIN channel c ON c.playlistId = fav.playlistId AND c.uniqueKey = fav.uniqueKey " +
            "JOIN channel_group g ON g.id = c.groupId " +
            "WHERE (:pid IS NULL OR fav.playlistId = :pid) AND c.pending = 0 ORDER BY fav.addedAt DESC",
    )
    fun pageFavorites(pid: Long?, now: Long): PagingSource<Int, ChannelListItem>

    @Query(
        "SELECT $LIST_COLUMNS FROM history h JOIN channel c ON c.playlistId = h.playlistId AND c.uniqueKey = h.uniqueKey " +
            "JOIN channel_group g ON g.id = c.groupId " +
            "WHERE h.playlistId = :pid AND c.pending = 0 ORDER BY h.watchedAt DESC LIMIT 50",
    )
    fun pageRecent(pid: Long, now: Long): PagingSource<Int, ChannelListItem>

    @Query(
        "SELECT $LIST_COLUMNS FROM channel c JOIN channel_group g ON g.id = c.groupId " +
            "WHERE c.playlistId = :pid AND c.pending = 0 AND c.hidden = 0 AND g.hidden = 0 " +
            "AND (c.rowid IN (SELECT rowid FROM channel_fts WHERE channel_fts MATCH :q) OR c.uniqueKey IN " +
            "(SELECT m.uniqueKey FROM epg_mapping m JOIN program p ON p.playlistId = m.playlistId AND p.epgChannelId = m.epgChannelId " +
            "WHERE m.playlistId = :pid AND p.pending = 0 AND p.start <= :now AND p.`end` > :now " +
            "AND p.rowid IN (SELECT rowid FROM program_fts WHERE program_fts MATCH :q))) $SORT",
    )
    fun search(pid: Long, q: String, sort: String, now: Long): PagingSource<Int, ChannelListItem>

    @Query(
        "SELECT c.groupId AS groupId, COUNT(*) AS count FROM channel c JOIN channel_group g ON g.id = c.groupId " +
            "WHERE c.playlistId = :pid AND c.pending = 0 AND c.hidden = 0 AND g.hidden = 0 GROUP BY c.groupId",
    )
    fun observeGroupCounts(pid: Long): Flow<List<GroupCount>>

    @Query(
        "SELECT COUNT(*) FROM favorite fav JOIN channel c ON c.playlistId = fav.playlistId AND c.uniqueKey = fav.uniqueKey " +
            "WHERE fav.playlistId = :pid AND c.pending = 0",
    )
    fun observeFavoriteCount(pid: Long): Flow<Int>

    @Query(
        "SELECT $LIST_COLUMNS FROM history h JOIN channel c ON c.playlistId = h.playlistId AND c.uniqueKey = h.uniqueKey " +
            "JOIN channel_group g ON g.id = c.groupId " +
            "WHERE h.playlistId = :pid AND c.pending = 0 ORDER BY h.watchedAt DESC LIMIT :limit",
    )
    fun observeRecent(pid: Long, now: Long, limit: Int): Flow<List<ChannelListItem>>

    @Query(
        "SELECT $LIST_COLUMNS FROM favorite fav JOIN channel c ON c.playlistId = fav.playlistId AND c.uniqueKey = fav.uniqueKey " +
            "JOIN channel_group g ON g.id = c.groupId " +
            "WHERE fav.playlistId = :pid AND c.pending = 0 ORDER BY fav.addedAt DESC LIMIT :limit",
    )
    fun observeFavorites(pid: Long, now: Long, limit: Int): Flow<List<ChannelListItem>>

    /** Visible channels of one group with now/next — the TV zapping list and the phone “同组频道” tab. */
    @Query(
        "SELECT $LIST_COLUMNS FROM channel c JOIN channel_group g ON g.id = c.groupId " +
            "WHERE c.playlistId = :pid AND c.groupId = :groupId AND c.pending = 0 AND c.hidden = 0 AND g.hidden = 0 " +
            "ORDER BY c.sortOrder LIMIT :limit",
    )
    fun observeGroupItems(pid: Long, groupId: Long, now: Long, limit: Int): Flow<List<ChannelListItem>>

    @Query("SELECT COUNT(*) FROM channel WHERE playlistId = :pid AND pending = 0 AND hidden = 0") fun observeVisibleCount(pid: Long): Flow<Int>

    @Query("SELECT * FROM channel WHERE playlistId = :pid AND groupId = :groupId AND pending = 0 ORDER BY sortOrder")
    fun observeGroupChannels(pid: Long, groupId: Long): Flow<List<ChannelEntity>>

    @Query("SELECT * FROM channel WHERE id = :id") suspend fun get(id: Long): ChannelEntity?

    @Query(
        "SELECT c.* FROM channel c JOIN channel_group g ON g.id = c.groupId WHERE c.playlistId = :pid AND c.pending = 0 " +
            "AND c.hidden = 0 AND g.hidden = 0 AND (:includeLocked = 1 OR (c.locked = 0 AND g.locked = 0)) " +
            "AND c.sortOrder > :order ORDER BY c.sortOrder LIMIT 1",
    )
    suspend fun next(pid: Long, order: Int, includeLocked: Boolean): ChannelEntity?

    @Query(
        "SELECT c.* FROM channel c JOIN channel_group g ON g.id = c.groupId WHERE c.playlistId = :pid AND c.pending = 0 " +
            "AND c.hidden = 0 AND g.hidden = 0 AND (:includeLocked = 1 OR (c.locked = 0 AND g.locked = 0)) " +
            "AND c.sortOrder < :order ORDER BY c.sortOrder DESC LIMIT 1",
    )
    suspend fun previous(pid: Long, order: Int, includeLocked: Boolean): ChannelEntity?

    @Query(
        "SELECT c.* FROM channel c JOIN channel_group g ON g.id = c.groupId WHERE c.playlistId = :pid AND c.pending = 0 AND c.hidden = 0 " +
            "AND (:includeLocked = 1 OR (c.locked = 0 AND g.locked = 0)) AND c.number = :number ORDER BY c.sortOrder LIMIT 1",
    )
    suspend fun byNumber(pid: Long, number: Int, includeLocked: Boolean): ChannelEntity?

    @Query("SELECT locked FROM channel_group WHERE id = :groupId") suspend fun isGroupLocked(groupId: Long): Boolean?

    @Query("SELECT * FROM channel WHERE playlistId = :pid AND pending = 0 AND uniqueKey = :key LIMIT 1")
    suspend fun byKey(pid: Long, key: String): ChannelEntity?

    @Query("SELECT * FROM channel WHERE playlistId = :pid AND pending = 0")
    suspend fun allLive(pid: Long): List<ChannelEntity>

    @Query("SELECT COUNT(*) FROM channel WHERE playlistId = :pid AND pending = 0") suspend fun liveCount(pid: Long): Int

    @Query("DELETE FROM channel WHERE playlistId = :pid AND pending = 1") suspend fun dropPendingChannels(pid: Long)

    @Query("DELETE FROM channel_group WHERE playlistId = :pid AND pending = 1") suspend fun dropPendingGroups(pid: Long)

    @Query("DELETE FROM channel WHERE playlistId = :pid AND pending = 0") suspend fun deleteLiveChannels(pid: Long)

    @Query("DELETE FROM channel_group WHERE playlistId = :pid AND pending = 0") suspend fun deleteLiveGroups(pid: Long)

    @Query("UPDATE channel SET pending = 0 WHERE playlistId = :pid") suspend fun promoteChannels(pid: Long)

    @Query("UPDATE channel_group SET pending = 0 WHERE playlistId = :pid") suspend fun promoteGroups(pid: Long)

    @Query(
        "INSERT INTO channel_group (playlistId, name, originalName, hidden, sortOrder, locked, pending) " +
            "SELECT o.playlistId, COALESCE(o.customName, o.name), o.name, COALESCE(o.hidden, 0), COALESCE(o.sortOrder, 100000), " +
            "COALESCE(o.locked, 0), 0 FROM group_override o WHERE o.playlistId = :pid AND o.custom = 1 AND NOT EXISTS " +
            "(SELECT 1 FROM channel_group g WHERE g.playlistId = :pid AND g.pending = 0 AND g.originalName = o.name)",
    )
    suspend fun insertCustomGroups(pid: Long)

    @Query(
        "UPDATE channel_group SET " +
            "name = COALESCE((SELECT o.customName FROM group_override o WHERE o.playlistId = channel_group.playlistId AND o.name = channel_group.originalName), originalName), " +
            "hidden = COALESCE((SELECT o.hidden FROM group_override o WHERE o.playlistId = channel_group.playlistId AND o.name = channel_group.originalName), hidden), " +
            "locked = COALESCE((SELECT o.locked FROM group_override o WHERE o.playlistId = channel_group.playlistId AND o.name = channel_group.originalName), locked), " +
            "sortOrder = COALESCE((SELECT o.sortOrder FROM group_override o WHERE o.playlistId = channel_group.playlistId AND o.name = channel_group.originalName), sortOrder) " +
            "WHERE playlistId = :pid AND pending = 0",
    )
    suspend fun applyGroupOverrides(pid: Long)

    @Query(
        "UPDATE channel SET " +
            "name = COALESCE((SELECT o.customName FROM channel_override o WHERE o.playlistId = channel.playlistId AND o.uniqueKey = channel.uniqueKey), name), " +
            "hidden = COALESCE((SELECT o.hidden FROM channel_override o WHERE o.playlistId = channel.playlistId AND o.uniqueKey = channel.uniqueKey), hidden), " +
            "locked = COALESCE((SELECT o.locked FROM channel_override o WHERE o.playlistId = channel.playlistId AND o.uniqueKey = channel.uniqueKey), locked), " +
            "sortOrder = COALESCE((SELECT o.sortOrder FROM channel_override o WHERE o.playlistId = channel.playlistId AND o.uniqueKey = channel.uniqueKey), sortOrder), " +
            "groupId = COALESCE((SELECT g.id FROM channel_override o JOIN channel_group g ON g.playlistId = o.playlistId " +
            "AND g.originalName = o.customGroup AND g.pending = 0 WHERE o.playlistId = channel.playlistId AND o.uniqueKey = channel.uniqueKey), groupId) " +
            "WHERE playlistId = :pid AND pending = 0",
    )
    suspend fun applyChannelOverrides(pid: Long)

    @Query("UPDATE channel SET groupName = COALESCE((SELECT g.name FROM channel_group g WHERE g.id = channel.groupId), groupName) WHERE playlistId = :pid AND pending = 0")
    suspend fun refreshGroupNames(pid: Long)

    /** Re-applies all user customisations (hidden, renamed, re-ordered, moved) to the live rows. */
    @Transaction
    suspend fun applyOverrides(pid: Long) {
        insertCustomGroups(pid)
        applyGroupOverrides(pid)
        applyChannelOverrides(pid)
        refreshGroupNames(pid)
    }

    /** Atomically replaces the live data with the freshly imported pending rows, then re-applies user edits. */
    @Transaction
    suspend fun commitImport(pid: Long) {
        deleteLiveChannels(pid)
        deleteLiveGroups(pid)
        promoteChannels(pid)
        promoteGroups(pid)
        applyOverrides(pid)
    }

    @Transaction
    suspend fun abortImport(pid: Long) { dropPendingChannels(pid); dropPendingGroups(pid) }

    @Transaction
    suspend fun deletePlaylistData(pid: Long) {
        deleteLiveChannels(pid)
        deleteLiveGroups(pid)
        abortImport(pid)
    }
}

@Dao
interface LibraryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun addFavorite(f: FavoriteEntity)

    @Query("DELETE FROM favorite WHERE playlistId = :pid AND uniqueKey = :key") suspend fun removeFavorite(pid: Long, key: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite WHERE playlistId = :pid AND uniqueKey = :key)") fun isFavorite(pid: Long, key: String): Flow<Boolean>

    @Query("SELECT * FROM favorite") suspend fun allFavorites(): List<FavoriteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertFavorites(list: List<FavoriteEntity>)

    @Upsert suspend fun upsertHistory(h: HistoryEntity)

    @Query("DELETE FROM history WHERE rowid NOT IN (SELECT rowid FROM history ORDER BY watchedAt DESC LIMIT :keep)") suspend fun trimHistory(keep: Int)

    @Query("DELETE FROM history") suspend fun clearHistory()

    @Query("SELECT COUNT(*) FROM history") fun historyCount(): Flow<Int>

    @Transaction
    suspend fun recordHistory(h: HistoryEntity, keep: Int) { upsertHistory(h); trimHistory(keep) }
}

@Dao
interface EditorDao {
    @Query("SELECT * FROM channel_override WHERE playlistId = :pid AND uniqueKey = :key") suspend fun channelOverride(pid: Long, key: String): ChannelOverrideEntity?

    @Upsert suspend fun upsertChannelOverride(o: ChannelOverrideEntity)

    @Query("SELECT * FROM group_override WHERE playlistId = :pid AND name = :name") suspend fun groupOverride(pid: Long, name: String): GroupOverrideEntity?

    @Upsert suspend fun upsertGroupOverride(o: GroupOverrideEntity)

    @Query("SELECT * FROM channel_override") suspend fun allChannelOverrides(): List<ChannelOverrideEntity>

    @Query("SELECT * FROM group_override") suspend fun allGroupOverrides(): List<GroupOverrideEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertChannelOverrides(list: List<ChannelOverrideEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertGroupOverrides(list: List<GroupOverrideEntity>)

    @Query("DELETE FROM channel_override WHERE playlistId = :pid") suspend fun deleteChannelOverrides(pid: Long)

    @Query("DELETE FROM group_override WHERE playlistId = :pid") suspend fun deleteGroupOverrides(pid: Long)

    @Query("DELETE FROM favorite WHERE playlistId = :pid") suspend fun deleteFavorites(pid: Long)

    @Query("DELETE FROM history WHERE playlistId = :pid") suspend fun deleteHistory(pid: Long)
}
