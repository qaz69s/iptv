package com.streambox.player.core.data.importer

import com.streambox.player.core.common.result.AppError
import com.streambox.player.core.common.result.AppException
import com.streambox.player.core.model.ImportFailure
import com.streambox.player.core.model.ImportStatus
import com.streambox.player.core.model.PlaylistType
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class M3uImporterTest {
    private val store = FakePlaylistImportStore().apply { addPlaylist(1) }

    private fun playlist(count: Int, groups: Int = 2): String = buildString {
        append("#EXTM3U url-tvg=\"https://epg/guide.xml\"\n")
        repeat(count) { i ->
            append("#EXTINF:-1 tvg-id=\"c$i\" group-title=\"G${i % groups}\",Channel $i\nhttps://s/$i.m3u8\n")
        }
    }

    private fun opener(text: String, total: Long? = text.length.toLong()) =
        PlaylistSourceOpener { OpenedSource(ByteArrayInputStream(text.toByteArray()), total) }

    private fun TestScope.importer(opener: PlaylistSourceOpener, batchSize: Int = 2) =
        M3uImporter(store, opener, StandardTestDispatcher(testScheduler), batchSize) { 1234L }

    private suspend fun M3uImporter.run(playlistId: Long = 1, progress: MutableList<ImportStatus> = mutableListOf()) =
        import(playlistId) { progress += it }

    @Test
    fun `imports every channel in batches and commits atomically`() = runTest {
        val status = importer(opener(playlist(5)), batchSize = 2).run()

        assertEquals(ImportStatus.Success(channels = 5, groups = 2, skippedLines = 0), status)
        assertEquals(5, store.visibleChannels(1).size)
        assertEquals(3, store.batches)
        assertEquals(2, store.maxBatchSize)
        assertEquals("https://epg/guide.xml", store.epgUrl)
    }

    @Test
    fun `default batch size is within the 500-1000 range`() {
        assertTrue(M3uImporter.DEFAULT_BATCH_SIZE in 500..1000)
    }

    @Test
    fun `channels keep playlist order and are linked to their groups`() = runTest {
        importer(opener(playlist(4)), batchSize = 3).run()

        val channels = store.visibleChannels(1)
        assertEquals((0 until 4).toList(), channels.map { it.sortOrder })
        assertEquals(listOf("G0", "G1"), store.visibleGroups(1).sortedBy { it.sortOrder }.map { it.name })
        val idsByName = store.visibleGroups(1).associate { it.name to it.id }
        store.channels.filter { it.generation == 1 }.forEach { row ->
            assertEquals(idsByName[row.channel.groupName], row.groupId)
        }
    }

    @Test
    fun `entries without group go to the uncategorized group`() = runTest {
        importer(opener("#EXTINF:-1,A\nhttps://s/a\nhttps://s/bare\n")).run()

        assertEquals(listOf(""), store.visibleGroups(1).map { it.name })
        assertEquals(2, store.visibleChannels(1).size)
    }

    @Test
    fun `refresh swaps in the new generation and removes the old one`() = runTest {
        store.seed(1, generation = 1, "Old A", "Old B")

        importer(opener(playlist(3))).run()

        assertEquals(listOf("Channel 0", "Channel 1", "Channel 2"), store.visibleChannels(1).map { it.name })
        assertEquals(3, store.channels.size) // old generation deleted after the swap
    }

    @Test
    fun `failure while reading keeps the previous channels`() = runTest {
        store.seed(1, generation = 1, "Old A", "Old B")
        val failing = PlaylistSourceOpener {
            OpenedSource(FailingAfter(playlist(50).toByteArray(), failAt = 400), null)
        }

        val status = importer(failing).run()

        assertInstanceOf(ImportStatus.Failed::class.java, status)
        assertEquals(listOf("Old A", "Old B"), store.visibleChannels(1).map { it.name })
        assertEquals(2, store.channels.size) // partial new rows discarded
        assertTrue(store.failures.containsKey(1))
    }

    @Test
    fun `storage failure in a later batch rolls back`() = runTest {
        store.seed(1, generation = 1, "Old")
        store.failOnBatch = 2

        val status = importer(opener(playlist(6))).run()

        assertInstanceOf(ImportStatus.Failed::class.java, status)
        assertEquals(listOf("Old"), store.visibleChannels(1).map { it.name })
        assertEquals(1, store.channels.size)
    }

    @Test
    fun `empty playlist fails without touching old data`() = runTest {
        store.seed(1, generation = 1, "Old")

        val status = importer(opener("#EXTM3U\n# nothing here\n")).run()

        assertEquals(ImportStatus.Failed(ImportFailure.EmptyPlaylist), status)
        assertEquals(listOf("Old"), store.visibleChannels(1).map { it.name })
    }

    @Test
    fun `http error is reported with its status code`() = runTest {
        val status = importer({ throw AppException(AppError.Http(404)) }).run()

        assertEquals(ImportStatus.Failed(ImportFailure.Http(404)), status)
    }

    @Test
    fun `cleartext refusal and timeouts are typed`() = runTest {
        assertEquals(
            ImportStatus.Failed(ImportFailure.CleartextNotAllowed),
            importer({ throw AppException(AppError.CleartextNotAllowed) }).run(),
        )
        assertEquals(
            ImportStatus.Failed(ImportFailure.Timeout),
            importer({ throw java.net.SocketTimeoutException() }).run(),
        )
    }

    @Test
    fun `unreadable local file maps to FileNotReadable`() = runTest {
        store.addPlaylist(2, PlaylistType.M3U_FILE, "content://docs/1")

        val status = importer({ throw AppException(AppError.Io("permission revoked")) }).run(playlistId = 2)

        assertEquals(ImportStatus.Failed(ImportFailure.FileNotReadable), status)
    }

    @Test
    fun `validation mismatch aborts the swap`() = runTest {
        store.seed(1, generation = 1, "Old")
        store.dropWrites = true

        val status = importer(opener(playlist(3))).run()

        assertInstanceOf(ImportStatus.Failed::class.java, status)
        assertEquals(listOf("Old"), store.visibleChannels(1).map { it.name })
    }

    @Test
    fun `progress goes connecting, running with growing counts, finalizing`() = runTest {
        val progress = mutableListOf<ImportStatus>()
        val text = playlist(5)

        importer(opener(text), batchSize = 2).run(progress = progress)

        assertEquals(ImportStatus.Connecting, progress.first())
        val running = progress.filterIsInstance<ImportStatus.Running>()
        assertEquals(listOf(0, 2, 4, 5), running.map { it.channels })
        assertTrue(running.all { it.totalBytes == text.length.toLong() })
        assertTrue(running.zipWithNext().all { (a, b) -> b.bytesRead >= a.bytesRead })
        assertEquals(ImportStatus.Finalizing(5), progress.last())
    }

    @Test
    fun `duplicate tvg ids get stable suffixes`() = runTest {
        val text = "#EXTINF:-1 tvg-id=\"cnn\",CNN HD\nhttps://s/hd\n#EXTINF:-1 tvg-id=\"cnn\",CNN SD\nhttps://s/sd\n"

        importer(opener(text)).run()

        assertEquals(listOf("tvg:cnn", "tvg:cnn#2"), store.visibleChannels(1).map { it.uniqueKey })
    }

    @Test
    fun `malformed lines are skipped and reported`() = runTest {
        val status = importer(opener("#EXTINF:-1,A\nnot a url\nhttps://s/a\n")).run()

        assertEquals(ImportStatus.Success(channels = 1, groups = 1, skippedLines = 1), status)
    }

    @Test
    fun `leftovers of an interrupted import are cleaned before a new one`() = runTest {
        store.seed(1, generation = 1, "Old")
        store.channels += FakePlaylistImportStore.ChannelRow(1, null, FakePlaylistImportStore.channel("Stale", 9), 2)

        importer(opener(playlist(1))).run()

        assertEquals(listOf("Channel 0"), store.visibleChannels(1).map { it.name })
        assertEquals(1, store.channels.size)
    }

    @Test
    fun `cancellation discards new rows and keeps old ones`() = runTest {
        store.seed(1, generation = 1, "Old")
        val opened = CompletableDeferred<Unit>()
        val blocking = PlaylistSourceOpener {
            opened.complete(Unit)
            OpenedSource(ByteArrayInputStream(playlist(10).toByteArray()), null)
        }
        val importer = importer(blocking, batchSize = 2)
        var cancelled = false
        val job = launch {
            try {
                importer.import(1) { status ->
                    if (status is ImportStatus.Running && status.channels >= 4) throw CancellationException("user")
                }
            } catch (e: CancellationException) {
                cancelled = true
                throw e
            }
        }
        advanceUntilIdle()
        job.join()

        assertTrue(cancelled)
        assertTrue(store.discarded >= 1)
        assertEquals(listOf("Old"), store.visibleChannels(1).map { it.name })
        assertEquals(1, store.channels.size)
    }

    @Test
    fun `unknown playlist fails fast`() = runTest {
        val status = importer(opener(playlist(1))).run(playlistId = 99)
        assertInstanceOf(ImportStatus.Failed::class.java, status)
        assertEquals(0, store.batches)
    }

    /** Emits [bytes] and throws once [failAt] bytes have been read (connection reset mid-download). */
    private class FailingAfter(private val bytes: ByteArray, private val failAt: Int) : InputStream() {
        private var position = 0

        override fun read(): Int {
            if (position >= failAt) throw IOException("connection reset")
            return if (position < bytes.size) bytes[position++].toInt() and 0xFF else -1
        }
    }
}
