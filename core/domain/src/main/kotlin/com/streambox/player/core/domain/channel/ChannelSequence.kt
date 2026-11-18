package com.streambox.player.core.domain.channel

import com.streambox.player.core.model.ZapDirection

/**
 * Zapping through an ordered channel list (the list as the browser shows it): next / previous with
 * wrap-around, "next" without wrap-around (auto-play next) and channel numbers as 1-based positions
 * (used inside the virtual groups "favorites" and "recently watched").
 */
object ChannelSequence {
    /**
     * The item after / before [current], wrapping at the ends. When [current] is not in the list (e.g. it
     * was removed meanwhile), NEXT starts at the first item and PREVIOUS at the last. Null for an empty list.
     */
    fun <T> adjacent(items: List<T>, current: T, direction: ZapDirection): T? {
        if (items.isEmpty()) return null
        val index = items.indexOf(current)
        val target = when (direction) {
            ZapDirection.NEXT -> if (index < 0) 0 else (index + 1) % items.size
            ZapDirection.PREVIOUS -> if (index < 0) items.lastIndex else (index - 1 + items.size) % items.size
        }
        return items[target]
    }

    /** The item after [current] without wrapping; null after the last one or when [current] is not listed. */
    fun <T> next(items: List<T>, current: T): T? {
        val index = items.indexOf(current)
        return if (index < 0) null else items.getOrNull(index + 1)
    }

    /** 1-based position [number] in the list; null when out of range. */
    fun <T> atNumber(items: List<T>, number: Int): T? = items.getOrNull(number - 1)
}
