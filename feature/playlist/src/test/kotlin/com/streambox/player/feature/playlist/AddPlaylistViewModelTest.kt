package com.streambox.player.feature.playlist

import com.streambox.player.core.domain.usecase.AddPlaylistUseCase
import com.streambox.player.core.domain.usecase.AddPlaylistUseCase.DraftProblem
import com.streambox.player.core.model.PlaylistType
import com.streambox.player.core.testing.FakePlaylistRepository
import com.streambox.player.core.testing.MainDispatcherExtension
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class AddPlaylistViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val repository = FakePlaylistRepository()
    private val viewModel = AddPlaylistViewModel(AddPlaylistUseCase(repository))

    @Test
    fun `empty form reports missing name and source`() = runTest {
        viewModel.submit()

        val state = viewModel.uiState.value
        assertEquals(setOf(DraftProblem.NAME_MISSING, DraftProblem.SOURCE_MISSING), state.problems)
        assertFalse(state.submitting)
        assertTrue(repository.addedDrafts.isEmpty())
    }

    @Test
    fun `valid url adds the playlist and starts the import`() = runTest {
        viewModel.onNameChange("  My TV ")
        viewModel.onUrlChange("https://example.com/list.m3u")
        viewModel.onUserAgentChange("  ")

        viewModel.submit()

        val id = viewModel.uiState.value.createdPlaylistId
        assertEquals(1L, id)
        assertEquals("My TV", repository.addedDrafts.single().name)
        assertNull(repository.addedDrafts.single().userAgent)
        assertEquals(listOf(1L), repository.startedImports)
    }

    @Test
    fun `plain http needs the opt-in`() = runTest {
        viewModel.onNameChange("A")
        viewModel.onUrlChange("http://example.com/list.m3u")
        assertTrue(viewModel.uiState.value.isPlainHttp)

        viewModel.submit()
        assertEquals(setOf(DraftProblem.CLEARTEXT_NOT_ALLOWED), viewModel.uiState.value.problems)

        viewModel.onAllowCleartextChange(true)
        assertTrue(viewModel.uiState.value.problems.isEmpty())
        viewModel.submit()
        assertEquals(true, repository.addedDrafts.single().allowCleartext)
    }

    @Test
    fun `editing a field clears its problem`() = runTest {
        viewModel.submit()
        viewModel.onNameChange("A")
        viewModel.onUrlChange("h")

        assertTrue(viewModel.uiState.value.problems.isEmpty())
    }

    @Test
    fun `picked file suggests a name and submits as file playlist`() = runTest {
        viewModel.onKindChange(SourceKind.FILE)
        viewModel.onFilePicked("content://docs/42", "Family.m3u8")

        assertEquals("Family", viewModel.uiState.value.name)
        viewModel.submit()
        val draft = repository.addedDrafts.single()
        assertEquals(PlaylistType.M3U_FILE, draft.type)
        assertEquals("content://docs/42", draft.source)
    }

    @Test
    fun `picked file keeps a typed name`() = runTest {
        viewModel.onNameChange("Mine")
        viewModel.onFilePicked("content://docs/1", "Other.m3u")

        assertEquals("Mine", viewModel.uiState.value.name)
    }

    @Test
    fun `onNavigated resets the form`() = runTest {
        viewModel.onNameChange("A")
        viewModel.onUrlChange("https://e.com/a.m3u")
        viewModel.submit()

        viewModel.onNavigated()

        assertEquals(AddPlaylistUiState(), viewModel.uiState.value)
    }

    @Test
    fun `picker unavailable is surfaced`() = runTest {
        viewModel.onPickerUnavailable()

        assertTrue(viewModel.uiState.value.pickerUnavailable)
    }
}
