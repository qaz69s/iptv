package com.streambox.player.core.database

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Stable channel keys (P3a): the last channel and other key-based records resolve after refreshes. */
@RunWith(RobolectricTestRunner::class)
class ChannelDaoKeyTest {
    private lateinit var test: TestDatabase

    @Before
    fun setUp() {
        test = TestDatabase()
    }

    @After
    fun tearDown() = test.close()

    @Test
    fun `a key resolves to the active generation's row after a refresh`() = runTest {
        val p = test.playlist()
        test.import(p, listOf("News" to listOf("a", "b")))
        val before = test.idOf(p, "b")

        test.import(p, listOf("News" to listOf("b")))

        val after = test.channels.idByKey(p, "b")
        assertNotEquals(before, after)
        assertEquals("b", after?.let { test.channels.playbackRow(it)?.uniqueKey })
        assertNull(test.channels.playbackRow(before))
    }

    @Test
    fun `a key that is gone or belongs to another playlist does not resolve`() = runTest {
        val first = test.playlist("one")
        val second = test.playlist("two")
        test.import(first, listOf("News" to listOf("a")))
        test.import(second, listOf("News" to listOf("b")))

        assertNull(test.channels.idByKey(first, "b"))
        test.import(first, listOf("News" to listOf("c")))
        assertNull(test.channels.idByKey(first, "a"))
    }

    @Test
    fun `positions of keys and groups in the current generation`() = runTest {
        val p = test.playlist()
        test.import(p, listOf("News" to listOf("a", "b"), "Sport" to listOf("c")))

        assertEquals(
            mapOf("a" to 0, "c" to 2),
            test.channels.channelPositions(p, listOf("a", "c", "zz")).associate { it.uniqueKey to it.position },
        )
        assertEquals(
            mapOf("Sport" to 1),
            test.channels.groupPositions(p, listOf("Sport")).associate { it.uniqueKey to it.position },
        )
    }
}
