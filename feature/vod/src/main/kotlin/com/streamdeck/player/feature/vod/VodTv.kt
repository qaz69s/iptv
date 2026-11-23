package com.streamdeck.player.feature.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.streamdeck.player.core.data.XtreamImporter
import com.streamdeck.player.core.database.VodItemEntity
import com.streamdeck.player.core.database.VodProgressEntity
import com.streamdeck.player.core.designsystem.ActionPanel
import com.streamdeck.player.core.designsystem.EmptyState
import com.streamdeck.player.core.designsystem.ErrorState
import com.streamdeck.player.core.designsystem.FocusSurface
import com.streamdeck.player.core.designsystem.KeyHint
import com.streamdeck.player.core.designsystem.LoadingState
import com.streamdeck.player.core.designsystem.PanelAction
import com.streamdeck.player.core.designsystem.SdButton
import com.streamdeck.player.core.designsystem.SdButtonStyle
import com.streamdeck.player.core.designsystem.SdChip
import com.streamdeck.player.core.designsystem.SdProgress
import com.streamdeck.player.core.designsystem.SdSearchField
import com.streamdeck.player.core.designsystem.SdTheme
import com.streamdeck.player.core.designsystem.SectionHeader
import com.streamdeck.player.core.network.XtreamEpisode

// ------------------------------------------------------------------------------------------- home

/** TV: categories on the left, poster grid (with the “继续观看” row on top) on the right. */
@Composable
internal fun TvVodHome(vm: VodHomeViewModel, onOpen: (Long, String, Long) -> Unit) {
    val playlists by vm.xtreamPlaylists.collectAsStateWithLifecycle()
    val active by vm.activeId.collectAsStateWithLifecycle()
    val tab by vm.tab.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val category by vm.category.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val continueRow by vm.continueWatching.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    val p = SdTheme.palette

    val list = playlists
    when {
        list == null -> LoadingState()
        list.isEmpty() -> EmptyState(stringResource(R.string.vod_needs_xtream), title = stringResource(R.string.vod_empty_title),
            icon = Icons.Outlined.VideoLibrary)
        else -> Row(Modifier.fillMaxSize().padding(top = 36.dp, end = 40.dp)) {
            Column(Modifier.width(270.dp).fillMaxHeight().padding(start = 12.dp, end = 24.dp)) {
                Text(stringResource(R.string.vod_title), style = MaterialTheme.typography.headlineMedium, color = p.textPrimary)
                if (list.size > 1) {
                    FocusSurface(
                        onClick = { picking = true }, modifier = Modifier.padding(top = 10.dp), shape = RoundedCornerShape(50), container = p.card,
                        focusFill = true, contentDescription = stringResource(R.string.choose_playlist),
                    ) { focused ->
                        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(list.firstOrNull { it.id == active }?.name.orEmpty(), color = if (focused) p.onFocusFill else p.textSecondary,
                                style = MaterialTheme.typography.labelLarge, maxLines = 1)
                            Icon(Icons.Default.KeyboardArrowDown, null, tint = if (focused) p.onFocusFill else p.textSecondary, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                Column(Modifier.padding(top = 22.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    VodTab.entries.forEach { t -> TvSideItem(stringResource(t.labelRes()), t == tab, t.icon()) { vm.selectTab(t) } }
                }
                Text(
                    stringResource(R.string.categories), color = p.textTertiary, style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp, start = 8.dp),
                )
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 24.dp)) {
                    item(key = "all") { TvSideItem(stringResource(R.string.all), category == null, null) { vm.category.value = null } }
                    items(categories, key = { it.categoryId }) { c -> TvSideItem(c.name, category == c.categoryId, null) { vm.category.value = c.categoryId } }
                }
            }
            Column(Modifier.weight(1f)) {
                SdSearchField(query, { vm.query.value = it }, stringResource(R.string.search_vod), Modifier.padding(start = 6.dp, end = 6.dp, bottom = 12.dp))
                val pid = active ?: return@Column
                val showRail = tab == VodTab.MOVIES && category == null && query.isBlank() && continueRow.isNotEmpty()
                if (tab == VodTab.MOVIES) {
                    val items = vm.movies.collectAsLazyPagingItems()
                    val rail: (@Composable () -> Unit)? = if (showRail) {
                        { TvContinueRail(continueRow) { onOpen(pid, XtreamImporter.KIND_MOVIE, it) } }
                    } else {
                        null
                    }
                    TvPosterGrid(items, { it.id }, rail) { m ->
                        PosterCard(m.name, m.poster, m.rating, progress["movie:${m.streamId}"].fraction(), { onOpen(pid, XtreamImporter.KIND_MOVIE,
                            m.streamId) }, tv = true)
                    }
                } else {
                    val items = vm.series.collectAsLazyPagingItems()
                    TvPosterGrid(items, { it.id }, null) { s ->
                        PosterCard(s.name, s.cover, s.rating, null, { onOpen(pid, XtreamImporter.KIND_SERIES, s.seriesId) }, tv = true, subtitle = s.genre)
                    }
                }
            }
        }
    }
    if (picking) {
        ActionPanel(
            title = stringResource(R.string.choose_playlist),
            actions = playlists.orEmpty().map { pl -> PanelAction(pl.name, selected = pl.id == active) { vm.selectedPlaylist.value = pl.id } },
            onDismiss = { picking = false },
        )
    }
}

/** Left-column entry: accent bar + bold text when selected, high-contrast fill when focused. */
@Composable
private fun TvSideItem(label: String, selected: Boolean, icon: ImageVector?, onClick: () -> Unit) {
    val p = SdTheme.palette
    FocusSurface(
        onClick = onClick, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
        container = if (selected) p.accent.copy(alpha = 0.14f) else Color.Transparent, focusFill = true, contentDescription = label,
    ) { focused ->
        val fg = when {
            focused -> p.onFocusFill
            selected -> p.accent
            else -> p.textSecondary
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(3.dp).height(18.dp).background(if (selected && !focused) p.accent else Color.Transparent, RoundedCornerShape(2.dp)))
            if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.padding(start = 10.dp).size(20.dp))
            Text(
                label, color = fg, style = MaterialTheme.typography.titleSmall, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}

@Composable
private fun <T : Any> TvPosterGrid(items: LazyPagingItems<T>, key: (T) -> Any, header: (@Composable () -> Unit)?, content: @Composable (T) -> Unit) {
    val refresh = items.loadState.refresh
    when {
        refresh is LoadState.Loading && items.itemCount == 0 -> LoadingState()
        refresh is LoadState.Error -> ErrorState(refresh.error.message ?: refresh.error.javaClass.simpleName, items::retry)
        items.itemCount == 0 -> EmptyState(stringResource(R.string.vod_empty_hint), title = stringResource(R.string.vod_empty), icon = Icons.Outlined.Movie)
        else -> LazyVerticalGrid(
            GridCells.Adaptive(168.dp),
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 6.dp, end = 6.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (header != null) item(key = "header", span = { GridItemSpan(maxLineSpan) }) { header() }
            items(items.itemCount, key = items.itemKey(key)) { i -> items[i]?.let { content(it) } }
        }
    }
}

@Composable
private fun TvContinueRail(list: List<Pair<VodItemEntity, VodProgressEntity>>, onOpen: (Long) -> Unit) {
    val p = SdTheme.palette
    Column {
        SectionHeader(stringResource(R.string.continue_watching), count = list.size)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(vertical = 14.dp)) {
            items(list, key = { it.first.id }) { (m, pr) ->
                FocusSurface(
                    onClick = { onOpen(m.streamId) }, modifier = Modifier.width(320.dp), shape = RoundedCornerShape(18.dp), container = p.card,
                    focusScale = 1.05f, contentDescription = m.name,
                ) {
                    Column {
                        Artwork(m.poster, m.name, Modifier.fillMaxWidth().aspectRatio(16f / 9f), corner = 0.dp)
                        Column(Modifier.padding(14.dp)) {
                            Text(m.name, color = p.textPrimary, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(stringResource(R.string.resume_at, formatPosition(pr.positionMs)), color = p.textTertiary,
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
                            SdProgress(pr.fraction() ?: 0f)
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------- detail

/** TV detail: full-bleed backdrop, big title, focused primary button; series add season chips and an episode row. */
@Composable
internal fun TvVodDetail(state: DetailState, progress: Map<String, VodProgressEntity>, season: Int?, vm: VodDetailViewModel, onPlay: (String) -> Unit) {
    val p = SdTheme.palette
    val primary = remember { FocusRequester() }
    val image = when (state) {
        is DetailState.Movie -> state.info?.cover ?: state.item?.poster
        is DetailState.Series -> state.info.cover ?: state.entity?.cover
        else -> null
    }
    Box(Modifier.fillMaxSize().background(p.background)) {
        Backdrop(image, Modifier.fillMaxSize(), horizontalFade = true)
        when (state) {
            DetailState.Loading -> LoadingState()
            is DetailState.Failed -> ErrorState(state.reason, vm::load, title = stringResource(R.string.detail_failed))
            is DetailState.Movie -> {
                val pr = progress[vm.movieProgressKey()]
                val title = state.item?.name.orEmpty()
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 72.dp, top = 56.dp, end = 56.dp, bottom = 40.dp)) {
                    TvInfoBlock(
                        image, title, metaParts(state.info?.rating ?: state.item?.rating, state.info?.releaseDate, state.info?.durationSec, state.info?.genre),
                        state.info?.plot,
                    ) {
                        SdButton(
                            if (pr.resumable()) stringResource(R.string.resume_at, formatPosition(pr?.positionMs ?: 0L)) else stringResource(R.string.play),
                            { onPlay(state.key) }, Modifier.focusRequester(primary), icon = Icons.Default.PlayArrow, large = true,
                        )
                        TvWatchedButton(pr?.watched == true) { vm.toggleWatched(state.key, pr?.watched == true) }
                    }
                    pr.fraction()?.let { SdProgress(it, Modifier.padding(start = 232.dp, top = 18.dp).widthIn(max = 420.dp)) }
                }
            }
            is DetailState.Series -> {
                val seasons = state.info.episodes.map { it.season }.distinct()
                val current = season ?: seasons.firstOrNull()
                val episodes = state.info.episodes.filter { it.season == current }
                val title = state.info.name ?: state.entity?.name.orEmpty()
                val resumeTarget = episodes.firstOrNull { progress[vm.progressKey(it)].resumable() }
                    ?: episodes.firstOrNull { progress[vm.progressKey(it)]?.watched != true }
                    ?: episodes.firstOrNull()
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 56.dp, bottom = 32.dp)) {
                    Column(Modifier.padding(start = 72.dp, end = 56.dp)) {
                        TvInfoBlock(
                            image, title,
                            metaParts(state.entity?.rating, state.entity?.releaseDate, null, state.entity?.genre,
                                extra = if (seasons.isNotEmpty()) stringResource(R.string.seasons_count, seasons.size) else null),
                            state.info.plot ?: state.entity?.plot,
                        ) {
                            if (resumeTarget != null) {
                                val pr = progress[vm.progressKey(resumeTarget)]
                                SdButton(
                                    if (pr.resumable()) stringResource(R.string.resume_episode, resumeTarget.season,
                                        resumeTarget.episode) else stringResource(R.string.play_episode, resumeTarget.season, resumeTarget.episode),
                                    { onPlay(vm.episodeKey(resumeTarget)) }, Modifier.focusRequester(primary), icon = Icons.Default.PlayArrow, large = true,
                                )
                            }
                        }
                    }
                    if (seasons.isEmpty()) {
                        Text(stringResource(R.string.no_episodes), color = p.textSecondary, style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 72.dp, top = 32.dp))
                    } else {
                        LazyRow(contentPadding = PaddingValues(start = 72.dp, end = 56.dp, top = 28.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(seasons) { n -> SdChip(stringResource(R.string.season_n, n), selected = n == current, onClick = { vm.season.value = n }) }
                        }
                        LazyRow(contentPadding = PaddingValues(start = 72.dp, end = 56.dp, top = 14.dp, bottom = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                            items(episodes, key = { it.id }) { e ->
                                val key = vm.episodeKey(e)
                                val ep = progress[vm.progressKey(e)]
                                TvEpisodeCard(e, ep, onPlay = { onPlay(key) }, onToggle = { vm.toggleWatched(key, ep?.watched == true) })
                            }
                        }
                        Row(Modifier.padding(start = 72.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            KeyHint(stringResource(R.string.key_ok), stringResource(R.string.play))
                            KeyHint(stringResource(R.string.key_hold_ok), stringResource(R.string.hint_toggle_watched))
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(state) { if (state is DetailState.Movie || state is DetailState.Series) runCatching { primary.requestFocus() } }
}

@Composable
private fun TvInfoBlock(image: String?, title: String, meta: List<String>, plot: String?, buttons: @Composable () -> Unit) {
    val p = SdTheme.palette
    Row(verticalAlignment = Alignment.Top) {
        Artwork(image, title, Modifier.width(200.dp).aspectRatio(2f / 3f), corner = 18.dp)
        Column(Modifier.padding(start = 32.dp).widthIn(max = 720.dp)) {
            Text(title, color = p.textPrimary, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, maxLines = 2,
                overflow = TextOverflow.Ellipsis)
            MetaLine(meta, Modifier.padding(top = 12.dp), large = true)
            if (!plot.isNullOrBlank()) {
                Text(plot, color = p.textSecondary, style = MaterialTheme.typography.bodyLarge, maxLines = 4, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 16.dp))
            }
            Row(Modifier.padding(top = 26.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) { buttons() }
        }
    }
}

@Composable
private fun TvWatchedButton(watched: Boolean, onClick: () -> Unit) {
    SdButton(
        stringResource(if (watched) R.string.mark_unwatched else R.string.mark_watched), onClick,
        icon = if (watched) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle, style = SdButtonStyle.Secondary, large = true,
    )
}

@Composable
private fun TvEpisodeCard(e: XtreamEpisode, pr: VodProgressEntity?, onPlay: () -> Unit, onToggle: () -> Unit) {
    val p = SdTheme.palette
    val watched = pr?.watched == true
    FocusSurface(
        onClick = onPlay, onLongClick = onToggle, modifier = Modifier.width(300.dp), shape = RoundedCornerShape(18.dp), container = p.card,
        focusScale = 1.05f, contentDescription = stringResource(R.string.episode_title, e.episode, e.title),
    ) {
        Column {
            Artwork(e.image, e.title, Modifier.fillMaxWidth().aspectRatio(16f / 9f), corner = 0.dp) {
                if (watched) WatchedMark(Modifier.align(Alignment.TopEnd).padding(8.dp).background(p.scrim, RoundedCornerShape(50)).padding(2.dp))
                pr.fraction()?.let { f -> SdProgress(f, Modifier.align(Alignment.BottomCenter).padding(8.dp)) }
            }
            Column(Modifier.padding(14.dp)) {
                Text(
                    stringResource(R.string.episode_title, e.episode, e.title), color = if (watched) p.textSecondary else p.textPrimary,
                    style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                e.durationSec?.takeIf { it > 0 }?.let {
                    Text(stringResource(R.string.minutes_short, it / 60), color = p.textTertiary, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}
