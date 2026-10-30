package com.retroforge.app.ui.settings

import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.retroforge.app.R
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.app.ui.ScreenFrame
import com.retroforge.core.ui.FocusCard

/**
 * While the controller test is open, every controller button and stick movement comes here instead of moving around
 * the menus (MainActivity hands them over), so A, B, L1 and R1 can be tested too. Phone keys still work as usual.
 */
object ControllerTap {
    var listener: ((InputEvent) -> Unit)? = null

    /** True when the event was a controller's and the test took it. */
    fun offer(event: InputEvent): Boolean {
        val listener = listener ?: return false
        val source = event.source
        val fromController = source and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
            source and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK ||
            source and InputDevice.SOURCE_DPAD == InputDevice.SOURCE_DPAD && event.device?.isVirtual == false &&
            (event as? KeyEvent)?.keyCode != KeyEvent.KEYCODE_BACK
        if (!fromController) return false
        listener(event)
        return true
    }
}

private class Button(val label: String, vararg val keys: Int)

private val buttons = listOf(
    Button("A", KeyEvent.KEYCODE_BUTTON_A), Button("B", KeyEvent.KEYCODE_BUTTON_B),
    Button("X", KeyEvent.KEYCODE_BUTTON_X), Button("Y", KeyEvent.KEYCODE_BUTTON_Y),
    Button("L1", KeyEvent.KEYCODE_BUTTON_L1), Button("R1", KeyEvent.KEYCODE_BUTTON_R1),
    Button("L2", KeyEvent.KEYCODE_BUTTON_L2), Button("R2", KeyEvent.KEYCODE_BUTTON_R2),
    Button("L3", KeyEvent.KEYCODE_BUTTON_THUMBL), Button("R3", KeyEvent.KEYCODE_BUTTON_THUMBR),
    Button("Select", KeyEvent.KEYCODE_BUTTON_SELECT), Button("Start", KeyEvent.KEYCODE_BUTTON_START),
    Button("Home", KeyEvent.KEYCODE_BUTTON_MODE),
    Button("↑", KeyEvent.KEYCODE_DPAD_UP), Button("↓", KeyEvent.KEYCODE_DPAD_DOWN),
    Button("←", KeyEvent.KEYCODE_DPAD_LEFT), Button("→", KeyEvent.KEYCODE_DPAD_RIGHT),
)

// Some controllers send B as Back, and A as the d-pad's centre.
private fun normalise(keyCode: Int) = when (keyCode) {
    KeyEvent.KEYCODE_BACK -> KeyEvent.KEYCODE_BUTTON_B
    KeyEvent.KEYCODE_DPAD_CENTER -> KeyEvent.KEYCODE_BUTTON_A
    else -> keyCode
}

// Pretend keys for d-pads that report as a hat axis and triggers that report as an axis.
private const val HAT = 10_000

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ControllerTestScreen() {
    val held = remember { mutableStateMapOf<Int, Boolean>() }
    val seen = remember { mutableStateMapOf<Int, Boolean>() }
    val axes = remember { mutableStateMapOf<Int, Float>() }
    var last by remember { mutableStateOf<Pair<String, String>?>(null) }
    val back = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher

    DisposableEffect(Unit) {
        val handler = Handler(Looper.getMainLooper())
        val leave = Runnable { back?.onBackPressed() }
        fun press(key: Int, down: Boolean) {
            if (down) { held[key] = true; seen[key] = true } else held.remove(key)
        }
        ControllerTap.listener = { event ->
            when (event) {
                is KeyEvent -> {
                    val key = normalise(event.keyCode)
                    val down = event.action == KeyEvent.ACTION_DOWN
                    if (event.repeatCount == 0) {
                        press(key, down)
                        if (down) last = KeyEvent.keyCodeToString(event.keyCode).removePrefix("KEYCODE_") to (event.device?.name ?: "")
                        // Holding B is the way out, since B itself is being tested.
                        if (key == KeyEvent.KEYCODE_BUTTON_B) {
                            if (down) handler.postDelayed(leave, 2000) else handler.removeCallbacks(leave)
                        }
                    }
                }
                is MotionEvent -> {
                    listOf(MotionEvent.AXIS_X, MotionEvent.AXIS_Y, MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ,
                        MotionEvent.AXIS_LTRIGGER, MotionEvent.AXIS_BRAKE, MotionEvent.AXIS_RTRIGGER, MotionEvent.AXIS_GAS,
                        MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y,
                    ).forEach { axes[it] = event.getAxisValue(it) }
                    val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
                    val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
                    press(HAT + KeyEvent.KEYCODE_DPAD_LEFT, hatX < -0.5f)
                    press(HAT + KeyEvent.KEYCODE_DPAD_RIGHT, hatX > 0.5f)
                    press(HAT + KeyEvent.KEYCODE_DPAD_UP, hatY < -0.5f)
                    press(HAT + KeyEvent.KEYCODE_DPAD_DOWN, hatY > 0.5f)
                    if (left(axes) > 0.5f) seen[HAT + KeyEvent.KEYCODE_BUTTON_L2] = true
                    if (right(axes) > 0.5f) seen[HAT + KeyEvent.KEYCODE_BUTTON_R2] = true
                    if (last == null) last = "" to (event.device?.name ?: "")
                }
            }
        }
        onDispose {
            ControllerTap.listener = null
            handler.removeCallbacks(leave)
        }
    }

    ScreenFrame(title = stringResource(R.string.controller_test_title), subtitle = stringResource(R.string.controller_test_subtitle)) {
        FocusCard(modifier = Modifier.fillMaxWidth()) {
            val (key, device) = last ?: ("" to "")
            CardTitle(if (last == null) stringResource(R.string.controller_test_nothing_yet) else device.ifEmpty { "?" })
            if (key.isNotEmpty()) CardBody(stringResource(R.string.controller_test_last_button, key))
            val checked = buttons.count { b -> b.keys.any { seen[it] == true || seen[HAT + it] == true } }
            CardBody(stringResource(R.string.controller_test_checked, checked, buttons.size))
        }
        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle(stringResource(R.string.controller_test_buttons))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                buttons.forEach { b ->
                    val down = b.keys.any { held[it] == true || held[HAT + it] == true } ||
                        (b.label == "L2" && left(axes) > 0.5f) || (b.label == "R2" && right(axes) > 0.5f)
                    val wasSeen = b.keys.any { seen[it] == true || seen[HAT + it] == true }
                    ButtonLamp(b.label, down, wasSeen)
                }
            }
        }
        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle(stringResource(R.string.controller_test_sticks))
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp), verticalAlignment = Alignment.CenterVertically) {
                Stick(stringResource(R.string.controller_test_left_stick), axes[MotionEvent.AXIS_X] ?: 0f, axes[MotionEvent.AXIS_Y] ?: 0f)
                Stick(stringResource(R.string.controller_test_right_stick), axes[MotionEvent.AXIS_Z] ?: 0f, axes[MotionEvent.AXIS_RZ] ?: 0f)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.width(120.dp)) {
                    Text("L2", style = MaterialTheme.typography.labelLarge)
                    LinearProgressIndicator(progress = { left(axes) }, modifier = Modifier.fillMaxWidth())
                    Text("R2", style = MaterialTheme.typography.labelLarge)
                    LinearProgressIndicator(progress = { right(axes) }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

private fun left(axes: Map<Int, Float>) = maxOf(axes[MotionEvent.AXIS_LTRIGGER] ?: 0f, axes[MotionEvent.AXIS_BRAKE] ?: 0f).coerceIn(0f, 1f)
private fun right(axes: Map<Int, Float>) = maxOf(axes[MotionEvent.AXIS_RTRIGGER] ?: 0f, axes[MotionEvent.AXIS_GAS] ?: 0f).coerceIn(0f, 1f)

@Composable
private fun ButtonLamp(label: String, down: Boolean, seen: Boolean) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    Text(
        label,
        style = MaterialTheme.typography.titleMedium,
        color = if (down) colors.onPrimary else if (seen) colors.primary else colors.onSurfaceVariant,
        modifier = Modifier
            .background(if (down) colors.primary else colors.surfaceVariant, shape)
            .border(2.dp, if (seen) colors.primary else colors.outline, shape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun Stick(label: String, x: Float, y: Float) {
    val colors = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(modifier = Modifier.size(96.dp)) {
            val r = size.minDimension / 2
            drawCircle(colors.outline, r - 2, style = Stroke(3f))
            drawCircle(colors.primary, r / 5, center + Offset(x * (r - r / 5), y * (r - r / 5)))
        }
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text("%+.2f  %+.2f".format(x, y), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
    }
}
