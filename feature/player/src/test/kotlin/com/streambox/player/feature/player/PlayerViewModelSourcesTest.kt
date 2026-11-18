package com.streambox.player.feature.player

import androidx.lifecycle.SavedStateHandle
import com.streambox.player.core.domain.usecase.RecordPlaybackStartUseCase
import com.streambox.player.core.domain.usecase.ToggleFavoriteUseCase
import com.streambox.player.core.model.AppSettings
import com.streambox.player.core.model.BrowseSettings
import com.streambox.player.core.model.ChannelSortOrder
import com.streambox.player.core.model.ChannelSource
import com.streambox.player.core.model.PlaybackSettings
import com.streambox.player.core.model.ZapDirection
import com.streambox.player.core.player.sleep.MonotonicClock
import com.streambox.player.core.testing.FakeChannelRepository
import com.streambox.player.core.testing.FakeChannelRepository.Companion.channel
import com.streambox.player.core.testing.FakeFavoritesRepository
import com.streambox.player.core.testing.FakeLastChannelRepository
import com.streambox.player.core.testing.FakeSettingsRepository
import com.streambox.player.core.testing.FakeWatchHistoryRepository
import com.streambox.player.core.testing.MainDispatcherExtension
import com.streambox.player.feature.player.navigation.PlayerSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * P3b: zapping, channel numbers and auto-play next follow the list the player was opened from (all /
 * group / favorites / recently watched) in the browser's sort order; the route's stable key survives
 * a refresh (K20); the panel's favorite toggle.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModelSourcesTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    // Display numbers = ids (position) unless set.
    private val all = listOf(
        "News" to channel(1, "delta"),
        "News" to channel(2, "alpha"),
        "Sports" to channel(3, "charlie"),
        "News" to channel(4, "bravo"),
    )
    private val repository = FakeChannelRepository(all)
    private val controller = FakePlaybackController()
    private val channels = MutableStateFlow(all.map { it.second })
    private val favorites = FakeFavoritesRepository(channels)
    private val history = FakeWatchHistoryRepository(channels)
    private var settings = FakeSettingsRepository()

    private fun sortBy(order: ChannelSortOrder, autoNext: Boolean = false) {
        settings = FakeSettingsRepository(
            AppSettings(
                browse = BrowseSettings(sortOrder = order),
                playback = PlaybackSettings(autoPlayNext = autoNext),
            ),
        )
    }

    private fun TestScope.started(
        channelId: Long,
        source: String?,
        group: String? = null,
        playlistId: Long? = null,
        uniqueKey: String? = null,
    ): PlayerViewModel {
        val viewModel = PlayerViewModel(
            SavedStateHandle(
                mapOf(
                    "channelId" to channelId,
                    "groupName" to group,
                    "source" to source,
                    "playlistId" to playlistId,
                    "uniqueKey" to uniqueKey,
                ),
            ),
            repository,
            controller,
            settings,
            RecordPlaybackStartUseCase(FakeLastChannelRepository(), history),
            favorites,
            ToggleFavoriteUseCase(favorites),
            history,
            MonotonicClock { mainDispatcher.testDispatcher.scheduler.currentTime },
        )
        backgroundScope.launch(mainDispatcher.testDispatcher) { viewModel.uiState.collect {} }
        viewModel.onStart()
        return viewModel
    }

    private fun PlayerViewModel.channelId() = uiState.value.channel?.channelId

    @Test
    fun `all channels zap in the browser's sort order`() = runTest {
        sortBy(ChannelSortOrder.Name) // alpha 2, bravo 4, charlie 3, delta 1
        val viewModel = started(channelId = 4, source = PlayerSource.ALL)

        viewModel.zap(ZapDirection.NEXT)
        assertEquals(3L, viewModel.channelId())
        viewModel.zap(ZapDirection.NEXT)
        viewModel.zap(ZapDirection.NEXT)
        assertEquals(2L, viewModel.channelId())
    }

    @Test
    fun `a group zaps inside the group in sort order, wrapping`() = runTest {
        sortBy(ChannelSortOrder.Name) // News: alpha 2, bravo 4, delta 1
        val viewModel = started(channelId = 1, source = PlayerSource.GROUP, group = "News")

        viewModel.zap(ZapDirection.NEXT)
        assertEquals(2L, viewModel.channelId())
        viewModel.zap(ZapDirection.PREVIOUS)
        viewModel.zap(ZapDirection.PREVIOUS)
        assertEquals(4L, viewModel.channelId())
    }

    @Test
    fun `favorites zap in display order and wrap around (user decision)`() = runTest {
        repository.favoriteIds.value = listOf(3L, 1L)
        val viewModel = started(channelId = 1, source = PlayerSource.FAVORITES)

        viewModel.zap(ZapDirection.NEXT)
        assertEquals(3L, viewModel.channelId())
        viewModel.zap(ZapDirection.PREVIOUS)
        assertEquals(1L, viewModel.channelId())
        viewModel.zap(ZapDirection.PREVIOUS)
        assertEquals(3L, viewModel.channelId())
    }

    @Test
    fun `a custom group zaps in its own order and wraps (P3c, like favorites)`() = runTest {
        repository.customGroupIds.value = mapOf(7L to listOf(4L, 2L, 3L))
        val viewModel = started(channelId = 3, source = PlayerSource.CUSTOM, group = "7")

        viewModel.zap(ZapDirection.NEXT)
        assertEquals(4L, viewModel.channelId())
        viewModel.zap(ZapDirection.NEXT)
        assertEquals(2L, viewModel.channelId())
    }

    @Test
    fun `custom group routes round-trip, and a broken id falls back to all channels`() {
        val source = ChannelSource.Custom(12)
        val (name, group) = PlayerSource.encode(source)
        assertEquals(source, PlayerSource.decode(name, group))
        assertEquals(ChannelSource.All, PlayerSource.decode(PlayerSource.CUSTOM, "x"))
    }

    @Test
    fun `media notification next and pip next zap inside favorites too`() = runTest {
        repository.favoriteIds.value = listOf(3L, 1L)
        val viewModel = started(channelId = 3, source = PlayerSource.FAVORITES)

        controller.channelNavigator?.zap(ZapDirection.NEXT)
        assertEquals(1L, viewModel.channelId())
        viewModel.onPipAction(PipAction.NEXT)
        assertEquals(3L, viewModel.channelId())
    }

    @Test
    fun `a number inside favorites is the 1-based position in the list (user decision)`() = runTest {
        repository.favoriteIds.value = listOf(3L, 4L, 1L)
        val viewModel = started(channelId = 3, source = PlayerSource.FAVORITES)

        viewModel.onDigit(2)
        advanceTimeBy(ChannelNumberInput.DEFAULT_TIMEOUT_MS + 1)
        assertEquals(4L, viewModel.channelId())

        viewModel.onDigit(4)
        advanceTimeBy(ChannelNumberInput.DEFAULT_TIMEOUT_MS + 1)
        assertEquals(4, viewModel.uiState.value.numberNotFound)
    }

    @Test
    fun `auto play next inside favorites follows the list and stops at its end`() = runTest {
        sortBy(ChannelSortOrder.Default, autoNext = true)
        repository.favoriteIds.value = listOf(4L, 2L)
        val viewModel = started(channelId = 4, source = PlayerSource.FAVORITES)

        controller.end()
        assertEquals(2L, viewModel.channelId())
        controller.end()
        assertEquals(2L, viewModel.channelId())
        assertEquals(listOf(4L, 2L), controller.played.map { it.channelId })
    }

    @Test
    fun `recently watched zaps through the order shown when the player opened`() = runTest {
        // Newest first: 2, 3, 1. Each channel that plays moves to the top of the history.
        listOf(1L, 3L, 2L).forEach { id -> history.record(all.first { it.second.id == id }.second.key) }
        val viewModel = started(channelId = 2, source = PlayerSource.RECENT)

        viewModel.zap(ZapDirection.NEXT)
        controller.startPlaying() // 3 is recorded and moves to the top
        viewModel.zap(ZapDirection.NEXT)
        controller.startPlaying()

        assertEquals(1L, viewModel.channelId())
        viewModel.zap(ZapDirection.NEXT)
        assertEquals(2L, viewModel.channelId())
    }

    @Test
    fun `a number inside recently watched is the position in that list`() = runTest {
        listOf(1L, 3L, 2L).forEach { id -> history.record(all.first { it.second.id == id }.second.key) }
        val viewModel = started(channelId = 2, source = PlayerSource.RECENT)

        viewModel.onDigit(3)
        advanceTimeBy(ChannelNumberInput.DEFAULT_TIMEOUT_MS + 1)

        assertEquals(1L, viewModel.channelId())
    }

    @Test
    fun `the route key finds the channel again after a refresh changed its id (K20)`() = runTest {
        // The route still carries id 99 from before the refresh; the key now belongs to channel 3.
        val key = all[2].second.key
        val viewModel =
            started(channelId = 99, source = PlayerSource.ALL, playlistId = key.playlistId, uniqueKey = key.uniqueKey)

        assertEquals(3L, viewModel.channelId())
        assertEquals(listOf(3L), controller.played.map { it.channelId })
    }

    @Test
    fun `a route id that still matches its key is used as is`() = runTest {
        val key = all[0].second.key
        val viewModel =
            started(channelId = 1, source = PlayerSource.ALL, playlistId = key.playlistId, uniqueKey = key.uniqueKey)

        assertEquals(1L, viewModel.channelId())
    }

    @Test
    fun `the panel toggles the current channel as favorite`() = runTest {
        val viewModel = started(channelId = 2, source = PlayerSource.ALL)
        assertFalse(viewModel.uiState.value.isFavorite)

        viewModel.toggleFavorite()
        assertTrue(viewModel.uiState.value.isFavorite)

        viewModel.toggleFavorite()
        assertFalse(viewModel.uiState.value.isFavorite)
    }
}
