package com.retroforge.core.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import java.util.Locale

/**
 * On-screen texts for code that has no screen to hand (status lines, toasts, messages made in the background), in
 * RetroForge's language ([AppLanguage]). Composables use stringResource instead.
 */
object AppStrings {
    @SuppressLint("StaticFieldLeak") // the application context
    private var app: Context? = null
    private var tag: String? = null
    private var localized: Context? = null

    /** Called once from Application.onCreate. */
    fun init(context: Context) { app = context.applicationContext }

    /** Unit tests (no Android resources) answer lookups themselves: (id, count for plurals or null, args) → text. */
    @androidx.annotation.VisibleForTesting
    var testLookup: ((Int, Int?, Array<out Any?>) -> String)? = null

    /** Forgets the language, after it changes. */
    internal fun reset() { tag = null; localized = null }

    internal fun context(): Context {
        val app = checkNotNull(app) { "AppStrings.init wasn't called" }
        val now = AppLanguage.current(app)
        localized?.let { if (now == tag) return it }
        val made = if (now.isEmpty() || Build.VERSION.SDK_INT >= 33) app
            else app.createConfigurationContext(Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(now)) })
        tag = now; localized = made
        return made
    }
}

/** [id] in RetroForge's language, with [args] filled in. */
fun str(@StringRes id: Int, vararg args: Any?): String =
    AppStrings.testLookup?.invoke(id, null, args) ?: AppStrings.context().getString(id, *args)

/** The form of [id] for [count], with [args] filled in. */
fun pluralStr(@PluralsRes id: Int, count: Int, vararg args: Any?): String =
    AppStrings.testLookup?.invoke(id, count, args) ?: AppStrings.context().resources.getQuantityString(id, count, *args)
