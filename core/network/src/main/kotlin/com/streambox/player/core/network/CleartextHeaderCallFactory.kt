package com.streambox.player.core.network

import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Call factory for the image loader (Coil). Turns the per-request
 * [com.streambox.player.core.common.net.CleartextImagePolicy.HEADER] into a [CleartextPolicy] tag for
 * [CleartextGuardInterceptor], which checks every hop, redirects included (K1).
 */
class CleartextHeaderCallFactory(private val client: OkHttpClient) : Call.Factory {
    override fun newCall(request: Request): Call = client.newCall(request)
}
