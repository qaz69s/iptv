package com.streamdeck.player.feature.vod

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.streamdeck.player.core.designsystem.rememberIsTv

/**
 * Movies & series browser for Xtream playlists (spec §4.6). Phone and TV get their own layouts
 * ([PhoneVodHome] / [TvVodHome]); both read the same [VodHomeViewModel].
 */
@Composable
fun VodHomeScreen(onOpen: (playlistId: Long, kind: String, id: Long) -> Unit, vm: VodHomeViewModel = hiltViewModel()) {
    if (rememberIsTv()) TvVodHome(vm, onOpen) else PhoneVodHome(vm, onOpen)
}

/** Movie / series details with resume, watched marks, seasons and episodes. */
@Composable
fun VodDetailScreen(onBack: () -> Unit, onPlay: (vodKey: String) -> Unit, vm: VodDetailViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val season by vm.season.collectAsStateWithLifecycle()
    if (rememberIsTv()) {
        TvVodDetail(state, progress, season, vm, onPlay)
    } else {
        PhoneVodDetail(state, progress, season, vm, onBack, onPlay)
    }
}
