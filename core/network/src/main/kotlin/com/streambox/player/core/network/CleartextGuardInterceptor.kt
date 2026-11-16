package com.streambox.player.core.network

import com.streambox.player.core.common.result.AppError
import com.streambox.player.core.common.result.AppException
import okhttp3.Interceptor
import okhttp3.Response

/** Request tag carrying the per-playlist "allow plain HTTP" choice. */
data class CleartextPolicy(val allowed: Boolean)

/**
 * Enforces the per-playlist cleartext switch in code. Android's network security config can only
 * allow cleartext per domain or globally, but IPTV playlists choose per list, so the manifest allows
 * cleartext and this interceptor blocks `http://` for lists that did not opt in.
 *
 * Installed as a *network* interceptor so it sees every redirect hop (an https URL redirecting to
 * http is blocked as well). TLS certificate validation is never relaxed.
 */
class CleartextGuardInterceptor(private val defaultAllowed: Boolean) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val allowed = request.tag(CleartextPolicy::class.java)?.allowed ?: defaultAllowed
        if (!request.url.isHttps && !allowed) throw AppException(AppError.CleartextNotAllowed)
        return chain.proceed(request)
    }
}
