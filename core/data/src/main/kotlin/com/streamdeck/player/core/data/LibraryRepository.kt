package com.streamdeck.player.core.data

import com.streamdeck.player.core.database.ChannelEntity
import com.streamdeck.player.core.database.FavoriteEntity
import com.streamdeck.player.core.database.HistoryEntity
import com.streamdeck.player.core.database.LibraryDao
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/** Favorites and watch history (spec §4.7). Both are keyed by playlistId + uniqueKey so they survive refreshes. */
@Singleton
class LibraryRepository @Inject constructor(private val dao: LibraryDao) {
    fun isFavorite(c: ChannelEntity): Flow<Boolean> = dao.isFavorite(c.playlistId, c.uniqueKey)

    suspend fun setFavorite(c: ChannelEntity, favorite: Boolean) {
        if (favorite) {
            dao.addFavorite(FavoriteEntity(c.playlistId, c.uniqueKey, System.currentTimeMillis()))
        } else {
            dao.removeFavorite(c.playlistId, c.uniqueKey)
        }
    }

    suspend fun recordWatch(c: ChannelEntity, positionMs: Long = 0) {
        dao.recordHistory(HistoryEntity(c.playlistId, c.uniqueKey, System.currentTimeMillis(), positionMs), HISTORY_LIMIT)
    }

    fun historyCount(): Flow<Int> = dao.historyCount()
    suspend fun clearHistory() = dao.clearHistory()

    companion object { const val HISTORY_LIMIT = 50 }
}
