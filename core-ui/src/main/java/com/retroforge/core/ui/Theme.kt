package com.retroforge.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Colours the design system needs beyond Material's scheme. */
@Immutable
data class ForgeColors(
    val focusRing: Color,
    val supported: Color,
    val experimental: Color,
    val unsupported: Color,
)

// Palette taken from the RetroForge key art: neon orange "RETRO", neon cyan "FORGE", workshop navy.
val NeonOrange = Color(0xFFFF8A2A)
val NeonCyan = Color(0xFF3ED6FF)
private val EmberDeep = Color(0xFFB34700)
private val CyanDeep = Color(0xFF00708F)
private val Anvil = Color(0xFF0B1020)
private val Iron = Color(0xFF151C2E)
private val Steel = Color(0xFF222B40)

private val DarkScheme = darkColorScheme(
    primary = NeonOrange,
    onPrimary = Color(0xFF1A0A00),
    primaryContainer = EmberDeep,
    onPrimaryContainer = Color(0xFFFFE3D1),
    secondary = NeonCyan,
    onSecondary = Color(0xFF001F29),
    background = Anvil,
    onBackground = Color(0xFFE6ECF7),
    surface = Iron,
    onSurface = Color(0xFFE6ECF7),
    surfaceVariant = Steel,
    onSurfaceVariant = Color(0xFFAEB8CC),
    outline = Color(0xFF4A5670),
)

private val LightScheme = lightColorScheme(
    primary = EmberDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDCC6),
    onPrimaryContainer = Color(0xFF331200),
    secondary = CyanDeep,
    onSecondary = Color.White,
    background = Color(0xFFF6F4F2),
    onBackground = Color(0xFF1B1C1F),
    surface = Color.White,
    onSurface = Color(0xFF1B1C1F),
    surfaceVariant = Color(0xFFECE7E3),
    onSurfaceVariant = Color(0xFF4F4A46),
    outline = Color(0xFF8A847F),
)

private val DarkForge = ForgeColors(
    focusRing = NeonOrange,
    supported = Color(0xFF5FD38A),
    experimental = Color(0xFFFFC857),
    unsupported = Color(0xFFFF6B6B),
)

private val LightForge = ForgeColors(
    focusRing = EmberDeep,
    supported = Color(0xFF1E7A43),
    experimental = Color(0xFF8A5A00),
    unsupported = Color(0xFFB3261E),
)

val LocalForgeColors = staticCompositionLocalOf { DarkForge }

private val ForgeTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(fontWeight = FontWeight.Black, letterSpacing = 1.sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = 0.5.sp),
    )
}

@Composable
fun RetroForgeTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalForgeColors provides if (dark) DarkForge else LightForge) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = ForgeTypography,
            content = content,
        )
    }
}
