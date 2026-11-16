package com.streambox.player.core.model

enum class PlaylistType { M3U_URL, M3U_FILE, XTREAM }

/** How a playlist refreshes itself. Scheduling (WorkManager) arrives in P7; P1 only stores the choice. */
enum class UpdateMode { MANUAL, ON_START, INTERVAL }

/**
 * A user-added source of channels. Credentials are never kept in plain text (see passwordEncrypted
 * in the database layer); [source] is a URL for M3U_URL / XTREAM and a SAF content URI for M3U_FILE.
 */
data class Playlist(
    val id: Long,
    val name: String,
    val type: PlaylistType,
    val source: String,
    val userAgent: String? = null,
    val referrer: String? = null,
    val epgUrl: String? = null,
    val updateMode: UpdateMode = UpdateMode.MANUAL,
    val updateIntervalHours: Int = 0,
    val lastUpdated: Long? = null,
    val lastError: String? = null,
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
    /** Per-playlist opt-in for plain HTTP (playlist download, redirects and streams). */
    val allowCleartext: Boolean = false,
    val channelCount: Int = 0,
    val groupCount: Int = 0,
)

/** What the user filled in on the "Add playlist" screen. */
data class PlaylistDraft(
    val name: String,
    val type: PlaylistType,
    val source: String,
    val userAgent: String? = null,
    val allowCleartext: Boolean = false,
)
