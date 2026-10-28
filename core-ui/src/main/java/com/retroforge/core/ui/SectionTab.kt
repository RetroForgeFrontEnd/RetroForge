package com.retroforge.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** One entry of the main section navigation (rail on wide screens, bar on phones). */
@Composable
fun SectionTab(
    label: String,
    icon: Painter,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Narrow padding and a smaller one-line label, for a phone's bottom bar. */
    compact: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val colors = MaterialTheme.colorScheme
    val content = if (selected) colors.primary else colors.onSurfaceVariant
    Surface(
        onClick = onClick,
        modifier = modifier.semantics { this.selected = selected; role = Role.Tab },
        shape = RoundedCornerShape(14.dp),
        color = if (selected) colors.primary.copy(alpha = 0.14f) else Color.Transparent,
        border = if (focused) BorderStroke(3.dp, LocalForgeColors.current.focusRing) else null,
        interactionSource = interaction,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = if (compact) 4.dp else 14.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(26.dp))
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
                color = content,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}
