package com.streambox.player.feature.playlist

/**
 * Up / down moves of the list editors (P3c: TV-friendly instead of drag and drop). Returns the list
 * with the item at [index] swapped with its neighbour, or null when it cannot move that way.
 */
internal fun <T> List<T>.movedOrNull(index: Int, up: Boolean): List<T>? {
    val target = if (up) index - 1 else index + 1
    if (index !in indices || target !in indices) return null
    return toMutableList().also {
        it[index] = this[target]
        it[target] = this[index]
    }
}
