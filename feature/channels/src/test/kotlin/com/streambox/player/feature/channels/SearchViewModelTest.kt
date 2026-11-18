package com.streambox.player.feature.channels

import androidx.lifecycle.SavedStateHandle
import com.streambox.player.core.domain.usecase.ToggleFavoriteUseCase
import com.streambox.player.core.model.ChannelGroupSummary
import com.streambox.player.core.testing.FakeChannelRepository
import com.streambox.player.core.testing.FakeChannelRepository.Companion.channel
import com.streambox.player.core.testing.FakeFavoritesRepository
import com.streambox.player.core.testing.FakePlaylistRepository
import com.streambox.player.core.testing.MainDispatcherExtension
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/** Requirements §4.2: search as you type with a 300 ms debounce. */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val repository = FakeChannelRepository(
        listOf(
            "News" to channel(1, "BBC News"),
            "Sports" to channel(2, "Sky Sports"),
            "Sports" to channel(3, "Eurosport"),
        ),
    )
    private val favorites = FakeFavoritesRepository(MutableStateFlow(emptyList()))

    private fun TestScope.viewModel(): SearchViewModel {
        val viewModel = SearchViewModel(
            SavedStateHandle(),
            repository,
            favorites,
            FakePlaylistRepository(),
            ToggleFavoriteUseCase(favorites),
        )
        backgroundScope.launch(mainDispatcher.testDispatcher) { viewModel.uiState.collect {} }
        return viewModel
    }

    @Test
    fun `typing searches once, 300 ms after the last keystroke`() = runTest {
        val viewModel = viewModel()

        viewModel.onQueryChange("s")
        advanceTimeBy(100)
        viewModel.onQueryChange("sp")
        advanceTimeBy(100)
        viewModel.onQueryChange("spo")
        assertEquals("spo", viewModel.uiState.value.query)
        assertTrue(viewModel.uiState.value.isSearching)

        advanceTimeBy(SearchViewModel.DEBOUNCE_MS - 1)
        runCurrent()
        assertTrue(repository.searches.isEmpty())

        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf("spo"), repository.searches)
        val state = viewModel.uiState.value
        assertFalse(state.isSearching)
        assertEquals(listOf("Sky Sports", "Eurosport"), state.results.channels.map { it.name })
        assertEquals(listOf(ChannelGroupSummary("Sports", 2)), state.results.groups)
    }

    @Test
    fun `clearing the text clears the results without searching`() = runTest {
        val viewModel = viewModel()
        viewModel.onQueryChange("news")
        advanceTimeBy(SearchViewModel.DEBOUNCE_MS + 1)
        runCurrent()
        assertEquals(1, viewModel.uiState.value.results.channels.size)

        viewModel.onQueryChange("  ")
        advanceTimeBy(SearchViewModel.DEBOUNCE_MS + 1)
        runCurrent()

        assertTrue(viewModel.uiState.value.results.isEmpty)
        assertEquals(listOf("news"), repository.searches)
    }

    @Test
    fun `the same text after trimming is not searched twice`() = runTest {
        val viewModel = viewModel()
        viewModel.onQueryChange("bbc")
        advanceTimeBy(SearchViewModel.DEBOUNCE_MS + 1)
        viewModel.onQueryChange("bbc ")
        advanceTimeBy(SearchViewModel.DEBOUNCE_MS + 1)
        runCurrent()

        assertEquals(listOf("bbc"), repository.searches)
    }

    @Test
    fun `a search result can be added to the favorites`() = runTest {
        val viewModel = viewModel()

        viewModel.onToggleFavorite(channel(2))
        runCurrent()

        assertEquals(setOf(channel(2).key), viewModel.uiState.value.favoriteKeys)
    }
}
