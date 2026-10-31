package com.retroforge.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    /** The selected tab's colour; the theme's primary when not given. */
    accent: Color? = null,
    /** A neon glow round the label in [glowColor], 0 (none) to 1 (full). */
    glow: Float = 0f,
    glowColor: Color = Color.Unspecified,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val colors = MaterialTheme.colorScheme
    val tint = accent ?: colors.primary
    val content = if (selected) tint else colors.onSurfaceVariant
    Surface(
        onClick = onClick,
        modifier = modifier.semantics { this.selected = selected; role = Role.Tab },
        shape = MaterialTheme.shapes.medium,
        color = if (selected) tint.copy(alpha = 0.14f) else Color.Transparent,
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
            val style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge
            // Shrinks a long label ("ROM hacks") to fit a narrow phone tab rather than clipping it.
            val glows = glow > 0f && glowColor != Color.Unspecified
            val text = style.copy(color = content, shadow = if (glows) Shadow(glowColor.copy(alpha = glow), Offset.Zero, blurRadius = 22f) else null)
            Box(contentAlignment = Alignment.Center) {
                // Drawn twice when glowing: one blurred shadow alone is too faint to read as neon.
                repeat(if (glows) 2 else 1) {
                    BasicText(
                        label,
                        style = text,
                        maxLines = 1,
                        softWrap = false,
                        autoSize = TextAutoSize.StepBased(minFontSize = 6.sp, maxFontSize = style.fontSize, stepSize = 0.5.sp),
                    )
                }
            }
        }
    }
}
