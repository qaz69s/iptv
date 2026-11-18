package com.streambox.player.core.database

import androidx.paging.PagingSource
import com.streambox.player.core.database.dao.SortCode
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** P3b: the four sort orders (paging and zap ids agree) and global search (FTS4 + LIKE fallbacks). */
@RunWith(RobolectricTestRunner::class)
class ChannelDaoBrowseTest {
    private lateinit var test: TestDatabase
    private val channels get() = test.channels

    @Before
    fun setUp() {
        test = TestDatabase()
    }

    @After
    fun tearDown() = test.close()

    private suspend fun pagedKeys(groupName: String?, sort: Int): List<String> {
        val result = channels.pagingSource(groupName, sort).load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 500, placeholdersEnabled = false),
        )
        return (result as PagingSource.LoadResult.Page).data.map { it.uniqueKey }
    }

    private suspend fun orderedKeys(groupName: String?, sort: Int): List<String> {
        val byId = pagedKeysById()
        return channels.orderedIds(groupName, sort).map { byId.getValue(it) }
    }

    private suspend fun pagedKeysById(): Map<Long, String> {
        val result = channels.pagingSource(null, SortCode.DEFAULT).load(
            PagingSource.LoadParams.Refresh(key = null, loadSize = 500, placeholdersEnabled = false),
        )
        return (result as PagingSource.LoadResult.Page).data.associate { it.id to it.uniqueKey }
    }

    /** Two playlists: "one" (sorted first) and "two". */
    private suspend fun twoPlaylists(): Pair<Long, Long> {
        val one = test.playlist("one", sortOrder = 0)
        val two = test.playlist("two", sortOrder = 1)
        test.import(
            one,
            listOf("News" to listOf("a", "b", "c")),
            names = mapOf("a" to "zulu", "b" to "Alpha", "c" to "mike"),
            numbers = mapOf("a" to 30, "c" to 1),
        )
        test.import(
            two,
            listOf("News" to listOf("d"), "Kids" to listOf("e")),
            names = mapOf(
                "d" to "bravo",
                "e" to "echo",
            ),
        )
        return one to two
    }

    @Test
    fun `default is playlist by playlist in import order`() = runTest {
        twoPlaylists()
        assertEquals(listOf("a", "b", "c", "d", "e"), pagedKeys(null, SortCode.DEFAULT))
    }

    @Test
    fun `name sorts across playlists ignoring case and uses the user's name`() = runTest {
        val (one, _) = twoPlaylists()
        test.overrides.updateChannel(one, "a") { it.copy(customName = "Charlie") }

        assertEquals(listOf("b", "d", "a", "e", "c"), pagedKeys(null, SortCode.NAME))
        assertEquals(listOf("b", "d", "a", "c"), pagedKeys("News", SortCode.NAME))
    }

    @Test
    fun `number sorts by tvg-chno, else the playlist position`() = runTest {
        twoPlaylists()
        // c=1, d=1 (position), e=2 (position), b=2 (position), a=30; ties by playlist then import order.
        assertEquals(listOf("c", "d", "b", "e", "a"), pagedKeys(null, SortCode.NUMBER))
    }

    @Test
    fun `custom uses the user's order and falls back to import order`() = runTest {
        val (one, _) = twoPlaylists()
        test.overrides.updateChannelPositions(one, mapOf("c" to 0, "a" to 2))

        assertEquals(listOf("c", "b", "a", "d", "e"), pagedKeys(null, SortCode.CUSTOM))
        assertEquals(listOf("a", "b", "c", "d", "e"), pagedKeys(null, SortCode.DEFAULT))
    }

    @Test
    fun `zap ids follow exactly the browsing order for every sort`() = runTest {
        twoPlaylists()
        for (sort in listOf(SortCode.DEFAULT, SortCode.NAME, SortCode.NUMBER, SortCode.CUSTOM)) {
            assertEquals("sort $sort", pagedKeys(null, sort), orderedKeys(null, sort))
            assertEquals("sort $sort", pagedKeys("News", sort), orderedKeys("News", sort))
        }
    }

    // --- Search ------------------------------------------------------------------------------------

    private suspend fun searchKeys(match: String, like: String, substring: Boolean = false) =
        channels.search(match, like, if (substring) 1 else 0, limit = 100).map { it.uniqueKey }

    @Test
    fun `fts finds word prefixes of the name`() = runTest {
        val p = test.playlist()
        test.import(
            p,
            listOf("News" to listOf("a", "b", "c")),
            names = mapOf("a" to "CCTV-1 综合", "b" to "BBC News", "c" to "Discovery"),
        )

        assertEquals(listOf("a"), searchKeys("\"cct\"*", "%cct%"))
        assertEquals(listOf("b"), searchKeys("\"bbc\"* \"new\"*", "%bbc new%"))
        assertEquals(listOf("a"), searchKeys("\"综合\"*", "%综合%"))
        assertTrue(searchKeys("\"overy\"*", "%overy%").isEmpty())
    }

    @Test
    fun `cjk text inside a word is found with the substring fallback`() = runTest {
        val p = test.playlist()
        test.import(p, listOf("News" to listOf("a", "b")), names = mapOf("a" to "凤凰新闻台", "b" to "体育"))

        assertTrue(searchKeys("\"新闻\"*", "%新闻%").isEmpty())
        assertEquals(listOf("a"), searchKeys("\"新闻\"*", "%新闻%", substring = true))
    }

    @Test
    fun `a renamed channel is found by its new name (user decision)`() = runTest {
        val p = test.playlist()
        test.import(p, listOf("News" to listOf("a", "b")), names = mapOf("a" to "Channel One", "b" to "Two"))
        test.overrides.updateChannel(p, "b") { it.copy(customName = "My Favourite") }

        assertEquals(listOf("b"), searchKeys("\"favo\"*", "%favo%"))
        // The original name still matches through the index.
        assertEquals(listOf("b"), searchKeys("\"two\"*", "%two%"))
    }

    @Test
    fun `search skips hidden channels, disabled playlists and old generations`() = runTest {
        val p = test.playlist()
        test.import(p, listOf("News" to listOf("a", "b")), names = mapOf("a" to "Sport A", "b" to "Sport B"))
        test.overrides.updateChannel(p, "b") { it.copy(hidden = true) }
        test.import(p, listOf("News" to listOf("a", "b")), names = mapOf("a" to "Sport A", "b" to "Sport B"))

        assertEquals(listOf("a"), searchKeys("\"sport\"*", "%sport%"))

        test.db.openHelper.writableDatabase.execSQL("UPDATE playlists SET enabled = 0 WHERE id = $p")
        assertTrue(searchKeys("\"sport\"*", "%sport%").isEmpty())
    }

    @Test
    fun `groups are found by their display name`() = runTest {
        val p = test.playlist()
        test.import(p, listOf("Sports HD" to listOf("a"), "News" to listOf("b"), "Kids" to listOf("c")))
        test.overrides.updateGroup(p, "Kids") { it.copy(customName = "Cartoons") }

        assertEquals(listOf("Sports HD"), channels.searchGroups("%sport%", 10).map { it.name })
        assertEquals(listOf("Cartoons"), channels.searchGroups("%cartoon%", 10).map { it.name })
        assertTrue(channels.searchGroups("%kids%", 10).isEmpty())
    }
}
