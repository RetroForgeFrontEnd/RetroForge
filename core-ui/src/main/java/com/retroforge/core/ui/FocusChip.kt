package com.retroforge.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** A small secondary action (e.g. "Move up", "Remove") with the same focus ring as [FocusCard]. */
@Composable
fun FocusChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    FocusTick(focused)
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = { UiSounds.select(); onClick() },
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = colors.surfaceVariant,
        border = if (focused) BorderStroke(3.dp, LocalForgeColors.current.focusRing) else BorderStroke(1.dp, colors.outline.copy(alpha = 0.4f)),
        interactionSource = interaction,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) colors.onSurface else colors.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}
