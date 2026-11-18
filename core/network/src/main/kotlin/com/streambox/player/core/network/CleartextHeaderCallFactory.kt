package com.streambox.player.core.network

import com.streambox.player.core.common.net.CleartextImagePolicy
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Call factory for the image loader (Coil). Turns the per-request
 * [com.streambox.player.core.common.net.CleartextImagePolicy.HEADER] into a [CleartextPolicy] tag for
 * [CleartextGuardInterceptor], which checks every hop, redirects included (K1).
 */
class CleartextHeaderCallFactory(private val client: OkHttpClient) : Call.Factory {
    override fun newCall(request: Request): Call {
        // Missing header = not allowed: an image without a known playlist never goes over plain HTTP.
        val allowed = request.header(CleartextImagePolicy.HEADER) == CleartextImagePolicy.ALLOWED
        val tagged = request.newBuilder()
            .removeHeader(CleartextImagePolicy.HEADER)
            // Tags are copied to redirect follow-ups, so the guard applies the same policy on every hop.
            .tag(CleartextPolicy::class.java, CleartextPolicy(allowed))
            .build()
        return client.newCall(tagged)
    }
}
