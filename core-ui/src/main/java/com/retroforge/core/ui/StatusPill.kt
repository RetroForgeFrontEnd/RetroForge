package com.retroforge.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class StatusTone { GOOD, CAUTION, BAD, NEUTRAL }

@Composable
fun StatusPill(text: String, tone: StatusTone, modifier: Modifier = Modifier) {
    val forge = LocalForgeColors.current
    val color: Color = when (tone) {
        StatusTone.GOOD -> forge.supported
        StatusTone.CAUTION -> forge.experimental
        StatusTone.BAD -> forge.unsupported
        StatusTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        modifier = modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}
