package com.streambox.player.core.domain.usecase

import com.streambox.player.core.domain.repository.PlaylistRepository
import com.streambox.player.core.model.PlaylistDraft
import com.streambox.player.core.model.PlaylistType
import javax.inject.Inject

/** Validates a draft, stores the playlist and kicks off its first import. */
class AddPlaylistUseCase @Inject constructor(private val repository: PlaylistRepository) {
    sealed interface Result {
        data class Added(val playlistId: Long) : Result

        data class Invalid(val problems: Set<DraftProblem>) : Result
    }

    enum class DraftProblem { NAME_MISSING, SOURCE_MISSING, URL_INVALID, CLEARTEXT_NOT_ALLOWED, TYPE_UNSUPPORTED }

    suspend operator fun invoke(draft: PlaylistDraft): Result {
        val normalized = draft.copy(
            name = draft.name.trim(),
            source = draft.source.trim(),
            userAgent = draft.userAgent?.trim()?.ifEmpty { null },
        )
        val problems = validate(normalized)
        if (problems.isNotEmpty()) return Result.Invalid(problems)
        val id = repository.addPlaylist(normalized)
        repository.startImport(id)
        return Result.Added(id)
    }

    companion object {
        private val HTTP_URL = Regex("^https?://[^\\s/?#]+[^\\s]*$", RegexOption.IGNORE_CASE)

        fun validate(draft: PlaylistDraft): Set<DraftProblem> = buildSet {
            if (draft.name.isBlank()) add(DraftProblem.NAME_MISSING)
            when (draft.type) {
                PlaylistType.M3U_URL -> when {
                    draft.source.isBlank() -> add(DraftProblem.SOURCE_MISSING)

                    !HTTP_URL.matches(draft.source) -> add(DraftProblem.URL_INVALID)

                    draft.source.startsWith("http://", ignoreCase = true) && !draft.allowCleartext ->
                        add(DraftProblem.CLEARTEXT_NOT_ALLOWED)
                }

                PlaylistType.M3U_FILE -> if (draft.source.isBlank()) add(DraftProblem.SOURCE_MISSING)

                PlaylistType.XTREAM -> add(DraftProblem.TYPE_UNSUPPORTED)
            }
        }
    }
}
