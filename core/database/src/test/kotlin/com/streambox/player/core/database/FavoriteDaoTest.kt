package com.streambox.player.core.database

import com.streambox.player.core.database.dao.ChannelKeyRow
import com.streambox.player.core.database.entity.FavoriteEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FavoriteDaoTest {
    private lateinit var test: TestDatabase
    private val favorites get() = test.favorites

    @Before
    fun setUp() {
        test = TestDatabase()
    }

    @After
    fun tearDown() = test.close()

    private suspend fun favoriteKeys() = favorites.observeFavoriteChannels().first().map { it.uniqueKey }

    @Test
    fun `favorites are listed by time added, oldest first`() = runTest {
        val p = test.playlist()
        test.import(p, listOf("News" to listOf("a", "b", "c")))

        favorites.insert(FavoriteEntity(p, "c", addedAt = 10))
        favorites.insert(FavoriteEntity(p, "a", addedAt = 20))
        favorites.insert(FavoriteEntity(p, "b", addedAt = 30))

        assertEquals(listOf("c", "a", "b"), favoriteKeys())
    }

    @Test
    fun `adding an existing favorite keeps its time`() = runTest {
        val p = test.playlist()
        test.import(p, listOf("News" to listOf("a", "b")))
        favorites.insert(FavoriteEntity(p, "a", addedAt = 10))
        favorites.insert(FavoriteEntity(p, "b", addedAt = 20))

        favorites.insert(FavoriteEntity(p, "a", addedAt = 99))

        assertEquals(listOf("a", "b"), favoriteKeys())
    }

    @Test
    fun `a custom order wins and later favorites follow it by time added`() = runTest {
        val p = test.playlist()
        test.import(p, listOf("News" to listOf("a", "b", "c", "d")))
        favorites.insert(FavoriteEntity(p, "a", addedAt = 1))
        favorites.insert(FavoriteEntity(p, "b", addedAt = 2))
        favorites.insert(FavoriteEntity(p, "c", addedAt = 3))

        favorites.reorder(listOf(ChannelKeyRow(p, "c"), ChannelKeyRow(p, "a")))
        favorites.insert(FavoriteEntity(p, "d", addedAt = 4))

        assertEquals(listOf("c", "a", "b", "d"), favoriteKeys())

        favorites.clearCustomOrder()
        assertEquals(listOf("a", "b", "c", "d"), favoriteKeys())
    }

    @Test
    fun `favorites span playlists`() = runTest {
        val first = test.playlist("one", sortOrder = 0)
        val second = test.playlist("two", sortOrder = 1)
        test.import(first, listOf("News" to listOf("a")))
        test.import(second, listOf("Sport" to listOf("a")))

        favorites.insert(FavoriteEntity(second, "a", addedAt = 1))
        favorites.insert(FavoriteEntity(first, "a", addedAt = 2))

        assertEquals(listOf(second, first), favorites.observeFavoriteChannels().first().map { it.playlistId })
    }

    @Test
    fun `favorites survive a refresh and resolve to the new channel rows`() = runTest {
        val p = test.playlist()
        test.import(p, listOf("News" to listOf("a", "b")))
        val oldId = test.idOf(p, "b")
        favorites.insert(FavoriteEntity(p, "b", addedAt = 1))

        val generation = test.import(p, listOf("News" to listOf("x", "b", "a")))

        val listed = favorites.observeFavoriteChannels().first()
        assertEquals(listOf("b"), listed.map { it.uniqueKey })
        assertEquals(generation, listed.single().generation)
        assertNotEquals(oldId, listed.single().id)
    }

    @Test
    fun `a favorite missing from the refresh is kept and comes back with its channel`() = runTest {
        val p = test.playlist()
        test.import(p, listOf("News" to listOf("a", "b")))
        favorites.insert(FavoriteEntity(p, "b", addedAt = 1))

        test.import(p, listOf("News" to listOf("a")))
        assertTrue(favoriteKeys().isEmpty())
        assertEquals(listOf(ChannelKeyRow(p, "b")), favorites.observeKeys().first())

        test.import(p, listOf("News" to listOf("a", "b")))
        assertEquals(listOf("b"), favoriteKeys())
    }

    @Test
    fun `deleting a playlist removes its favorites`() = runTest {
        val p = test.playlist()
        test.import(p, listOf("News" to listOf("a")))
        favorites.insert(FavoriteEntity(p, "a", addedAt = 1))

        test.playlists.delete(p)

        assertTrue(favoriteKeys().isEmpty())
        assertTrue(favorites.observeKeys().first().isEmpty())
    }

    @Test
    fun `removing a favorite and the is-favorite flag`() = runTest {
        val p = test.playlist()
        test.import(p, listOf("News" to listOf("a")))
        favorites.insert(FavoriteEntity(p, "a", addedAt = 1))
        assertTrue(favorites.observeIsFavorite(p, "a").first())

        assertEquals(1, favorites.delete(p, "a"))

        assertFalse(favorites.observeIsFavorite(p, "a").first())
        assertTrue(favoriteKeys().isEmpty())
    }
}
