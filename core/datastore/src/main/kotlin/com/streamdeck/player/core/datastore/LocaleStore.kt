package com.streamdeck.player.core.datastore

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * The UI language must be known synchronously in Activity.attachBaseContext, so it lives in SharedPreferences
 * rather than DataStore. Empty tag = follow system.
 */
object LocaleStore {
    private const val FILE = "locale"
    private const val KEY = "tag"
    val SUPPORTED = listOf("", "zh-CN", "en")

    fun get(ctx: Context): String = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, "") ?: ""

    fun set(ctx: Context, tag: String) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, tag).apply()
    }

    fun wrap(base: Context): Context {
        val tag = get(base)
        if (tag.isEmpty()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }
}
