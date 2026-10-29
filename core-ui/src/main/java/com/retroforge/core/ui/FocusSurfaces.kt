package com.retroforge.core.ui

import com.retroforge.theme.BackgroundStyle
import androidx.compose.ui.draw.shadow
import androidx.compose.animation.core.animateDpAsState
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
    FocusTick(focused)
    val scale by animateFloatAsState(if (focused) 1.03f else 1f, label = "focusScale")
    val forge = LocalForgeColors.current
    val border = when {
        focused -> BorderStroke(3.dp, forge.focusRing)
        selected -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    }
    val shape = MaterialTheme.shapes.medium
    val theme = LocalThemeSpec.current
    // Glowing themes light the focused card up in the focus colour.
    val glow by animateDpAsState(if (focused && theme.focusGlow) 20.dp else 0.dp, label = "focusGlow")
    val surfaceModifier = modifier
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .shadow(glow, shape, ambientColor = forge.focusRing, spotColor = forge.focusRing)
    // Over a gradient background, cards let a little of it through.
    val cardColor = MaterialTheme.colorScheme.surface.copy(alpha = if (theme.background.style == BackgroundStyle.SOLID) 1f else 0.86f)
    val body: @Composable () -> Unit = { Column(Modifier.padding(20.dp), content = content) }
    if (onClick != null) {
        Surface(
            onClick = { UiSounds.select(); onClick() },
            modifier = surfaceModifier,
            shape = shape,
            border = border,
            color = cardColor,
            interactionSource = interaction,
            content = body,
        )
    } else {
        Surface(
            modifier = surfaceModifier.focusable(interactionSource = interaction),
            shape = shape,
            border = border,
            color = cardColor,
            content = body,
        )
    }
}

/** A tick when this item gains focus by controller or remote (touch doesn't move focus around, so it stays quiet). */
@Composable
internal fun FocusTick(focused: Boolean) {
    val keyboard = androidx.compose.ui.platform.LocalInputModeManager.current.inputMode == androidx.compose.ui.input.InputMode.Keyboard
    androidx.compose.runtime.LaunchedEffect(focused) { if (focused && keyboard) UiSounds.move() }
}
