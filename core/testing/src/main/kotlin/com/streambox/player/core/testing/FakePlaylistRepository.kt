package com.streambox.player.core.testing

import com.streambox.player.core.domain.repository.PlaylistRepository
import com.streambox.player.core.model.ImportStatus
import com.streambox.player.core.model.Playlist
import com.streambox.player.core.model.PlaylistDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** In-memory [PlaylistRepository]; tests drive import progress through [emitStatus]. */
class FakePlaylistRepository(initial: List<Playlist> = emptyList()) : PlaylistRepository {
    private val items = MutableStateFlow(initial)
    private val statuses = MutableStateFlow<Map<Long, ImportStatus>>(emptyMap())
    private var nextId = (initial.maxOfOrNull { it.id } ?: 0) + 1

    val startedImports = mutableListOf<Long>()
    val cancelledImports = mutableListOf<Long>()
    val addedDrafts = mutableListOf<PlaylistDraft>()

    override val playlists: Flow<List<Playlist>> = items

    override fun observePlaylist(id: Long): Flow<Playlist?> = items.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun addPlaylist(draft: PlaylistDraft): Long {
        addedDrafts += draft
        val id = nextId++
        items.update {
            it + Playlist(
                id = id,
                name = draft.name,
                type = draft.type,
                source = draft.source,
                userAgent = draft.userAgent,
                allowCleartext = draft.allowCleartext,
                sortOrder = it.size,
            )
        }
        return id
    }

    override suspend fun deletePlaylist(id: Long) = items.update { list -> list.filterNot { it.id == id } }

    override suspend fun setAllowCleartext(id: Long, allowed: Boolean) =
        items.update { list -> list.map { if (it.id == id) it.copy(allowCleartext = allowed) else it } }

    override suspend fun setEnabled(id: Long, enabled: Boolean) =
        items.update { list -> list.map { if (it.id == id) it.copy(enabled = enabled) else it } }

    override suspend fun reorderPlaylists(orderedIds: List<Long>) = items.update { list ->
        val listed = orderedIds.distinct().mapNotNull { id -> list.firstOrNull { it.id == id } }
        (listed + list.filterNot { it in listed }).mapIndexed { index, playlist -> playlist.copy(sortOrder = index) }
    }

    override fun startImport(id: Long) {
        startedImports += id
        emitStatus(id, ImportStatus.Connecting)
    }

    override fun cancelImport(id: Long) {
        cancelledImports += id
        emitStatus(id, ImportStatus.Cancelled)
    }

    override fun importStatus(id: Long): Flow<ImportStatus> = statuses.map { it[id] ?: ImportStatus.Idle }

    fun emitStatus(id: Long, status: ImportStatus) = statuses.update { it + (id to status) }
}
