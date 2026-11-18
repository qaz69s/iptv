package com.streambox.player.core.database

import com.streambox.player.core.database.entity.WatchHistoryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WatchHistoryDaoTest {
    private lateinit var test: TestDatabase
    private val history get() = test.history

    @Before
    fun setUp() {
        test = TestDatabase()
    }

    @After
    fun tearDown() = test.close()

    private suspend fun keys(limit: Int = CAP) = history.observe(limit).first().map { it.channel.uniqueKey }

    @Test
    fun `newest first and a channel watched again moves to the top`() = runTest {
        val p = test.playlist()
        test.import(p, listOf("News" to listOf("a", "b", "c")))

        history.record(WatchHistoryEntity(p, "a", watchedAt = 1), CAP)
        history.record(WatchHistoryEntity(p, "b", watchedAt = 2), CAP)
        history.record(WatchHistoryEntity(p, "c", watchedAt = 3), CAP)
        history.record(WatchHistoryEntity(p, "a", watchedAt = 4), CAP)

        assertEquals(listOf("a", "c", "b"), keys())
        assertEquals(4L, history.observe(CAP).first().first().watchedAt)
        assertEquals(3, history.count())
    }

    @Test
    fun `history keeps only the 50 most recent channels`() = runTest {
        val p = test.playlist()
        val all = (1..60).map { "k$it" }
        test.import(p, listOf("All" to all))

        all.forEachIndexed { index, key -> history.record(WatchHistoryEntity(p, key, watchedAt = index.toLong()), CAP) }

        assertEquals(CAP, history.count())
        val listed = keys()
        assertEquals(CAP, listed.size)
        assertEquals("k60", listed.first())
        assertEquals("k11", listed.last())
    }

    @Test
    fun `history survives a refresh`() = runTest {
        val p = test.playlist()
        test.import(p, listOf("News" to listOf("a", "b")))
        history.record(WatchHistoryEntity(p, "b", watchedAt = 5), CAP)

        val generation = test.import(p, listOf("Other" to listOf("b", "a")))

        val row = history.observe(CAP).first().single()
        assertEquals("b", row.channel.uniqueKey)
        assertEquals(generation, row.channel.generation)
    }

    @Test
    fun `clear and remove`() = runTest {
        val p = test.playlist()
        test.import(p, listOf("News" to listOf("a", "b")))
        history.record(WatchHistoryEntity(p, "a", watchedAt = 1), CAP)
        history.record(WatchHistoryEntity(p, "b", watchedAt = 2), CAP)

        history.delete(p, "b")
        assertEquals(listOf("a"), keys())

        history.clear()
        assertTrue(keys().isEmpty())
        assertEquals(0, history.count())
    }

    private companion object {
        const val CAP = 50
    }
}
