package com.streambox.player.core.database

import androidx.paging.PagingSource
import com.streambox.player.core.database.entity.ChannelEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Hide / rename / reorder overrides: applied at once, and re-applied to every refreshed generation. */
@RunWith(RobolectricTestRunner::class)
class OverrideDaoTest {
    private lateinit var test: TestDatabase
    private val overrides get() = test.overrides
    private val channels get() = test.channels

    @Before
    fun setUp() {
        test = TestDatabase()
    }

    @After
    fun tearDown() = test.close()

    private suspend fun visible(groupName: String? = null): List<ChannelEntity> {
        val result = channels.pagingSource(groupName).load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 500, placeholdersEnabled = false),
        )
        return (result as PagingSource.LoadResult.Page).data
    }

    private suspend fun visibleKeys(groupName: String? = null) = visible(groupName).map { it.uniqueKey }

    private suspend fun groupNames() = channels.observeGroups().first().map { it.name }

    private suspend fun samplePlaylist(): Long {
        val p = test.playlist()
        test.import(p, listOf("News" to listOf("n1", "n2", "n3"), "Sport" to listOf("s1", "s2")))
        return p
    }

    @Test
    fun `hiding a channel applies at once and survives a refresh`() = runTest {
        val p = samplePlaylist()

        overrides.updateChannel(p, "n2") { it.copy(hidden = true) }
        assertEquals(listOf("n1", "n3", "s1", "s2"), visibleKeys())
        assertEquals(4, channels.observeChannelCount().first())

        test.import(p, listOf("News" to listOf("n3", "n2", "n1"), "Sport" to listOf("s1", "s2")))
        assertEquals(listOf("n3", "n1", "s1", "s2"), visibleKeys())
        assertEquals(listOf(2, 2), channels.observeGroups().first().map { it.channelCount })

        overrides.updateChannel(p, "n2") { it.copy(hidden = false) }
        assertEquals(listOf("n3", "n2", "n1", "s1", "s2"), visibleKeys())
        assertNull(overrides.channelOverride(p, "n2"))
    }

    @Test
    fun `renaming a channel survives a refresh and blank restores the name`() = runTest {
        val p = samplePlaylist()

        overrides.updateChannel(p, "s1") { it.copy(customName = "My Sport") }
        test.import(p, listOf("Sport" to listOf("s1")))

        val row = visible().single()
        assertEquals("My Sport", row.customName)
        assertEquals("Channel s1", row.name)
        assertEquals("My Sport", channels.playbackRow(row.id)?.name)

        overrides.updateChannel(p, "s1") { it.copy(customName = null) }
        assertEquals("Channel s1", channels.playbackRow(test.idOf(p, "s1"))?.name)
    }

    @Test
    fun `a custom channel order drives browsing and zapping and survives a refresh`() = runTest {
        val p = samplePlaylist()

        // n3 first, then n1, n2: n3 takes n1's slot (0), n1 -> 1, n2 -> 2.
        overrides.updateChannelPositions(p, mapOf("n3" to 0, "n1" to 1, "n2" to 2))
        assertEquals(listOf("n3", "n1", "n2"), visibleKeys("News"))

        test.import(p, listOf("News" to listOf("n1", "n2", "n3"), "Sport" to listOf("s1", "s2")))
        assertEquals(listOf("n3", "n1", "n2", "s1", "s2"), visibleKeys())

        val n1 = checkNotNull(channels.position(test.idOf(p, "n1")))
        assertEquals(test.idOf(p, "n2"), channels.nextAfter("News", n1.playlistSort, p, n1.channelSort, n1.importSort))
        assertEquals(
            test.idOf(p, "n3"),
            channels.previousBefore("News", n1.playlistSort, p, n1.channelSort, n1.importSort),
        )
        assertEquals(test.idOf(p, "n3"), channels.first("News"))
        assertEquals(test.idOf(p, "n2"), channels.last("News"))
    }

    @Test
    fun `hiding a group hides it and its channels everywhere until shown again`() = runTest {
        val p = samplePlaylist()

        overrides.updateGroup(p, "Sport") { it.copy(hidden = true) }
        assertEquals(listOf("News"), groupNames())
        assertEquals(listOf("n1", "n2", "n3"), visibleKeys())

        test.import(p, listOf("Sport" to listOf("s1", "s2", "s3"), "News" to listOf("n1")))
        assertEquals(listOf("News"), groupNames())
        assertEquals(listOf("n1"), visibleKeys())

        overrides.updateGroup(p, "Sport") { it.copy(hidden = false) }
        assertEquals(listOf("Sport", "News"), groupNames())
        assertEquals(listOf("s1", "s2", "s3", "n1"), visibleKeys())
        assertEquals(listOf(3, 1), channels.observeGroups().first().map { it.channelCount })
    }

    @Test
    fun `a channel hidden itself stays hidden when its group is shown again`() = runTest {
        val p = samplePlaylist()
        overrides.updateChannel(p, "s2") { it.copy(hidden = true) }
        overrides.updateGroup(p, "Sport") { it.copy(hidden = true) }

        overrides.updateGroup(p, "Sport") { it.copy(hidden = false) }

        assertEquals(listOf("s1"), visibleKeys("Sport"))
    }

    @Test
    fun `renamed and reordered groups survive a refresh and filter by their new name`() = runTest {
        val p = samplePlaylist()

        overrides.updateGroup(p, "Sport") { it.copy(customName = "Football") }
        overrides.updateGroupPositions(p, mapOf("Sport" to 0, "News" to 1))
        test.import(p, listOf("News" to listOf("n1"), "Sport" to listOf("s1")))

        assertEquals(listOf("Football", "News"), groupNames())
        assertEquals(listOf("s1"), visibleKeys("Football"))
        assertTrue(visibleKeys("Sport").isEmpty())
        assertEquals("Football", channels.playbackRow(test.idOf(p, "s1"))?.groupName)
    }

    @Test
    fun `overrides of one playlist do not touch another playlist with the same keys`() = runTest {
        val first = test.playlist("one", sortOrder = 0)
        val second = test.playlist("two", sortOrder = 1)
        test.import(first, listOf("News" to listOf("a")))
        test.import(second, listOf("News" to listOf("a")))

        overrides.updateChannel(first, "a") { it.copy(hidden = true) }
        overrides.updateGroup(second, "News") { it.copy(customName = "Second") }

        assertEquals(listOf(second), visible().map { it.playlistId })
        // The first playlist's "News" has no visible channel left, so only the renamed group is listed.
        assertEquals(listOf("Second"), groupNames())
    }

    @Test
    fun `overrides are listed per playlist and removed with it`() = runTest {
        val p = samplePlaylist()
        overrides.updateChannel(p, "n1") { it.copy(hidden = true) }
        overrides.updateGroup(p, "News") { it.copy(customName = "Nachrichten") }

        assertEquals(listOf("n1"), overrides.observeChannelOverrides(p).first().map { it.uniqueKey })
        assertEquals(listOf("News"), overrides.observeGroupOverrides(p).first().map { it.groupName })

        test.playlists.delete(p)
        assertTrue(overrides.observeChannelOverrides(p).first().isEmpty())
        assertTrue(overrides.observeGroupOverrides(p).first().isEmpty())
    }
}
