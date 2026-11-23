package com.streamdeck.player.core.common

private val queryParam = Regex("""(?i)([?&](?:password|pass|pwd|token|key|auth|sig|signature|username|user)=)[^&#]*""")
private val userInfo = Regex("""(?i)(://)[^/@\s]+@""")
private val xtreamPath = Regex("""(?i)/(live|movie|series|timeshift)/[^/]+/[^/]+/""")

/** Removes credentials from URLs before they reach logs or error messages (spec §5 安全). */
fun String.redactSecrets(): String = this
    .replace(queryParam) { "${it.groupValues[1]}***" }
    .replace(userInfo) { "${it.groupValues[1]}***@" }
    .replace(xtreamPath) { "/${it.groupValues[1]}/***/***/" }
