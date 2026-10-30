package com.retroforge.pcplayer

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retroforge.pcplayer.steam.SteamAccount
import com.retroforge.pcplayer.steam.SteamCloudSync
import com.retroforge.pcplayer.steam.SteamGameSetup
import com.winlator.cmod.widget.XServerView
import com.winlator.cmod.xserver.Pointer
import com.winlator.cmod.xserver.ScreenInfo
import com.winlator.cmod.xserver.Window
import com.winlator.cmod.xserver.XKeycode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File

/**
 * RetroForge's own player for Windows PC games: full screen, landscape. A loading screen covers Wine starting up
 * until the game's window appears. An on-screen Xbox controller sits over the game (for games without controller
 * support its buttons send keyboard and mouse, as does a real controller); touches elsewhere work as a mouse (tap = left click where you touch, drag = move with the button held,
 * two-finger tap = right click). Back, the ☰ button or a swipe in from either edge opens the same kind of menu as
 * the retro player, and pauses the game while it's open.
 */
class PcPlayerActivity : ComponentActivity() {

    private sealed interface Phase {
        data class Loading(val step: String) : Phase
        data class Failed(val message: String) : Phase
        data object Playing : Phase
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var runtime: PcRuntime
    private lateinit var frame: FrameLayout
    private lateinit var title: String
    private var session: PcSession? = null
    private var view: XServerView? = null
    private var controlsView: PcControlsView? = null
    private var quitting = false
    /** When the game's window first showed (null until it does); and whether this run is in the history yet. */
    private var shownAt: Long? = null
    private var recorded = false
    /** Steam Cloud saves for a Steam game (null for other games). */
    private var cloud: SteamCloudSync? = null

    private var phase by mutableStateOf<Phase>(Phase.Loading("Starting"))
    private var menuOpen by mutableStateOf(false)
    // Both remembered per game.
    private var padShown by mutableStateOf(true)
    private var keyboardMode by mutableStateOf(false)
    /** Beside a game narrower than the screen (4:3): its picture blurred, or black. For all games. */
    private var blurredSides by mutableStateOf(true)
    private var exitMessage by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        runtime = PcRuntime(this)
        frame = FrameLayout(this).apply { setBackgroundColor(android.graphics.Color.BLACK) }
        frame.addView(ComposeView(this).apply { setContent { Overlay() } }, matchParent())
        setContentView(frame)
        hideSystemBars()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    session == null -> finish()
                    menuOpen -> closeMenu()
                    else -> openMenu()
                }
            }
        })

        val exe = intent.getStringExtra(EXTRA_EXE)?.let(::File)
        title = intent.getStringExtra(EXTRA_TITLE) ?: exe?.nameWithoutExtension ?: "PC game"
        if (exe == null || !exe.isFile) {
            phase = Phase.Failed("The game's .exe wasn't found:\n${exe?.path ?: "(none given)"}")
            return
        }
        scope.launch {
            padShown = prefs().getBoolean("pad_shown:${exe.path}", true)
            // Keyboard & mouse unless the player chose the Xbox controller: games don't yet receive the virtual
            // Xbox pad's input through Wine's XInput (the pad reaches Wine's controller driver, and stops there).
            // usesXInput() picks the default again once that works.
            keyboardMode = if (prefs().contains("keyboard_mode:${exe.path}")) prefs().getBoolean("keyboard_mode:${exe.path}", true)
            else XINPUT_PAD_WORKS.not() || withContext(Dispatchers.IO) { !usesXInput(exe) }
            launch {
                runtime.state.collectLatest { state ->
                    if (state is RuntimeState.Working) phase = Phase.Loading("${state.step}… ${state.percent}%")
                }
            }
            if (!runtime.ensure()) {
                phase = Phase.Failed((runtime.state.value as? RuntimeState.Failed)?.reason ?: "The PC runtime couldn't be installed")
                return@launch
            }
            val steam = SteamGameSetup(runtime.root, SteamAccount(this@PcPlayerActivity))
            if (steam.steamDlls(exe.parentFile!!).isNotEmpty()) {
                phase = Phase.Loading("Checking your Steam account")
                val appId = steam.appId(exe.parentFile!!, intent.getIntExtra(EXTRA_STEAM_APP_ID, 0))
                val problem = if (appId == null) {
                    "This game uses Steam, but RetroForge couldn't tell which Steam game it is."
                } else {
                    runCatching { withContext(Dispatchers.IO) { steam.prepare(exe.parentFile!!, appId) } }.exceptionOrNull()?.let {
                        it.message ?: "Steam couldn't be set up for this game"
                    }
                }
                if (problem != null) {
                    phase = Phase.Failed(problem)
                    return@launch
                }
                // Saves newer in Steam Cloud (from a PC, a Deck, another phone) come down before the game starts. If
                // Steam can't be reached the game starts anyway with the saves already here.
                val sync = SteamCloudSync(SteamAccount(this@PcPlayerActivity), runtime.root, File(runtime.prefix, "drive_c"), exe.parentFile!!, appId!!)
                cloud = sync
                phase = Phase.Loading("Checking Steam Cloud")
                runCatching { withContext(Dispatchers.IO) { withTimeout(CLOUD_TIMEOUT_MS) { sync.download { phase = Phase.Loading(it) } } } }
                    .onFailure { Log.w(TAG, "Steam Cloud download failed", it) }
            }
            startGame(exe)
        }
    }

    private fun matchParent() = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)

    private fun startGame(exe: File) {
        phase = Phase.Loading("Starting Windows")
        val newSession = PcSession(this, runtime, exe, screenSize())
        val xView = XServerView(this, newSession.xServer)
        newSession.xServer.setRenderer(xView.renderer)
        // The game's own window fills the screen (Wine's desktop stays behind it), and the console window some games
        // open (text output, warnings) isn't shown.
        xView.renderer.setForceFullscreenWMClass(newSession.program.name.lowercase())
        xView.renderer.setUnviewableWMClasses("conhost.exe", "wineconsole")
        blurredSides = prefs().getBoolean("blurred_sides", true)
        xView.renderer.setBlurredSides(blurredSides)
        xView.isFocusable = true
        xView.isFocusableInTouchMode = true
        xView.setOnTouchListener(TouchMouse(newSession, xView))
        val pad = PcControlsView(this, newSession)
        // Under the menu and loading screen (the Compose layer stays on top), over the game.
        frame.addView(xView, 0, matchParent())
        frame.addView(pad, 1, matchParent())
        view = xView
        controlsView = pad
        session = newSession
        applyControls()
        newSession.onExit = { code -> runOnUiThread { onWineExit(code, newSession) } }
        xView.requestFocus()
        try {
            newSession.start()
        } catch (e: Exception) {
            newSession.stop()
            session = null
            phase = Phase.Failed("The game couldn't be started: ${e.message}")
            return
        }
        // Keep the loading screen up until the game's window is showing (Wine's start-up is just a black desktop).
        scope.launch {
            val started = System.currentTimeMillis()
            while (phase is Phase.Loading && System.currentTimeMillis() - started < LOADING_LIMIT_MS) {
                if (gameWindowShown(newSession, newSession.program.name.lowercase())) {
                    shownAt = System.currentTimeMillis()
                    break
                }
                phase = Phase.Loading("Starting $title")
                delay(300)
            }
            if (phase is Phase.Loading) phase = Phase.Playing
        }
    }

    /**
     * Windows' screen: 720 high and as wide as the phone's screen is in proportion (1560 × 720 on a 19.5:9 phone),
     * so it fills the phone with nothing to letterbox, and games offer a resolution that fits it exactly.
     */
    private fun screenSize(): ScreenInfo {
        val metrics = resources.displayMetrics
        val long = maxOf(metrics.widthPixels, metrics.heightPixels).toFloat()
        val short = minOf(metrics.widthPixels, metrics.heightPixels).toFloat()
        val width = ((SCREEN_HEIGHT * long / short) / 8).toInt() * 8
        return ScreenInfo(width.coerceIn(SCREEN_HEIGHT * 4 / 3, SCREEN_HEIGHT * 3), SCREEN_HEIGHT)
    }

    private fun gameWindowShown(session: PcSession, wmClass: String): Boolean = runCatching {
        fun shown(window: Window): Boolean = window.attributes.isMapped && (
            (window.getClassName().contains(wmClass) && window.width >= 200 && window.height >= 150) ||
                window.getChildren().any(::shown))
        session.xServer.windowManager.rootWindow.getChildren().any(::shown)
    }.getOrDefault(false)

    private fun applyControls() {
        val pad = controlsView ?: return
        pad.keyboardMode = keyboardMode
        pad.visibility = if (padShown) View.VISIBLE else View.INVISIBLE
    }

    private fun prefs() = getSharedPreferences("pc_player", Context.MODE_PRIVATE)

    /**
     * Whether the game reads controllers through XInput (its .exe or a DLL beside it names xinput). If not, the
     * controller sends keyboard and mouse until the player chooses otherwise.
     */
    private fun usesXInput(exe: File): Boolean {
        val files = listOf(exe) + exe.parentFile!!.listFiles { f -> f.extension.equals("dll", true) }.orEmpty()
        val needle = "xinput".toByteArray()
        var budget = 400L * 1024 * 1024
        for (file in files) {
            if (budget <= 0) break
            budget -= file.length()
            if (runCatching { file.inputStream().buffered().use { contains(it, needle) } }.getOrDefault(false)) return true
        }
        return false
    }

    /** Whether [needle] (lower case ASCII) appears in the stream, ignoring case. */
    private fun contains(input: java.io.InputStream, needle: ByteArray): Boolean {
        var matched = 0
        while (true) {
            val b = input.read()
            if (b < 0) return false
            val lower = if (b in 'A'.code..'Z'.code) b + 32 else b
            matched = if (lower == needle[matched].toInt()) matched + 1 else if (lower == needle[0].toInt()) 1 else 0
            if (matched == needle.size) return true
        }
    }

    private fun chooseControls(shown: Boolean = padShown, keyboard: Boolean = keyboardMode) {
        padShown = shown
        keyboardMode = keyboard
        intent.getStringExtra(EXTRA_EXE)?.let {
            prefs().edit().putBoolean("pad_shown:$it", shown).putBoolean("keyboard_mode:$it", keyboard).apply()
        }
        applyControls()
    }

    private fun openMenu() {
        if (phase != Phase.Playing || menuOpen) return
        controlsView?.releaseAll()
        session?.setPaused(true)
        menuOpen = true
    }

    private fun closeMenu() {
        menuOpen = false
        session?.setPaused(false)
        hideSystemBars()
        view?.requestFocus()
    }

    private fun tapKey(key: XKeycode) {
        val xServer = session?.xServer ?: return
        xServer.injectKeyPress(key)
        xServer.injectKeyRelease(key)
    }

    private fun showKeyboard() {
        closeMenu()
        view?.requestFocus()
        @Suppress("DEPRECATION")
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
    }

    @Composable
    private fun Overlay() {
        MaterialTheme(colorScheme = darkColorScheme()) {
            Box(Modifier.fillMaxSize()) {
                when (val p = phase) {
                    is Phase.Loading -> Loading(p.step)
                    is Phase.Failed -> Centered {
                        Text("Couldn't start $title", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(p.message, color = Color(0xFFDDDDDD), textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 16.dp))
                        BackButton()
                    }
                    Phase.Playing -> {
                        if (!menuOpen) {
                            // Swipe in from either edge for the menu, as in the retro player; or the ☰ button.
                            EdgeSwipe(Modifier.align(Alignment.CenterStart), fromLeft = true)
                            EdgeSwipe(Modifier.align(Alignment.CenterEnd), fromLeft = false)
                            TextButton(
                                onClick = { openMenu() },
                                modifier = Modifier.align(Alignment.TopCenter).padding(6.dp)
                                    .background(Color(0x55000000), RoundedCornerShape(10.dp)),
                            ) { Text("☰", color = Color(0xDDFFFFFF), fontSize = 20.sp) }
                        } else {
                            PauseMenu()
                        }
                    }
                }
                exitMessage?.let { message ->
                    Centered {
                        Text("The game closed", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text(message, color = Color(0xFFDDDDDD), fontSize = 13.sp, textAlign = TextAlign.Center,
                            // Short enough that the button still fits on a phone held sideways (the log scrolls).
                            modifier = Modifier.padding(vertical = 12.dp).heightIn(max = 140.dp).verticalScroll(rememberScrollState()))
                        BackButton()
                    }
                }
            }
        }
    }

    @Composable
    private fun BackButton() {
        Button(
            onClick = { quit() },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF8A1F), contentColor = Color.Black),
        ) { Text("Back to RetroForge", fontWeight = FontWeight.Bold) }
    }

    /** The loading screen: the game's name on black, so Wine starting up (a bare Windows desktop) isn't seen. */
    @Composable
    private fun Loading(step: String) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                Text("PC game", color = Color(0xFFFF8A1F), fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp, bottom = 28.dp))
                CircularProgressIndicator(color = Color(0xFFFF8A1F), modifier = Modifier.size(36.dp))
                Text(step, color = Color(0xFFBBBBC4), fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp))
            }
        }
    }

    @Composable
    private fun EdgeSwipe(modifier: Modifier, fromLeft: Boolean) {
        Box(
            modifier.fillMaxHeight().width(20.dp).pointerInput(fromLeft) {
                var travelled = 0f
                detectHorizontalDragGestures(onDragStart = { travelled = 0f }) { _, dx ->
                    travelled += if (fromLeft) dx else -dx
                    if (travelled > 48.dp.toPx()) {
                        travelled = Float.NEGATIVE_INFINITY
                        openMenu()
                    }
                }
            },
        )
    }

    @Composable
    private fun Centered(content: @Composable () -> Unit) {
        Box(Modifier.fillMaxSize().background(Color(0xCC000000)).padding(32.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
        }
    }

    /** The same look as the retro player's pause menu. */
    @Composable
    private fun PauseMenu() {
        // Padded so the card stays clear of the screen's edges in landscape (it scrolls when taller).
        Box(Modifier.fillMaxSize().background(Color(0xCC000000)).padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
            Column(Modifier.width(340.dp).heightIn(max = 560.dp).background(Color(0xFF1C1C22), RoundedCornerShape(16.dp))) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.width(1.dp))
                    TextButton(onClick = { closeMenu() }) { Text("✕", color = Color.White, fontSize = 20.sp) }
                }
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val wide = Modifier.fillMaxWidth()
                    Text(title, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Text("Paused · PC game", color = Color(0xFFAAAAB4), fontSize = 13.sp)
                    MenuButton(onClick = { closeMenu() }, modifier = wide) { Text("Resume") }
                    MenuButton(onClick = { chooseControls(shown = !padShown) }, modifier = wide) {
                        Text("On-screen controller: ${if (padShown) "Shown" else "Hidden"}")
                    }
                    MenuButton(onClick = { chooseControls(keyboard = !keyboardMode) }, modifier = wide) {
                        Text("Controller works as: ${if (keyboardMode) "Keyboard & mouse" else "Xbox controller"}")
                    }
                    MenuButton(onClick = {
                        blurredSides = !blurredSides
                        prefs().edit().putBoolean("blurred_sides", blurredSides).apply()
                        view?.renderer?.setBlurredSides(blurredSides)
                    }, modifier = wide) { Text("Sides of 4:3 games: ${if (blurredSides) "Blurred" else "Black"}") }
                    if (keyboardMode) Text(
                        "For games without controller support. Sticks: move (WASD) and mouse. RT/LT: left/right click. " +
                            "A Space · B Ctrl · X R · Y E · LB Q · RB next weapon · Start Esc · Back Tab · L3 Shift.",
                        color = Color(0xFFAAAAB4), fontSize = 12.sp, textAlign = TextAlign.Center,
                    )
                    MenuButton(onClick = { showKeyboard() }, modifier = wide) { Text("Keyboard") }
                    MenuButton(onClick = { closeMenu(); tapKey(XKeycode.KEY_ESC) }, modifier = wide) { Text("Press Esc") }
                    MenuButton(onClick = { closeMenu(); tapKey(XKeycode.KEY_ENTER) }, modifier = wide) { Text("Press Enter") }
                    MenuButton(onClick = { quit() }, modifier = wide) { Text("Quit to RetroForge") }
                }
            }
        }
    }

    @Composable
    private fun MenuButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
        OutlinedButton(onClick = onClick, modifier = modifier, border = BorderStroke(1.dp, Color(0xFF55555F)), content = content)
    }

    /** Notes this run in the game's history on this device (once): whether the game showed, and for how long. */
    private fun recordRun() {
        if (recorded) return
        recorded = true
        val exe = intent.getStringExtra(EXTRA_EXE)?.let(::File) ?: return
        val shown = shownAt
        val seconds = if (shown == null) 0L else (System.currentTimeMillis() - shown) / 1000
        Thread { runCatching { PcHistory.record(applicationContext, exe, shown != null, seconds) } }.start()
    }

    private fun onWineExit(code: Int, ended: PcSession) {
        if (quitting || isFinishing) return
        recordRun()
        // The game quit by itself: back to RetroForge (saving to Steam Cloud first). Anything else shows Wine's last
        // lines, which say what went wrong.
        if (code == 0) {
            quit()
            return
        }
        val tail = runCatching { ended.log.readLines().takeLast(12).joinToString("\n") }.getOrDefault("")
        menuOpen = false
        controlsView?.visibility = View.GONE
        exitMessage = "Wine finished (code $code).\n\n$tail"
    }

    private fun quit() {
        if (quitting) return
        quitting = true
        if (session != null) recordRun()
        val ending = session
        session = null
        val sync = cloud
        if (sync == null) {
            Thread { ending?.stop() }.start()
            finish()
            return
        }
        // A Steam game: once Wine has stopped (its saves written), changed saves go up to Steam Cloud before the
        // player closes, so Android can't end the upload by closing the app.
        menuOpen = false
        exitMessage = null
        controlsView?.visibility = View.GONE
        phase = Phase.Loading("Saving to Steam Cloud")
        scope.launch {
            withContext(Dispatchers.IO) {
                // Wine's processes end within seconds; the rest of the shutdown isn't worth waiting on longer.
                ending?.let { Thread { it.stop() }.apply { start() }.join(STOP_WAIT_MS) }
                runCatching { withTimeout(CLOUD_TIMEOUT_MS) { sync.upload() } }.onFailure { Log.w(TAG, "Steam Cloud upload failed", it) }
            }
            finish()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event)
        val current = session ?: return super.dispatchKeyEvent(event)
        if (menuOpen) return super.dispatchKeyEvent(event)
        // A real controller: its buttons go through the on-screen controller's (Xbox pad, or keyboard & mouse).
        val pad = controlsView
        if (pad != null && event.isFromSource(InputDevice.SOURCE_GAMEPAD)) {
            if (event.keyCode == KeyEvent.KEYCODE_BUTTON_MODE) {
                if (event.action == KeyEvent.ACTION_DOWN) openMenu()
                return true
            }
            padButton(event.keyCode)?.let { button ->
                if (event.repeatCount == 0) pad.hardwareButton(button, event.action == KeyEvent.ACTION_DOWN)
                return true
            }
        }
        return current.xServer.keyboard.onKeyEvent(event) || super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        val pad = controlsView
        if (pad == null || menuOpen || !event.isFromSource(InputDevice.SOURCE_JOYSTICK) || event.action != MotionEvent.ACTION_MOVE) {
            return super.dispatchGenericMotionEvent(event)
        }
        pad.hardwareStick(true, event.getAxisValue(MotionEvent.AXIS_X), event.getAxisValue(MotionEvent.AXIS_Y))
        pad.hardwareStick(false, event.getAxisValue(MotionEvent.AXIS_Z), event.getAxisValue(MotionEvent.AXIS_RZ))
        pad.hardwareTrigger(VirtualPad.Axis.LEFT_TRIGGER, maxOf(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE)))
        pad.hardwareTrigger(VirtualPad.Axis.RIGHT_TRIGGER, maxOf(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS)))
        // Many controllers report the D-pad as a hat.
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        pad.hardwareButton(VirtualPad.Button.LEFT, hatX < -0.5f)
        pad.hardwareButton(VirtualPad.Button.RIGHT, hatX > 0.5f)
        pad.hardwareButton(VirtualPad.Button.UP, hatY < -0.5f)
        pad.hardwareButton(VirtualPad.Button.DOWN, hatY > 0.5f)
        return true
    }

    private fun padButton(keyCode: Int): VirtualPad.Button? = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A -> VirtualPad.Button.A
        KeyEvent.KEYCODE_BUTTON_B -> VirtualPad.Button.B
        KeyEvent.KEYCODE_BUTTON_X -> VirtualPad.Button.X
        KeyEvent.KEYCODE_BUTTON_Y -> VirtualPad.Button.Y
        KeyEvent.KEYCODE_BUTTON_L1 -> VirtualPad.Button.LB
        KeyEvent.KEYCODE_BUTTON_R1 -> VirtualPad.Button.RB
        KeyEvent.KEYCODE_BUTTON_SELECT -> VirtualPad.Button.BACK
        KeyEvent.KEYCODE_BUTTON_START -> VirtualPad.Button.START
        KeyEvent.KEYCODE_BUTTON_THUMBL -> VirtualPad.Button.LEFT_STICK
        KeyEvent.KEYCODE_BUTTON_THUMBR -> VirtualPad.Button.RIGHT_STICK
        KeyEvent.KEYCODE_DPAD_UP -> VirtualPad.Button.UP
        KeyEvent.KEYCODE_DPAD_DOWN -> VirtualPad.Button.DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> VirtualPad.Button.LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> VirtualPad.Button.RIGHT
        else -> null
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onResume() {
        super.onResume()
        view?.onResume()
    }

    override fun onPause() {
        // Leaving the app (home, notification) opens the menu, so the game waits paused.
        if (!quitting) openMenu()
        view?.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        scope.cancel()
        if (!quitting) session?.let { ending -> Thread { ending.stop() }.start() }
        session = null
        super.onDestroy()
    }

    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            return
        }
        window.insetsController?.let {
            it.hide(WindowInsets.Type.systemBars())
            it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /** Touch as a mouse: the pointer goes where the finger is (in the game's picture), so a tap clicks there. */
    private class TouchMouse(private val session: PcSession, private val view: XServerView) : View.OnTouchListener {
        private var leftDown = false
        private var secondFinger = false

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouch(v: View, event: MotionEvent): Boolean {
            val xServer = session.xServer
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    secondFinger = false
                    move(event.x, event.y)
                    xServer.injectPointerButtonPress(Pointer.Button.BUTTON_LEFT)
                    leftDown = true
                }
                MotionEvent.ACTION_POINTER_DOWN -> {
                    // A second finger turns the touch into a right click.
                    secondFinger = true
                    if (leftDown) xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_LEFT)
                    leftDown = false
                }
                MotionEvent.ACTION_MOVE -> if (!secondFinger) move(event.x, event.y)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (leftDown) xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_LEFT)
                    if (secondFinger && event.actionMasked == MotionEvent.ACTION_UP) {
                        xServer.injectPointerButtonPress(Pointer.Button.BUTTON_RIGHT)
                        xServer.injectPointerButtonRelease(Pointer.Button.BUTTON_RIGHT)
                    }
                    leftDown = false
                    secondFinger = false
                }
            }
            return true
        }

        private fun move(x: Float, y: Float) {
            val t = view.renderer.viewTransformation
            val screen = session.xServer.screenInfo
            if (t.viewWidth <= 0 || t.viewHeight <= 0) return
            val gx = ((x - t.viewOffsetX) / t.viewWidth * screen.width).toInt().coerceIn(0, screen.width - 1)
            val gy = ((y - t.viewOffsetY) / t.viewHeight * screen.height).toInt().coerceIn(0, screen.height - 1)
            // A window shown stretched to the screen sits smaller elsewhere on the X screen; aim at it there.
            val (wx, wy) = view.renderer.shownToScreen(gx, gy)
            session.xServer.injectPointerMove(wx, wy)
        }
    }

    companion object {
        const val EXTRA_EXE = "exe"
        const val EXTRA_TITLE = "title"
        const val EXTRA_STEAM_APP_ID = "steam_app_id"
        private const val SCREEN_HEIGHT = 720
        private const val LOADING_LIMIT_MS = 90_000L
        private const val CLOUD_TIMEOUT_MS = 90_000L
        private const val STOP_WAIT_MS = 8_000L
        /** Whether games receive the virtual Xbox pad yet (see where keyboardMode is chosen). */
        private const val XINPUT_PAD_WORKS = false
        private const val TAG = "PcPlayer"

        fun intent(context: Context, exe: File, title: String, steamAppId: Int? = null): Intent =
            Intent(context, PcPlayerActivity::class.java).putExtra(EXTRA_EXE, exe.path).putExtra(EXTRA_TITLE, title)
                .apply { steamAppId?.let { putExtra(EXTRA_STEAM_APP_ID, it) } }
    }
}
