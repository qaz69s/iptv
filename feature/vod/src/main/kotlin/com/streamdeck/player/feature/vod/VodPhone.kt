package com.streamdeck.player.feature.vod

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.streamdeck.player.core.database.VodProgressEntity
import com.streamdeck.player.core.designsystem.ActionPanel
import com.streamdeck.player.core.designsystem.EmptyState
import com.streamdeck.player.core.designsystem.ErrorState
import com.streamdeck.player.core.designsystem.FocusSurface
import com.streamdeck.player.core.designsystem.LoadingState
import com.streamdeck.player.core.designsystem.PanelAction
import com.streamdeck.player.core.designsystem.SdButton
import com.streamdeck.player.core.designsystem.SdButtonStyle
import com.streamdeck.player.core.designsystem.SdChip
import com.streamdeck.player.core.designsystem.SdIconButton
import com.streamdeck.player.core.designsystem.SdProgress
import com.streamdeck.player.core.designsystem.SdSearchField
import com.streamdeck.player.core.designsystem.SdTheme
import com.streamdeck.player.core.designsystem.SectionHeader
import com.streamdeck.player.core.network.XtreamEpisode

// ------------------------------------------------------------------------------------------- home

@Composable
internal fun PhoneVodHome(vm: VodHomeViewModel, onOpen: (Long, String, Long) -> Unit) {
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

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.vod_title), style = MaterialTheme.typography.headlineMedium, color = p.textPrimary, modifier = Modifier.weight(1f))
            val list = playlists.orEmpty()
            if (list.size > 1) {
                val name = list.firstOrNull { it.id == active }?.name.orEmpty()
                FocusSurface(onClick = { picking = true }, shape = RoundedCornerShape(50), container = p.card, border = p.stroke,
                    contentDescription = stringResource(R.string.choose_playlist)) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(name, color = p.textPrimary, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                        Icon(Icons.Default.KeyboardArrowDown, null, tint = p.textSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        val list = playlists
        when {
            list == null -> LoadingState()
            list.isEmpty() -> EmptyState(stringResource(R.string.vod_needs_xtream), title = stringResource(R.string.vod_empty_title),
                icon = Icons.Outlined.VideoLibrary)
            else -> {
                SdSearchField(query, { vm.query.value = it }, stringResource(R.string.search_vod), Modifier.padding(horizontal = 20.dp))
                Row(Modifier.padding(start = 20.dp, top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    VodTab.entries.forEach { t ->
                        SdChip(stringResource(t.labelRes()), selected = t == tab, onClick = { vm.selectTab(t) }, leading = t.icon())
                    }
                }
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item(key = "all") { SdChip(stringResource(R.string.all), selected = category == null, onClick = { vm.category.value = null },
                        compact = true) }
                    items(categories, key = { it.categoryId }) { c ->
                        SdChip(c.name, selected = category == c.categoryId, onClick = { vm.category.value = c.categoryId }, compact = true)
                    }
                }
                val pid = active ?: return@Column
                val showRail = tab == VodTab.MOVIES && category == null && query.isBlank() && continueRow.isNotEmpty()
                if (tab == VodTab.MOVIES) {
                    val items = vm.movies.collectAsLazyPagingItems()
                    val rail: (@Composable () -> Unit)? = if (showRail) {
                        { ContinueRail(continueRow) { onOpen(pid, XtreamImporter.KIND_MOVIE, it) } }
                    } else {
                        null
                    }
                    PhonePosterGrid(items, { it.id }, header = rail) { m ->
                        PosterCard(m.name, m.poster, m.rating, progress["movie:${m.streamId}"].fraction(), { onOpen(pid, XtreamImporter.KIND_MOVIE,
                            m.streamId) })
                    }
                } else {
                    val items = vm.series.collectAsLazyPagingItems()
                    PhonePosterGrid(items, { it.id }, header = null) { s ->
                        PosterCard(s.name, s.cover, s.rating, null, { onOpen(pid, XtreamImporter.KIND_SERIES, s.seriesId) }, subtitle = s.genre)
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

@Composable
private fun <T : Any> PhonePosterGrid(
    items: LazyPagingItems<T>,
    key: (T) -> Any,
    header: (@Composable () -> Unit)?,
    content: @Composable (T) -> Unit,
) {
    val refresh = items.loadState.refresh
    when {
        refresh is LoadState.Loading && items.itemCount == 0 -> LoadingState()
        refresh is LoadState.Error -> ErrorState(refresh.error.message ?: refresh.error.javaClass.simpleName, items::retry)
        items.itemCount == 0 -> EmptyState(stringResource(R.string.vod_empty_hint), title = stringResource(R.string.vod_empty), icon = Icons.Outlined.Movie)
        else -> LazyVerticalGrid(
            GridCells.Adaptive(108.dp),
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (header != null) item(key = "header", span = { GridItemSpan(maxLineSpan) }) { header() }
            items(items.itemCount, key = items.itemKey(key)) { i -> items[i]?.let { content(it) } }
        }
    }
}

@Composable
private fun ContinueRail(list: List<Pair<com.streamdeck.player.core.database.VodItemEntity, VodProgressEntity>>, onOpen: (Long) -> Unit) {
    val p = SdTheme.palette
    Column {
        SectionHeader(stringResource(R.string.continue_watching), Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(list, key = { it.first.id }) { (m, pr) ->
                FocusSurface(onClick = { onOpen(m.streamId) }, modifier = Modifier.width(240.dp), shape = RoundedCornerShape(18.dp), container = p.card,
                    contentDescription = m.name) {
                    Column {
                        Artwork(m.poster, m.name, Modifier.fillMaxWidth().aspectRatio(16f / 9f), corner = 0.dp) {
                            Box(Modifier.align(Alignment.Center).size(44.dp).background(p.scrim, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.PlayArrow, null, tint = p.onScrim)
                            }
                        }
                        Column(Modifier.padding(12.dp)) {
                            Text(m.name, color = p.textPrimary, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                stringResource(R.string.resume_at, formatPosition(pr.positionMs)), color = p.textTertiary,
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                            )
                            SdProgress(pr.fraction() ?: 0f)
                        }
                    }
                }
            }
        }
    }
}

internal fun VodTab.labelRes() = if (this == VodTab.MOVIES) R.string.movies else R.string.series
internal fun VodTab.icon() = if (this == VodTab.MOVIES) Icons.Outlined.Movie else Icons.Outlined.VideoLibrary

// ------------------------------------------------------------------------------------------- detail

@Composable
internal fun PhoneVodDetail(
    state: DetailState,
    progress: Map<String, VodProgressEntity>,
    season: Int?,
    vm: VodDetailViewModel,
    onBack: () -> Unit,
    onPlay: (String) -> Unit,
) {
    val p = SdTheme.palette
    Box(Modifier.fillMaxSize().background(p.background)) {
        when (state) {
            DetailState.Loading -> LoadingState()
            is DetailState.Failed -> ErrorState(state.reason, vm::load, title = stringResource(R.string.detail_failed))
            is DetailState.Movie -> {
                val pr = progress[vm.movieProgressKey()]
                val title = state.item?.name.orEmpty()
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                    item { PhoneHero(state.info?.cover ?: state.item?.poster, title, metaParts(state.info?.rating ?: state.item?.rating,
                        state.info?.releaseDate, state.info?.durationSec, state.info?.genre)) }
                    item {
                        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                SdButton(
                                    if (pr.resumable()) {
                                        stringResource(R.string.resume_at,
                                            formatPosition(pr?.positionMs ?: 0L))
                                    } else {
                                        stringResource(R.string.play)
                                    },
                                    { onPlay(state.key) }, Modifier.weight(1f), icon = Icons.Default.PlayArrow, large = true,
                                )
                                WatchedToggle(pr?.watched == true) { vm.toggleWatched(state.key, pr?.watched == true) }
                            }
                            pr.fraction()?.let { SdProgress(it) }
                            state.info?.plot?.takeIf { it.isNotBlank() }?.let { Text(it, color = p.textSecondary, style = MaterialTheme.typography.bodyLarge) }
                        }
                    }
                }
            }
            is DetailState.Series -> {
                val seasons = state.info.episodes.map { it.season }.distinct()
                val current = season ?: seasons.firstOrNull()
                val title = state.info.name ?: state.entity?.name.orEmpty()
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
                    item {
                        PhoneHero(
                            state.info.cover ?: state.entity?.cover, title,
                            metaParts(state.entity?.rating, state.entity?.releaseDate, null, state.entity?.genre,
                                extra = if (seasons.isNotEmpty()) stringResource(R.string.seasons_count, seasons.size) else null),
                        )
                    }
                    (state.info.plot ?: state.entity?.plot)?.takeIf { it.isNotBlank() }?.let { plot ->
                        item { Text(plot, color = p.textSecondary, style = MaterialTheme.typography.bodyLarge, maxLines = 5, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 20.dp)) }
                    }
                    if (seasons.isEmpty()) {
                        item { Box(Modifier.fillMaxWidth().height(260.dp)) { EmptyState(stringResource(R.string.no_episodes)) } }
                    } else {
                        item {
                            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(seasons) { n -> SdChip(stringResource(R.string.season_n, n), selected = n == current, onClick = { vm.season.value = n }) }
                            }
                        }
                        val episodes = state.info.episodes.filter { it.season == current }
                        items(episodes, key = { it.id }) { e ->
                            val key = vm.episodeKey(e)
                            val ep = progress[vm.progressKey(e)]
                            PhoneEpisodeRow(e, ep, onPlay = { onPlay(key) }, onToggle = { vm.toggleWatched(key, ep?.watched == true) })
                        }
                    }
                }
            }
        }
        SdIconButton(
            Icons.AutoMirrored.Filled.ArrowBack, stringResource(com.streamdeck.player.core.designsystem.R.string.ds_back), onBack,
            Modifier.windowInsetsPadding(WindowInsets.statusBars).padding(12.dp), container = p.scrim, tint = p.onScrim,
        )
    }
}

@Composable
private fun PhoneHero(image: String?, title: String, meta: List<String>) {
    val p = SdTheme.palette
    Box(Modifier.fillMaxWidth().height(360.dp)) {
        Backdrop(image, Modifier.fillMaxSize())
        Row(Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.Bottom) {
            Artwork(image, title, Modifier.width(104.dp).aspectRatio(2f / 3f))
            Column(Modifier.padding(start = 16.dp)) {
                Text(title, color = p.textPrimary, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 3,
                    overflow = TextOverflow.Ellipsis)
                MetaLine(meta, Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun WatchedToggle(watched: Boolean, onClick: () -> Unit) {
    SdButton(
        stringResource(if (watched) R.string.watched else R.string.mark_watched), onClick,
        icon = if (watched) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle, style = SdButtonStyle.Secondary, large = true,
    )
}

@Composable
private fun PhoneEpisodeRow(e: XtreamEpisode, pr: VodProgressEntity?, onPlay: () -> Unit, onToggle: () -> Unit) {
    val p = SdTheme.palette
    val watched = pr?.watched == true
    FocusSurface(onClick = onPlay, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(0.dp), container = Color.Transparent,
        contentDescription = e.title) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Artwork(e.image, e.title, Modifier.width(132.dp).aspectRatio(16f / 9f), corner = 12.dp) {
                pr.fraction()?.let { f -> SdProgress(f, Modifier.align(Alignment.BottomCenter).padding(6.dp)) }
                Box(Modifier.align(Alignment.Center).size(32.dp).background(p.scrim, RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PlayArrow, null, tint = p.onScrim, modifier = Modifier.size(20.dp))
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(
                    stringResource(R.string.episode_title, e.episode, e.title), color = if (watched) p.textSecondary else p.textPrimary,
                    style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                val sub = listOfNotNull(e.durationSec?.takeIf { it > 0 }?.let { stringResource(R.string.minutes_short, it / 60) },
                    if (watched) stringResource(R.string.watched) else null)
                if (sub.isNotEmpty()) {
                    Text(sub.joinToString(" · "), color = p.textTertiary, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 2.dp))
                }
                e.plot?.takeIf { it.isNotBlank() }?.let { Text(it, color = p.textTertiary, style = MaterialTheme.typography.bodySmall, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)) }
            }
            SdIconButton(
                if (watched) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                stringResource(if (watched) R.string.mark_unwatched else R.string.mark_watched), onToggle,
                size = 40.dp, container = Color.Transparent, tint = if (watched) p.accent else p.textTertiary,
            )
        }
    }
}
