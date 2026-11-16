package com.streambox.player.core.data.repository

import com.streambox.player.core.common.dispatcher.ApplicationScope
import com.streambox.player.core.data.importer.PlaylistImporter
import com.streambox.player.core.database.dao.PlaylistDao
import com.streambox.player.core.database.entity.PlaylistEntity
import com.streambox.player.core.domain.repository.PlaylistRepository
import com.streambox.player.core.model.ImportStatus
import com.streambox.player.core.model.Playlist
import com.streambox.player.core.model.PlaylistDraft
import com.streambox.player.core.model.PlaylistType
import com.streambox.player.core.model.UpdateMode
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Singleton
class DefaultPlaylistRepository @Inject internal constructor(
    private val playlistDao: PlaylistDao,
    private val importer: PlaylistImporter,
    private val uriPermissions: UriPermissions,
    @ApplicationScope private val scope: CoroutineScope,
) : PlaylistRepository {
    private val statuses = MutableStateFlow<Map<Long, ImportStatus>>(emptyMap())
    private val jobs = HashMap<Long, Job>()

    override val playlists: Flow<List<Playlist>> = playlistDao.observeAll().map { list -> list.map { it.toModel() } }

    override fun observePlaylist(id: Long): Flow<Playlist?> = playlistDao.observe(id).map { it?.toModel() }

    override suspend fun addPlaylist(draft: PlaylistDraft): Long {
        if (draft.type == PlaylistType.M3U_FILE) uriPermissions.persistRead(draft.source)
        return playlistDao.insert(
            PlaylistEntity(
                name = draft.name,
                type = draft.type.name,
                url = draft.source,
                userAgent = draft.userAgent,
                allowCleartext = draft.allowCleartext,
                sortOrder = playlistDao.maxSortOrder() + 1,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun deletePlaylist(id: Long) {
        cancelImport(id)
        val playlist = playlistDao.get(id)
        playlistDao.delete(id) // channels and groups cascade
        if (playlist?.type == PlaylistType.M3U_FILE.name) uriPermissions.releaseRead(playlist.url)
        statuses.update { it - id }
    }

    override suspend fun setAllowCleartext(id: Long, allowed: Boolean) = playlistDao.setAllowCleartext(id, allowed)

    override fun startImport(id: Long) {
        synchronized(jobs) {
            if (jobs[id]?.isActive == true) return
            statuses.update { it + (id to ImportStatus.Connecting) }
            val job = scope.launch {
                val result = importer.import(id) { status -> statuses.update { it + (id to status) } }
                statuses.update { it + (id to result) }
            }
            jobs[id] = job
            job.invokeOnCompletion { synchronized(jobs) { if (jobs[id] === job) jobs.remove(id) } }
        }
    }

    override fun cancelImport(id: Long) {
        val job = synchronized(jobs) { jobs.remove(id) } ?: return
        job.cancel()
        statuses.update { it + (id to ImportStatus.Cancelled) }
    }

    override fun importStatus(id: Long): Flow<ImportStatus> =
        statuses.map { it[id] ?: ImportStatus.Idle }.distinctUntilChanged()
}

internal fun PlaylistEntity.toModel() = Playlist(
    id = id,
    name = name,
    type = PlaylistType.valueOf(type),
    source = url,
    userAgent = userAgent,
    referrer = referrer,
    epgUrl = epgUrl,
    updateMode = runCatching { UpdateMode.valueOf(updateMode) }.getOrDefault(UpdateMode.MANUAL),
    updateIntervalHours = updateIntervalHours,
    lastUpdated = lastUpdated,
    lastError = lastError,
    enabled = enabled,
    sortOrder = sortOrder,
    allowCleartext = allowCleartext,
    channelCount = channelCount,
    groupCount = groupCount,
)
