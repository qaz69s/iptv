package com.streambox.player.core.domain.repository

import com.streambox.player.core.model.Channel
import com.streambox.player.core.model.ChannelKey
import com.streambox.player.core.model.CustomGroup
import kotlinx.coroutines.flow.Flow

/**
 * The user's own groups (P3c, user decision 4): a channel can be in several custom groups, from any
 * playlist. Custom groups show as virtual groups in the channel browser and are zapped / numbered like
 * favorites (the list order, wrapping; a channel number is the 1-based position).
 */
interface CustomGroupsRepository {
    /** Every custom group in the user's order, with the number of channels currently visible. */
    val groups: Flow<List<CustomGroup>>

    /** Visible channels of the group in the user's order. */
    fun channels(groupId: Long): Flow<List<Channel>>

    /** Ids of the custom groups that contain the channel. */
    fun groupsOf(key: ChannelKey): Flow<Set<Long>>

    /** Creates a group at the end; null for a blank name. An existing group of the same name is reused. */
    suspend fun create(name: String): Long?

    /** False (nothing changed) for a blank name. */
    suspend fun rename(groupId: Long, name: String): Boolean

    suspend fun delete(groupId: Long)

    /** Puts the groups in this order; groups not listed follow. */
    suspend fun reorderGroups(orderedIds: List<Long>)

    /** Appends the channel to the group; no-op when it is already there. */
    suspend fun addChannel(groupId: Long, key: ChannelKey)

    suspend fun removeChannel(groupId: Long, key: ChannelKey)

    /** Puts the group's channels in this order; channels not listed follow. */
    suspend fun reorderChannels(groupId: Long, orderedKeys: List<ChannelKey>)
}
