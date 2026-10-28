package com.retroforge.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retroforge.theme.BuiltInThemes
import com.retroforge.theme.FontStyle
import com.retroforge.theme.Palette
import com.retroforge.theme.ThemeFormat
import com.retroforge.theme.ThemeSpec

/** Colours the design system needs beyond Material's scheme. */
@Immutable
data class ForgeColors(
    val focusRing: Color,
    val supported: Color,
    val experimental: Color,
    val unsupported: Color,
    val wordmarkRetro: Color,
    val wordmarkForge: Color,
    val wordmarkGlow: Boolean,
)

/** The theme in use, for screens that follow its layout choices (grid or list, tile shape). */
val LocalThemeSpec = staticCompositionLocalOf { BuiltInThemes.default }

val LocalForgeColors = staticCompositionLocalOf {
    forgeColors(BuiltInThemes.default, BuiltInThemes.default.palette(dark = true))
}

private fun c(argb: Long) = Color(argb.toInt())

/** [color], unless it would nearly vanish on the background (e.g. a light theme's dark ink in dark mode). */
private fun readable(color: Long, p: Palette): Long =
    if (kotlin.math.abs(ThemeFormat.luminance(color) - ThemeFormat.luminance(p.background)) < 0.25) p.text else color

private fun forgeColors(theme: ThemeSpec, p: Palette) = ForgeColors(
    focusRing = c(p.focus),
    supported = c(p.good),
    experimental = c(p.caution),
    unsupported = c(p.bad),
    wordmarkRetro = c(readable(theme.wordmarkRetro, p)),
    wordmarkForge = c(readable(theme.wordmarkForge, p)),
    wordmarkGlow = theme.wordmarkGlow,
)

private fun colorScheme(p: Palette, dark: Boolean) = if (dark) {
    darkColorScheme(
        primary = c(p.primary), onPrimary = c(p.onPrimary), primaryContainer = c(p.surfaceVariant), onPrimaryContainer = c(p.text),
        secondary = c(p.secondary), onSecondary = c(p.onSecondary), background = c(p.background), onBackground = c(p.text),
        surface = c(p.surface), onSurface = c(p.text), surfaceVariant = c(p.surfaceVariant), onSurfaceVariant = c(p.textMuted),
        outline = c(p.outline), error = c(p.bad),
    )
} else {
    lightColorScheme(
        primary = c(p.primary), onPrimary = c(p.onPrimary), primaryContainer = c(p.surfaceVariant), onPrimaryContainer = c(p.text),
        secondary = c(p.secondary), onSecondary = c(p.onSecondary), background = c(p.background), onBackground = c(p.text),
        surface = c(p.surface), onSurface = c(p.text), surfaceVariant = c(p.surfaceVariant), onSurfaceVariant = c(p.textMuted),
        outline = c(p.outline), error = c(p.bad),
    )
}

// Bundled fonts (SIL Open Font License; licences in assets/licences). Orbitron and Exo 2 are variable fonts.
private val pressStart = FontFamily(Font(R.font.press_start_2p))
private val orbitron = FontFamily((400..900 step 100).map { w ->
    Font(R.font.orbitron, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
})
private val exo2 = FontFamily((300..900 step 100).map { w ->
    Font(R.font.exo2, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))
})
private val rajdhani = FontFamily(Font(R.font.rajdhani_medium, FontWeight.Normal), Font(R.font.rajdhani_medium, FontWeight.Medium), Font(R.font.rajdhani_bold, FontWeight.SemiBold), Font(R.font.rajdhani_bold, FontWeight.Bold))

private fun typography(theme: ThemeSpec): Typography {
    // Headings and body text can use different faces: a pixel or futuristic face reads well large, not in paragraphs.
    val (headingFamily, family) = when (theme.font) {
        FontStyle.SANS -> FontFamily.SansSerif to FontFamily.SansSerif
        FontStyle.SERIF -> FontFamily.Serif to FontFamily.Serif
        FontStyle.MONO -> FontFamily.Monospace to FontFamily.Monospace
        // Android has no system rounded face; the default face with heavier headings gives the softer look.
        FontStyle.ROUNDED -> FontFamily.Default to FontFamily.Default
        FontStyle.PIXEL -> pressStart to exo2
        FontStyle.TECH -> orbitron to exo2
        FontStyle.DISPLAY -> rajdhani to rajdhani
    }
    // Press Start 2P is very wide; its headings are set smaller so titles still fit.
    val headingScale = if (theme.font == FontStyle.PIXEL) 0.62f else 1f
    val heading = FontWeight(theme.headlineWeight)
    fun TextStyle.themed(weight: FontWeight? = null) =
        copy(fontFamily = family, fontWeight = weight ?: fontWeight, fontSize = fontSize * theme.textScale, lineHeight = lineHeight * theme.textScale)
    fun TextStyle.headline(weight: FontWeight? = null) =
        copy(fontFamily = headingFamily, fontWeight = weight ?: fontWeight, fontSize = fontSize * theme.textScale * headingScale, lineHeight = lineHeight * theme.textScale * maxOf(headingScale, 0.8f))
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.headline(heading), displayMedium = base.displayMedium.headline(heading),
        displaySmall = base.displaySmall.headline(FontWeight.Black).copy(letterSpacing = 1.sp),
        headlineLarge = base.headlineLarge.headline(heading), headlineMedium = base.headlineMedium.headline(heading),
        headlineSmall = base.headlineSmall.headline(heading),
        titleLarge = base.titleLarge.headline(FontWeight.SemiBold), titleMedium = base.titleMedium.themed(FontWeight.SemiBold), titleSmall = base.titleSmall.themed(),
        bodyLarge = base.bodyLarge.themed(), bodyMedium = base.bodyMedium.themed(), bodySmall = base.bodySmall.themed(),
        labelLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 15.sp * theme.textScale, letterSpacing = 0.5.sp),
        labelMedium = base.labelMedium.themed(), labelSmall = base.labelSmall.themed(),
    )
}

/** Shapes from the theme's corner radius; small elements get proportionally smaller corners. */
private fun shapes(theme: ThemeSpec): Shapes {
    val r = theme.cornerRadius
    return Shapes(
        extraSmall = RoundedCornerShape((r / 4).dp), small = RoundedCornerShape((r / 2).dp),
        medium = RoundedCornerShape(r.dp), large = RoundedCornerShape((r * 5 / 4).dp), extraLarge = RoundedCornerShape((r * 3 / 2).dp),
    )
}

@Composable
fun RetroForgeTheme(theme: ThemeSpec = BuiltInThemes.default, dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val palette = theme.palette(dark)
    // A theme with only one brightness keeps it whichever mode is chosen.
    val isDark = if (theme.dark == null || theme.light == null) theme.dark != null else dark
    val scheme = remember(theme, isDark) { colorScheme(palette, isDark) }
    val type = remember(theme) { typography(theme) }
    val shape = remember(theme) { shapes(theme) }
    CompositionLocalProvider(LocalForgeColors provides forgeColors(theme, palette), LocalThemeSpec provides theme) {
        MaterialTheme(colorScheme = scheme, typography = type, shapes = shape, content = content)
    }
}
