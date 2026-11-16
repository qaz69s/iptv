package com.streambox.player.core.data.repository

import app.cash.turbine.test
import com.streambox.player.core.data.importer.PlaylistImporter
import com.streambox.player.core.database.dao.PlaylistDao
import com.streambox.player.core.database.entity.PlaylistEntity
import com.streambox.player.core.model.ImportStatus
import com.streambox.player.core.model.PlaylistDraft
import com.streambox.player.core.model.PlaylistType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DefaultPlaylistRepositoryTest {
    private val dao = FakePlaylistDao()
    private val permissions = RecordingUriPermissions()

    private fun TestScope.repository(importer: PlaylistImporter) =
        DefaultPlaylistRepository(dao, importer, permissions, backgroundScope)

    @Test
    fun `added playlists get increasing sort order and are observable`() = runTest {
        val repo = repository { _, _ -> ImportStatus.Success(1, 1, 0) }

        val first = repo.addPlaylist(PlaylistDraft("A", PlaylistType.M3U_URL, "https://a/list.m3u"))
        val second = repo.addPlaylist(
            PlaylistDraft("B", PlaylistType.M3U_URL, "https://b/list.m3u", allowCleartext = true),
        )

        val playlists = repo.playlists.first()
        assertEquals(listOf(first, second), playlists.map { it.id })
        assertEquals(listOf(0, 1), playlists.map { it.sortOrder })
        assertTrue(playlists[1].allowCleartext)
    }

    @Test
    fun `local files keep a persisted read permission which is released on delete`() = runTest {
        val repo = repository { _, _ -> ImportStatus.Success(1, 1, 0) }

        val id = repo.addPlaylist(PlaylistDraft("F", PlaylistType.M3U_FILE, "content://doc/1"))
        repo.deletePlaylist(id)

        assertEquals(listOf("content://doc/1"), permissions.persisted)
        assertEquals(listOf("content://doc/1"), permissions.released)
        assertTrue(repo.playlists.first().isEmpty())
    }

    @Test
    fun `import status flows from connecting through progress to the final result`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repo = repository { _, progress ->
            progress(ImportStatus.Running(10, 100, 5))
            gate.await()
            ImportStatus.Success(5, 1, 0)
        }

        repo.importStatus(1).test {
            assertEquals(ImportStatus.Idle, awaitItem())
            repo.startImport(1)
            assertEquals(ImportStatus.Connecting, awaitItem())
            assertEquals(ImportStatus.Running(10, 100, 5), awaitItem())
            gate.complete(Unit)
            assertEquals(ImportStatus.Success(5, 1, 0), awaitItem())
        }
    }

    @Test
    fun `starting an import twice while running does not start a second one`() = runTest {
        var runs = 0
        val gate = CompletableDeferred<Unit>()
        val repo = repository { _, _ ->
            runs++
            gate.await()
            ImportStatus.Success(1, 1, 0)
        }

        repo.startImport(1)
        runCurrent()
        repo.startImport(1)
        runCurrent()
        gate.complete(Unit)
        runCurrent()

        assertEquals(1, runs)
    }

    @Test
    fun `cancel stops the running import`() = runTest {
        val repo = repository { _, _ ->
            CompletableDeferred<Unit>().await()
            ImportStatus.Success(1, 1, 0)
        }

        repo.startImport(1)
        runCurrent()
        repo.cancelImport(1)
        runCurrent()

        assertEquals(ImportStatus.Cancelled, repo.importStatus(1).first())
    }

    @Test
    fun `cleartext switch is persisted`() = runTest {
        val repo = repository { _, _ -> ImportStatus.Success(1, 1, 0) }
        val id = repo.addPlaylist(PlaylistDraft("A", PlaylistType.M3U_URL, "https://a"))

        repo.setAllowCleartext(id, true)

        assertTrue(repo.observePlaylist(id).first()!!.allowCleartext)
    }
}

private class RecordingUriPermissions : UriPermissions {
    val persisted = mutableListOf<String>()
    val released = mutableListOf<String>()

    override fun persistRead(uri: String) {
        persisted += uri
    }

    override fun releaseRead(uri: String) {
        released += uri
    }
}

private class FakePlaylistDao : PlaylistDao {
    private val rows = MutableStateFlow<List<PlaylistEntity>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<PlaylistEntity>> = rows.map { list ->
        list.sortedWith(compareBy({ it.sortOrder }, { it.id }))
    }

    override fun observe(id: Long): Flow<PlaylistEntity?> = rows.map { list -> list.firstOrNull { it.id == id } }

    override suspend fun get(id: Long): PlaylistEntity? = rows.value.firstOrNull { it.id == id }

    override suspend fun insert(playlist: PlaylistEntity): Long {
        val id = nextId++
        rows.update { it + playlist.copy(id = id) }
        return id
    }

    override suspend fun maxSortOrder(): Int = rows.value.maxOfOrNull { it.sortOrder } ?: -1

    override suspend fun delete(id: Long) = rows.update { list -> list.filterNot { it.id == id } }

    override suspend fun setAllowCleartext(id: Long, allowed: Boolean) = modify(id) {
        it.copy(allowCleartext = allowed)
    }

    override suspend fun setLastError(id: Long, error: String?) = modify(id) { it.copy(lastError = error) }

    override suspend fun activateGeneration(
        id: Long,
        generation: Int,
        channelCount: Int,
        groupCount: Int,
        epgUrl: String?,
        updatedAt: Long,
    ): Int {
        modify(id) { it.copy(activeGeneration = generation, channelCount = channelCount, groupCount = groupCount) }
        return 1
    }

    private fun modify(id: Long, change: (PlaylistEntity) -> PlaylistEntity) =
        rows.update { list -> list.map { if (it.id == id) change(it) else it } }
}
