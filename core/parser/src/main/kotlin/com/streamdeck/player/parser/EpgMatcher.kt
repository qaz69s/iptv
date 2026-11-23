package com.streamdeck.player.parser

import java.text.Normalizer

/** Minimal view of a playlist channel for EPG matching. */
data class MatchChannel(val key: String, val tvgId: String?, val tvgName: String?, val name: String)

/**
 * Channel ↔ EPG matching (spec §4.4): exact tvg-id, then tvg-name against display names,
 * then normalised name equality, then a conservative fuzzy match (edit distance ≤ 1 for names ≥ 6 chars).
 */
class EpgMatcher(epgChannels: List<XmltvChannel>) {
    private val byId = HashMap<String, String>()
    private val byIdLower = HashMap<String, String>()
    private val byDisplay = HashMap<String, String>()
    private val byNormalized = HashMap<String, String>()
    private val buckets = HashMap<String, MutableList<Pair<String, String>>>()

    init {
        epgChannels.forEach { c ->
            byId.putIfAbsent(c.id, c.id)
            byIdLower.putIfAbsent(c.id.lowercase(), c.id)
            c.displayNames.forEach { n ->
                byDisplay.putIfAbsent(n.trim().lowercase(), c.id)
                val norm = normalize(n)
                if (norm.isNotEmpty()) {
                    byNormalized.putIfAbsent(norm, c.id)
                    buckets.getOrPut(norm.take(BUCKET)) { mutableListOf() } += norm to c.id
                }
            }
            val idNorm = normalize(c.id)
            if (idNorm.isNotEmpty()) byNormalized.putIfAbsent(idNorm, c.id)
        }
    }

    fun match(c: MatchChannel): String? {
        c.tvgId?.let { id -> byId[id] ?: byIdLower[id.lowercase()] }?.let { return it }
        c.tvgName?.let { byDisplay[it.trim().lowercase()] }?.let { return it }
        byDisplay[c.name.trim().lowercase()]?.let { return it }
        val candidates = listOfNotNull(c.tvgName, c.name).map(::normalize).filter { it.isNotEmpty() }
        candidates.forEach { n -> byNormalized[n]?.let { return it } }
        candidates.filter { it.length >= FUZZY_MIN }.forEach { n ->
            buckets[n.take(BUCKET)]?.firstOrNull { (other, _) -> withinOneEdit(n, other) }?.let { return it.second }
        }
        return null
    }

    companion object {
        private const val BUCKET = 3
        private const val FUZZY_MIN = 6
        private val countryPrefix = Regex("""^[a-z]{2,3}\s*[:|]\s*""")
        private val qualitySuffix = Regex("""\b(uhd|fhd|hd|sd|4k|hevc|h265|1080p|720p)\b""")
        private val nonAlnum = Regex("""[^\p{L}\p{N}+]""")
        private val diacritics = Regex("""\p{M}+""")

        /** “UK: BBC One HD” → “bbcone”; keeps “+1” because it is a different channel. */
        fun normalize(name: String): String {
            var s = Normalizer.normalize(name.lowercase(), Normalizer.Form.NFD).replace(diacritics, "")
            s = s.replace(countryPrefix, "").replace(qualitySuffix, " ")
            return s.replace(nonAlnum, "")
        }

        fun withinOneEdit(a: String, b: String): Boolean {
            if (a == b) return true
            if (kotlin.math.abs(a.length - b.length) > 1) return false
            var i = 0
            var j = 0
            var edits = 0
            while (i < a.length && j < b.length) {
                if (a[i] == b[j]) { i++; j++; continue }
                if (++edits > 1) return false
                when {
                    a.length > b.length -> i++
                    a.length < b.length -> j++
                    else -> { i++; j++ }
                }
            }
            return edits + (a.length - i) + (b.length - j) <= 1
        }
    }
}
