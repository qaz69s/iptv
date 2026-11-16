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

    /**
     * Starts (or restarts) downloading + parsing the playlist in an application-wide scope so it
     * survives leaving the screen. A running import for the same playlist is left untouched.
     */
    fun startImport(id: Long)

    fun cancelImport(id: Long)

    fun importStatus(id: Long): Flow<ImportStatus>
}
