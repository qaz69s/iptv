package com.streambox.player.core.domain.repository

import com.streambox.player.core.model.ImportStatus
import com.streambox.player.core.model.Playlist
import com.streambox.player.core.model.PlaylistDraft
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {
    val playlists: Flow<List<Playlist>>

    fun observePlaylist(id: Long): Flow<Playlist?>

    /** Persists the playlist metadata (no channels yet) and returns its id. */
    suspend fun addPlaylist(draft: PlaylistDraft): Long

    suspend fun deletePlaylist(id: Long)

    suspend fun setAllowCleartext(id: Long, allowed: Boolean)

    /** A disabled playlist keeps its data, favorites and edits; its channels are just not shown (P3c). */
    suspend fun setEnabled(id: Long, enabled: Boolean)

    /** Puts the playlists in this order (P3c: move up / down); playlists not listed follow. */
    suspend fun reorderPlaylists(orderedIds: List<Long>)

    /**
     * Starts (or restarts) downloading + parsing the playlist in an application-wide scope so it
     * survives leaving the screen. A running import for the same playlist is left untouched.
     */
    fun startImport(id: Long)

    fun cancelImport(id: Long)

    fun importStatus(id: Long): Flow<ImportStatus>
}
