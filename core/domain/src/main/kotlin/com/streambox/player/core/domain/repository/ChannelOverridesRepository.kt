package com.streambox.player.core.domain.repository

import com.streambox.player.core.model.ChannelKey
import com.streambox.player.core.model.ChannelOverride
import com.streambox.player.core.model.GroupOverride
import kotlinx.coroutines.flow.Flow

/**
 * The user's edits of a playlist (requirements §4.1 list editor): hide / rename / reorder channels and
 * groups. Stored apart from the imported data and re-applied after every refresh. Groups are
 * identified by the name the playlist gives them (empty = channels without a group).
 */
interface ChannelOverridesRepository {
    fun channelOverrides(playlistId: Long): Flow<List<ChannelOverride>>

    fun groupOverrides(playlistId: Long): Flow<List<GroupOverride>>

    suspend fun setChannelHidden(key: ChannelKey, hidden: Boolean)

    /** Blank or null [name] restores the playlist's name. */
    suspend fun renameChannel(key: ChannelKey, name: String?)

    /**
     * Puts the channels of [orderedKeys] (one playlist) into this order, inside the positions they
     * occupy now, so channels not listed keep their place. Keys not in the current data are ignored.
     */
    suspend fun reorderChannels(playlistId: Long, orderedKeys: List<String>)

    /** Removes the custom position of every channel of the playlist. */
    suspend fun resetChannelOrder(playlistId: Long)

    suspend fun setGroupHidden(playlistId: Long, groupName: String, hidden: Boolean)

    /** Blank or null [name] restores the playlist's group name. */
    suspend fun renameGroup(playlistId: Long, groupName: String, name: String?)

    /** Like [reorderChannels], for the groups of one playlist. */
    suspend fun reorderGroups(playlistId: Long, orderedGroupNames: List<String>)
}
