package com.streambox.player.core.database

import androidx.room.Room
import com.streambox.player.core.database.entity.ChannelEntity
import com.streambox.player.core.database.entity.ChannelGroupEntity
import com.streambox.player.core.database.entity.PlaylistEntity
import org.robolectric.RuntimeEnvironment

/** In-memory database plus helpers that mimic the importer (generations, atomic swap). */
internal class TestDatabase {
    val db: StreamBoxDatabase = Room.inMemoryDatabaseBuilder(
        RuntimeEnvironment.getApplication(),
        StreamBoxDatabase::class.java,
    ).allowMainThreadQueries().build()

    val playlists = db.playlistDao()
    val channels = db.channelDao()
    val favorites = db.favoriteDao()
    val history = db.watchHistoryDao()
    val overrides = db.overrideDao()

    suspend fun playlist(name: String = "P", sortOrder: Int = 0): Long = playlists.insert(
        PlaylistEntity(name = name, type = "M3U_URL", url = "https://x/$name.m3u", sortOrder = sortOrder),
    )

    /**
     * Imports [groups] (group name -> channel keys, in order) as a new generation of [playlistId] and
     * activates it like `RoomPlaylistImportStore.verifyAndCommit`: overrides applied, counts, swap,
     * older generations deleted.
     */
    suspend fun import(playlistId: Long, groups: List<Pair<String, List<String>>>): Int {
        val generation = (playlists.get(playlistId)?.activeGeneration ?: 0) + 1
        val groupIds = channels.insertGroups(
            groups.mapIndexed { index, (name, _) ->
                ChannelGroupEntity(playlistId = playlistId, name = name, sortOrder = index, generation = generation)
            },
        )
        var position = 0
        val rows = groups.zip(groupIds).flatMap { (group, groupId) ->
            group.second.map { key -> channel(playlistId, groupId, key, position++, generation) }
        }
        channels.insertChannels(rows)
        overrides.applyToGeneration(playlistId, generation)
        channels.updateGroupCounts(playlistId, generation)
        playlists.activateGeneration(
            id = playlistId,
            generation = generation,
            channelCount = rows.size,
            groupCount = groups.size,
            epgUrl = null,
            updatedAt = 0,
        )
        channels.deleteChannelsExcept(playlistId, generation)
        channels.deleteGroupsExcept(playlistId, generation)
        return generation
    }

    suspend fun idOf(playlistId: Long, key: String): Long = checkNotNull(channels.idByKey(playlistId, key)) { key }

    fun close() = db.close()

    private fun channel(playlistId: Long, groupId: Long, key: String, position: Int, generation: Int) = ChannelEntity(
        playlistId = playlistId,
        groupId = groupId,
        uniqueKey = key,
        name = "Channel $key",
        logoUrl = null,
        streamUrl = "https://s/$key.m3u8",
        tvgId = key,
        tvgName = null,
        number = null,
        catchupType = null,
        catchupSource = null,
        catchupDays = null,
        timeshift = null,
        userAgent = null,
        referrer = null,
        sortOrder = position,
        generation = generation,
    )
}
