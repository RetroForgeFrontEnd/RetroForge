package com.retroforge.core.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * A card that is reachable by D-pad/controller and shows an unmistakable focus ring.
 * Pass [onClick] = null for a focusable, read-only card (still navigable so TV users can read it).
 */
@Composable
fun FocusCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    selected: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.03f else 1f, label = "focusScale")
    val forge = LocalForgeColors.current
    val border = when {
        focused -> BorderStroke(3.dp, forge.focusRing)
        selected -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    }
    val shape = RoundedCornerShape(16.dp)
    val surfaceModifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale }
    val body: @Composable () -> Unit = { Column(Modifier.padding(20.dp), content = content) }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = surfaceModifier,
            shape = shape,
            border = border,
            color = MaterialTheme.colorScheme.surface,
            interactionSource = interaction,
            content = body,
        )
    } else {
        Surface(
            modifier = surfaceModifier.focusable(interactionSource = interaction),
            shape = shape,
            border = border,
            color = MaterialTheme.colorScheme.surface,
            content = body,
        )
    }
}
