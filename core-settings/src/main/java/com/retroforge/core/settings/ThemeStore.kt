package com.retroforge.core.settings

import com.retroforge.theme.BuiltInThemes
import com.retroforge.theme.ThemeFormat
import com.retroforge.theme.ThemeFormatException
import com.retroforge.theme.ThemeSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Built-in themes plus the user's own (imported or converted), stored as theme files in the app's private
 * `themes` folder. A theme that no longer parses is skipped, never crashes the app.
 */
class ThemeStore(private val dir: File) {

    private val _userThemes = MutableStateFlow(load())
    val userThemes: StateFlow<List<ThemeSpec>> = _userThemes.asStateFlow()

    val builtIn: List<ThemeSpec> get() = BuiltInThemes.all

    fun find(id: String?): ThemeSpec =
        _userThemes.value.firstOrNull { it.id == id } ?: builtIn.firstOrNull { it.id == id } ?: BuiltInThemes.default

    /** Adds [theme]; an id clashing with a built-in or existing theme gets "-2", "-3" … */
    fun save(theme: ThemeSpec): ThemeSpec {
        val taken = (builtIn + _userThemes.value).map { it.id }.toSet()
        val id = if (theme.id !in taken) theme.id else (2..99).map { "${theme.id.take(44)}-$it" }.first { it !in taken }
        val saved = theme.copy(id = id)
        dir.mkdirs()
        val temp = File(dir, "$id.json.tmp")
        temp.writeText(ThemeFormat.write(saved))
        if (!temp.renameTo(File(dir, "$id.json"))) error("couldn't save the theme")
        _userThemes.value = load()
        return saved
    }

    /** Reads a theme file the user picked. */
    fun importFile(text: String): ThemeSpec = save(ThemeFormat.parse(text))

    fun delete(id: String) {
        if (id in BuiltInThemes.ids) return
        File(dir, "$id.json").delete()
        _userThemes.value = load()
    }

    private fun load(): List<ThemeSpec> = dir.listFiles { f -> f.name.endsWith(".json") }.orEmpty().sortedBy { it.name }.mapNotNull { f ->
        try {
            ThemeFormat.parse(f.readText())
        } catch (_: ThemeFormatException) {
            null
        }
    }
}
