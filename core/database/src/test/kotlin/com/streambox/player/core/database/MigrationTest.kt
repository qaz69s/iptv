package com.streambox.player.core.database

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.streambox.player.core.database.entity.FavoriteEntity
import com.streambox.player.core.database.entity.WatchHistoryEntity
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Upgrades a real v1 database (created from the committed schema `1.json`, as P1 / P2 shipped it)
 * with [StreamBoxDatabase.MIGRATIONS] (v1 -> v2 -> v3). Opening it through Room validates every table,
 * index and FTS table against the current entities, so a migration that does not produce exactly the
 * latest exported schema fails here.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var file: File
    private var room: StreamBoxDatabase? = null

    @Before
    fun setUp() {
        file = context.getDatabasePath(NAME)
        context.deleteDatabase(NAME)
    }

    @After
    fun tearDown() {
        room?.close()
        context.deleteDatabase(NAME)
    }

    @Test
    fun `v1 upgrades to v2 keeping playlists groups and channels`() = runTest {
        createV1Database()

        val db = openWithMigrations()

        val playlist = checkNotNull(db.playlistDao().get(1))
        assertEquals("Old list", playlist.name)
        assertEquals(1, playlist.activeGeneration)
        val row = checkNotNull(db.channelDao().playbackRow(10))
        assertEquals("Old channel", row.name)
        assertEquals("tvg:old", row.uniqueKey)
        assertEquals("News", row.groupName)
        assertEquals(10L, db.channelDao().idByKey(1, "tvg:old"))
        assertEquals(listOf("News"), db.channelDao().observeGroups().first().map { it.name })
    }

    @Test
    fun `migrated database accepts favorites history and overrides`() = runTest {
        createV1Database()
        val db = openWithMigrations()

        db.favoriteDao().insert(FavoriteEntity(1, "tvg:old", addedAt = 5))
        db.watchHistoryDao().record(WatchHistoryEntity(1, "tvg:old", watchedAt = 6), cap = 50)
        db.overrideDao().updateChannel(1, "tvg:old") { it.copy(customName = "Renamed") }

        assertEquals(listOf(10L), db.favoriteDao().observeFavoriteChannels().first().map { it.id })
        assertEquals(listOf(10L), db.watchHistoryDao().observe(50).first().map { it.channel.id })
        assertEquals("Renamed", db.channelDao().playbackRow(10)?.name)
        assertNull(db.overrideDao().groupOverride(1, "News"))
    }

    @Test
    fun `migrated database accepts custom groups (v3)`() = runTest {
        createV1Database()
        val db = openWithMigrations()

        val group = db.customGroupDao().createGroup("Mine", createdAt = 1)
        db.customGroupDao().addMember(group, 1, "tvg:old", addedAt = 2)

        assertEquals(listOf(10L), db.customGroupDao().observeChannels(group).first().map { it.id })
        assertEquals(1, db.customGroupDao().observeGroups().first().single().channelCount)
        db.playlistDao().delete(1)
        assertEquals(emptyList<Long>(), db.customGroupDao().memberKeys(group).map { it.playlistId })
    }

    @Test
    fun `full text index keeps working after the migration`() = runTest {
        createV1Database()
        val db = openWithMigrations()

        val sqlite = db.openHelper.writableDatabase
        sqlite.execSQL(
            "INSERT INTO channels (playlistId, groupId, uniqueKey, name, streamUrl, hidden, sortOrder, locked, " +
                "generation) VALUES (1, 5, 'tvg:new', 'Fresh Channel', 'https://s/new', 0, 1, 0, 1)",
        )
        val names = mutableListOf<String>()
        sqlite.query("SELECT name FROM channels_fts WHERE channels_fts MATCH 'channel'").use { cursor ->
            while (cursor.moveToNext()) names += cursor.getString(0)
        }

        assertEquals(listOf("Fresh Channel", "Old channel"), names.sorted())
    }

    @Test
    fun `every schema version up to the current one has a migration`() {
        val versions = schemaFiles().map { it.nameWithoutExtension.toInt() }.sorted()
        val current = versions.last()

        assertEquals((1..current).toList(), versions)
        val covered = StreamBoxDatabase.MIGRATIONS.map { it.startVersion to it.endVersion }
        assertEquals((1 until current).map { it to it + 1 }, covered)
        assertTrue(current >= 3)
    }

    private fun openWithMigrations(): StreamBoxDatabase =
        Room.databaseBuilder(context, StreamBoxDatabase::class.java, NAME)
            .apply { StreamBoxDatabase.MIGRATIONS.forEach { addMigrations(it) } }
            .allowMainThreadQueries()
            .build()
            .also {
                room = it
                // Opening runs the migration and Room's schema validation.
                it.openHelper.writableDatabase
            }

    /** Creates the v1 schema exactly as exported in `1.json` and fills it with one playlist. */
    private fun createV1Database() {
        file.parentFile?.mkdirs()
        val schema = JSONObject(schemaFile(1).readText()).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            schema.getJSONArray("entities").objects().forEach { entity -> createEntity(db, entity) }
            schema.getJSONArray("setupQueries").strings().forEach(db::execSQL)
            insertV1Rows(db)
            db.version = 1
        }
    }

    /** Table, its indices and (for FTS tables) the content sync triggers, as Room creates them. */
    private fun createEntity(db: SQLiteDatabase, entity: JSONObject) {
        val table = entity.getString("tableName")
        db.execSQL(entity.getString("createSql").withTable(table))
        entity.optJSONArray("indices")?.objects()?.forEach { db.execSQL(it.getString("createSql").withTable(table)) }
        entity.optJSONArray("contentSyncTriggers")?.strings()?.forEach(db::execSQL)
    }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }

    private fun insertV1Rows(db: SQLiteDatabase) {
        db.insertOrThrow(
            "playlists",
            null,
            ContentValues().apply {
                put("id", 1)
                put("name", "Old list")
                put("type", "M3U_URL")
                put("url", "https://x/list.m3u")
                put("updateMode", "MANUAL")
                put("updateIntervalHours", 0)
                put("enabled", 1)
                put("sortOrder", 0)
                put("allowCleartext", 0)
                put("activeGeneration", 1)
                put("channelCount", 1)
                put("groupCount", 1)
                put("createdAt", 0)
            },
        )
        db.insertOrThrow(
            "channel_groups",
            null,
            ContentValues().apply {
                put("id", 5)
                put("playlistId", 1)
                put("name", "News")
                put("hidden", 0)
                put("sortOrder", 0)
                put("locked", 0)
                put("generation", 1)
                put("channelCount", 1)
            },
        )
        db.insertOrThrow(
            "channels",
            null,
            ContentValues().apply {
                put("id", 10)
                put("playlistId", 1)
                put("groupId", 5)
                put("uniqueKey", "tvg:old")
                put("name", "Old channel")
                put("streamUrl", "https://s/old.m3u8")
                put("hidden", 0)
                put("sortOrder", 0)
                put("locked", 0)
                put("generation", 1)
            },
        )
    }

    private fun String.withTable(table: String) = replace("\${TABLE_NAME}", table)

    private fun schemaFiles(): List<File> = SCHEMA_DIR.listFiles { f -> f.extension == "json" }.orEmpty().toList()

    private fun schemaFile(version: Int) = File(SCHEMA_DIR, "$version.json")

    private companion object {
        const val NAME = "migration-test.db"

        /** Unit tests run with the module directory as working directory. */
        val SCHEMA_DIR = File("schemas/com.streambox.player.core.database.StreamBoxDatabase")
    }
}
