package com.streambox.player.core.common.log

/**
 * Masks credentials in URLs before they reach logs or crash reports.
 *
 * Covers: userinfo (`http://user:pass@host`), sensitive query parameters
 * (`?username=..&password=..&token=..`) and Xtream-style paths
 * (`/live/<user>/<pass>/<id>.ts`, `/movie/..`, `/series/..`, `/timeshift/..`).
 */
object UrlRedactor {
    private const val MASK = "***"

    private val sensitiveParams = setOf(
        "username", "user", "password", "pass", "pwd", "token", "auth", "key", "apikey",
        "api_key", "access_token", "sig", "signature", "secret",
    )

    private val userInfoRegex = Regex("""^([a-zA-Z][a-zA-Z0-9+.-]*://)([^/@?#]+)@""")
    private val queryParamRegex = Regex("""([?&;])([^=&#;]+)=([^&#;]*)""")
    private val xtreamPathRegex = Regex("""/(live|movie|series)/([^/]+)/([^/]+)/""")
    private val xtreamTimeshiftRegex = Regex("""/timeshift/([^/]+)/([^/]+)/""")

    fun redact(url: String): String {
        var result = userInfoRegex.replace(url) { "${it.groupValues[1]}$MASK@" }
        result = queryParamRegex.replace(result) { match ->
            val (separator, name, _) = match.destructured
            if (name.lowercase() in sensitiveParams) "$separator$name=$MASK" else match.value
        }
        result = xtreamPathRegex.replace(result) { "/${it.groupValues[1]}/$MASK/$MASK/" }
        result = xtreamTimeshiftRegex.replace(result) { "/timeshift/$MASK/$MASK/" }
        return result
    }
}
