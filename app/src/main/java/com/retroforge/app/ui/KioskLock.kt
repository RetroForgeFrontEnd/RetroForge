package com.retroforge.app.ui

import com.retroforge.app.R
import androidx.compose.ui.res.stringResource
import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.key
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.retroforge.app.Kiosk
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.FocusChip
import com.retroforge.core.ui.LocalForgeColors
import com.retroforge.core.ui.StatusPill
import com.retroforge.core.ui.StatusTone
import com.retroforge.core.ui.UiSounds
import com.retroforge.core.ui.str

/** One key of the number pad: big enough to hit with a thumb, with the usual focus ring for a controller. */
@Composable
private fun PadKey(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Surface(
        onClick = { UiSounds.select(); onClick() },
        modifier = modifier.size(width = 76.dp, height = 56.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = if (focused) BorderStroke(3.dp, LocalForgeColors.current.focusRing) else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        interactionSource = interaction,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/**
 * An on-screen number pad for a PIN: works by touch, with a controller (the D-pad moves between keys, A presses) and
 * with a keyboard's number keys. [onDone] gets the digits on OK; [message] shows under the dots (a wrong PIN).
 */
@Composable
fun PinPad(title: String, message: String?, onDone: (String) -> Unit, onCancel: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
    fun type(digit: Char) { if (pin.length < 8) pin += digit }
    fun done() { if (pin.isNotEmpty()) { onDone(pin); pin = "" } }
    Column(
        Modifier.padding(20.dp).onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            when (val code = event.key.nativeKeyCode) {
                in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> { type('0' + (code - KeyEvent.KEYCODE_0)); true }
                in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> { type('0' + (code - KeyEvent.KEYCODE_NUMPAD_0)); true }
                KeyEvent.KEYCODE_DEL -> { pin = pin.dropLast(1); true }
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> { done(); true }
                else -> false
            }
        },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        Text(if (pin.isEmpty()) "–" else "●".repeat(pin.length), fontSize = 26.sp, letterSpacing = 6.sp, color = MaterialTheme.colorScheme.onSurface)
        if (message != null) Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { d -> PadKey(d.toString(), { type(d) }, if (d == '1') Modifier.focusRequester(first) else Modifier) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PadKey("⌫", { pin = pin.dropLast(1) })
            PadKey("0", { type('0') })
            PadKey(stringResource(R.string.kiosk_ok), ::done)
        }
        FocusChip(stringResource(R.string.kiosk_cancel), onClick = onCancel)
    }
}

/** Asks for the kiosk PIN; [onUnlocked] runs once it's right. */
@Composable
fun KioskPinDialog(onUnlocked: () -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    Dialog(onDismissRequest = onCancel) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            PinPad(stringResource(R.string.kiosk_enter_pin), message, onDone = { pin ->
                when {
                    Kiosk.unlock(context, pin) -> onUnlocked()
                    Kiosk.waitLeft() > 0 -> message = str(R.string.kiosk_wait, (Kiosk.waitLeft() + 999) / 1000)
                    else -> message = str(R.string.kiosk_wrong)
                }
            }, onCancel = onCancel)
        }
    }
}

/**
 * For actions kiosk mode guards (deleting, emulator settings): `guard { delete() }` runs it at once when nothing is
 * locked, else after the PIN.
 */
@Composable
fun rememberKioskGuard(): (() -> Unit) -> Unit {
    val context = LocalContext.current
    val pending = remember { mutableStateOf<(() -> Unit)?>(null) }
    pending.value?.let { action -> KioskPinDialog(onUnlocked = { pending.value = null; action() }, onCancel = { pending.value = null }) }
    return remember { { action -> if (Kiosk.isOpen(context)) action() else pending.value = action } }
}

/** A page kiosk mode guards (Settings, the ROM Manager): the PIN pad first, the page once it's right. */
@Composable
fun KioskLocked(onLeave: () -> Unit, content: @Composable () -> Unit) {
    val context = LocalContext.current
    // Read on every composition: leaving RetroForge locks it again (Kiosk.lock), and the page closes with it.
    if (Kiosk.isOpen(context)) {
        content()
        return
    }
    var message by remember { mutableStateOf<String?>(null) }
    ScreenFrame(title = stringResource(R.string.kiosk_locked_title), subtitle = stringResource(R.string.kiosk_locked_body)) {
        FocusCard(modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                PinPad(stringResource(R.string.kiosk_enter_pin), message, onDone = { pin ->
                    when {
                        Kiosk.unlock(context, pin) -> Unit
                        Kiosk.waitLeft() > 0 -> message = str(R.string.kiosk_wait, (Kiosk.waitLeft() + 999) / 1000)
                        else -> message = str(R.string.kiosk_wrong)
                    }
                }, onCancel = onLeave)
            }
        }
        CardBody(stringResource(R.string.kiosk_forgot))
    }
}

/** Settings: turn kiosk mode on with a PIN, change it, or turn it off, and how to get back in without it. */
@Composable
fun KioskCard() {
    val context = LocalContext.current
    var on by remember { mutableStateOf(Kiosk.isOn(context)) }
    // Choosing a PIN: null = not choosing, "" = first entry, else the first entry waiting to be confirmed.
    var choosing by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.kiosk_title)) { StatusPill(if (on) stringResource(R.string.settings_on) else stringResource(R.string.player_off), if (on) StatusTone.GOOD else StatusTone.NEUTRAL) }
        CardBody(stringResource(R.string.kiosk_body))
    }
    ChipRow {
        FocusChip(if (on) stringResource(R.string.kiosk_change_pin) else stringResource(R.string.kiosk_set_pin), onClick = { message = null; choosing = "" })
        if (on) FocusChip(stringResource(R.string.kiosk_turn_off), onClick = { Kiosk.turnOff(context); on = false })
    }
    FocusCard(modifier = Modifier.fillMaxWidth()) { CardBody(stringResource(R.string.kiosk_forgot)) }
    choosing?.let { firstPin ->
        Dialog(onDismissRequest = { choosing = null }) {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                // A new key per step, so the pad starts empty with the focus on 1 again.
                androidx.compose.runtime.key(firstPin) {
                    PinPad(
                        if (firstPin.isEmpty()) stringResource(R.string.kiosk_new_pin) else stringResource(R.string.kiosk_confirm_pin),
                        message,
                        onDone = { pin ->
                            when {
                                firstPin.isEmpty() && pin.length < 4 -> message = str(R.string.kiosk_new_pin)
                                firstPin.isEmpty() -> { message = null; choosing = pin }
                                pin == firstPin -> { Kiosk.setPin(context, pin); on = true; choosing = null }
                                else -> { message = str(R.string.kiosk_mismatch); choosing = "" }
                            }
                        },
                        onCancel = { choosing = null },
                    )
                }
            }
        }
    }
}
