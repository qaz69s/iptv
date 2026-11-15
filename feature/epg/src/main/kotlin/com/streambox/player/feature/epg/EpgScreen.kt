package com.streambox.player.feature.epg

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.streambox.player.core.designsystem.component.EmptyState
import com.streambox.player.core.designsystem.icon.StreamBoxIcons
import com.streambox.player.core.designsystem.theme.StreamBoxTheme

@Composable
internal fun EpgScreen(modifier: Modifier = Modifier) {
    EmptyState(
        title = stringResource(R.string.feature_epg_empty_title),
        message = stringResource(R.string.feature_epg_empty_message),
        icon = StreamBoxIcons.Epg,
        modifier = modifier,
    )
}

@Preview
@Composable
private fun EpgScreenPreview() {
    StreamBoxTheme { EpgScreen() }
}
