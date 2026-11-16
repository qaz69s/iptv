package com.streambox.player.core.data.importer

import com.streambox.player.core.model.PlaylistType

/** In-memory store with the same visibility rules as the Room implementation. */
class FakePlaylistImportStore : PlaylistImportStore {
    data class GroupRow(val id: Long, val playlistId: Long, val name: String, val sortOrder: Int, val generation: Int)

    data class ChannelRow(val playlistId: Long, val groupId: Long?, val channel: NewChannel, val generation: Int)

    val sources = HashMap<Long, PlaylistSource>()
    val activeGeneration = HashMap<Long, Int>()
    val groups = ArrayList<GroupRow>()
    val channels = ArrayList<ChannelRow>()
    val failures = HashMap<Long, String>()
    var epgUrl: String? = null
    var batches = 0
    var discarded = 0
    var maxBatchSize = 0

    /** Simulates a storage error on the given (1-based) batch. */
    var failOnBatch: Int? = null

    /** Simulates lost writes to exercise validation. */
    var dropWrites = false
    private var nextGroupId = 1L

    fun addPlaylist(id: Long, type: PlaylistType = PlaylistType.M3U_URL, location: String = "https://h/list.m3u") {
        sources[id] = PlaylistSource(id, type, location, userAgent = null, allowCleartext = false)
        activeGeneration[id] = 0
    }

    /** Rows a reader would see (active generation only). */
    fun visibleChannels(playlistId: Long): List<NewChannel> = channels.filter {
        it.playlistId == playlistId && it.generation == activeGeneration[playlistId]
    }.map { it.channel }

    fun visibleGroups(playlistId: Long): List<GroupRow> =
        groups.filter { it.playlistId == playlistId && it.generation == activeGeneration[playlistId] }

    fun seed(playlistId: Long, generation: Int, vararg names: String) {
        val groupId = nextGroupId++
        groups += GroupRow(groupId, playlistId, "Old", 0, generation)
        names.forEachIndexed { index, name ->
            channels += ChannelRow(playlistId, groupId, channel(name, index), generation)
        }
        activeGeneration[playlistId] = generation
    }

    override suspend fun source(playlistId: Long): PlaylistSource? = sources[playlistId]

    override suspend fun beginImport(playlistId: Long): Int {
        val active = activeGeneration[playlistId] ?: 0
        channels.removeAll { it.playlistId == playlistId && it.generation != active }
        groups.removeAll { it.playlistId == playlistId && it.generation != active }
        return active + 1
    }

    override suspend fun writeBatch(
        playlistId: Long,
        generation: Int,
        newGroups: List<NewGroup>,
        channels: List<NewChannel>,
        knownGroupIds: Map<String, Long>,
    ): Map<String, Long> {
        batches++
        maxBatchSize = maxOf(maxBatchSize, channels.size)
        if (failOnBatch == batches) throw java.io.IOException("disk full")
        val inserted = newGroups.associate { group ->
            val id = nextGroupId++
            groups += GroupRow(id, playlistId, group.name, group.sortOrder, generation)
            group.name to id
        }
        if (!dropWrites) {
            channels.forEach { channel ->
                val groupId = inserted[channel.groupName] ?: knownGroupIds.getValue(channel.groupName)
                this.channels += ChannelRow(playlistId, groupId, channel, generation)
            }
        }
        return inserted
    }

    override suspend fun verifyAndCommit(
        playlistId: Long,
        generation: Int,
        expectedChannels: Int,
        epgUrl: String?,
        updatedAt: Long,
    ): Int {
        val written = channels.count { it.playlistId == playlistId && it.generation == generation }
        if (written != expectedChannels) throw ImportValidationException("expected $expectedChannels, found $written")
        activeGeneration[playlistId] = generation
        this.epgUrl = epgUrl
        failures.remove(playlistId)
        return groups.count { it.playlistId == playlistId && it.generation == generation }
    }

    override suspend fun deleteInactive(playlistId: Long) {
        val active = activeGeneration.getValue(playlistId)
        channels.removeAll { it.playlistId == playlistId && it.generation != active }
        groups.removeAll { it.playlistId == playlistId && it.generation != active }
    }

    override suspend fun discard(playlistId: Long, generation: Int) {
        discarded++
        channels.removeAll { it.playlistId == playlistId && it.generation == generation }
        groups.removeAll { it.playlistId == playlistId && it.generation == generation }
    }

    override suspend fun recordFailure(playlistId: Long, message: String) {
        failures[playlistId] = message
    }

    companion object {
        fun channel(name: String, index: Int) = NewChannel(
            groupName = "Old", uniqueKey = "url:$index", name = name, logoUrl = null, streamUrl = "https://old/$index",
            tvgId = null, tvgName = null, number = null, catchupType = null, catchupSource = null, catchupDays = null,
            timeshift = null, userAgent = null, referrer = null, sortOrder = index,
        )
    }
}
