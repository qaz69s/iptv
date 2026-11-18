package com.streambox.player.core.data.repository

import com.streambox.player.core.database.dao.ChannelKeyRow
import com.streambox.player.core.database.dao.CustomGroupDao
import com.streambox.player.core.database.dao.CustomGroupRow
import com.streambox.player.core.model.ChannelKey
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Name rules of custom groups and how keys reach the DAO (P3c). */
class DefaultCustomGroupsRepositoryTest {
    private val dao = mockk<CustomGroupDao>(relaxed = true)
    private val repository = DefaultCustomGroupsRepository(dao)

    @Test
    fun `a blank name creates nothing`() = runTest {
        assertNull(repository.create("   "))
        coVerify(exactly = 0) { dao.createGroup(any(), any()) }
    }

    @Test
    fun `names are trimmed and an existing group of the same name is reused`() = runTest {
        every { dao.observeGroups() } returns flowOf(listOf(CustomGroupRow(4, "Kids", 0)))
        coEvery { dao.createGroup("Sports", any()) } returns 9

        assertEquals(4L, repository.create(" Kids "))
        assertEquals(9L, repository.create(" Sports"))
        coVerify(exactly = 1) { dao.createGroup(any(), any()) }
    }

    @Test
    fun `renaming to a blank name is refused`() = runTest {
        assertFalse(repository.rename(3, " "))
        assertTrue(repository.rename(3, " Movies "))
        coVerify(exactly = 1) { dao.renameGroup(3, "Movies") }
    }

    @Test
    fun `channels are stored by their stable key`() = runTest {
        repository.addChannel(2, ChannelKey(1, "tvg:a"))
        repository.reorderChannels(2, listOf(ChannelKey(2, "b"), ChannelKey(1, "tvg:a")))
        repository.removeChannel(2, ChannelKey(1, "tvg:a"))

        coVerify { dao.addMember(2, 1, "tvg:a", any()) }
        coVerify { dao.reorderMembers(2, listOf(ChannelKeyRow(2, "b"), ChannelKeyRow(1, "tvg:a"))) }
        coVerify { dao.deleteMember(2, 1, "tvg:a") }
    }
}
