package com.streambox.player.core.database

import com.streambox.player.core.database.dao.ChannelKeyRow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Custom groups (schema v3, P3c): cross-playlist members by stable key, order, refresh, cascades. */
@RunWith(RobolectricTestRunner::class)
class CustomGroupDaoTest {
    private lateinit var test: TestDatabase
    private val dao get() = test.customGroups

    @Before
    fun setUp() {
        test = TestDatabase()
    }

    @After
    fun tearDown() = test.close()

    private suspend fun memberKeys(groupId: Long) = dao.observeChannels(groupId).first().map { it.uniqueKey }

    @Test
    fun `groups are listed in creation order with visible channel counts`() = runTest {
        val p = test.playlist()
        test.import(p, listOf("News" to listOf("n1", "n2")))
        val kids = dao.createGroup("Kids", createdAt = 1)
        val movies = dao.createGroup("Movies", createdAt = 2)
        dao.addMember(kids, p, "n1", addedAt = 3)
        dao.addMember(kids, p, "n2", addedAt = 4)
        dao.addMember(kids, p, "gone", addedAt = 5)

        val groups = dao.observeGroups().first()
        assertEquals(listOf(kids, movies), groups.map { it.id })
        assertEquals(listOf(2, 0), groups.map { it.channelCount })
    }

    @Test
    fun `a group mixes playlists, keeps its order and survives a refresh`() = runTest {
        val a = test.playlist("A", sortOrder = 0)
        val b = test.playlist("B", sortOrder = 1)
        test.import(a, listOf("News" to listOf("a1", "a2")))
        test.import(b, listOf("Sport" to listOf("b1")))
        val group = dao.createGroup("Mix", createdAt = 0)
        dao.addMember(group, b, "b1", addedAt = 1)
        dao.addMember(group, a, "a2", addedAt = 2)
        dao.addMember(group, a, "a2", addedAt = 3) // already there: keeps its place
        assertEquals(listOf("b1", "a2"), memberKeys(group))

        test.import(a, listOf("News" to listOf("a2", "a1")))
        assertEquals(listOf("b1", "a2"), memberKeys(group))
        assertEquals(test.idOf(a, "a2"), dao.observeChannels(group).first()[1].id)
    }

    @Test
    fun `hidden channels and disabled playlists are not listed but stay members`() = runTest {
        val p = test.playlist()
        test.import(p, listOf("News" to listOf("n1", "n2")))
        val group = dao.createGroup("G", createdAt = 0)
        dao.addMember(group, p, "n1", addedAt = 1)
        dao.addMember(group, p, "n2", addedAt = 2)

        test.overrides.updateChannel(p, "n1") { it.copy(hidden = true) }
        assertEquals(listOf("n2"), memberKeys(group))

        test.playlists.setEnabled(p, false)
        assertEquals(emptyList<String>(), memberKeys(group))
        assertEquals(0, dao.observeGroups().first().single().channelCount)

        test.playlists.setEnabled(p, true)
        test.overrides.updateChannel(p, "n1") { it.copy(hidden = false) }
        assertEquals(listOf("n1", "n2"), memberKeys(group))
    }

    @Test
    fun `reordering members and groups puts the listed ones first`() = runTest {
        val p = test.playlist()
        test.import(p, listOf("News" to listOf("n1", "n2", "n3")))
        val first = dao.createGroup("First", createdAt = 0)
        val second = dao.createGroup("Second", createdAt = 0)
        listOf("n1", "n2", "n3").forEachIndexed { i, key -> dao.addMember(first, p, key, addedAt = i.toLong()) }

        dao.reorderMembers(first, listOf(ChannelKeyRow(p, "n3"), ChannelKeyRow(p, "n1")))
        assertEquals(listOf("n3", "n1", "n2"), memberKeys(first))

        dao.reorderGroups(listOf(second))
        assertEquals(listOf(second, first), dao.observeGroups().first().map { it.id })
    }

    @Test
    fun `channel membership is observable and removing works`() = runTest {
        val p = test.playlist()
        test.import(p, listOf("News" to listOf("n1")))
        val a = dao.createGroup("A", createdAt = 0)
        val b = dao.createGroup("B", createdAt = 0)
        dao.addMember(a, p, "n1", addedAt = 0)
        dao.addMember(b, p, "n1", addedAt = 0)
        assertEquals(setOf(a, b), dao.observeGroupsOf(p, "n1").first().toSet())

        dao.deleteMember(a, p, "n1")
        assertEquals(listOf(b), dao.observeGroupsOf(p, "n1").first())
    }

    @Test
    fun `deleting a group or a playlist removes the members`() = runTest {
        val a = test.playlist("A")
        val b = test.playlist("B")
        test.import(a, listOf("News" to listOf("a1")))
        test.import(b, listOf("News" to listOf("b1")))
        val keep = dao.createGroup("Keep", createdAt = 0)
        val drop = dao.createGroup("Drop", createdAt = 0)
        dao.addMember(keep, a, "a1", addedAt = 0)
        dao.addMember(keep, b, "b1", addedAt = 1)
        dao.addMember(drop, a, "a1", addedAt = 0)

        dao.deleteGroup(drop)
        test.playlists.delete(b)

        assertEquals(listOf(keep), dao.observeGroups().first().map { it.id })
        assertEquals(listOf(ChannelKeyRow(a, "a1")), dao.memberKeys(keep))
        assertEquals(listOf(keep), dao.observeGroupsOf(a, "a1").first())
    }
}
