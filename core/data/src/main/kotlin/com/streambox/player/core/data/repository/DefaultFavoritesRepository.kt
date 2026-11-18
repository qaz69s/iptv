package com.streambox.player.core.data.repository

import com.streambox.player.core.database.dao.ChannelKeyRow
import com.streambox.player.core.database.dao.FavoriteDao
import com.streambox.player.core.database.entity.FavoriteEntity
import com.streambox.player.core.domain.repository.FavoritesRepository
import com.streambox.player.core.model.Channel
import com.streambox.player.core.model.ChannelKey
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

internal class DefaultFavoritesRepository @Inject constructor(
    private val favoriteDao: FavoriteDao,
    private val clock: Clock,
) : FavoritesRepository {
    override val favorites: Flow<List<Channel>> =
        favoriteDao.observeFavoriteChannels().map { rows -> rows.map { it.toModel() } }

    override val favoriteKeys: Flow<Set<ChannelKey>> = favoriteDao.observeKeys()
        .map { rows -> rows.mapTo(mutableSetOf()) { ChannelKey(it.playlistId, it.uniqueKey) } }
        .distinctUntilChanged()

    override fun isFavorite(key: ChannelKey): Flow<Boolean> =
        favoriteDao.observeIsFavorite(key.playlistId, key.uniqueKey).distinctUntilChanged()

    override suspend fun add(key: ChannelKey) =
        favoriteDao.insert(FavoriteEntity(key.playlistId, key.uniqueKey, addedAt = clock.millis()))

    override suspend fun remove(key: ChannelKey) {
        favoriteDao.delete(key.playlistId, key.uniqueKey)
    }

    override suspend fun reorder(keys: List<ChannelKey>) =
        favoriteDao.reorder(keys.distinct().map { ChannelKeyRow(it.playlistId, it.uniqueKey) })

    override suspend fun clearCustomOrder() = favoriteDao.clearCustomOrder()
}
