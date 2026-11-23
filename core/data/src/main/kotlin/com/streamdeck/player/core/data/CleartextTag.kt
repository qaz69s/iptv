package com.streamdeck.player.core.data

import com.streamdeck.player.core.network.CleartextAllowed
import okhttp3.Request

/** Marks a request as allowed to use plain HTTP when the owning playlist opted in (spec §5). */
internal fun Request.Builder.cleartextTag(allowed: Boolean): Request.Builder =
    if (allowed) tag(CleartextAllowed::class.java, CleartextAllowed) else this
