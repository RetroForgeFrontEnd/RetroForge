package com.retroforge.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** The two-tone neon "RETROFORGE" logotype; [short] renders the "RF" monogram. */
@Composable
fun Wordmark(modifier: Modifier = Modifier, fontSize: TextUnit = 40.sp, short: Boolean = false) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val retro = if (dark) NeonOrange else MaterialTheme.colorScheme.primary
    val forge = if (dark) NeonCyan else MaterialTheme.colorScheme.secondary
    fun glow(color: Color) =
        if (dark) Shadow(color = color.copy(alpha = 0.75f), blurRadius = 18f) else null
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = retro, shadow = glow(retro))) { append(if (short) "R" else "RETRO") }
            withStyle(SpanStyle(color = forge, shadow = glow(forge))) { append(if (short) "F" else "FORGE") }
        },
        style = TextStyle(fontSize = fontSize, fontWeight = FontWeight.Black, letterSpacing = (fontSize.value * 0.06f).sp),
        modifier = modifier,
    )
}

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
