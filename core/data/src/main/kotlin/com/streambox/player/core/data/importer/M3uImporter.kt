package com.streambox.player.core.data.importer

import com.streambox.player.core.common.dispatcher.Dispatcher
import com.streambox.player.core.common.dispatcher.StreamBoxDispatcher
import com.streambox.player.core.common.result.AppError
import com.streambox.player.core.common.result.toAppError
import com.streambox.player.core.model.ImportFailure
import com.streambox.player.core.model.ImportStatus
import com.streambox.player.core.model.PlaylistType
import com.streambox.player.core.parser.m3u.M3uEntry
import com.streambox.player.core.parser.m3u.M3uInput
import com.streambox.player.core.parser.m3u.M3uParser
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Runs one import and reports progress; returns the final status (Success or Failed). Cancellable. */
fun interface PlaylistImporter {
    suspend fun import(playlistId: Long, onProgress: (ImportStatus) -> Unit): ImportStatus
}

/**
 * Streaming M3U import: download/open -> parse line by line -> write in transactions of [batchSize]
 * channels -> validate -> atomic swap. Memory stays bounded by one batch plus the group/key indexes.
 * Any failure (or cancellation) discards the new generation, so the previous channels stay intact.
 */
class M3uImporter internal constructor(
    private val store: PlaylistImportStore,
    private val opener: PlaylistSourceOpener,
    private val ioDispatcher: CoroutineDispatcher,
    private val batchSize: Int,
    private val now: () -> Long,
) : PlaylistImporter {
    @Inject
    constructor(
        store: PlaylistImportStore,
        opener: PlaylistSourceOpener,
        @Dispatcher(StreamBoxDispatcher.IO) ioDispatcher: CoroutineDispatcher,
    ) : this(store, opener, ioDispatcher, DEFAULT_BATCH_SIZE, System::currentTimeMillis)

    override suspend fun import(playlistId: Long, onProgress: (ImportStatus) -> Unit): ImportStatus =
        withContext(ioDispatcher) {
            val source = store.source(playlistId)
                ?: return@withContext ImportStatus.Failed(ImportFailure.Other("playlist $playlistId not found"))
            onProgress(ImportStatus.Connecting)
            val generation = store.beginImport(playlistId)
            try {
                val outcome = opener.open(source).use { opened ->
                    parseAndWrite(source, generation, opened, onProgress)
                }
                if (outcome.channels == 0) {
                    store.discard(playlistId, generation)
                    return@withContext fail(playlistId, ImportFailure.EmptyPlaylist)
                }
                onProgress(ImportStatus.Finalizing(outcome.channels))
                val groups = store.verifyAndCommit(playlistId, generation, outcome.channels, outcome.epgUrl, now())
                runCatching { store.deleteInactive(playlistId) } // old rows are already invisible
                ImportStatus.Success(outcome.channels, groups, outcome.skippedLines)
            } catch (e: CancellationException) {
                withContext(NonCancellable) { store.discard(playlistId, generation) }
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                withContext(NonCancellable) { store.discard(playlistId, generation) }
                fail(playlistId, e.toImportFailure(source.type))
            }
        }

    private class Outcome(val channels: Int, val epgUrl: String?, val skippedLines: Long)

    @Suppress("LongMethod")
    private suspend fun parseAndWrite(
        source: PlaylistSource,
        generation: Int,
        opened: OpenedSource,
        onProgress: (ImportStatus) -> Unit,
    ): Outcome {
        val counting = CountingInputStream(opened.stream)
        val parser = M3uParser(M3uInput.reader(counting))
        val groupIds = HashMap<String, Long>()
        val pendingGroups = LinkedHashMap<String, NewGroup>()
        val keyOccurrences = HashMap<String, Int>()
        val batch = ArrayList<NewChannel>(batchSize)
        var written = 0
        var position = 0

        suspend fun flush() {
            if (batch.isEmpty() && pendingGroups.isEmpty()) return
            val inserted = store.writeBatch(
                source.playlistId,
                generation,
                pendingGroups.values.toList(),
                batch.toList(),
                groupIds,
            )
            groupIds.putAll(inserted)
            written += batch.size
            batch.clear()
            pendingGroups.clear()
            onProgress(ImportStatus.Running(counting.count, opened.totalBytes, written))
        }

        onProgress(ImportStatus.Running(0, opened.totalBytes, 0))
        while (true) {
            currentCoroutineContext().ensureActive()
            val entry = parser.nextEntry() ?: break
            val groupName = entry.groupTitle.orEmpty()
            if (groupName !in groupIds && groupName !in pendingGroups) {
                pendingGroups[groupName] = NewGroup(groupName, groupIds.size + pendingGroups.size)
            }
            batch += entry.toNewChannel(groupName, dedupe(entry.uniqueKey, keyOccurrences), position++)
            if (batch.size >= batchSize) flush()
        }
        flush()
        val epgUrl = parser.header.epgUrls.firstOrNull()
        return Outcome(written, epgUrl, parser.stats.malformedLines)
    }

    /** Duplicate keys inside one playlist (same tvg-id for SD/HD variants) get a stable `#n` suffix. */
    private fun dedupe(key: String, occurrences: HashMap<String, Int>): String {
        val seen = occurrences[key] ?: 0
        occurrences[key] = seen + 1
        return if (seen == 0) key else "$key#${seen + 1}"
    }

    private suspend fun fail(playlistId: Long, failure: ImportFailure): ImportStatus.Failed {
        withContext(NonCancellable) { runCatching { store.recordFailure(playlistId, failure.toString()) } }
        return ImportStatus.Failed(failure)
    }

    companion object {
        const val DEFAULT_BATCH_SIZE = 500

        internal fun M3uEntry.toNewChannel(groupName: String, key: String, position: Int) = NewChannel(
            groupName = groupName,
            uniqueKey = key,
            name = name,
            logoUrl = tvgLogo,
            streamUrl = url,
            tvgId = tvgId,
            tvgName = tvgName,
            number = tvgChno,
            catchupType = catchup,
            catchupSource = catchupSource,
            catchupDays = catchupDays,
            timeshift = timeshift,
            userAgent = userAgent,
            referrer = referrer,
            sortOrder = position,
        )

        internal fun Throwable.toImportFailure(type: PlaylistType): ImportFailure {
            if (this is ImportValidationException) return ImportFailure.Other(message.orEmpty())
            return when (val error = toAppError()) {
                is AppError.Http -> ImportFailure.Http(error.code)

                AppError.Timeout -> ImportFailure.Timeout

                AppError.NoNetwork -> ImportFailure.NoNetwork

                AppError.CleartextNotAllowed -> ImportFailure.CleartextNotAllowed

                is AppError.Io -> if (type ==
                    PlaylistType.M3U_FILE
                ) {
                    ImportFailure.FileNotReadable
                } else {
                    ImportFailure.Other(error.detail)
                }

                is AppError.Parse -> ImportFailure.Other(error.detail)

                is AppError.Unknown -> ImportFailure.Other(error.detail)
            }
        }
    }
}
