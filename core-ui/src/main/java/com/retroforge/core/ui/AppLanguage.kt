package com.retroforge.core.ui

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * RetroForge's language: the phone's own, or one the player picked in Settings. On Android 13 and newer it's the
 * system's per-app language (which also shows under Settings → Apps → RetroForge → Language); on older versions
 * RetroForge keeps the choice itself and each screen applies it through [wrap].
 */
object AppLanguage {
    /** The languages RetroForge has, by tag, with their own names. "" is the phone's language. */
    val choices = listOf(
        "" to null,
        "en" to "English",
        "es" to "Español",
        "fr" to "Français",
        "de" to "Deutsch",
        "pt" to "Português",
        "it" to "Italiano",
    )

    private const val PREFS = "app_language"
    private const val KEY = "tag"

    /** The chosen language's tag, or "" for the phone's. */
    fun current(context: Context): String =
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales.takeIf { !it.isEmpty }?.get(0)?.language.orEmpty()
        } else {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
        }

    /** Switches to [tag] ("" for the phone's language). Screens already open need recreating to show it. */
    fun set(context: Context, tag: String) {
        if (Build.VERSION.SDK_INT >= 33) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
        } else {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tag).apply()
        }
    }

    /** For an Activity's attachBaseContext on Android 12 and older: [base] in the chosen language. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return base
        val tag = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()
        if (tag.isEmpty()) return base
        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration).apply { setLocale(locale) }
        return base.createConfigurationContext(config)
    }
}
