package com.streambox.player.core.domain.usecase

import com.streambox.player.core.domain.repository.PlaylistRepository
import com.streambox.player.core.domain.usecase.AddPlaylistUseCase.DraftProblem
import com.streambox.player.core.model.PlaylistDraft
import com.streambox.player.core.model.PlaylistType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class AddPlaylistUseCaseTest {
    private val repository = mockk<PlaylistRepository>(relaxed = true)
    private val useCase = AddPlaylistUseCase(repository)

    private fun url(source: String, cleartext: Boolean = false, name: String = "TV") =
        PlaylistDraft(name = name, type = PlaylistType.M3U_URL, source = source, allowCleartext = cleartext)

    @Test
    fun `stores the trimmed draft and starts its import`() = runTest {
        coEvery { repository.addPlaylist(any()) } returns 5L

        val result = useCase(
            PlaylistDraft(" TV ", PlaylistType.M3U_URL, " https://e.com/a.m3u ", userAgent = "  "),
        )

        assertEquals(AddPlaylistUseCase.Result.Added(5L), result)
        coVerifyOrder {
            repository.addPlaylist(PlaylistDraft("TV", PlaylistType.M3U_URL, "https://e.com/a.m3u", userAgent = null))
            repository.startImport(5L)
        }
    }

    @Test
    fun `invalid draft is not stored`() = runTest {
        val result = useCase(url("", name = " "))

        assertEquals(
            AddPlaylistUseCase.Result.Invalid(setOf(DraftProblem.NAME_MISSING, DraftProblem.SOURCE_MISSING)),
            result,
        )
        coVerify(exactly = 0) { repository.addPlaylist(any()) }
        verify(exactly = 0) { repository.startImport(any()) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["ftp://e.com/a.m3u", "e.com/a.m3u", "https://", "https://e.com/a b.m3u"])
    fun `rejects non-http urls`(source: String) {
        assertEquals(setOf(DraftProblem.URL_INVALID), AddPlaylistUseCase.validate(url(source)))
    }

    @Test
    fun `plain http requires the cleartext opt-in`() {
        assertEquals(
            setOf(DraftProblem.CLEARTEXT_NOT_ALLOWED),
            AddPlaylistUseCase.validate(url("http://e.com/a.m3u")),
        )
        assertTrue(AddPlaylistUseCase.validate(url("HTTP://e.com/a.m3u", cleartext = true)).isEmpty())
    }

    @Test
    fun `file playlists only need a source`() {
        assertTrue(
            AddPlaylistUseCase.validate(PlaylistDraft("F", PlaylistType.M3U_FILE, "content://x/1")).isEmpty(),
        )
        assertEquals(
            setOf(DraftProblem.SOURCE_MISSING),
            AddPlaylistUseCase.validate(PlaylistDraft("F", PlaylistType.M3U_FILE, "")),
        )
    }

    @Test
    fun `xtream is not supported yet`() {
        assertEquals(
            setOf(DraftProblem.TYPE_UNSUPPORTED),
            AddPlaylistUseCase.validate(PlaylistDraft("X", PlaylistType.XTREAM, "https://x")),
        )
    }
}
