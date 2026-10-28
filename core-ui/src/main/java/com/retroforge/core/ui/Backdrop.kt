package com.retroforge.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import com.retroforge.theme.BackgroundStyle
import kotlin.math.cos
import kotlin.math.sin

/** The active theme's page background (flat or gradient) with its scanlines and vignette, behind [content]. */
@Composable
fun ThemeBackdrop(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val theme = LocalThemeSpec.current
    val base = MaterialTheme.colorScheme.background
    val dark = base.luminance() < 0.5f
    val colors = (if (dark) theme.background.dark else theme.background.light).map { Color(it.toInt()) }
    val angle = Math.toRadians(theme.background.angle.toDouble())
    val scanlines = theme.scanlines
    val vignette = theme.vignette
    Box(
        modifier
            .fillMaxSize()
            .drawWithContent {
                val brush = when {
                    colors.size < 2 || theme.background.style == BackgroundStyle.SOLID -> SolidColor(colors.firstOrNull() ?: base)
                    theme.background.style == BackgroundStyle.RADIAL ->
                        Brush.radialGradient(colors, center = Offset(size.width * 0.3f, size.height * 0.15f), radius = maxOf(size.width, size.height) * 0.9f)
                    else -> {
                        val dx = cos(angle).toFloat() * size.width / 2
                        val dy = sin(angle).toFloat() * size.height / 2
                        Brush.linearGradient(colors, start = center - Offset(dx, dy), end = center + Offset(dx, dy))
                    }
                }
                drawRect(brush)
                drawContent()
                if (scanlines) {
                    var y = 0f
                    while (y < size.height) {
                        drawRect(Color.Black.copy(alpha = 0.10f), topLeft = Offset(0f, y), size = androidx.compose.ui.geometry.Size(size.width, 1.5f))
                        y += 4f
                    }
                }
                if (vignette) {
                    drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)), center = center, radius = maxOf(size.width, size.height) * 0.75f))
                }
            },
        content = content,
    )
}
