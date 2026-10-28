package com.retroforge.theme

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** ARGB colour, 0xAARRGGBB. */
typealias Argb = Long

/** Every colour a theme sets for one brightness (dark or light). */
data class Palette(
    val background: Argb,
    val surface: Argb,
    val surfaceVariant: Argb,
    val primary: Argb,
    val onPrimary: Argb,
    val secondary: Argb,
    val onSecondary: Argb,
    val text: Argb,
    val textMuted: Argb,
    val outline: Argb,
    val focus: Argb,
    val good: Argb,
    val caution: Argb,
    val bad: Argb,
)

/** SANS/SERIF/MONO/ROUNDED use Android's faces; PIXEL, TECH and DISPLAY use bundled open-licence fonts. */
enum class FontStyle { SANS, SERIF, MONO, ROUNDED, PIXEL, TECH, DISPLAY }
enum class GameView { GRID, LIST }
enum class TileAspect(val ratio: Float) { WIDE(4f / 3f), SQUARE(1f), PORTRAIT(3f / 4f) }
enum class SystemView { GRID, CAROUSEL }

/**
 * How the home screen is built: DASHBOARD (hero banner then rows), CROSSBAR (a sideways category bar over a list
 * of that category's games), HERO (the focused game fills the screen above a row of tiles), BLADES (swipeable
 * full-height panels with tabs).
 */
enum class HomeLayout { DASHBOARD, CROSSBAR, HERO, BLADES }
enum class BackgroundStyle { SOLID, LINEAR, RADIAL }

/** The page background: flat, or a gradient through [colors] (per brightness; empty means the palette's background). */
data class Background(val style: BackgroundStyle, val dark: List<Argb>, val light: List<Argb>, val angle: Int)

/** A RetroForge theme: colours, shapes, type and layout. Pure data; core-ui turns it into Compose theming. */
data class ThemeSpec(
    val id: String,
    val name: String,
    val author: String,
    val description: String,
    val license: String?,
    val dark: Palette?,
    val light: Palette?,
    val cornerRadius: Int,
    val font: FontStyle,
    val headlineWeight: Int,
    val textScale: Float,
    val wordmarkRetro: Argb,
    val wordmarkForge: Argb,
    val wordmarkGlow: Boolean,
    val gameView: GameView,
    val tileAspect: TileAspect,
    val systemView: SystemView = SystemView.GRID,
    val homeLayout: HomeLayout = HomeLayout.DASHBOARD,
    /** Artwork across the top of the dashboard home. Only "key-art" (RetroForge's own banner) for now; theme packages will add images. */
    val banner: String? = null,
    val background: Background = Background(BackgroundStyle.SOLID, emptyList(), emptyList(), 135),
    /** CRT-style scanlines over every screen. */
    val scanlines: Boolean = false,
    /** Darkened edges. */
    val vignette: Boolean = false,
    /** Focused cards glow in the focus colour. */
    val focusGlow: Boolean = false,
    /** Where it came from, for imported themes ("ES-DE theme canvas-es"). */
    val importedFrom: String? = null,
) {
    /** The palette to use; a theme with only one brightness uses it for both. */
    fun palette(dark: Boolean): Palette = (if (dark) this.dark ?: light else light ?: this.dark)!!
}

class ThemeFormatException(message: String) : Exception(message)

/**
 * Reads and writes the documented theme format ("retroforge-theme", version 1; docs/retroforge-theme-format.md).
 * Themes are data only: nothing in them is executed, links aren't followed, and every value is checked.
 */
object ThemeFormat {
    const val FORMAT = "retroforge-theme"
    const val VERSION = 1
    const val MAX_BYTES = 64 * 1024
    /** Banner art a theme may name. */
    val BANNERS = setOf("key-art")

    @Serializable
    private data class RawPalette(
        val background: String, val surface: String, val surfaceVariant: String? = null,
        val primary: String, val onPrimary: String? = null, val secondary: String, val onSecondary: String? = null,
        val text: String, val textMuted: String? = null, val outline: String? = null, val focus: String? = null,
        val good: String? = null, val caution: String? = null, val bad: String? = null,
    )

    @Serializable
    private data class RawShape(val cornerRadius: Int = 16)

    @Serializable
    private data class RawType(val font: String = "sans", val headlineWeight: Int = 700, val scale: Float = 1f)

    @Serializable
    private data class RawWordmark(val retro: String? = null, val forge: String? = null, val glow: Boolean = true)

    @Serializable
    private data class RawLayout(val gameView: String = "grid", val tileAspect: String = "wide", val systemView: String = "grid", val home: String = "dashboard", val banner: String? = null)

    @Serializable
    private data class RawBackground(val style: String = "solid", val dark: List<String> = emptyList(), val light: List<String> = emptyList(), val angle: Int = 135)

    @Serializable
    private data class RawEffects(val scanlines: Boolean = false, val vignette: Boolean = false, val focusGlow: Boolean = false)

    @Serializable
    private data class RawTheme(
        val format: String, val version: Int,
        val id: String, val name: String, val author: String = "", val description: String = "", val license: String? = null,
        val dark: RawPalette? = null, val light: RawPalette? = null,
        val shape: RawShape = RawShape(), val type: RawType = RawType(),
        val wordmark: RawWordmark = RawWordmark(), val layout: RawLayout = RawLayout(),
        val background: RawBackground = RawBackground(), val effects: RawEffects = RawEffects(),
        val importedFrom: String? = null,
    )

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val idPattern = Regex("[a-z0-9][a-z0-9-]{1,47}")

    fun parse(text: String): ThemeSpec {
        if (text.length > MAX_BYTES) throw ThemeFormatException("theme file is larger than 64 KB")
        if (text.trimStart().startsWith("<")) throw ThemeFormatException("this is an XML or web page, not a RetroForge theme (.json)")
        val raw = try {
            json.decodeFromString(RawTheme.serializer(), text)
        } catch (e: Exception) {
            throw ThemeFormatException("not a valid theme file: ${e.message?.lineSequence()?.firstOrNull()}")
        }
        if (raw.format != FORMAT) throw ThemeFormatException("\"format\" must be \"$FORMAT\"")
        if (raw.version > VERSION) throw ThemeFormatException("made for a newer RetroForge (theme version ${raw.version})")
        if (!idPattern.matches(raw.id)) throw ThemeFormatException("\"id\" must be 2-48 lowercase letters, digits or dashes")
        val name = clean(raw.name, 40).ifEmpty { throw ThemeFormatException("\"name\" is empty") }
        if (raw.dark == null && raw.light == null) throw ThemeFormatException("a theme needs a \"dark\" or \"light\" palette")
        val dark = raw.dark?.let { palette(it, dark = true) }
        val light = raw.light?.let { palette(it, dark = false) }
        val main = dark ?: light!!
        return ThemeSpec(
            id = raw.id,
            name = name,
            author = clean(raw.author, 60),
            description = clean(raw.description, 300),
            license = raw.license?.let { clean(it, 60) }?.ifEmpty { null },
            dark = dark,
            light = light,
            cornerRadius = raw.shape.cornerRadius.coerceIn(0, 32),
            font = enumOf(raw.type.font, FontStyle.entries, "type.font"),
            headlineWeight = (raw.type.headlineWeight / 100 * 100).coerceIn(300, 900),
            textScale = raw.type.scale.coerceIn(0.85f, 1.3f),
            wordmarkRetro = raw.wordmark.retro?.let { color(it, "wordmark.retro") } ?: main.primary,
            wordmarkForge = raw.wordmark.forge?.let { color(it, "wordmark.forge") } ?: main.secondary,
            wordmarkGlow = raw.wordmark.glow,
            gameView = enumOf(raw.layout.gameView, GameView.entries, "layout.gameView"),
            tileAspect = enumOf(raw.layout.tileAspect, TileAspect.entries, "layout.tileAspect"),
            systemView = enumOf(raw.layout.systemView, SystemView.entries, "layout.systemView"),
            homeLayout = enumOf(raw.layout.home, HomeLayout.entries, "layout.home"),
            banner = raw.layout.banner?.takeIf { it in BANNERS },
            background = Background(
                enumOf(raw.background.style, BackgroundStyle.entries, "background.style"),
                raw.background.dark.take(4).map { color(it, "background.dark") },
                raw.background.light.take(4).map { color(it, "background.light") },
                ((raw.background.angle % 360) + 360) % 360,
            ),
            scanlines = raw.effects.scanlines,
            vignette = raw.effects.vignette,
            focusGlow = raw.effects.focusGlow,
            importedFrom = raw.importedFrom?.let { clean(it, 120) },
        )
    }

    /** Writes [theme] back in the documented format (used for imported themes). */
    fun write(theme: ThemeSpec): String {
        fun hex(c: Argb) = if ((c ushr 24) == 0xFFL) "#%06X".format(c and 0xFFFFFF) else "#%08X".format(c)
        fun raw(p: Palette) = RawPalette(
            hex(p.background), hex(p.surface), hex(p.surfaceVariant), hex(p.primary), hex(p.onPrimary), hex(p.secondary), hex(p.onSecondary),
            hex(p.text), hex(p.textMuted), hex(p.outline), hex(p.focus), hex(p.good), hex(p.caution), hex(p.bad),
        )
        return json.encodeToString(
            RawTheme.serializer(),
            RawTheme(
                FORMAT, VERSION, theme.id, theme.name, theme.author, theme.description, theme.license,
                theme.dark?.let(::raw), theme.light?.let(::raw), RawShape(theme.cornerRadius),
                RawType(theme.font.name.lowercase(), theme.headlineWeight, theme.textScale),
                RawWordmark(hex(theme.wordmarkRetro), hex(theme.wordmarkForge), theme.wordmarkGlow),
                RawLayout(theme.gameView.name.lowercase(), theme.tileAspect.name.lowercase(), theme.systemView.name.lowercase(), theme.homeLayout.name.lowercase(), theme.banner),
                RawBackground(theme.background.style.name.lowercase(), theme.background.dark.map(::hex), theme.background.light.map(::hex), theme.background.angle),
                RawEffects(theme.scanlines, theme.vignette, theme.focusGlow),
                theme.importedFrom,
            ),
        )
    }

    private fun palette(p: RawPalette, dark: Boolean): Palette {
        val background = color(p.background, "background")
        val surface = color(p.surface, "surface")
        val primary = color(p.primary, "primary")
        val secondary = color(p.secondary, "secondary")
        val text = color(p.text, "text")
        return Palette(
            background = background,
            surface = surface,
            surfaceVariant = p.surfaceVariant?.let { color(it, "surfaceVariant") } ?: mix(surface, text, 0.08f),
            primary = primary,
            onPrimary = p.onPrimary?.let { color(it, "onPrimary") } ?: readableOn(primary),
            secondary = secondary,
            onSecondary = p.onSecondary?.let { color(it, "onSecondary") } ?: readableOn(secondary),
            text = text,
            textMuted = p.textMuted?.let { color(it, "textMuted") } ?: mix(text, background, 0.3f),
            outline = p.outline?.let { color(it, "outline") } ?: mix(text, background, 0.6f),
            focus = p.focus?.let { color(it, "focus") } ?: primary,
            good = p.good?.let { color(it, "good") } ?: if (dark) 0xFF5FD38A else 0xFF1E7A43,
            caution = p.caution?.let { color(it, "caution") } ?: if (dark) 0xFFFFC857 else 0xFF8A5A00,
            bad = p.bad?.let { color(it, "bad") } ?: if (dark) 0xFFFF6B6B else 0xFFB3261E,
        )
    }

    /** "#RRGGBB", "#AARRGGBB", "RRGGBB" or ES-DE's "RRGGBBAA" (with [esOrder]). */
    fun color(value: String, field: String, esOrder: Boolean = false): Argb {
        val hex = value.trim().removePrefix("#")
        if (!Regex("[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?").matches(hex)) throw ThemeFormatException("\"$field\" isn't a colour: $value")
        val v = hex.toLong(16)
        return when {
            hex.length == 6 -> 0xFF000000 or v
            esOrder -> ((v and 0xFF) shl 24) or (v ushr 8)
            else -> v
        }
    }

    /** Black or white, whichever reads better on [c]. */
    fun readableOn(c: Argb): Argb = if (luminance(c) > 0.45) 0xFF111111 else 0xFFFFFFFF

    fun luminance(c: Argb): Double {
        fun ch(shift: Int) = ((c ushr shift) and 0xFF) / 255.0
        return 0.2126 * ch(16) + 0.7152 * ch(8) + 0.0722 * ch(0)
    }

    fun mix(a: Argb, b: Argb, t: Float): Argb {
        fun ch(c: Argb, s: Int) = ((c ushr s) and 0xFF).toInt()
        fun m(s: Int) = (ch(a, s) + (ch(b, s) - ch(a, s)) * t).toInt().coerceIn(0, 255).toLong()
        return 0xFF000000 or (m(16) shl 16) or (m(8) shl 8) or m(0)
    }

    private fun clean(s: String, max: Int) = s.filter { !it.isISOControl() }.trim().take(max)

    private fun <E : Enum<E>> enumOf(value: String, entries: List<E>, field: String): E =
        entries.firstOrNull { it.name.equals(value.trim(), ignoreCase = true) }
            ?: throw ThemeFormatException("\"$field\" must be one of ${entries.joinToString { it.name.lowercase() }}")
}
