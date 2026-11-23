package com.streamdeck.player.core.database

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface VodDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertCategories(list: List<VodCategoryEntity>)
    @Insert suspend fun insertItems(list: List<VodItemEntity>)
    @Insert suspend fun insertSeries(list: List<SeriesEntity>)

    @Query("DELETE FROM vod_category WHERE playlistId = :pid AND pending = 1") suspend fun dropPendingCategories(pid: Long)
    @Query("DELETE FROM vod_item WHERE playlistId = :pid AND pending = 1") suspend fun dropPendingItems(pid: Long)
    @Query("DELETE FROM series WHERE playlistId = :pid AND pending = 1") suspend fun dropPendingSeries(pid: Long)
    @Query("DELETE FROM vod_category WHERE playlistId = :pid AND pending = 0") suspend fun deleteLiveCategories(pid: Long)
    @Query("DELETE FROM vod_item WHERE playlistId = :pid AND pending = 0") suspend fun deleteLiveItems(pid: Long)
    @Query("DELETE FROM series WHERE playlistId = :pid AND pending = 0") suspend fun deleteLiveSeries(pid: Long)
    @Query("UPDATE vod_category SET pending = 0 WHERE playlistId = :pid") suspend fun promoteCategories(pid: Long)
    @Query("UPDATE vod_item SET pending = 0 WHERE playlistId = :pid") suspend fun promoteItems(pid: Long)
    @Query("UPDATE series SET pending = 0 WHERE playlistId = :pid") suspend fun promoteSeries(pid: Long)

    @Transaction
    suspend fun abortImport(pid: Long) { dropPendingCategories(pid); dropPendingItems(pid); dropPendingSeries(pid) }

    @Transaction
    suspend fun commitImport(pid: Long) {
        deleteLiveCategories(pid)
        deleteLiveItems(pid)
        deleteLiveSeries(pid)
        promoteCategories(pid)
        promoteItems(pid)
        promoteSeries(pid)
    }

    @Transaction
    suspend fun deleteAll(pid: Long) {
        abortImport(pid)
        deleteLiveCategories(pid)
        deleteLiveItems(pid)
        deleteLiveSeries(pid)
        deleteProgress(pid)
    }

    @Query("SELECT * FROM vod_category WHERE playlistId = :pid AND kind = :kind AND pending = 0 ORDER BY sortOrder")
    fun categories(pid: Long, kind: String): Flow<List<VodCategoryEntity>>

    @Query(
        "SELECT * FROM vod_item WHERE playlistId = :pid AND pending = 0 AND (:cat IS NULL OR categoryId = :cat) " +
            "AND (:q = '' OR name LIKE '%' || :q || '%') ORDER BY sortOrder",
    )
    fun pageItems(pid: Long, cat: String?, q: String): PagingSource<Int, VodItemEntity>

    @Query(
        "SELECT * FROM series WHERE playlistId = :pid AND pending = 0 AND (:cat IS NULL OR categoryId = :cat) " +
            "AND (:q = '' OR name LIKE '%' || :q || '%') ORDER BY sortOrder",
    )
    fun pageSeries(pid: Long, cat: String?, q: String): PagingSource<Int, SeriesEntity>

    @Query("SELECT * FROM vod_item WHERE playlistId = :pid AND streamId = :streamId AND pending = 0 LIMIT 1") suspend fun item(pid: Long, streamId: Long): VodItemEntity?
    @Query("SELECT * FROM series WHERE playlistId = :pid AND seriesId = :seriesId AND pending = 0 LIMIT 1") suspend fun seriesById(pid: Long, seriesId: Long): SeriesEntity?

    @Upsert suspend fun upsertProgress(p: VodProgressEntity)
    @Query("SELECT * FROM vod_progress WHERE playlistId = :pid AND `key` = :key") suspend fun progress(pid: Long, key: String): VodProgressEntity?
    @Query("SELECT * FROM vod_progress WHERE playlistId = :pid") fun observeProgress(pid: Long): Flow<List<VodProgressEntity>>
    @Query("DELETE FROM vod_progress WHERE playlistId = :pid") suspend fun deleteProgress(pid: Long)
}
