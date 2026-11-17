package com.streambox.player.feature.player

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChannelNumberInputTest {
    private val committed = mutableListOf<Int>()

    private fun TestScope.input() = ChannelNumberInput(backgroundScope, timeoutMs = TIMEOUT_MS, maxDigits = 3) {
        committed += it
    }

    @Test
    fun `digits accumulate and commit after the timeout`() = runTest {
        val input = input()

        input.onDigit(1)
        input.onDigit(2)
        assertEquals("12", input.digits.value)
        advanceTimeBy(TIMEOUT_MS - 1)
        runCurrent()
        assertTrue(committed.isEmpty())

        advanceTimeBy(2)
        runCurrent()
        assertEquals(listOf(12), committed)
        assertNull(input.digits.value)
        assertFalse(input.isActive)
    }

    @Test
    fun `each digit restarts the timeout`() = runTest {
        val input = input()

        input.onDigit(4)
        advanceTimeBy(TIMEOUT_MS - 100)
        input.onDigit(2)
        advanceTimeBy(TIMEOUT_MS - 100)
        runCurrent()
        assertTrue(committed.isEmpty())

        advanceTimeBy(200)
        runCurrent()
        assertEquals(listOf(42), committed)
    }

    @Test
    fun `commits at once when max digits are typed or on commitNow`() = runTest {
        val input = input()

        input.onDigit(1)
        input.onDigit(0)
        input.onDigit(5)
        assertEquals(listOf(105), committed)

        input.onDigit(7)
        input.commitNow()
        assertEquals(listOf(105, 7), committed)
        assertNull(input.digits.value)
    }

    @Test
    fun `backspace removes the last digit and clears the entry when empty`() = runTest {
        val input = input()
        assertFalse(input.backspace())

        input.onDigit(3)
        input.onDigit(8)
        assertTrue(input.backspace())
        assertEquals("3", input.digits.value)

        assertTrue(input.backspace())
        assertNull(input.digits.value)
        advanceTimeBy(TIMEOUT_MS * 2)
        runCurrent()
        assertTrue(committed.isEmpty())
    }

    @Test
    fun `backspace restarts the timeout for the remaining digits`() = runTest {
        val input = input()

        input.onDigit(5)
        input.onDigit(6)
        advanceTimeBy(TIMEOUT_MS - 100)
        input.backspace()
        advanceTimeBy(TIMEOUT_MS - 100)
        runCurrent()
        assertTrue(committed.isEmpty())

        advanceTimeBy(200)
        runCurrent()
        assertEquals(listOf(5), committed)
    }

    @Test
    fun `zero entries and cancel commit nothing`() = runTest {
        val input = input()

        input.onDigit(0)
        input.onDigit(0)
        advanceTimeBy(TIMEOUT_MS + 1)
        runCurrent()

        input.onDigit(9)
        input.cancel()
        advanceTimeBy(TIMEOUT_MS + 1)
        runCurrent()

        assertTrue(committed.isEmpty())
        assertNull(input.digits.value)
    }

    private companion object {
        const val TIMEOUT_MS = 1_000L
    }
}
