package com.streamdeck.player.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Verifies the atomic import swap and that user edits survive a refresh (spec §4.1 / §8.2). */
@RunWith(AndroidJUnit4::class)
class ImportAndOverridesTest {
    private lateinit var db: AppDatabase
    private val dao get() = db.channelDao()

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
    }

    @After fun tearDown() = db.close()

    private suspend fun import(pid: Long, names: List<String>) {
        val g = dao.insertGroup(ChannelGroupEntity(playlistId = pid, name = "News", originalName = "News", pending = true))
        dao.insertChannels(
            names.mapIndexed { i, n ->
                ChannelEntity(
                    playlistId = pid, groupId = g, groupName = "News", uniqueKey = "id:$n", name = n, logoUrl = null, streamUrl = "http://s/$n",
                    tvgId = n, tvgName = null, number = i + 1, catchupType = null, catchupSource = null, catchupDays = null,
                    userAgent = null, referrer = null, sortOrder = i, importOrder = i, pending = true,
                )
            },
        )
        dao.commitImport(pid)
    }

    @Test fun abortKeepsLiveData() = runTest {
        val pid = db.playlistDao().insert(PlaylistEntity(name = "p", type = PlaylistType.M3U_URL, url = "u"))
        import(pid, listOf("a", "b"))
        dao.insertChannels(listOf(dao.allLive(pid).first().copy(id = 0, pending = true)))
        dao.abortImport(pid)
        assertEquals(2, dao.liveCount(pid))
    }

    @Test fun overridesSurviveRefresh() = runTest {
        val pid = db.playlistDao().insert(PlaylistEntity(name = "p", type = PlaylistType.M3U_URL, url = "u"))
        import(pid, listOf("a", "b", "c"))
        val editor = db.editorDao()
        editor.upsertChannelOverride(ChannelOverrideEntity(pid, "id:b", customName = "Bee", hidden = true))
        editor.upsertGroupOverride(GroupOverrideEntity(pid, "Kids", custom = true))
        editor.upsertChannelOverride(ChannelOverrideEntity(pid, "id:c", customGroup = "Kids"))
        dao.applyOverrides(pid)

        import(pid, listOf("a", "b", "c", "d"))
        val live = dao.allLive(pid).associateBy { it.uniqueKey }
        assertEquals("Bee", live.getValue("id:b").name)
        assertTrue(live.getValue("id:b").hidden)
        assertEquals("Kids", live.getValue("id:c").groupName)
        assertEquals(4, live.size)
    }
}
