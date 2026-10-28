package com.retroforge.theme

/** The themes that ship with RetroForge (resources/themes), all original and CC0. */
object BuiltInThemes {
    val ids = listOf("forge-neon", "paper", "phosphor", "console-blue", "arcade-sunset")
    const val DEFAULT = "forge-neon"

    val all: List<ThemeSpec> by lazy {
        ids.map { id ->
            val text = checkNotNull(BuiltInThemes::class.java.getResourceAsStream("/themes/$id.json")) { "theme $id missing" }
                .bufferedReader().use { it.readText() }
            ThemeFormat.parse(text)
        }
    }

    val default: ThemeSpec get() = all.first { it.id == DEFAULT }
}

/** What a colour import found, for showing the user exactly what came across. */
data class ImportReport(val used: List<Pair<String, String>>, val notImported: List<String>, val license: String?)

/**
 * Builds a RetroForge theme from the colour variables of an EmulationStation theme (ES-DE, RetroBat, Batocera …).
 * Only colours are imported: ES layouts (views, carousels, positioned elements) and artwork don't map onto
 * RetroForge's screens. The original theme is only read.
 */
object EsThemeColors {
    private val variable = Regex("""<([A-Za-z0-9_]+)>\s*#?([0-9A-Fa-f]{6}(?:[0-9A-Fa-f]{2})?)\s*</\1>""")

    // Variable-name hints for each role, best first. ES themes name their variables freely; these are the common ones.
    private val roles = listOf(
        "background" to listOf("backgroundcolor", "mainbackground", "bgcolor", "background", "backdropcolor"),
        "surface" to listOf("menubackgroundcolor", "panelcolor", "cardcolor", "gamelistbackground", "metadatabackground", "boxcolor"),
        "text" to listOf("textcolor", "fontcolor", "gamelistprimarycolor", "primarycolor", "maincolor", "foregroundcolor"),
        "textMuted" to listOf("gamelistsecondarycolor", "secondarycolor", "metadatacolor", "infocolor", "subtextcolor", "helptextcolor"),
        "primary" to listOf("selectorcolor", "gamelistselectorcolor", "systemcarouselselectorcolor", "accentcolor", "highlightcolor", "selectedcolor"),
        "secondary" to listOf("accent2color", "secondaryaccent", "logocolor", "gamelistlogocolor", "systeminfocolor"),
    )

    /** Every colour variable in [files] (theme XML texts), first definition winning. */
    fun variables(files: List<String>): Map<String, String> {
        val out = linkedMapOf<String, String>()
        for (text in files) {
            if (text.contains("<!DOCTYPE", ignoreCase = true)) continue
            for (m in variable.findAll(text)) out.putIfAbsent(m.groupValues[1], m.groupValues[2])
        }
        return out
    }

    fun import(themeName: String, files: List<String>, license: String?): Pair<ThemeSpec?, ImportReport> {
        val vars = variables(files)
        val lower = vars.mapKeys { it.key.lowercase() }
        val used = mutableListOf<Pair<String, String>>()
        val picked = mutableMapOf<String, Argb>()
        for ((role, hints) in roles) {
            // Exact names first (any hint), then partial ones, so "textcolor" doesn't grab "helptextcolor" when a real one exists.
            val candidates = hints.mapNotNull { h -> lower.entries.firstOrNull { it.key == h } } +
                hints.flatMap { h -> lower.entries.filter { it.key.contains(h) && it.key != h } }
            val background = picked["background"]
            val hit = candidates.firstOrNull { c ->
                // Text must be readable on the background; a near-match is skipped for the next candidate.
                role != "text" || background == null ||
                    kotlin.math.abs(ThemeFormat.luminance(ThemeFormat.color(c.value, role, esOrder = true)) - ThemeFormat.luminance(background)) >= 0.3
            }
            if (hit != null) {
                picked[role] = ThemeFormat.color(hit.value, role, esOrder = true) or 0xFF000000
                used += role to vars.keys.first { it.lowercase() == hit.key }
            }
        }
        val background = picked["background"]
        // No readable text colour in the theme: pick black or white for its background and say so.
        val text = picked["text"] ?: background?.let { ThemeFormat.readableOn(it).also { used += "text" to "(chosen for readability)" } }
        val notImported = listOf("layouts and positions", "system carousels", "artwork, logos and fonts", "sounds")
        if (background == null || text == null) {
            return null to ImportReport(used, listOf("no background or text colour found") + notImported, license)
        }
        val dark = ThemeFormat.luminance(background) < 0.5
        fun standsOut(c: Argb) = kotlin.math.abs(ThemeFormat.luminance(c) - ThemeFormat.luminance(background)) >= 0.12
        // Accents mark the selected tab, buttons and focus, so they must stand out against the background.
        val primary = listOfNotNull(picked["primary"], picked["secondary"]).firstOrNull(::standsOut) ?: text
        val secondary = picked["secondary"]?.takeIf(::standsOut) ?: ThemeFormat.mix(primary, text, 0.5f)
        fun readableWith(c: Argb, other: Argb) = kotlin.math.abs(ThemeFormat.luminance(c) - ThemeFormat.luminance(other)) >= 0.3
        // Cards carry the same text as the background, so a card colour the text can't be read on is replaced.
        val surface = picked["surface"]?.takeIf { it != background && readableWith(it, text) }
            ?: ThemeFormat.mix(background, text, 0.07f).also { if (picked["surface"] != null) used.removeAll { u -> u.first == "surface" } }
        val muted = picked["textMuted"]?.takeIf { readableWith(it, surface) && readableWith(it, background) }
            ?: ThemeFormat.mix(text, background, 0.3f).also { if (picked["textMuted"] != null) used.removeAll { u -> u.first == "textMuted" } }
        val palette = Palette(
            background = background,
            surface = surface,
            surfaceVariant = ThemeFormat.mix(background, text, 0.14f),
            primary = primary,
            onPrimary = ThemeFormat.readableOn(primary),
            secondary = secondary,
            onSecondary = ThemeFormat.readableOn(secondary),
            text = text,
            textMuted = muted,
            outline = ThemeFormat.mix(text, background, 0.6f),
            focus = primary,
            good = if (dark) 0xFF5FD38A else 0xFF1E7A43,
            caution = if (dark) 0xFFFFC857 else 0xFF8A5A00,
            bad = if (dark) 0xFFFF6B6B else 0xFFB3261E,
        )
        val id = "imported-" + themeName.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(38).ifEmpty { "theme" }
        val theme = ThemeSpec(
            id = id, name = themeName.take(40), author = "", description = "Colours imported from the EmulationStation theme \"$themeName\".",
            license = license, dark = if (dark) palette else null, light = if (dark) null else palette,
            cornerRadius = 12, font = FontStyle.SANS, headlineWeight = 700, textScale = 1f,
            wordmarkRetro = primary, wordmarkForge = secondary, wordmarkGlow = dark,
            gameView = GameView.GRID, tileAspect = TileAspect.WIDE, importedFrom = "EmulationStation theme \"$themeName\"",
        )
        return theme to ImportReport(used, notImported, license)
    }

    /** A licence name from a theme's LICENSE/README text, when it states a common one. */
    fun licenseOf(text: String): String? = when {
        Regex("CC0|Public Domain Dedication", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "CC0-1.0"
        Regex("NonCommercial-ShareAlike|BY-NC-SA", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "CC BY-NC-SA (non-commercial)"
        Regex("NonCommercial|BY-NC", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "CC BY-NC (non-commercial)"
        Regex("ShareAlike|BY-SA", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "CC BY-SA"
        Regex("Attribution|CC BY", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "CC BY"
        Regex("\\bMIT License\\b", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "MIT"
        Regex("GNU General Public License|\\bGPL", RegexOption.IGNORE_CASE).containsMatchIn(text) -> "GPL"
        else -> null
    }
}
