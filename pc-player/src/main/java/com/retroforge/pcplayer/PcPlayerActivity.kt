package com.retroforge.pcplayer

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.retroforge.pcplayer.steam.SteamAccount
import com.retroforge.pcplayer.steam.SteamGameSetup
import com.winlator.cmod.widget.XServerView
import com.winlator.cmod.xserver.Pointer
import com.winlator.cmod.xserver.ScreenInfo
import com.winlator.cmod.xserver.XKeycode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * RetroForge's own player for Windows PC games: full screen, landscape. Touch works as a mouse (tap = left click
 * where you touch, drag = move with the button held, two-finger tap = right click); Esc, Enter and a keyboard
 * button sit in the top corner; Back asks whether to quit.
 */
class PcPlayerActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var runtime: PcRuntime
    private lateinit var frame: FrameLayout
    private lateinit var status: TextView
    private var session: PcSession? = null
    private var view: XServerView? = null
    private var quitting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        runtime = PcRuntime(this)
        frame = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        status = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
        }
        frame.addView(status, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        setContentView(frame)
        hideSystemBars()

        val exe = intent.getStringExtra(EXTRA_EXE)?.let(::File)
        if (exe == null || !exe.isFile) {
            status.text = "The game's .exe wasn't found:\n${exe?.path ?: "(none given)"}"
            return
        }
        status.text = "Starting ${intent.getStringExtra(EXTRA_TITLE) ?: exe.nameWithoutExtension}…"
        scope.launch {
            launch {
                runtime.state.collectLatest { state ->
                    if (state is RuntimeState.Working) status.text = "${state.step}… ${state.percent}%"
                }
            }
            if (!runtime.ensure()) {
                status.text = (runtime.state.value as? RuntimeState.Failed)?.reason ?: "The PC runtime couldn't be installed"
                return@launch
            }
            val steam = SteamGameSetup(runtime.root, SteamAccount(this@PcPlayerActivity))
            if (steam.steamDlls(exe.parentFile!!).isNotEmpty()) {
                status.text = "Checking your Steam account…"
                val appId = steam.appId(exe.parentFile!!, intent.getIntExtra(EXTRA_STEAM_APP_ID, 0))
                val problem = if (appId == null) {
                    "This game uses Steam, but RetroForge couldn't tell which Steam game it is."
                } else {
                    runCatching { withContext(Dispatchers.IO) { steam.prepare(exe.parentFile!!, appId) } }.exceptionOrNull()?.let {
                        it.message ?: "Steam couldn't be set up for this game"
                    }
                }
                if (problem != null) {
                    status.text = problem
                    return@launch
                }
            }
            startGame(exe)
        }
    }

    private fun startGame(exe: File) {
        val newSession = PcSession(this, runtime, exe, ScreenInfo(SCREEN_WIDTH, SCREEN_HEIGHT))
        val xView = XServerView(this, newSession.xServer)
        newSession.xServer.setRenderer(xView.renderer)
        // The game's own window fills the screen (Wine's desktop stays behind it), and the console window some games
        // open (text output, warnings) isn't shown.
        xView.renderer.setForceFullscreenWMClass(exe.name.lowercase())
        xView.renderer.setUnviewableWMClasses("conhost.exe", "wineconsole")
        xView.isFocusable = true
        xView.isFocusableInTouchMode = true
        xView.setOnTouchListener(TouchMouse(newSession, xView))
        frame.addView(xView, 0)
        frame.addView(buttons(newSession), FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END,
        ))
        status.text = ""
        status.visibility = View.GONE
        view = xView
        session = newSession
        newSession.onExit = { code -> runOnUiThread { onWineExit(code, newSession) } }
        xView.requestFocus()
        try {
            newSession.start()
        } catch (e: Exception) {
            newSession.stop()
            session = null
            status.visibility = View.VISIBLE
            status.bringToFront()
            status.text = "The game couldn't be started: ${e.message}"
        }
    }

    private fun buttons(session: PcSession) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        alpha = 0.6f
        fun key(label: String, keycode: XKeycode) = addView(Button(context).apply {
            text = label
            isFocusable = false
            setOnClickListener {
                session.xServer.injectKeyPress(keycode)
                session.xServer.injectKeyRelease(keycode)
            }
        })
        key("Esc", XKeycode.KEY_ESC)
        key("Enter", XKeycode.KEY_ENTER)
        addView(Button(context).apply {
            text = "⌨"
            isFocusable = false
            setOnClickListener {
                view?.requestFocus()
                @Suppress("DEPRECATION")
                (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).toggleSoftInput(InputMethodManager.SHOW_FORCED, 0)
            }
        })
        addView(Button(context).apply {
            text = "Quit"
            isFocusable = false
            setOnClickListener { askQuit() }
        })
    }

    private fun onWineExit(code: Int, ended: PcSession) {
        if (quitting || isFinishing) return
        val tail = runCatching { ended.log.readLines().takeLast(12).joinToString("\n") }.getOrDefault("")
        AlertDialog.Builder(this)
            .setTitle("The game closed")
            .setMessage("Wine finished (code $code).\n\n$tail")
            .setPositiveButton("OK") { _, _ -> quit() }
            .setOnCancelListener { quit() }
            .show()
    }

    private fun askQuit() {
        AlertDialog.Builder(this)
            .setTitle("Quit the game?")
            .setMessage("Anything not saved in the game is lost.")
            .setPositiveButton("Quit") { _, _ -> quit() }
            .setNegativeButton("Keep playing", null)
            .show()
    }

    private fun quit() {
        if (quitting) return
        quitting = true
        val ending = session
        session = null
        Thread { ending?.stop() }.start()
        finish()
    }

    @Deprecated("Back asks before quitting")
    override fun onBackPressed() {
        if (session == null) super.onBackPressed() else askQuit()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) return super.dispatchKeyEvent(event)
        val current = session ?: return super.dispatchKeyEvent(event)
        return current.xServer.keyboard.onKeyEvent(event) || super.dispatchKeyEvent(event)
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
            session.xServer.injectPointerMove(gx, gy)
        }
    }

    companion object {
        const val EXTRA_EXE = "exe"
        const val EXTRA_TITLE = "title"
        const val EXTRA_STEAM_APP_ID = "steam_app_id"
        private const val SCREEN_WIDTH = 1280
        private const val SCREEN_HEIGHT = 720

        fun intent(context: Context, exe: File, title: String, steamAppId: Int? = null): Intent =
            Intent(context, PcPlayerActivity::class.java).putExtra(EXTRA_EXE, exe.path).putExtra(EXTRA_TITLE, title)
                .apply { steamAppId?.let { putExtra(EXTRA_STEAM_APP_ID, it) } }
    }
}
