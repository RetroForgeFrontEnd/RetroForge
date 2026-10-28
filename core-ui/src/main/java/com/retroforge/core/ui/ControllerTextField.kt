package com.retroforge.core.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation

/**
 * A text box that works with a controller or TV remote as well as touch. Moving onto it with the d-pad only
 * highlights it (no keyboard popping up and trapping focus); A / OK opens the keyboard; up or down leaves it.
 * A touch tap opens the keyboard straight away, as usual.
 */
@Composable
fun ControllerTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    var editing by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val interaction = remember { MutableInteractionSource() }
    LaunchedEffect(interaction) {
        interaction.interactions.collect { if (it is PressInteraction.Release) editing = true }
    }
    LaunchedEffect(editing) {
        if (editing) keyboard?.show() else keyboard?.hide()
    }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        // Read-only until chosen, so the keyboard only appears when asked for.
        readOnly = !editing,
        singleLine = singleLine,
        label = label,
        visualTransformation = visualTransformation,
        interactionSource = interaction,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { editing = false }),
        modifier = modifier
            .onFocusChanged { if (!it.isFocused) editing = false }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    !editing && (event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.ButtonA) -> {
                        editing = true
                        true
                    }
                    // Up/down always leave the box (a text field would otherwise keep them to move its cursor).
                    event.key == Key.DirectionDown || event.key == Key.DirectionUp -> {
                        editing = false
                        focus.moveFocus(if (event.key == Key.DirectionDown) FocusDirection.Down else FocusDirection.Up)
                        true
                    }
                    !editing && (event.key == Key.DirectionLeft || event.key == Key.DirectionRight) -> {
                        focus.moveFocus(if (event.key == Key.DirectionLeft) FocusDirection.Left else FocusDirection.Right)
                        true
                    }
                    editing && event.key == Key.Back -> {
                        editing = false
                        true
                    }
                    else -> false
                }
            },
    )
}
