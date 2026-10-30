package com.retroforge.pcplayer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import com.retroforge.pcplayer.VirtualPad.Axis
import com.retroforge.pcplayer.VirtualPad.Button
import com.winlator.cmod.xserver.Pointer
import com.winlator.cmod.xserver.XKeycode
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * On-screen Xbox controller for a Windows game, drawn over the game. Games with controller support get it as an
 * Xbox 360 pad (through [VirtualPad]); for games without, [keyboardMode] makes the same controls send keyboard and
 * mouse instead, as Steam Input does: left stick = WASD, right stick = mouse, RT/LT = left/right click, A = Space,
 * B = Ctrl, X = R, Y = E, LB = Q, RB = next weapon (wheel), Start = Esc, Back = Tab, L3 = Shift, R3 = middle click,
 * D-pad = arrow keys. A touch that starts away from every control isn't taken, so it reaches the game underneath
 * as the touch mouse.
 */
@SuppressLint("ViewConstructor")
class PcControlsView(context: Context, private val session: PcSession) : View(context) {

    /** What a button does while held. */
    private sealed interface Action {
        data class Pad(val button: Button) : Action
        data class Trigger(val axis: Axis) : Action
        data class Key(val key: XKeycode) : Action
        data class Mouse(val button: Pointer.Button) : Action
    }

    /**
     * A control placed by its centre in dp: x from the left edge (or from the right when negative, or from the
     * middle when [fromCentre]), y from the bottom edge.
     */
    private sealed class Control(val x: Float, val y: Float, val radius: Float, val label: String, val fromCentre: Boolean) {
        var cx = 0f
        var cy = 0f
        var r = 0f
        var pointer = -1
    }

    private class Stick(x: Float, y: Float, radius: Float, val left: Boolean) : Control(x, y, radius, "", false) {
        var dx = 0f
        var dy = 0f
        val keysDown = mutableSetOf<XKeycode>()
    }

    private class Press(x: Float, y: Float, radius: Float, label: String, val action: Action, fromCentre: Boolean = false) :
        Control(x, y, radius, label, fromCentre) {
        /** What was sent when it went down, so letting go releases the same thing even if the mode changed. */
        var sent: Action? = null
    }

    /** Send keyboard and mouse instead of the controller (games without controller support). */
    var keyboardMode = false
        set(value) {
            releaseAll()
            field = value
            session.pad.connected = !value
        }

    private val controls: List<Control> = listOf(
        Stick(110f, 110f, 70f, left = true),
        Stick(-250f, 80f, 55f, left = false),
        Press(-100f, 82f, 26f, "A", Action.Pad(Button.A)),
        Press(-52f, 130f, 26f, "B", Action.Pad(Button.B)),
        Press(-148f, 130f, 26f, "X", Action.Pad(Button.X)),
        Press(-100f, 178f, 26f, "Y", Action.Pad(Button.Y)),
        Press(260f, 110f, 22f, "▲", Action.Pad(Button.UP)),
        Press(260f, 30f, 22f, "▼", Action.Pad(Button.DOWN)),
        Press(220f, 70f, 22f, "◀", Action.Pad(Button.LEFT)),
        Press(300f, 70f, 22f, "▶", Action.Pad(Button.RIGHT)),
        Press(50f, 235f, 28f, "LB", Action.Pad(Button.LB)),
        Press(50f, 300f, 28f, "LT", Action.Trigger(Axis.LEFT_TRIGGER)),
        Press(-50f, 235f, 28f, "RB", Action.Pad(Button.RB)),
        Press(-50f, 300f, 28f, "RT", Action.Trigger(Axis.RIGHT_TRIGGER)),
        Press(-45f, 28f, 20f, "⧉", Action.Pad(Button.BACK), fromCentre = true),
        Press(45f, 28f, 20f, "☰", Action.Pad(Button.START), fromCentre = true),
        Press(-180f, 175f, 18f, "L3", Action.Pad(Button.LEFT_STICK)),
        Press(-320f, 175f, 18f, "R3", Action.Pad(Button.RIGHT_STICK)),
    )

    private val density = resources.displayMetrics.density

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(70, 0, 0, 0) }
    private val pressedFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(110, 255, 255, 255) }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = Color.argb(150, 255, 255, 255)
    }
    private val knob = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(120, 255, 255, 255) }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 255, 255, 255)
        setShadowLayer(3f, 0f, 1f, Color.BLACK)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    /** In keyboard mode the right stick moves the mouse for as long as it's held over, like a real stick. */
    private val mouseStick = object : Runnable {
        override fun run() {
            val stick = controls.first { it is Stick && !it.left } as Stick
            if (!keyboardMode || stick.pointer < 0) return
            val speed = hypot(stick.dx, stick.dy)
            if (speed > STICK_DEAD_ZONE) {
                // Faster towards the edge: fine aim near the middle.
                val scale = MOUSE_SPEED * speed
                session.xServer.injectPointerMoveDelta((stick.dx * scale).roundToInt(), (stick.dy * scale).roundToInt())
            }
            postOnAnimation(this)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        // Laid out for a phone; a little smaller so they cover less of the game.
        val unit = density * SIZE
        for (control in controls) {
            val x = control.x * unit
            control.cx = when {
                control.fromCentre -> w / 2f + x
                x < 0 -> w + x
                else -> x
            }
            control.cy = h - control.y * unit
            control.r = control.radius * unit
        }
    }

    override fun onDraw(canvas: Canvas) {
        for (control in controls) {
            val held = control.pointer >= 0
            canvas.drawCircle(control.cx, control.cy, control.r, if (held && control is Press) pressedFill else fill)
            canvas.drawCircle(control.cx, control.cy, control.r, ring)
            if (control is Stick) {
                val k = control.r * 0.45f
                canvas.drawCircle(control.cx + control.dx * (control.r - k), control.cy + control.dy * (control.r - k), k, knob)
            }
            if (control.label.isNotEmpty()) {
                text.textSize = 0.55f * control.r
                canvas.drawText(control.label, control.cx, control.cy + text.textSize * 0.35f, text)
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = event.actionIndex
                val hit = controlAt(event.getX(i), event.getY(i))
                // Nothing here: let the touch through to the game (touch mouse), if it's the first finger.
                if (hit == null) return event.actionMasked != MotionEvent.ACTION_DOWN
                hit.pointer = event.getPointerId(i)
                when (hit) {
                    is Press -> press(hit, true)
                    is Stick -> {
                        move(hit, event.getX(i), event.getY(i))
                        if (!hit.left && keyboardMode) postOnAnimation(mouseStick)
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> for (i in 0 until event.pointerCount) {
                val id = event.getPointerId(i)
                (controls.firstOrNull { it.pointer == id } as? Stick)?.let { move(it, event.getX(i), event.getY(i)) }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val id = event.getPointerId(event.actionIndex)
                controls.firstOrNull { it.pointer == id }?.let { end(it) }
            }
            MotionEvent.ACTION_CANCEL -> releaseAll()
        }
        invalidate()
        return true
    }

    private fun controlAt(x: Float, y: Float): Control? = controls
        .filter { it.pointer < 0 && hypot(x - it.cx, y - it.cy) <= it.r * 1.25f }
        .minByOrNull { hypot(x - it.cx, y - it.cy) / it.r }

    private fun move(stick: Stick, x: Float, y: Float) {
        var dx = (x - stick.cx) / stick.r
        var dy = (y - stick.cy) / stick.r
        val length = hypot(dx, dy)
        if (length > 1f) {
            dx /= length
            dy /= length
        }
        stick.dx = dx
        stick.dy = dy
        when {
            !keyboardMode -> {
                session.pad.setAxis(if (stick.left) Axis.LEFT_X else Axis.RIGHT_X, dx)
                session.pad.setAxis(if (stick.left) Axis.LEFT_Y else Axis.RIGHT_Y, dy)
            }
            stick.left -> {
                val wanted = buildSet {
                    if (dy < -WASD_THRESHOLD) add(XKeycode.KEY_W)
                    if (dy > WASD_THRESHOLD) add(XKeycode.KEY_S)
                    if (dx < -WASD_THRESHOLD) add(XKeycode.KEY_A)
                    if (dx > WASD_THRESHOLD) add(XKeycode.KEY_D)
                }
                (stick.keysDown - wanted).forEach { session.xServer.injectKeyRelease(it) }
                (wanted - stick.keysDown).forEach { session.xServer.injectKeyPress(it) }
                stick.keysDown.clear()
                stick.keysDown.addAll(wanted)
            }
            // The right stick's mouse movement runs in mouseStick.
        }
    }

    private fun end(control: Control) {
        control.pointer = -1
        when (control) {
            is Press -> press(control, false)
            is Stick -> {
                move(control, control.cx, control.cy)
                control.dx = 0f
                control.dy = 0f
            }
        }
    }

    private fun press(control: Press, down: Boolean) {
        val action = if (down) (if (keyboardMode) asKeyboard(control.action) else control.action) else control.sent
        control.sent = if (down) action else null
        val xServer = session.xServer
        when (action) {
            null -> Unit
            is Action.Pad -> session.pad.setButton(action.button, down)
            is Action.Trigger -> session.pad.setAxis(action.axis, if (down) 1f else 0f)
            is Action.Key -> if (down) xServer.injectKeyPress(action.key) else xServer.injectKeyRelease(action.key)
            is Action.Mouse -> if (down) xServer.injectPointerButtonPress(action.button) else xServer.injectPointerButtonRelease(action.button)
        }
    }

    /** Keys a game wants instead of the usual ones (see [KEY_LAYOUTS]), set from the game's .exe name. */
    var keyLayout: Map<Button, XKeycode> = emptyMap()

    private fun asKeyboard(action: Action): Action = when (action) {
        is Action.Trigger -> Action.Mouse(if (action.axis == Axis.RIGHT_TRIGGER) Pointer.Button.BUTTON_LEFT else Pointer.Button.BUTTON_RIGHT)
        is Action.Pad -> keyLayout[action.button]?.let { Action.Key(it) } ?: when (action.button) {
            Button.A -> Action.Key(XKeycode.KEY_SPACE)
            Button.B -> Action.Key(XKeycode.KEY_CTRL_L)
            Button.X -> Action.Key(XKeycode.KEY_R)
            Button.Y -> Action.Key(XKeycode.KEY_E)
            Button.LB -> Action.Key(XKeycode.KEY_Q)
            Button.RB -> Action.Mouse(Pointer.Button.BUTTON_SCROLL_DOWN)
            Button.START -> Action.Key(XKeycode.KEY_ESC)
            Button.BACK, Button.GUIDE -> Action.Key(XKeycode.KEY_TAB)
            Button.LEFT_STICK -> Action.Key(XKeycode.KEY_SHIFT_L)
            Button.RIGHT_STICK -> Action.Mouse(Pointer.Button.BUTTON_MIDDLE)
            Button.UP -> Action.Key(XKeycode.KEY_UP)
            Button.DOWN -> Action.Key(XKeycode.KEY_DOWN)
            Button.LEFT -> Action.Key(XKeycode.KEY_LEFT)
            Button.RIGHT -> Action.Key(XKeycode.KEY_RIGHT)
        }
        else -> action
    }

    // A real controller (Bluetooth/USB) goes through the same controls, so keyboard mode works for it too.
    private val hardwareSent = mutableMapOf<Any, Action>()
    private val hardwareSticks = mapOf(true to Stick(0f, 0f, 1f, left = true), false to Stick(0f, 0f, 1f, left = false))

    /** A real controller's button. */
    fun hardwareButton(button: Button, down: Boolean) = hardwarePress(button, Action.Pad(button), down)

    /** A real controller's trigger, from 0 to 1. */
    fun hardwareTrigger(axis: Axis, value: Float) {
        if (!keyboardMode) {
            session.pad.setAxis(axis, value)
            return
        }
        hardwarePress(axis, Action.Trigger(axis), value > 0.5f)
    }

    /** A real controller's stick, each from -1 to 1. */
    fun hardwareStick(left: Boolean, dx: Float, dy: Float) {
        val stick = hardwareSticks.getValue(left)
        val wasHeld = stick.pointer >= 0
        val held = hypot(dx, dy) > STICK_DEAD_ZONE
        stick.pointer = if (held) 0 else -1
        stick.r = 1f
        move(stick, if (held) dx else 0f, if (held) dy else 0f)
        if (!left && keyboardMode && held && !wasHeld) postOnAnimation(hardwareMouseStick)
    }

    private val hardwareMouseStick = object : Runnable {
        override fun run() {
            val stick = hardwareSticks.getValue(false)
            if (!keyboardMode || stick.pointer < 0) return
            val scale = MOUSE_SPEED * hypot(stick.dx, stick.dy)
            session.xServer.injectPointerMoveDelta((stick.dx * scale).roundToInt(), (stick.dy * scale).roundToInt())
            postOnAnimation(this)
        }
    }

    private fun hardwarePress(id: Any, action: Action, down: Boolean) {
        if (down == (id in hardwareSent)) return
        val sent = if (down) (if (keyboardMode) asKeyboard(action) else action) else hardwareSent.remove(id) ?: return
        if (down) hardwareSent[id] = sent
        val xServer = session.xServer
        when (sent) {
            is Action.Pad -> session.pad.setButton(sent.button, down)
            is Action.Trigger -> session.pad.setAxis(sent.axis, if (down) 1f else 0f)
            is Action.Key -> if (down) xServer.injectKeyPress(sent.key) else xServer.injectKeyRelease(sent.key)
            is Action.Mouse -> if (down) xServer.injectPointerButtonPress(sent.button) else xServer.injectPointerButtonRelease(sent.button)
        }
    }

    /** Lets go of everything held (switching mode, hiding the controls, opening the menu). */
    fun releaseAll() {
        for (control in controls) if (control.pointer >= 0) end(control)
        hardwareSent.keys.toList().forEach { id ->
            hardwarePress(id, Action.Pad(Button.A), false)
        }
        hardwareSticks.values.forEach { hardwareStick(it.left, 0f, 0f) }
        session.pad.reset()
        invalidate()
    }

    private companion object {
        const val SIZE = 0.85f
        const val WASD_THRESHOLD = 0.35f
        const val STICK_DEAD_ZONE = 0.15f
        /** Mouse movement per frame (X screen pixels) with the right stick fully over. */
        const val MOUSE_SPEED = 14f
    }
}

/**
 * Keyboard-mode keys for games whose own keys differ from the usual ones, by the game's .exe name (lower case).
 * Toby Fox's games use Z to confirm, X to cancel and C for the menu.
 */
internal val KEY_LAYOUTS: Map<String, Map<VirtualPad.Button, XKeycode>> = mapOf(
    "undertale.exe" to mapOf(VirtualPad.Button.A to XKeycode.KEY_Z, VirtualPad.Button.B to XKeycode.KEY_X, VirtualPad.Button.Y to XKeycode.KEY_C),
    "deltarune.exe" to mapOf(VirtualPad.Button.A to XKeycode.KEY_Z, VirtualPad.Button.B to XKeycode.KEY_X, VirtualPad.Button.Y to XKeycode.KEY_C),
)
