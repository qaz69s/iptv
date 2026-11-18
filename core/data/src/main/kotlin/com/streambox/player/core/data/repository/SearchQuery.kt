package com.streambox.player.core.data.repository

/**
 * Turns what the user typed into the search arguments of the channel DAO.
 * - [ftsMatch]: one quoted prefix term per word (`"cctv"* "news"*`, all must match), built only from
 *   letters and digits so no FTS operator or quote typed by the user can break the expression; null when
 *   the text has no word at all.
 * - [likePattern]: `%text%` with `%`, `_` and the escape character `\` escaped (contains, ASCII case
 *   insensitive in SQLite).
 * - [substring]: the text has non-ASCII letters (e.g. Chinese). FTS4's simple tokenizer keeps a run of
 *   CJK characters as one word, so "新闻" would not find "凤凰新闻台" by prefix; names are then also
 *   matched with LIKE.
 */
internal data class SearchQuery(val ftsMatch: String?, val likePattern: String, val substring: Boolean) {
    companion object {
        fun parse(text: String): SearchQuery? {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return null
            val words = trimmed.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
            val match = words.takeIf { it.isNotEmpty() }?.joinToString(" ") { "\"$it\"*" }
            val escaped = buildString {
                trimmed.forEach { char ->
                    if (char == '%' || char == '_' || char == '\\') append('\\')
                    append(char)
                }
            }
            return SearchQuery(
                ftsMatch = match,
                likePattern = "%$escaped%",
                substring = trimmed.any { it.code > ASCII_MAX },
            )
        }

        private const val ASCII_MAX = 127
    }
}
