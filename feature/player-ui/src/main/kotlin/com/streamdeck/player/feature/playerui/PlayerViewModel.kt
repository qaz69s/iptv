package com.streamdeck.player.feature.playerui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streamdeck.player.core.data.ChannelRepository
import com.streamdeck.player.core.data.EpgRepository
import com.streamdeck.player.core.data.LibraryRepository
import com.streamdeck.player.core.data.ParentalRepository
import com.streamdeck.player.core.data.ResolvedStream
import com.streamdeck.player.core.data.StreamResolver
import com.streamdeck.player.core.data.VodRepository
import com.streamdeck.player.core.data.Program
import com.streamdeck.player.core.database.ChannelEntity
import com.streamdeck.player.core.database.ChannelListItem
import com.streamdeck.player.core.datastore.AppSettings
import com.streamdeck.player.core.datastore.AspectMode
import com.streamdeck.player.core.datastore.SettingsRepository
import com.streamdeck.player.core.datastore.SubtitleStyle
import com.streamdeck.player.core.player.ExternalSubtitle
import com.streamdeck.player.core.player.PipCoordinator
import com.streamdeck.player.core.player.PlaybackError
import com.streamdeck.player.core.player.PlayerController
import com.streamdeck.player.core.player.SleepTimer
import com.streamdeck.player.core.player.StreamRequest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

object PlayerArgs {
    const val CATCHUP_START = "catchupStart"
    const val CATCHUP_END = "catchupEnd"
    const val VOD_KEY = "vodKey"
}

/** Active catch-up window in real UTC ms; null = live. */
data class CatchupWindow(val start: Long, val end: Long)

@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.DelicateCoroutinesApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val channels: ChannelRepository,
    private val resolver: StreamResolver,
    private val library: LibraryRepository,
    private val epg: EpgRepository,
    private val vodRepo: VodRepository,
    private val settingsRepo: SettingsRepository,
    private val parental: ParentalRepository,
    val controller: PlayerController,
    val sleep: SleepTimer,
    val pip: PipCoordinator,
) : ViewModel() {
    private val _channel = MutableStateFlow<ChannelEntity?>(null)
    val channel: StateFlow<ChannelEntity?> = _channel.asStateFlow()

    val settings: StateFlow<AppSettings> = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    /** Per-session aspect override; null = use the default from settings. */
    private val _aspect = MutableStateFlow<AspectMode?>(null)
    val aspect: StateFlow<AspectMode?> = _aspect.asStateFlow()

    private val _catchup = MutableStateFlow<CatchupWindow?>(null)
    val catchup: StateFlow<CatchupWindow?> = _catchup.asStateFlow()

    val isFavorite: StateFlow<Boolean> = _channel.flatMapLatest { c -> if (c == null) flowOf(false) else library.isFavorite(c) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Now/next for the overlay (spec §4.3 覆盖层). */
    val program: StateFlow<OverlayProgram?> = _channel.flatMapLatest { c ->
        if (c == null) flowOf(null) else epg.nowNext(c.playlistId, c.uniqueKey).map { nn ->
            nn.now?.let { OverlayProgram(it.title, it.start, it.end, nn.next?.title, nn.next?.start, it.category) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Today's programmes of the channel (phone “节目单” tab), real time, from local midnight to next morning. */
    val schedule: StateFlow<List<Program>> = _channel.flatMapLatest { c ->
        if (c == null) flowOf(emptyList()) else {
            val zone = ZoneId.systemDefault()
            val from = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
            epg.programs(c.playlistId, c.uniqueKey, from, from + SCHEDULE_SPAN_MS)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Channels of the current group with now/next (TV zapping list, phone “同组频道”). Hidden while locked. */
    val groupItems: StateFlow<List<ChannelListItem>> = _channel.flatMapLatest { c ->
        if (c == null) flowOf(emptyList()) else channels.groupItems(c.playlistId, c.groupId).map { list ->
            if (parental.isUnlocked()) list else list.filter { !it.channel.locked && !it.groupLocked }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Catch-up is possible for this finished programme (archive window of the channel). */
    fun canCatchup(p: Program, now: Long = System.currentTimeMillis()): Boolean {
        val c = _channel.value ?: return false
        return resolver.supportsCatchup(c) && p.isPast(now) && p.start >= resolver.archiveStart(c, now)
    }

    fun playProgram(p: Program) {
        val c = _channel.value ?: return
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            when {
                p.isLive(now) -> if (_catchup.value != null) playLive(c)
                canCatchup(p, now) -> playCatchup(c, p.start, p.end)
                else -> Unit
            }
        }
    }

    /** “从头看”: restarts the airing programme through catch-up when the channel supports it. */
    fun startOver() {
        val c = _channel.value ?: return
        val prog = program.value ?: return
        if (!resolver.supportsCatchup(c)) return
        viewModelScope.launch { playCatchup(c, prog.startMs, System.currentTimeMillis()) }
    }

    val canStartOver: Boolean get() = supportsCatchup && program.value != null

    fun playChannel(id: Long) {
        if (id == _channel.value?.id && _catchup.value == null) return
        viewModelScope.launch { channels.get(id)?.let { playLive(it) } }
    }

    private val _numberInput = MutableStateFlow("")
    val numberInput: StateFlow<String> = _numberInput.asStateFlow()
    private var numberJob: Job? = null

    val supportsCatchup: Boolean get() = _channel.value?.let(resolver::supportsCatchup) ?: false

    private suspend fun playLive(c: ChannelEntity) {
        _channel.value = c
        _catchup.value = null
        start(c.name, c.logoUrl, resolver.resolve(c), live = true, startPositionMs = 0)
        settingsRepo.setLastChannel(c.playlistId, c.uniqueKey)
        library.recordWatch(c)
    }

    private suspend fun playCatchup(c: ChannelEntity, startMs: Long, endMs: Long) {
        _channel.value = c
        val stream = runCatching { resolver.resolveCatchup(c, startMs, endMs) }.getOrElse {
            controller.reportError(PlaybackError.Other(it.message ?: "catch-up unavailable"))
            return
        }
        _catchup.value = CatchupWindow(startMs, endMs)
        start(c.name, c.logoUrl, stream, live = false, startPositionMs = 0)
    }

    private fun start(title: String, logo: String?, s: ResolvedStream, live: Boolean, startPositionMs: Long) {
        controller.play(
            StreamRequest(
                url = s.url, title = title, artworkUrl = logo, userAgent = s.userAgent, referrer = s.referrer,
                headers = s.headers, isLive = live, startPositionMs = startPositionMs, allowCleartext = s.allowCleartext,
            ),
        )
    }

    /**
     * Time-shift (spec §4.5): moves the playback point by [deltaMs] relative to the current absolute position.
     * Moving past “now” returns to live; the start is clamped to the archive window.
     */
    fun timeshift(deltaMs: Long) {
        val c = _channel.value ?: return
        if (!resolver.supportsCatchup(c)) return
        val now = System.currentTimeMillis()
        val position = _catchup.value?.let { w -> w.start + (controller.playerFlow.value?.currentPosition ?: 0L) } ?: now
        val target = (position + deltaMs).coerceAtLeast(resolver.archiveStart(c, now))
        viewModelScope.launch {
            if (target >= now - LIVE_EDGE_MS) playLive(c) else playCatchup(c, target, now)
        }
    }

    fun goLive() { _channel.value?.let { c -> viewModelScope.launch { playLive(c) } } }

    fun next() { viewModelScope.launch { _channel.value?.let { channels.next(it, parental.isUnlocked()) }?.let { playLive(it) } } }
    fun previous() { viewModelScope.launch { _channel.value?.let { channels.previous(it, parental.isUnlocked()) }?.let { playLive(it) } } }

    fun toggleFavorite() {
        val c = _channel.value ?: return
        viewModelScope.launch { library.setFavorite(c, !isFavorite.value) }
    }

    /** Remote-control digit entry: commits automatically after [NUMBER_TIMEOUT_MS] of inactivity (spec §4.2). */
    fun digit(d: Int) {
        _numberInput.value = (_numberInput.value + d).takeLast(MAX_DIGITS)
        numberJob?.cancel()
        numberJob = viewModelScope.launch {
            delay(NUMBER_TIMEOUT_MS)
            commitNumber()
        }
    }

    fun commitNumber() {
        numberJob?.cancel()
        val n = _numberInput.value.toIntOrNull()
        _numberInput.value = ""
        val pid = _channel.value?.playlistId ?: return
        if (n != null) viewModelScope.launch { channels.byNumber(pid, n, parental.isUnlocked())?.let { playLive(it) } }
    }

    fun setAspect(mode: AspectMode) { _aspect.value = mode }

    fun setSubtitleStyle(style: SubtitleStyle) { viewModelScope.launch { settingsRepo.update { it.copy(subtitle = style) } } }

    fun addSubtitle(uri: String, name: String) {
        controller.addExternalSubtitle(ExternalSubtitle(uri, SubtitleMime.forName(name), name))
    }

    private val _vodTitle = MutableStateFlow<String?>(null)
    /** Title shown instead of a channel for movies / episodes. */
    val vodTitle: StateFlow<String?> = _vodTitle.asStateFlow()
    private var vodKey: String? = null
    private var progressJob: Job? = null

    private suspend fun playVod(key: String, resumeAllowed: Boolean = true) {
        val item = vodRepo.resolve(key) ?: return
        vodKey = key
        _vodTitle.value = item.title
        val start = if (resumeAllowed) vodRepo.resumePosition(key) else 0L
        controller.play(
            StreamRequest(
                url = item.stream.url, title = item.title, artworkUrl = item.poster, userAgent = item.stream.userAgent,
                headers = item.stream.headers, isLive = false, startPositionMs = start, allowCleartext = item.stream.allowCleartext,
            ),
        )
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                delay(PROGRESS_SAVE_MS)
                saveVodProgress()
            }
        }
    }

    private suspend fun saveVodProgress() {
        val key = vodKey ?: return
        val p = controller.playerFlow.value ?: return
        if (p.duration > 0) vodRepo.saveProgress(key, p.currentPosition, p.duration)
    }

    /** Called when playback ends: marks watched and, if enabled, starts the next episode (spec §4.6). */
    fun onEnded() {
        val key = vodKey ?: return
        viewModelScope.launch {
            vodRepo.markWatched(key)
            if (settings.value.autoplayNext) vodRepo.nextEpisodeKey(key)?.let { playVod(it, resumeAllowed = false) }
        }
    }

    init {
        run {
        val vodKey: String? = savedState[PlayerArgs.VOD_KEY]
        if (vodKey != null) {
            viewModelScope.launch { playVod(vodKey) }
            return@run
        }
            val id: Long = checkNotNull(savedState[ARG_CHANNEL_ID])
            val start: Long? = savedState[PlayerArgs.CATCHUP_START]
            val end: Long? = savedState[PlayerArgs.CATCHUP_END]
            viewModelScope.launch {
                val c = channels.get(id) ?: return@launch
                if (start != null && end != null) playCatchup(c, start, end) else playLive(c)
            }
        }
    }

    override fun onCleared() {
        progressJob?.cancel()
        val key = vodKey
        val p = controller.playerFlow.value
        if (key != null && p != null && p.duration > 0) {
            val pos = p.currentPosition
            val dur = p.duration
            kotlinx.coroutines.GlobalScope.launch { vodRepo.saveProgress(key, pos, dur) }
        }
        controller.stop()
        sleep.cancel()
    }

    companion object {
        const val ARG_CHANNEL_ID = "channelId"
        const val NUMBER_TIMEOUT_MS = 2_000L
        private const val MAX_DIGITS = 5
        private const val LIVE_EDGE_MS = 15_000L
        private const val PROGRESS_SAVE_MS = 10_000L
        private const val SCHEDULE_SPAN_MS = 30 * 3_600_000L
    }
}

internal object SubtitleMime {
    fun forName(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
        "vtt" -> androidx.media3.common.MimeTypes.TEXT_VTT
        "ass", "ssa" -> androidx.media3.common.MimeTypes.TEXT_SSA
        "ttml", "xml", "dfxp" -> androidx.media3.common.MimeTypes.APPLICATION_TTML
        else -> androidx.media3.common.MimeTypes.APPLICATION_SUBRIP
    }
}
