package com.streambox.player.core.database

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** List editor reads (P3c): hidden items included, self- vs group-hidden, user order; playlist switches. */
@RunWith(RobolectricTestRunner::class)
class EditorQueriesTest {
    private lateinit var test: TestDatabase
    private val channels get() = test.channels
    private val overrides get() = test.overrides

    @Before
    fun setUp() {
        test = TestDatabase()
    }

    @After
    fun tearDown() = test.close()

    private suspend fun samplePlaylist(): Long {
        val p = test.playlist()
        test.import(p, listOf("News" to listOf("n1", "n2", "n3"), "Sport" to listOf("s1")))
        return p
    }

    @Test
    fun `editor groups include hidden and renamed groups in user order`() = runTest {
        val p = samplePlaylist()
        overrides.updateGroup(p, "News") { it.copy(hidden = true, customName = "Nachrichten") }
        overrides.updateGroupPositions(p, mapOf("Sport" to 0, "News" to 1))

        val groups = channels.observeEditorGroups(p).first()

        assertEquals(listOf("Sport", "News"), groups.map { it.name })
        val news = groups[1]
        assertTrue(news.hidden)
        assertEquals("Nachrichten", news.customName)
        assertEquals(3, news.channelCount)
        assertFalse(channels.observeGroups().first().any { it.name == "Nachrichten" })
    }

    @Test
    fun `editor channels tell self-hidden from hidden through the group`() = runTest {
        val p = samplePlaylist()
        overrides.updateChannel(p, "n2") { it.copy(hidden = true) }
        var rows = channels.observeEditorChannels(p, "News").first()
        assertEquals(listOf("n1", "n2", "n3"), rows.map { it.uniqueKey })
        assertEquals(listOf(false, true, false), rows.map { it.hidden })
        assertEquals(listOf(false, true, false), rows.map { it.selfHidden })

        overrides.updateGroup(p, "News") { it.copy(hidden = true) }
        rows = channels.observeEditorChannels(p, "News").first()
        assertEquals(listOf(true, true, true), rows.map { it.hidden })
        assertEquals(listOf(false, true, false), rows.map { it.selfHidden })
    }

    @Test
    fun `editor channels follow the custom order and show custom names`() = runTest {
        val p = samplePlaylist()
        overrides.updateChannelPositions(p, mapOf("n3" to 0, "n1" to 2))
        overrides.updateChannel(p, "n3") { it.copy(customName = "Mine") }

        val rows = channels.observeEditorChannels(p, "News").first()

        assertEquals(listOf("n3", "n2", "n1"), rows.map { it.uniqueKey })
        assertEquals("Mine", rows.first().customName)
        assertEquals(listOf(2, 1, 0), rows.map { it.sortOrder })
    }

    @Test
    fun `a disabled playlist keeps its data but nothing of it is browsable`() = runTest {
        val p = samplePlaylist()

        test.playlists.setEnabled(p, false)
        assertEquals(0, channels.observeChannelCount().first())
        assertEquals(emptyList<String>(), channels.observeGroups().first().map { it.name })
        assertEquals(null, channels.idByKey(p, "n1"))
        assertFalse(checkNotNull(test.playlists.get(p)).enabled)
        assertEquals(4, channels.countChannels(p, checkNotNull(test.playlists.get(p)).activeGeneration))

        test.playlists.setEnabled(p, true)
        assertEquals(4, channels.observeChannelCount().first())
    }

    @Test
    fun `playlist order decides the order of merged groups`() = runTest {
        val a = test.playlist("A", sortOrder = 0)
        val b = test.playlist("B", sortOrder = 1)
        test.import(a, listOf("Alpha" to listOf("a1")))
        test.import(b, listOf("Beta" to listOf("b1")))
        assertEquals(listOf("Alpha", "Beta"), channels.observeGroups().first().map { it.name })

        test.playlists.setSortOrder(a, 2)
        assertEquals(listOf("Beta", "Alpha"), channels.observeGroups().first().map { it.name })
    }
}
