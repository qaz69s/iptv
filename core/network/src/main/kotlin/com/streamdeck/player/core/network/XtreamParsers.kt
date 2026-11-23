package com.streamdeck.player.core.network

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

data class XtreamAuth(val authorized: Boolean, val status: String?, val expiresAtSec: Long?, val maxConnections: Int?, val serverTimezone: String?)
data class XtreamCategory(val id: String, val name: String)
data class XtreamLiveStream(
    val streamId: Long,
    val name: String,
    val number: Int?,
    val icon: String?,
    val epgChannelId: String?,
    val categoryId: String?,
    val tvArchive: Boolean,
    val archiveDays: Int?,
)
data class XtreamVod(val streamId: Long, val name: String, val icon: String?, val rating: Float?, val categoryId: String?, val extension: String, val added: Long?)
data class XtreamSeries(val seriesId: Long, val name: String, val cover: String?, val plot: String?, val rating: Float?, val genre: String?, val releaseDate: String?, val categoryId: String?)
data class XtreamEpisode(val id: Long, val season: Int, val episode: Int, val title: String, val extension: String, val plot: String?, val durationSec: Int?, val image: String?)
data class XtreamSeriesInfo(val name: String?, val plot: String?, val cover: String?, val episodes: List<XtreamEpisode>)
data class XtreamVodInfo(val plot: String?, val durationSec: Int?, val rating: Float?, val genre: String?, val cover: String?, val releaseDate: String?)

/**
 * Tolerant decoders: Xtream panels mix numbers and strings, use "" or null for missing values,
 * and sometimes return objects where arrays are expected.
 */
object XtreamParsers {
    private fun JsonElement?.obj(): JsonObject? = this as? JsonObject
    private fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.trim()?.ifEmpty { null }
    private fun JsonObject.long(k: String): Long? = str(k)?.toDoubleOrNull()?.toLong()
    private fun JsonObject.int(k: String): Int? = long(k)?.toInt()
    private fun JsonObject.float(k: String): Float? = str(k)?.toFloatOrNull()
    private fun JsonObject.bool(k: String): Boolean = when (str(k)?.lowercase()) { "1", "true" -> true; else -> false }

    fun auth(e: JsonElement): XtreamAuth {
        val u = e.obj()?.get("user_info").obj()
        val s = e.obj()?.get("server_info").obj()
        return XtreamAuth(
            authorized = u != null && u.bool("auth") && (u.str("status")?.equals("Active", true) != false),
            status = u?.str("status"),
            expiresAtSec = u?.long("exp_date"),
            maxConnections = u?.int("max_connections"),
            serverTimezone = s?.str("timezone"),
        )
    }

    fun category(e: JsonElement): XtreamCategory? {
        val o = e.obj() ?: return null
        val id = o.str("category_id") ?: return null
        return XtreamCategory(id, o.str("category_name") ?: id)
    }

    fun live(e: JsonElement): XtreamLiveStream? {
        val o = e.obj() ?: return null
        val id = o.long("stream_id") ?: return null
        return XtreamLiveStream(
            streamId = id,
            name = o.str("name") ?: id.toString(),
            number = o.int("num"),
            icon = o.str("stream_icon"),
            epgChannelId = o.str("epg_channel_id"),
            categoryId = o.str("category_id"),
            tvArchive = o.bool("tv_archive"),
            archiveDays = o.int("tv_archive_duration"),
        )
    }

    fun vod(e: JsonElement): XtreamVod? {
        val o = e.obj() ?: return null
        val id = o.long("stream_id") ?: return null
        return XtreamVod(id, o.str("name") ?: id.toString(), o.str("stream_icon"), o.float("rating"), o.str("category_id"), o.str("container_extension") ?: "mp4", o.long("added"))
    }

    fun series(e: JsonElement): XtreamSeries? {
        val o = e.obj() ?: return null
        val id = o.long("series_id") ?: return null
        return XtreamSeries(id, o.str("name") ?: id.toString(), o.str("cover"), o.str("plot"), o.float("rating"), o.str("genre"), o.str("releaseDate") ?: o.str("release_date"), o.str("category_id"))
    }

    fun seriesInfo(e: JsonElement): XtreamSeriesInfo {
        val root = e.obj()
        val info = root?.get("info").obj()
        val episodesNode = root?.get("episodes")
        val seasons: List<Pair<Int?, JsonElement>> = when (episodesNode) {
            is JsonObject -> episodesNode.entries.map { it.key.toIntOrNull() to it.value }
            is JsonArray -> episodesNode.map { null to it }
            else -> emptyList()
        }
        val episodes = seasons.flatMap { (seasonKey, list) ->
            val items = when (list) {
                is JsonArray -> list.toList()
                is JsonObject -> listOf(list)
                else -> emptyList()
            }
            items.mapNotNull { item -> episode(item, seasonKey) }
        }.sortedWith(compareBy({ it.season }, { it.episode }))
        return XtreamSeriesInfo(info?.str("name"), info?.str("plot"), info?.str("cover"), episodes)
    }

    private fun episode(e: JsonElement, seasonKey: Int?): XtreamEpisode? {
        val o = e.obj() ?: return null
        val id = o.long("id") ?: return null
        val info = o["info"].obj()
        return XtreamEpisode(
            id = id,
            season = o.int("season") ?: seasonKey ?: 1,
            episode = o.int("episode_num") ?: 0,
            title = o.str("title") ?: "",
            extension = o.str("container_extension") ?: "mp4",
            plot = info?.str("plot"),
            durationSec = info?.int("duration_secs"),
            image = info?.str("movie_image"),
        )
    }

    fun vodInfo(e: JsonElement): XtreamVodInfo {
        val info = e.obj()?.get("info").obj()
        return XtreamVodInfo(
            plot = info?.str("plot") ?: info?.str("description"),
            durationSec = info?.int("duration_secs"),
            rating = info?.float("rating"),
            genre = info?.str("genre"),
            cover = info?.str("movie_image") ?: info?.str("cover_big"),
            releaseDate = info?.str("releasedate") ?: info?.str("release_date"),
        )
    }
}
