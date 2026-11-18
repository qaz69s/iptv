package com.streambox.player.feature.channels

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import coil3.PlatformContext
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import com.streambox.player.core.common.net.CleartextImagePolicy
import com.streambox.player.core.model.Channel

/**
 * Logo request that obeys the channel's playlist cleartext switch (K1): an `http://` logo of a playlist
 * without "allow plain HTTP" is not requested at all, and every request carries the choice as a header
 * so a redirect to http is blocked by the cleartext guard too. Null = show the initial only.
 */
internal fun logoRequest(context: PlatformContext, url: String?, allowCleartext: Boolean): ImageRequest? {
    if (!CleartextImagePolicy.isAllowed(url, allowCleartext)) return null
    val headers = NetworkHeaders.Builder()
        .set(CleartextImagePolicy.HEADER, CleartextImagePolicy.headerValue(allowCleartext))
        .build()
    return ImageRequest.Builder(context).data(url).httpHeaders(headers).build()
}

@Composable
internal fun ChannelLogo(
    channel: Channel,
    allowCleartext: Boolean,
    modifier: Modifier = Modifier,
    size: DpSize = DpSize(56.dp, 40.dp),
) {
    val shape = RoundedCornerShape(8.dp)
    val context = LocalPlatformContext.current
    val request = remember(context, channel.logoUrl, allowCleartext) {
        logoRequest(context, channel.logoUrl, allowCleartext)
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        // Initial shows while the logo loads, and stays when there is none or it fails.
        Text(
            channel.name.trim().take(1).uppercase(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (request != null) {
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(4.dp),
            )
        }
    }
}
