package com.streambox.player.core.domain.usecase

import com.streambox.player.core.domain.repository.FavoritesRepository
import com.streambox.player.core.model.ChannelKey
import javax.inject.Inject
import kotlinx.coroutines.flow.first

/** Adds the channel to the favorites, or removes it when it is one already. @return the new state. */
class ToggleFavoriteUseCase @Inject constructor(private val favoritesRepository: FavoritesRepository) {
    suspend operator fun invoke(key: ChannelKey): Boolean {
        val favorite = !favoritesRepository.isFavorite(key).first()
        if (favorite) favoritesRepository.add(key) else favoritesRepository.remove(key)
        return favorite
    }
}
