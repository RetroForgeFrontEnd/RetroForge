package com.retroforge.app.player

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.retroforge.player.NativePlayer
import com.retroforge.player.Pad
import com.retroforge.player.PlayerCores
import android.content.res.Configuration
import android.os.Handler
import android.os.Looper
import com.retroforge.player.Rect
import com.retroforge.player.ScreenShape
import com.retroforge.player.Viewport
import android.content.SharedPreferences
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.input.pointer.pointerInput
import com.retroforge.player.Video
import com.retroforge.retro.HotkeyCombo
import java.io.File
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import com.retroforge.player.CoreChoices
import com.retroforge.player.CoreManager
import com.retroforge.player.CoreOption
import java.io.IOException
import java.util.concurrent.Executors
import java.util.zip.ZipInputStream
import org.apache.commons.compress.archivers.sevenz.SevenZFile

/**
 * RetroForge's built-in player. Runs in its own process (":player"), so a core that crashes or leaks can't take the
 * library with it, and every game starts with a clean core. Settings come in the intent; nothing is shared with
 * the main process except files.
 */
class PlayerActivity : ComponentActivity() {

    private sealed interface Phase {
        data object Loading : Phase
        data object Playing : Phase
        data class Failed(val message: String) : Phase
    }

    private var phase by mutableStateOf<Phase>(Phase.Loading)
    private var loadingDetail by mutableStateOf<String?>(null)
    private var menuOpen by mutableStateOf(false)
    private var showTouch by mutableStateOf(true)
    private var video by mutableStateOf<Video?>(null)
    private var notice by mutableStateOf<String?>(null)
    private var shape by mutableStateOf(ScreenShape.ORIGINAL)
    private var touchEnabled by mutableStateOf(true)
    private var consoleLook by mutableStateOf(true)
    private var volume by mutableStateOf(1f)
    // Per system, shared with Settings: graphics.<system> = vulkan/opengl, scale.<system> = internal resolution 1-10.
    private lateinit var videoChoices: CoreChoices
    private var vulkanOn by mutableStateOf(false)
    private var scale by mutableStateOf(1)
    // FPS counter (performance check), kept for all games; fps is null while it's off.
    private var showFps by mutableStateOf(false)
    private var fps by mutableStateOf<Int?>(null)
    private var filter by mutableStateOf(com.retroforge.player.ScreenFilter.SHARP)
    private var pictureRect by mutableStateOf(Rect(0, 0, 0, 0))
    // The Bezel Project's frame (landscape only): the image, its see-through window (image pixels), whether it's on,
    // and where it's drawn on screen (null when it isn't shown).
    private var bezelImage by mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    private var bezelWindow: Rect? = null
    private var bezelOn by mutableStateOf(true)
    private var bezelPlace by mutableStateOf<Rect?>(null)
    private var bezelBusy by mutableStateOf(false)
    private var bezelStatus by mutableStateOf<String?>(null)
    private val bezels by lazy { Bezels(File(filesDir, "bezels"), "RetroForge/${com.retroforge.app.BuildConfig.VERSION_NAME}") }
    private enum class MenuPage { MAIN, PICTURE, SOUND_CONTROLS, EMULATOR, OPTIONS, EMULATORS, CHEATS, CONTROLS, NETPLAY }

    // Controller remapping, per system: "keyCode:button,keyCode:button".
    private var remap by mutableStateOf<Map<Int, Int>>(emptyMap())
    private var waitingFor by mutableStateOf<Int?>(null)

    private fun loadRemap(): Map<Int, Int> = prefs.getString("map.$system", null).orEmpty().split(',').mapNotNull { pair ->
        val (key, button) = pair.split(':').takeIf { it.size == 2 } ?: return@mapNotNull null
        (key.toIntOrNull() ?: return@mapNotNull null) to (button.toIntOrNull() ?: return@mapNotNull null)
    }.toMap()

    private fun saveRemap(next: Map<Int, Int>) {
        remap = next
        input.remap = next
        prefs.edit().putString("map.$system", next.entries.joinToString(",") { "${it.key}:${it.value}" }).apply()
    }

    /** Waits for a controller button, then makes it press [button] in the game. */
    private fun remapButton(button: Int) {
        waitingFor = button
        input.capture = { keyCode ->
            runOnUiThread {
                waitingFor = null
                // A key does one thing: drop its old job, and the default key for this button stops pressing it.
                val next = remap.filterKeys { it != keyCode }.toMutableMap()
                next[keyCode] = button
                saveRemap(next)
            }
        }
    }

    // On-screen buttons, per system: which game button each one presses ("label=button;label=button").
    private var touchMap by mutableStateOf<Map<String, Int>>(emptyMap())

    private fun loadTouchMap(): Map<String, Int> = prefs.getString("tmap.$system", null).orEmpty().split(';').mapNotNull { pair ->
        val (label, button) = pair.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
        label to (button.toIntOrNull() ?: return@mapNotNull null)
    }.toMap()

    private fun saveTouchMap(next: Map<String, Int>) {
        touchMap = next
        prefs.edit().putString("tmap.$system", next.entries.joinToString(";") { "${it.key}=${it.value}" }).apply()
    }

    /** The system's pad with the user's on-screen choices applied. */
    private fun touchLayout(): PadLayout {
        val base = padLayout()
        fun List<Pair<String, Int>>.mapped() = map { (label, button) -> label to (touchMap[label] ?: button) }
        return base.copy(face = base.face.mapped(), shoulders = base.shoulders.mapped(), triggers = base.triggers.mapped())
    }

    /** The system's game buttons, named as on its controller. */
    private fun gameButtons(): List<Pair<String, Int>> {
        val layout = padLayout()
        return (layout.face + layout.shoulders + layout.triggers).distinctBy { it.second } +
            listOfNotNull(if (layout.hasSelect) "Select" to Pad.SELECT else null, "Start" to Pad.START)
    }

    /** Which controller key presses [button] now: the user's choice, else the usual one. */
    private fun keyFor(button: Int): String {
        val chosen = remap.entries.firstOrNull { it.value == button }?.key
        val code = chosen ?: DEFAULT_KEYS.firstOrNull { PlayerInput.buttonFor(it) == button && it !in remap }
        return code?.let { KeyEvent.keyCodeToString(it).removePrefix("KEYCODE_").removePrefix("BUTTON_").replace('_', ' ') } ?: "none"
    }
    private var cheats by mutableStateOf<List<Cheat>>(emptyList())
    /** What the core calls player 1's buttons (libretro id → name), once the game has loaded. */
    private var coreButtons by mutableStateOf<Map<Int, String>>(emptyMap())
    private fun padLayout() = PadLayout.forSystem(system, coreButtons)
    private var cheatStatus by mutableStateOf<String?>(null)
    // While the first download runs, only the status shows (no half-built list or code boxes flashing past).
    private var fetchingCheats by mutableStateOf(false)
    private var newCode by mutableStateOf("")
    private var newName by mutableStateOf("")
    private val cheatStore by lazy { CheatStore(File(filesDir, "cheats/$system/$saveBase.json")) }

    /** Turns on exactly the enabled cheats; tells the user when this emulator has no cheat support. */
    private fun applyCheats(list: List<Cheat>) {
        val ok = NativePlayer.applyCheats(list.filter { it.enabled }.map { it.code }.toTypedArray())
        if (!ok && list.any { it.enabled }) runOnUiThread { cheatStatus = "This emulator doesn't support cheats." }
    }

    private fun updateCheats(list: List<Cheat>) {
        cheats = list
        worker.execute {
            cheatStore.save(list)
            applyCheats(list)
        }
    }

    private fun openCheats() {
        menuPage = MenuPage.CHEATS
        cheats = cheatStore.load()
        if (cheatStore.downloaded) return
        cheatStatus = "Looking for cheats for this game…"
        fetchingCheats = true
        worker.execute {
            val found = runCatching {
                cheatStore.fetch(system, intent.getStringExtra(EXTRA_FILE_NAME).orEmpty(), "RetroForge/${com.retroforge.app.BuildConfig.VERSION_NAME}")
            }
            runOnUiThread {
                fetchingCheats = false
                found.onSuccess { list ->
                    if (list == null) cheatStatus = "libretro's cheat database has no cheats for this game. You can add your own code below."
                    else { cheats = list; cheatStatus = null }
                }.onFailure { cheatStatus = "Couldn't get cheats (${it.message}). Check the internet connection." }
            }
        }
    }
    private var menuPage by mutableStateOf(MenuPage.MAIN)
    private var fastForwardOn by mutableStateOf(false)

    // Netplay (Netplay.kt, netplay.cpp): this game played together with a second device.
    private var netGameId = ""
    /** NativePlayer.netplayState: 0 off, 1 hosting, 2 joining, 3 starting, 4 playing, 5 failed. */
    private var netState by mutableStateOf(0)
    private var netStatus by mutableStateOf("")
    /** The status line over the game: shown while connecting or when something's wrong, and for a few seconds once playing. */
    private var netShowStatus by mutableStateOf(false)
    private var netPlayingSince = 0L
    private var netHosts by mutableStateOf<List<NetplayHost>?>(null)
    private var netSearching by mutableStateOf(false)
    private var netAddress by mutableStateOf("")
    private var announcer: Netplay.Announcer? = null
    /** The router's port opened for internet players while hosting, and what the netplay page says about it. */
    @Volatile private var upnp: Upnp? = null
    private var netInternet by mutableStateOf<String?>(null)
    private val netPlaying get() = netState in 3..4
    private val netGuest get() = netPlaying && NativePlayer.netplayPlayer() == 1

    private fun hostNetplay() {
        val error = NativePlayer.netplayHost(Netplay.GAME_PORT, netGameId, Netplay.delay(this))
        if (error != null) { notice = error; return }
        connectPlayerTwo()
        announcer?.stop()
        announcer = Netplay.Announcer(this, intent.getStringExtra(EXTRA_TITLE).orEmpty(), netGameId, Netplay.name(this))
        pollNetplay()
        // Friends on the internet: ask the router to send the netplay port here.
        netInternet = "Checking whether friends on the internet can join…"
        Thread {
            val local = Netplay.localAddresses().firstOrNull()
            val router = Upnp.find()
            val text = if (router != null && local != null && router.open(Netplay.GAME_PORT, local)) {
                upnp = router
                router.externalAddress()?.let { "Friends on the internet can join at $it (your router opened the port)." }
                    ?: "Your router opened the port for friends on the internet."
            } else {
                "For friends on the internet, your router needs port ${Netplay.GAME_PORT} (TCP) sent to ${local ?: "this device"}: " +
                    "it didn't allow RetroForge to do it. Players on the same Wi-Fi can join anyway."
            }
            runOnUiThread { if (netState != 0) netInternet = text }
        }.start()
    }

    private fun closeRouterPort() {
        val router = upnp ?: return
        upnp = null
        Thread { router.close(Netplay.GAME_PORT) }.start()
    }

    private fun joinNetplay(address: String, port: Int = Netplay.GAME_PORT) {
        NativePlayer.netplayJoin(address, port, netGameId, Netplay.delay(this))?.let { notice = it; return }
        connectPlayerTwo()
        pollNetplay()
    }

    /** Plugs a controller into port 2 for the other player (only port 1 has one otherwise). */
    private fun connectPlayerTwo() = NativePlayer.setPortDevice(1, PlayerCores.portDevice(coreId) ?: 1)

    private fun leaveNetplay() {
        announcer?.stop()
        announcer = null
        closeRouterPort()
        netInternet = null
        NativePlayer.netplayLeave()
        pollNetplay()
    }

    private fun findNetplayHosts() {
        netSearching = true
        worker.execute {
            val found = Netplay.find(this)
            runOnUiThread { netHosts = found; netSearching = false }
        }
    }

    /** Netplay's state and status line, kept up to date while a session is on. */
    private fun pollNetplay() {
        val wasPlaying = netState == 4
        netState = NativePlayer.netplayState()
        netStatus = NativePlayer.netplayStatus()
        if (netState == 4 && !wasPlaying) netPlayingSince = android.os.SystemClock.elapsedRealtime()
        netShowStatus = netState != 0 && netStatus.isNotBlank() &&
            (netState != 4 || netStatus.startsWith("Waiting") || android.os.SystemClock.elapsedRealtime() - netPlayingSince < 4000)
        // Once someone has joined, nobody else needs to find this game.
        if (netState >= 3) { announcer?.stop(); announcer = null }
        if (netState != 0 && !isFinishing) window.decorView.postDelayed({ pollNetplay() }, 500)
    }
    private var coreOptions by mutableStateOf<List<CoreOption>>(emptyList())
    @Volatile private var switching = false
    private lateinit var coreChoices: CoreChoices
    private lateinit var cores: CoreManager
    private lateinit var prefs: SharedPreferences
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    @Volatile private var picture = Rect(0, 0, 0, 0)
    private var gameView: SurfaceView? = null

    private val worker = Executors.newSingleThreadExecutor()
    private val surfaceLock = Any()
    private var surface: Surface? = null
    @Volatile private var loaded = false
    @Volatile private var quitting = false
    private var foreground = false
    private var hasTouch = true

    // Controllers coming and going mid-game: touch controls hide when one connects and come back when the last one goes.
    private val controllerListener = object : android.hardware.input.InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) { if (controllerConnected()) showTouch = false }
        override fun onInputDeviceRemoved(deviceId: Int) { if (!controllerConnected()) showTouch = hasTouch }
        override fun onInputDeviceChanged(deviceId: Int) = onInputDeviceAdded(deviceId)
    }

    private fun controllerConnected() = android.view.InputDevice.getDeviceIds().any { id ->
        val device = android.view.InputDevice.getDevice(id) ?: return@any false
        !device.isVirtual && (device.sources and android.view.InputDevice.SOURCE_GAMEPAD == android.view.InputDevice.SOURCE_GAMEPAD ||
            device.sources and android.view.InputDevice.SOURCE_JOYSTICK == android.view.InputDevice.SOURCE_JOYSTICK)
    }

    private lateinit var system: String
    /** Android's keyboard for computer systems (Keyboard in the in-game menu). */
    private val keyboard by lazy { KeyboardCatcher(this).also { addContentView(it, android.view.ViewGroup.LayoutParams(1, 1)) } }
    private lateinit var input: PlayerInput
    private var resume = true
    private lateinit var savesDir: File
    private lateinit var statesDir: File
    private lateinit var saveBase: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // The volume buttons change the game's sound (media), never the ringer.
        volumeControlStream = android.media.AudioManager.STREAM_MUSIC
        window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK))
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }

        val extras = intent.extras ?: return finish()
        // TV boxes and handhelds with a pad: no touch controls unless the screen is actually touched.
        hasTouch = packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TOUCHSCREEN)
        showTouch = hasTouch && !controllerConnected()
        system = extras.getString(EXTRA_SYSTEM).orEmpty()
        resume = extras.getBoolean(EXTRA_RESUME, true)
        val fileName = extras.getString(EXTRA_FILE_NAME).orEmpty()
        saveBase = fileName.substringBeforeLast('.')
        val base = getExternalFilesDir(null) ?: filesDir
        // Per-system player choices. Only this process uses these preferences.
        prefs = getSharedPreferences("player", MODE_PRIVATE)
        coreChoices = CoreChoices(File(filesDir, "player/cores.properties"))
        videoChoices = CoreChoices(File(filesDir, "player/video.properties"))
        showFps = videoChoices.get("show_fps") == "true"
        scale = videoChoices.get("scale.$system")?.toIntOrNull() ?: 1
        cores = CoreManager(File(filesDir, "cores"), "RetroForge/${com.retroforge.app.BuildConfig.VERSION_NAME}")
        shape = ScreenShape.of(prefs.getString("shape.$system", null))
        touchEnabled = prefs.getBoolean("touch.$system", true)
        consoleLook = prefs.getBoolean("look.$system", true)
        // Per system from the in-game menu, otherwise Settings → Playing → Bezels.
        bezelOn = prefs.getBoolean("bezel.$system", getSharedPreferences("views", MODE_PRIVATE).getBoolean(Bezels.PREF, true))
        // Per system, or every game when the VR tab says so.
        vr = prefs.getBoolean("vr.$system", false) || getSharedPreferences("views", MODE_PRIVATE).getBoolean("vr_by_default", false)
        if (vr) requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        volume = prefs.getFloat("volume.$system", 1f)
        filter = com.retroforge.player.ScreenFilter.of(prefs.getString("filter.$system", null))
        savesDir = File(base, "saves/$system").apply { mkdirs() }
        statesDir = File(base, "states/$system").apply { mkdirs() }
        input = PlayerInput(
            menuCombo = HotkeyCombo.of(extras.getInt(EXTRA_MENU_COMBO, HotkeyCombo.DOWN_SELECT.value)),
            quitCombo = HotkeyCombo.of(extras.getInt(EXTRA_QUIT_COMBO, HotkeyCombo.START_SELECT.value)),
            fastForwardOnR2 = extras.getBoolean(EXTRA_FAST_FORWARD_R2, false),
            onMask = { if (loaded) NativePlayer.setButtons(0, it) },
            onAnalog = { stick, x, y -> if (loaded) NativePlayer.setAnalog(0, stick, x, y) },
            onFastForward = { if (loaded) NativePlayer.setFastForward(it) },
            onMenu = { runOnUiThread { openMenu() } },
            onQuit = { runOnUiThread { quit() } },
        )

        remap = loadRemap()
        input.remap = remap
        touchMap = loadTouchMap()
        // Back (the gesture, the button and the Back key): Android 16's back gesture only reaches callbacks like this
        // one, never dispatchKeyEvent.
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = onBack()
        })

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                PlayerScreen(touchLayout(), extras.getString(EXTRA_TITLE).orEmpty())
            }
        }

        val corePath = extras.getString(EXTRA_CORE_PATH).orEmpty()
        val uri = Uri.parse(extras.getString(EXTRA_URI).orEmpty())
        worker.execute { load(corePath, uri, fileName, File(base, "system").apply { mkdirs() }) }
    }

    private lateinit var coreId: String
    private val handler = Handler(Looper.getMainLooper())

    private fun isLandscape() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun load(corePath: String, uri: Uri, fileName: String, systemDir: File) {
        coreId = File(corePath).name.substringBefore("_libretro")
        if (com.retroforge.player.CoreAssets.needed(coreId, systemDir)) {
            runOnUiThread { loadingDetail = "Getting the emulator's data files (first time only)…" }
            runCatching { com.retroforge.player.CoreAssets.fetch(coreId, systemDir, "RetroForge/${com.retroforge.app.BuildConfig.VERSION_NAME}") }
                .onFailure { android.util.Log.w("RetroForgePlayer", "data files: ${it.message}") } // the game still runs without them
            runOnUiThread { loadingDetail = null }
        }
        // The user's emulator settings for this core, then the ones that follow the screen's orientation.
        // N64 and PSP texture packs (Settings → Texture packs) are used as soon as one is installed.
        val texturePacks = when {
            coreId.startsWith("mupen64plus") && File(systemDir, "Mupen64plus/cache").list().orEmpty().any { it.endsWith(".hts", true) } ->
                // Downloaded packs are single .hts files (GLideN64's enhanced storage), with full alpha as their authors ask.
                mapOf("mupen64plus-txHiresEnable" to "True", "mupen64plus-EnableEnhancedHighResStorage" to "True", "mupen64plus-txHiresFullAlphaChannel" to "True")
            coreId.startsWith("mupen64plus") && File(systemDir, "Mupen64plus/hires_texture").list().orEmpty().isNotEmpty() ->
                mapOf("mupen64plus-txHiresEnable" to "True")
            coreId == "ppsspp" && File(savesDir, "TEXTURES").list().orEmpty().isNotEmpty() -> mapOf("ppsspp_texture_replacement" to "enabled")
            else -> emptyMap()
        }
        vulkanOn = PlayerCores.usesVulkan(coreId, videoChoices.get("graphics.$system"))
        val options = PlayerCores.defaultOptions(coreId) + PlayerCores.graphicsOptions(coreId, vulkanOn) + texturePacks + savedOptions() +
            PlayerCores.orientationOptions(coreId, isLandscape()) + wonderSwanRotation(uri, fileName) +
            mapOf("retroforge_vfs" to if (coreId in PlayerCores.streamingCores) "on" else "off")
        // Play! keeps its memory cards and settings under $EXTERNAL_STORAGE ("/sdcard/Play Data Files"), which apps can't
        // write to any more, and stops when it can't. This process is the player's alone, so point it at our own folder.
        if (coreId == "play") runCatching { android.system.Os.setenv("EXTERNAL_STORAGE", File(systemDir, "play").apply { mkdirs() }.path, true) }
        val error = NativePlayer.openCore(corePath, systemDir.path, savesDir.path, options.keys.toTypedArray(), options.values.toTypedArray())
        if (error != null) return fail(error)
        applyScale()
        val info = NativePlayer.coreInfo()
        val ok = try {
            loadContent(uri, fileName, info.needsFile, info.extensions, systemDir)
        } catch (e: IOException) {
            return fail("Couldn't read the game: ${e.message}")
        } catch (e: SecurityException) {
            return fail("RetroForge no longer has access to this game's folder. Add the folder again in Settings.")
        }
        if (!ok) return fail("${info.name} couldn't start this game. The file may be damaged, or it may need BIOS files.")

        sram().takeIf { it.isFile }?.let { NativePlayer.restoreSaveRam(it.readBytes()) }
        if (resume) autoState().takeIf { it.isFile }?.let { NativePlayer.loadState(it.readBytes()) }
        val av = NativePlayer.video()
        synchronized(surfaceLock) {
            loaded = true
            surface?.let { NativePlayer.setSurface(it) }
        }
        NativePlayer.setFilter(filter.ordinal)
        NativePlayer.setSideBySideSource(PlayerCores.sideBySide(coreId))
        NativePlayer.setVr(vr)
        NativePlayer.setVolume(volume)
        runOnUiThread { if (foreground && playingSince == 0L) playingSince = android.os.SystemClock.elapsedRealtime() }
        cheatStore.load().also { saved -> runOnUiThread { cheats = saved } }.let(::applyCheats)
        PlayerCores.portDevice(coreId)?.let { NativePlayer.setPortDevice(0, it) }
        NativePlayer.buttonNames().toList().chunked(2).associate { (id, name) -> id.toInt() to name }.let { names ->
            android.util.Log.i("RetroForgePlayer", "buttons $coreId: " + names.entries.joinToString { "${it.key}=${it.value}" })
            runOnUiThread { coreButtons = names }
        }
        netGameId = Netplay.gameId(system, fileName, runCatching {
            contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getLong(0) else -1L
            }
        }.getOrNull() ?: -1L)
        NativePlayer.start()
        loadBezel(fileName)
        runOnUiThread {
            video = av
            coreOptions = NativePlayer.options()
            applyViewport()
            // Cores that turn or resize the picture once running (WonderSwan's upright games) get picked up.
            refreshVideo(40)
            phase = Phase.Playing
            setUpSecondScreen()
            NativePlayer.setPaused(!foreground || menuOpen)
            surface?.let { setFrameRate(it, av) }
        }
        // Started from a game's page as "Play together": host, or join the address given.
        intent.getStringExtra(EXTRA_NETPLAY)?.let { how ->
            runOnUiThread {
                if (how == NETPLAY_HOST) hostNetplay()
                else if (how == NETPLAY_FIND) { openMenu(); menuPage = MenuPage.NETPLAY; findNetplayHosts() }
                else how.removePrefix("join:").let { joinNetplay(it.substringBeforeLast(':'), it.substringAfterLast(':').toIntOrNull() ?: Netplay.GAME_PORT) }
            }
        }
    }

    /**
     * Gives the core the game the way it wants it: in memory straight from the folder when it can take that,
     * otherwise as a file copied into the cache. A .zip or .7z is unpacked first unless the core reads it itself.
     */
    private fun loadContent(uri: Uri, fileName: String, needsFile: Boolean, extensions: Set<String>, systemDir: File): Boolean {
        val cache = File(cacheDir, "player").apply { deleteRecursively(); mkdirs() }
        // A game that's a folder (RetroBat's DOS "Game.pc" folders): copied with everything in it, started from its program.
        if (FolderGame.isFolder(contentResolver, uri)) {
            val root = File(externalCacheDir ?: cacheDir, "games")
            val dir = File(root, Integer.toHexString(uri.toString().hashCode()))
            GameFiles.prune(root, keep = 2, current = dir)
            val start = FolderGame.expose(contentResolver, uri, fileName, dir) { percent ->
                runOnUiThread { loadingDetail = "Preparing the game for the first time… $percent%" }
            }
            return NativePlayer.loadGame(start.path, -1, false)
        }
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext == "zip" && "zip" !in extensions) {
            val file = contentResolver.openInputStream(uri)?.use { stream ->
                ZipInputStream(stream).use { zip ->
                    val entries = generateSequence { zip.nextEntry }.filter { !it.isDirectory }
                    val entry = entries.firstOrNull { it.name.substringAfterLast('.').lowercase() in extensions }
                        ?: throw IOException("the zip has no file this emulator can open")
                    File(cache, contentName(entry.name.substringAfterLast('/'))).also { out -> out.outputStream().use { zip.copyTo(it) } }
                }
            } ?: throw IOException("couldn't open it")
            return NativePlayer.loadGame(file.path, -1, !needsFile)
        }
        if (ext == "7z" && "7z" !in extensions) {
            // 7-Zip needs random access, so the archive is copied to the cache first.
            val archive = File(cache, "game.7z")
            contentResolver.openInputStream(uri)?.use { input -> archive.outputStream().use { input.copyTo(it) } } ?: throw IOException("couldn't open it")
            val file = SevenZFile.builder().setFile(archive).get().use { seven ->
                val entry = seven.entries.firstOrNull { !it.isDirectory && it.name.substringAfterLast('.').lowercase() in extensions }
                    ?: throw IOException("the 7z has no file this emulator can open")
                File(cache, contentName(entry.name.substringAfterLast('/'))).also { out ->
                    seven.getInputStream(entry).use { input -> out.outputStream().use { input.copyTo(it) } }
                }
            }
            archive.delete()
            return NativePlayer.loadGame(file.path, -1, !needsFile)
        }
        val named = File(cache, contentName(fileName))
        if (needsFile) {
            val root = File(externalCacheDir ?: cacheDir, "games")
            val dir = File(root, Integer.toHexString(uri.toString().hashCode()))
            val files = GameFiles(contentResolver, dir)
            // Streamed first: the core reads the game straight from its folder or network drive through the player's
            // VFS, so even a 4 GB disc starts at once. A copy made earlier is used when there is one (it's local), and
            // a copy is made when the core can't stream or the user chose "Copy games to the phone first".
            val copyFirst = intent.getBooleanExtra(EXTRA_COPY_FIRST, false)
            if (!copyFirst && !File(dir, fileName).isFile && coreId in PlayerCores.streamingCores && NativePlayer.vfsRequested()) {
                val streamDir = File(cacheDir, "stream").apply { deleteRecursively(); mkdirs() }
                val opened = runCatching { files.open(uri, fileName, PlayerCores.companions(system), fallback = systemDir) }.getOrNull()
                if (opened != null) {
                    opened.forEach { (name, pfd) -> pfd.use { NativePlayer.addStream(File(streamDir, name).path, it.fd) } }
                    if (NativePlayer.loadGame(File(streamDir, fileName).path, -1, false)) return true
                    android.util.Log.w("RetroForgePlayer", "$coreId couldn't stream $fileName; copying it instead")
                }
            }
            // Copied once into RetroForge's cache and reused next time; only the two most recent games are kept.
            GameFiles.prune(root, keep = 2, current = dir)
            val path = files.expose(uri, fileName, PlayerCores.companions(system), fallback = systemDir) { percent ->
                runOnUiThread { loadingDetail = "Preparing the game for the first time… $percent%" }
            }
            return NativePlayer.loadGame(path, -1, false)
        }
        return contentResolver.openFileDescriptor(uri, "r")?.use { pfd -> NativePlayer.loadGame(named.path, pfd.fd, false) }
            ?: throw IOException("couldn't open it")
    }

    /**
     * The name the core sees for a game file. b2 (BBC Micro) runs the program named in square brackets in a disk's name,
     * but catalogue names use brackets for tags ("Citadel (1985)(Superior)[h TSTH][bootfile].ssd" makes it type
     * CHAIN"bootfile"), so for b2 the brackets go and it boots the disk the usual way.
     */
    private fun contentName(name: String) =
        if (coreId == "b2") name.replace(Regex("""\s*\[[^\]]*\]"""), "").ifBlank { name } else name

    private fun fail(message: String) = runOnUiThread { phase = Phase.Failed(message) }

    private fun sram() = File(savesDir, "$saveBase.srm")
    // States belong to one emulator (another can't load them), so their names include it. In-game saves (.srm) are shared.
    private fun autoState() = File(statesDir, "$saveBase.$coreId.auto.state")
    private fun slotState() = File(statesDir, "$saveBase.$coreId.state")

    private fun savedOptions(): Map<String, String> = prefs.all
        .filterKeys { it.startsWith("opt.$coreId.") }
        .mapNotNull { (k, v) -> (v as? String)?.let { k.removePrefix("opt.$coreId.") to it } }
        .toMap()

    /** Widescreen hack on or off (cores that have one), kept like any emulator setting. */
    private fun chooseWidescreen() {
        val (key, wide, original) = PlayerCores.widescreenOption(coreId) ?: return
        val current = coreOptions.firstOrNull { it.key == key }?.value
        val next = if (current == wide) original else wide
        prefs.edit().putString("opt.$coreId.$key", next).apply()
        NativePlayer.setOption(key, next)
        coreOptions = NativePlayer.options()
        refreshVideo(10)
        // Mupen64Plus only reads its picture shape at start: restart the game (its place is saved first).
        if (key.startsWith("mupen")) {
            notice = "Restarting in ${if (next == wide) "widescreen" else "4:3"}…"
            restartWith(Intent(intent))
        }
    }

    private fun chooseOption(option: CoreOption) {
        if (option.choices.isEmpty()) return
        val next = option.choices[(option.choices.indexOf(option.value) + 1) % option.choices.size]
        prefs.edit().putString("opt.$coreId.${option.key}", next).apply()
        NativePlayer.setOption(option.key, next)
        coreOptions = NativePlayer.options()
        refreshVideo(10) // some settings change the picture (borders, layouts)
    }

    /**
     * WonderSwan games made to be held upright (Tetris, Klonoa, many puzzle games) say so in their header: bit 0 of the
     * byte 4 from the end. Beetle Cygne then turns the picture ("portrait"), and the player shows it upright.
     */
    private fun wonderSwanRotation(uri: Uri, fileName: String): Map<String, String> {
        if (coreId != "mednafen_wswan") return emptyMap()
        val vertical = runCatching {
            if (fileName.endsWith(".zip", ignoreCase = true)) {
                contentResolver.openInputStream(uri)?.use { stream ->
                    ZipInputStream(stream).use { zip ->
                        generateSequence { zip.nextEntry }.firstOrNull { it.name.substringAfterLast('.').lowercase() in setOf("ws", "wsc") }
                            ?.let { zip.readBytes() }
                    }
                }?.let { rom -> rom.size >= 16 && rom[rom.size - 4].toInt() and 1 == 1 }
            } else {
                contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    java.io.FileInputStream(pfd.fileDescriptor).channel.use { ch ->
                        val size = ch.size()
                        if (size < 16) null else java.nio.ByteBuffer.allocate(1).also { ch.read(it, size - 4) }.get(0).toInt() and 1 == 1
                    }
                }
            }
        }.getOrNull() == true
        return if (vertical) mapOf("wswan_rotate_display" to "portrait") else emptyMap()
    }

    /** The internal resolution chosen for this system: the Nth value of the core's own resolution option. */
    private fun applyScale() {
        val (key, _) = PlayerCores.resolutionOption(coreId) ?: return
        val choices = NativePlayer.options().firstOrNull { it.key == key }?.choices ?: return
        if (choices.isEmpty()) return
        NativePlayer.setOption(key, choices[(scale.coerceAtLeast(1) - 1).coerceAtMost(choices.size - 1)])
    }

    /** Vulkan or OpenGL for this system: the game restarts in the other one (its place is saved first). */
    private fun chooseGraphics() {
        val next = if (vulkanOn) "opengl" else "vulkan"
        videoChoices.set("graphics.$system", next)
        notice = "Restarting with ${if (next == "vulkan") "Vulkan" else "OpenGL"}…"
        restartWith(Intent(intent))
    }

    /** The next internal resolution, 1x up to what the core offers, then back to 1x. */
    private fun chooseScale() {
        val (key, max) = PlayerCores.resolutionOption(coreId) ?: return
        val available = (coreOptions.firstOrNull { it.key == key }?.choices?.size ?: max).coerceAtMost(max)
        scale = if (scale >= available) 1 else scale + 1
        videoChoices.set("scale.$system", if (scale == 1) null else scale.toString())
        applyScale()
        coreOptions = NativePlayer.options()
        refreshVideo(10)
        // PCSX2 only reads its resolution at start.
        if (coreId == "pcsx2") {
            notice = "Restarting at ${scale}×…"
            restartWith(Intent(intent))
        }
    }

    /** Restarts the game in another of the system's emulators; the in-game save carries over. */
    private fun switchEmulator(core: PlayerCores.PlayerCore) {
        if (core.id == coreId || switching) return
        switching = true
        notice = "Getting ${core.name}…"
        lifecycleScope.launch {
            if (!cores.ensure(core.id)) {
                switching = false
                notice = "Couldn't download ${core.name}. Check the internet connection."
                return@launch
            }
            coreChoices.set(system, core.id)
            restartWith(Intent(intent).putExtra(EXTRA_CORE_PATH, cores.file(core.id).path))
        }
    }

    /** Saves and closes this game, then starts the player again with [next] (another emulator or another game). */
    private fun restartWith(next: Intent) {
        switching = true
        quitting = true
        worker.execute {
            if (loaded) {
                NativePlayer.stop()
                persist()
                NativePlayer.close()
                loaded = false
            }
            runOnUiThread {
                // The relauncher (main process) starts the new player once this one has closed.
                startActivity(Intent(this@PlayerActivity, PlayerRelaunchActivity::class.java).putExtra(PlayerRelaunchActivity.EXTRA_NEXT, next))
                finish()
            }
        }
    }

    // Another game started from the library while this one was still open (in the background): save this one and switch.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getStringExtra(EXTRA_URI) == this.intent.getStringExtra(EXTRA_URI) &&
            intent.getStringExtra(EXTRA_CORE_PATH) == this.intent.getStringExtra(EXTRA_CORE_PATH)
        ) return
        if (!quitting) restartWith(Intent(intent))
    }

    private fun writeSafely(file: File, data: ByteArray) {
        val part = File(file.path + ".part")
        part.writeBytes(data)
        if (!part.renameTo(file)) {
            file.delete()
            part.renameTo(file)
        }
    }

    /** Writes the in-game save (and the resume state when that's on). Called on [worker]. */
    private fun persist() {
        if (!loaded) return
        NativePlayer.saveRam()?.let { writeSafely(sram(), it) }
        if (resume) NativePlayer.saveState()?.let { writeSafely(autoState(), it) }
    }

    private fun openMenu() {
        if (phase != Phase.Playing || menuOpen) return
        menuOpen = true
        menuPage = MenuPage.MAIN
        input.releaseAll()
        NativePlayer.setPaused(true)
    }

    /**
     * Saves the game's picture as it's shown (filters and all) to Pictures/RetroForge: the menu closes first, then the
     * screen is copied and cropped to the picture.
     */
    private fun takeScreenshot() {
        val view = gameView ?: return
        closeMenu()
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        handler.postDelayed({
            if (view.width == 0 || view.height == 0) return@postDelayed
            val screen = android.graphics.Bitmap.createBitmap(view.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
            android.view.PixelCopy.request(view, screen, { result ->
                if (result != android.view.PixelCopy.SUCCESS) {
                    android.widget.Toast.makeText(this, "Couldn't take a screenshot", android.widget.Toast.LENGTH_SHORT).show()
                    return@request
                }
                val r = picture
                val x = r.x.coerceIn(0, screen.width - 1)
                val y = r.y.coerceIn(0, screen.height - 1)
                val shot = if (r.w > 0 && r.h > 0) android.graphics.Bitmap.createBitmap(screen, x, y, r.w.coerceAtMost(screen.width - x), r.h.coerceAtMost(screen.height - y)) else screen
                worker.execute {
                    val saved = runCatching { saveScreenshot(shot) }.isSuccess
                    runOnUiThread {
                        android.widget.Toast.makeText(this, if (saved) "Screenshot saved to Pictures/RetroForge" else "Couldn't save the screenshot", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }, handler)
        }, 350)
    }

    private fun saveScreenshot(bitmap: android.graphics.Bitmap) {
        val name = "${intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "RetroForge" }.replace(Regex("""[\/:*?"<>|]"""), "")} ${java.time.LocalDateTime.now().toString().take(19).replace(':', '-')}.png"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, name)
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/RetroForge")
            }
            val uri = contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("no media store")
            contentResolver.openOutputStream(uri)?.use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } ?: error("couldn't write")
        } else {
            val dir = File(getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES), "RetroForge").apply { mkdirs() }
            File(dir, name).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun closeMenu() {
        menuOpen = false
        notice = null
        if (loaded && foreground) NativePlayer.setPaused(false)
    }

    private fun quit() {
        if (quitting) return
        quitting = true
        worker.execute {
            if (loaded) {
                NativePlayer.stop()
                persist()
                NativePlayer.close()
                loaded = false
            }
            runOnUiThread { finish() }
        }
    }

    // Rotating can change the picture itself (the DS's screens go side by side); the new shape is picked up once
    // the core has drawn with it.
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (!loaded) return
        val options = PlayerCores.orientationOptions(coreId, newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE && !dualScreen)
        if (options.isEmpty()) return
        options.forEach { (key, value) -> NativePlayer.setOption(key, value) }
        refreshVideo(15)
    }

    private fun refreshVideo(tries: Int) {
        handler.postDelayed({
            if (!loaded) return@postDelayed
            val now = NativePlayer.video()
            if (now != video) {
                video = now
                applyViewport()
            } else if (tries > 1) {
                refreshVideo(tries - 1)
            }
        }, 100)
    }

    private val playtime by lazy { Playtime(File(filesDir, "player/playtime.properties")) }

    // Dual-screen devices (AYN Thor, AYANEO Pocket DS …): DS games show the touch screen on the second display.
    private var dualScreen by mutableStateOf(false)

    // Cardboard VR: a picture per eye (real 3D for Virtual Boy), phone held sideways, no buttons or frame on screen.
    private var vr by mutableStateOf(false)

    private fun chooseVr(on: Boolean) {
        vr = on
        prefs.edit().putBoolean("vr.$system", on).apply()
        NativePlayer.setVr(on)
        requestedOrientation = if (on) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        applyViewport()
    }
    private var secondScreen: android.app.Presentation? = null
    private var secondSize = 0 to 0

    private fun setUpSecondScreen() {
        if (system != "nds" || secondScreen != null || !loaded) return
        // Settings → Dual screen (the main app's view preferences; this process reads them fresh for each game).
        if (!getSharedPreferences("views", MODE_PRIVATE).getBoolean("dual_screen_ds", true)) return
        val displays = getSystemService(android.hardware.display.DisplayManager::class.java)
            .getDisplays(android.hardware.display.DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        val display = displays.firstOrNull() ?: return
        val view = SurfaceView(this)
        view.holder.addCallback(object : android.view.SurfaceHolder.Callback {
            override fun surfaceCreated(holder: android.view.SurfaceHolder) = Unit
            override fun surfaceChanged(holder: android.view.SurfaceHolder, format: Int, width: Int, height: Int) {
                secondSize = width to height
                // Both DS screens stacked, so the top one is the main screen's and the bottom one this display's.
                PlayerCores.orientationOptions(coreId, landscape = false).forEach { (k, v) -> NativePlayer.setOption(k, v) }
                NativePlayer.setSecondSurface(holder.surface)
                dualScreen = true
                refreshVideo(10)
            }
            override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {
                NativePlayer.setSecondSurface(null)
                dualScreen = false
                PlayerCores.orientationOptions(coreId, isLandscape()).forEach { (k, v) -> NativePlayer.setOption(k, v) }
                refreshVideo(10)
            }
        })
        view.setOnTouchListener { _, event -> secondScreenTouch(event); true }
        secondScreen = android.app.Presentation(this, display).apply {
            setContentView(view)
            setOnDismissListener { secondScreen = null }
            runCatching { show() }.onFailure { secondScreen = null }
        }
    }

    /** A touch on the second display: the picture there is the bottom half of the core's (fitted, centred). */
    private fun secondScreenTouch(event: MotionEvent) {
        val av = video ?: return
        val (w, h) = secondSize
        if (w <= 0 || h <= 0) return
        val aspect = av.aspect * 2
        var rw = w.toFloat()
        var rh = rw / aspect
        if (rh > h) {
            rh = h.toFloat()
            rw = rh * aspect
        }
        val rx = (w - rw) / 2
        val ry = (h - rh) / 2
        val down = event.actionMasked != MotionEvent.ACTION_UP && event.actionMasked != MotionEvent.ACTION_CANCEL
        NativePlayer.setPointer((event.x - rx) / rw, 0.5f + 0.5f * (event.y - ry) / rh, down)
    }
    private var playingSince = 0L

    /** Adds the time since the game came to the front to its play time. */
    private fun countPlaytime() {
        if (playingSince == 0L) return
        val played = android.os.SystemClock.elapsedRealtime() - playingSince
        playingSince = 0L
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME).orEmpty()
        // At once, not on the worker: quitting ends the process straight after this.
        runCatching { playtime.add(system, fileName, played) }
    }

    override fun onResume() {
        super.onResume()
        getSystemService(android.hardware.input.InputManager::class.java).registerInputDeviceListener(controllerListener, null)
        if (controllerConnected()) showTouch = false
        foreground = true
        if (loaded) playingSince = android.os.SystemClock.elapsedRealtime()
        if (loaded && !menuOpen) NativePlayer.setPaused(false)
        setUpSecondScreen()
    }

    override fun onPause() {
        super.onPause()
        getSystemService(android.hardware.input.InputManager::class.java).unregisterInputDeviceListener(controllerListener)
        secondScreen?.dismiss()
        foreground = false
        countPlaytime()
        if (loaded && !quitting) {
            input.releaseAll()
            NativePlayer.setPaused(true)
            // Android may end the player while it's in the background; keep the save and the resume point.
            worker.execute { if (!quitting) persist() }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        announcer?.stop()
        closeRouterPort()
        if (isFinishing) {
            worker.shutdown()
            // The player process exists for this one game. Ending it gives the next game a clean core. Not when switching
            // emulator: the old core is already closed, and the new player may already be starting in this process.
            if (!switching) Process.killProcess(Process.myPid())
        }
    }

    // ---------- input ----------

    /** Back in the menu goes up a page, then closes it; in the game it opens the menu (or leaves a game that failed). */
    private fun onBack() {
        when {
            menuOpen -> if (menuPage != MenuPage.MAIN) menuPage = MenuPage.MAIN else closeMenu()
            phase is Phase.Failed -> finish()
            else -> openMenu()
        }
    }

    // ComponentActivity marks its dispatchKeyEvent as library-internal, but overriding Activity's is how every app
    // reads controller keys.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // System keys go to Android: Back to the back callback (see onCreate), as the gesture does, volume to the sound.
        // Menu stays ours.
        if (event.isSystem && event.keyCode != KeyEvent.KEYCODE_MENU) return super.dispatchKeyEvent(event)
        // Remap buttons is waiting for "press the button you want": that press is the answer, not menu navigation.
        if (menuOpen && input.capture != null) {
            input.onKey(event)
            return true
        }
        if (menuOpen) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_MODE, KeyEvent.KEYCODE_MENU -> {
                    if (event.action == KeyEvent.ACTION_UP) onBack()
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_A -> return super.dispatchKeyEvent(
                    KeyEvent(event.downTime, event.eventTime, event.action, KeyEvent.KEYCODE_DPAD_CENTER, event.repeatCount, event.metaState, event.deviceId, event.scanCode, event.flags, event.source),
                )
            }
            return super.dispatchKeyEvent(event)
        }
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_MODE || event.keyCode == KeyEvent.KEYCODE_MENU) {
            if (event.action == KeyEvent.ACTION_UP) onBack()
            return true
        }
        // A real keyboard (Bluetooth or USB) types into computers; on other systems its keys stay controller keys.
        if (phase == Phase.Playing && system in PlayerCores.keyboardSystems && isTypingKeyboard(event)) {
            val key = com.retroforge.player.RetroKeys.fromAndroid(event.keyCode)
            if (key != 0 && (event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_UP)) {
                NativePlayer.setKey(key, event.action == KeyEvent.ACTION_DOWN, event.unicodeChar, com.retroforge.player.RetroKeys.modifiers(event.metaState))
                return true
            }
        }
        if (phase == Phase.Playing && input.onKey(event)) {
            showTouch = false
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    /** A key from a real typing keyboard (or Android's virtual one), never a gamepad or a TV remote's d-pad. */
    private fun isTypingKeyboard(event: KeyEvent): Boolean {
        if (event.isFromSource(android.view.InputDevice.SOURCE_GAMEPAD) || event.isFromSource(android.view.InputDevice.SOURCE_JOYSTICK)) return false
        return event.deviceId == android.view.KeyCharacterMap.VIRTUAL_KEYBOARD ||
            event.device?.keyboardType == android.view.InputDevice.KEYBOARD_TYPE_ALPHABETIC
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (!menuOpen && phase == Phase.Playing && input.onMotion(event)) {
            showTouch = false
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN && hasTouch) showTouch = true
        return super.dispatchTouchEvent(event)
    }

    // ---------- screen ----------

    private val surfaceCallback = object : SurfaceHolder.Callback {
        override fun surfaceCreated(holder: SurfaceHolder) {}

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            surfaceWidth = width
            surfaceHeight = height
            applyViewport()
            synchronized(surfaceLock) {
                surface = holder.surface
                if (loaded) NativePlayer.setSurface(holder.surface)
            }
            video?.let { setFrameRate(holder.surface, it) }
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            synchronized(surfaceLock) {
                surface = null
                if (loaded) NativePlayer.setSurface(null)
            }
        }
    }

    /** Places the picture for the current screen shape; the touchscreen mapping uses the same rectangle. */
    private fun applyViewport() {
        val av = video ?: return
        // With a bezel, the whole bezel fits the screen and the picture fills its window.
        val image = bezelImage
        val window = bezelWindow
        if (image != null && window != null && bezelOn && isLandscape() && !vr && !dualScreen && surfaceWidth > 0 && surfaceHeight > 0) {
            val scale = minOf(surfaceWidth.toFloat() / image.width, surfaceHeight.toFloat() / image.height)
            val x = (surfaceWidth - image.width * scale) / 2
            val y = (surfaceHeight - image.height * scale) / 2
            bezelPlace = Rect(x.toInt(), y.toInt(), (image.width * scale).toInt(), (image.height * scale).toInt())
            picture = Rect((x + window.x * scale).toInt(), (y + window.y * scale).toInt(), (window.w * scale).toInt(), (window.h * scale).toInt())
            pictureRect = picture
            if (loaded) picture.let { NativePlayer.setViewport(it.x, it.y, it.w, it.h) }
            return
        }
        bezelPlace = null
        // With the console look, the picture keeps a margin for the screen surround.
        val inset = if (consoleLook && !vr) (bezelMargin(!isLandscape()).value * resources.displayMetrics.density).toInt() else 0
        // With a second display the main screen shows only the top half (the DS's upper screen).
        val sbs = PlayerCores.sideBySide(coreId)
        val shown = when {
            dualScreen -> av.copy(height = av.height / 2, aspect = av.aspect * 2)
            // Both eyes side by side (Virtual Boy 3D): one eye is shown, so the picture takes one eye's shape, whatever
            // the core reports (its aspect ratio covers both eyes).
            sbs -> {
                val eyeWidth = if (av.width.toFloat() / av.height > 2.5f) av.width / 2 else av.width
                av.copy(width = eyeWidth, aspect = eyeWidth.toFloat() / av.height)
            }
            else -> av
        }
        val fitted = Viewport.fit(shape, surfaceWidth - 2 * inset, surfaceHeight - 2 * inset, shown)
        picture = Rect(fitted.x + inset, fitted.y + inset, fitted.w, fitted.h)
        pictureRect = picture
        if (loaded) picture.let { NativePlayer.setViewport(it.x, it.y, it.w, it.h) }
    }

    private fun chooseShape(next: ScreenShape) {
        shape = next
        prefs.edit().putString("shape.$system", next.name).apply()
        applyViewport()
    }

    private fun chooseFilter(next: com.retroforge.player.ScreenFilter) {
        filter = next
        prefs.edit().putString("filter.$system", next.name).apply()
        NativePlayer.setFilter(next.ordinal)
    }

    private fun chooseVolume() {
        volume = when {
            volume < 1.4f -> 1.5f
            volume < 1.9f -> 2f
            else -> 1f
        }
        prefs.edit().putFloat("volume.$system", volume).apply()
        NativePlayer.setVolume(volume)
    }

    private fun chooseLook(on: Boolean) {
        consoleLook = on
        prefs.edit().putBoolean("look.$system", on).apply()
        applyViewport()
    }

    private fun chooseBezel(on: Boolean) {
        bezelOn = on
        prefs.edit().putBoolean("bezel.$system", on).apply()
        applyViewport()
    }

    /**
     * The system bezel straight away, then the game's own once it's downloaded (the first time the game is played).
     * Nothing when this system's bezels haven't been downloaded.
     */
    private fun loadBezel(fileName: String) {
        if (!bezels.downloaded(system)) return
        fun show(file: File?) {
            val bitmap = file?.let { android.graphics.BitmapFactory.decodeFile(it.path) } ?: return
            val window = Bezels.window(bitmap) ?: return
            val image = bitmap.asImageBitmap()
            image.prepareToDraw()
            runOnUiThread {
                bezelImage = image
                bezelWindow = window
                applyViewport()
            }
        }
        Thread {
            show(bezels.systemBezel(system))
            runCatching { show(bezels.gameBezel(system, fileName)) }
                .onFailure { android.util.Log.w("RetroForgePlayer", "bezel: ${it.message}") }
        }.start()
    }

    /** From the in-game menu: this system's bezels, so the game shows its bezel straight away. */
    private fun downloadBezels() {
        bezelBusy = true
        bezelStatus = "Downloading bezels from The Bezel Project…"
        Thread {
            val error = runCatching { bezels.install(system) }.exceptionOrNull()
            runOnUiThread {
                bezelBusy = false
                bezelStatus = if (error == null) "Bezels downloaded. They show in landscape." else "Couldn't download bezels: ${error.message}"
                if (error == null) chooseBezel(true)
            }
            if (error == null) loadBezel(intent.getStringExtra(EXTRA_FILE_NAME).orEmpty())
        }.start()
    }

    private fun chooseTouch(on: Boolean) {
        touchEnabled = on
        prefs.edit().putBoolean("touch.$system", on).apply()
    }

    private fun setFrameRate(surface: Surface, av: Video) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && av.fps > 0) {
            runCatching { surface.setFrameRate(av.fps, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE) }
        }
    }

    // The touchscreen as a trackpad for computers with a mouse: where the last finger was, when it went down, whether
    // it moved (a tap is a click), and two fingers (a right click).
    private var padLastX = 0f
    private var padLastY = 0f
    private var padDownAt = 0L
    private var padMoved = false
    private var padTwoFingers = false

    /**
     * Touches on the picture itself go to the core as a touchscreen (the DS's lower screen, for example), and on
     * computers also as a mouse: drag to move it, tap to click, tap with two fingers to right-click.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun pointerListener(view: SurfaceView) = view.setOnTouchListener { v, event ->
        val rect = picture
        if (!loaded || rect.w <= 0 || rect.h <= 0 || dualScreen) return@setOnTouchListener false
        val down = event.actionMasked != MotionEvent.ACTION_UP && event.actionMasked != MotionEvent.ACTION_CANCEL
        NativePlayer.setPointer((event.x - rect.x) / rect.w, (event.y - rect.y) / rect.h, down)
        if (system in PlayerCores.mouseSystems) trackpad(event, rect.w.toFloat())
        true
    }

    private fun trackpad(event: MotionEvent, pictureWidth: Float) {
        // Game pixels per screen pixel, a little faster than 1:1 so the whole screen is reachable in one swipe.
        val speed = 1.5f * (video?.width ?: 320) / pictureWidth
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                padLastX = event.x; padLastY = event.y; padDownAt = event.eventTime; padMoved = false; padTwoFingers = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> padTwoFingers = true
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - padLastX
                val dy = event.y - padLastY
                if (padMoved || kotlin.math.abs(dx) + kotlin.math.abs(dy) > 12) {
                    padMoved = true
                    NativePlayer.setMouse((dx * speed).toInt(), (dy * speed).toInt(), 0)
                    padLastX = event.x; padLastY = event.y
                }
            }
            MotionEvent.ACTION_UP -> if (!padMoved && event.eventTime - padDownAt < 300) {
                // A tap: press and release the button a few frames apart, so the game sees the click.
                val button = if (padTwoFingers) 2 else 1
                NativePlayer.setMouse(0, 0, button)
                handler.postDelayed({ NativePlayer.setMouse(0, 0, 0) }, 90)
            }
        }
    }

    @Composable
    private fun PlayerScreen(layout: PadLayout, title: String) {
        // The FPS counter: frames put on screen over each second.
        LaunchedEffect(showFps, phase) {
            fps = null
            if (!showFps || phase != Phase.Playing) return@LaunchedEffect
            var last = NativePlayer.framesShown()
            while (true) {
                kotlinx.coroutines.delay(1000)
                val now = NativePlayer.framesShown()
                fps = (now - last).toInt().coerceAtLeast(0)
                last = now
            }
        }
        // No background here: the game surface sits behind the window, and anything painted would cover it.
        Box(Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val portrait = maxHeight > maxWidth
                // Compact controls when the full-width picture wouldn't leave room for the normal ones (the DS's two screens).
                val aspect = video?.aspect ?: (4f / 3f)
                val compact = portrait && maxHeight - maxWidth / aspect < touchControlsHeight(layout, compact = false)
                val controls: @Composable (Modifier) -> Unit = { modifier ->
                    if (touchEnabled && showTouch && !vr && phase == Phase.Playing && !menuOpen) {
                        TouchControls(
                            layout, onMask = { input.setTouch(it) }, onMenu = { openMenu() }, modifier = modifier,
                            compact = compact, landscape = !portrait,
                            onAnalog = { stick, x, y -> if (loaded) NativePlayer.setAnalog(0, stick, (x * 32767).toInt(), (y * 32767).toInt()) },
                        )
                    }
                }
                val skin = if (consoleLook && !vr) ConsoleSkin.forSystem(system) else null
                if (portrait) {
                    // The picture gets the space the controls leave free (all of it when they're off), fitted and centred
                    // by the screen shape, so a tall picture like the DS's two screens never runs under the buttons.
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.fillMaxWidth().weight(1f)) {
                            GameSurface(Modifier.fillMaxSize())
                            skin?.let { ConsoleFrame(it, pictureRect, Modifier.fillMaxSize(), bezelMargin(portrait = true)) }
                        }
                        // The pad's half of the body: a gentle top-to-bottom gradient for some depth.
                        Box(Modifier.fillMaxWidth().then(if (skin != null) Modifier.background(Brush.verticalGradient(listOf(skin.body, skin.bodyShade))) else Modifier)) {
                            CompositionLocalProvider(LocalConsoleSkin provides skin) { controls(Modifier.fillMaxWidth().padding(bottom = 8.dp)) }
                        }
                    }
                } else {
                    // Landscape: the picture uses the full height; the controls sit over it at the sides.
                    GameSurface(Modifier.fillMaxSize())
                    val bezel = bezelImage
                    val place = bezelPlace
                    if (bezel != null && place != null) {
                        // The Bezel Project's frame round the picture, instead of the console look.
                        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                            drawImage(
                                bezel, dstOffset = androidx.compose.ui.unit.IntOffset(place.x, place.y),
                                dstSize = androidx.compose.ui.unit.IntSize(place.w, place.h),
                                filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                            )
                        }
                    } else {
                        skin?.let { ConsoleFrame(it, pictureRect, Modifier.fillMaxSize()) }
                    }
                    CompositionLocalProvider(LocalConsoleSkin provides skin) { controls(Modifier.fillMaxSize()) }
                }
            }
            // Drawn last, so the console look and the controls never cover them.
            if (netShowStatus) {
                Text(
                    netStatus,
                    color = if (netState == 4) Color(0xFF9FE3B0) else if (netState == 5) Color(0xFFFF9A8B) else Color(0xFFFFC56B),
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 36.dp, end = 12.dp)
                        .background(Color(0x99000000), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            fps?.let { shown ->
                val target = video?.fps?.toInt() ?: 60
                Text(
                    "$shown / $target fps",
                    color = if (shown >= target - 2) Color(0xFF9FE3B0) else Color(0xFFFFC56B),
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.TopStart).padding(top = 36.dp, start = 12.dp)
                        .background(Color(0x99000000), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }

            if (phase == Phase.Playing && !menuOpen) {
                // Swipe in from either edge for the menu. (With gesture navigation the system's own back swipe does the same.)
                EdgeSwipe(Modifier.align(Alignment.CenterStart), fromLeft = true)
                EdgeSwipe(Modifier.align(Alignment.CenterEnd), fromLeft = false)
            }
            when (val p = phase) {
                Phase.Loading -> Centered {
                    CircularProgressIndicator()
                    Text("Starting $title…", color = Color.White, modifier = Modifier.padding(top = 16.dp))
                    loadingDetail?.let { Text(it, color = Color(0xFFBBBBC4), fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
                }
                is Phase.Failed -> Centered {
                    Text("Couldn't start $title", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(p.message, color = Color(0xFFDDDDDD), textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 16.dp))
                    Button(onClick = { finish() }) { Text("Back to RetroForge") }
                }
                Phase.Playing -> if (menuOpen) PauseMenu(title)
            }
        }
    }

    @Composable
    private fun GameSurface(modifier: Modifier) {
        AndroidView(factory = { context ->
            SurfaceView(context).apply {
                holder.addCallback(surfaceCallback)
                pointerListener(this)
                gameView = this
            }
        }, modifier = modifier)
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

    @Composable
    private fun PauseMenu(title: String) {
        val first = remember(menuPage) { FocusRequester() }
        LaunchedEffect(menuPage) { runCatching { first.requestFocus() } }
        Box(Modifier.fillMaxSize().background(Color(0xCC000000)), contentAlignment = Alignment.Center) {
          Column(Modifier.width(320.dp).heightIn(max = 560.dp).background(Color(0xFF1C1C22), RoundedCornerShape(16.dp))) {
            // A header that doesn't scroll: back (on sub-pages) and close (back to the game) are always in reach.
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (menuPage != MenuPage.MAIN) {
                    androidx.compose.material3.TextButton(onClick = { menuPage = MenuPage.MAIN }) { Text("‹ Back", color = Color.White) }
                } else {
                    androidx.compose.foundation.layout.Spacer(Modifier.width(1.dp))
                }
                androidx.compose.material3.TextButton(onClick = { closeMenu() }) { Text("✕", color = Color.White, fontSize = 20.sp) }
            }
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val wide = Modifier.fillMaxWidth()
                val coreName = PlayerCores.byId(coreId)?.name ?: coreId
                when (menuPage) {
                    MenuPage.MAIN -> {
                        Text(title, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Text("Paused · $coreName", color = Color(0xFFAAAAB4), fontSize = 13.sp)
                        notice?.let { Text(it, color = Color(0xFF9FE3B0), fontSize = 13.sp, textAlign = TextAlign.Center) }
                        MenuButton(onClick = { closeMenu() }, modifier = wide.focusRequester(first)) { Text("Resume") }
                        MenuButton(onClick = {
                            worker.execute {
                                val saved = NativePlayer.saveState()?.let { writeSafely(slotState(), it); true } == true
                                runOnUiThread { notice = if (saved) "State saved" else "This emulator can't save states" }
                            }
                        }, modifier = wide) { Text("Save state") }
                        MenuButton(onClick = {
                            if (netGuest) { notice = "During netplay only player 1 can load a state."; return@MenuButton }
                            worker.execute {
                                val file = slotState()
                                val done = file.isFile && NativePlayer.loadState(file.readBytes())
                                runOnUiThread { if (done) closeMenu() else notice = "No saved state yet" }
                            }
                        }, modifier = wide) { Text("Load state") }
                        MenuButton(onClick = {
                            if (netPlaying) { notice = "Fast-forward is off during netplay: both players run at the same speed."; return@MenuButton }
                            fastForwardOn = !fastForwardOn
                            NativePlayer.setFastForward(fastForwardOn)
                        }, modifier = wide) { Text("Fast-forward: ${if (fastForwardOn) "On" else "Off"}") }
                        // Multi-disc games (an .m3u): put in the next disc when the game asks for it.
                        val discs = remember(menuPage) { if (loaded) NativePlayer.discInfo() else null }
                        if (discs != null) {
                            val (count, current) = discs
                            MenuButton(onClick = {
                                val next = (current + 1) % count
                                notice = if (NativePlayer.insertDisc(next)) "Disc ${next + 1} inserted" else "Couldn't change disc"
                                closeMenu()
                            }, modifier = wide) { Text("Disc ${current + 1} of $count · change to disc ${(current + 1) % count + 1}") }
                        }
                        if (system in PlayerCores.keyboardSystems) {
                            MenuButton(onClick = { closeMenu(); keyboard.show() }, modifier = wide) { Text("Keyboard") }
                        }
                        MenuButton(onClick = { takeScreenshot() }, modifier = wide) { Text("Take a screenshot") }
                        // The rest in three groups, so the menu stays short.
                        MenuButton(onClick = { menuPage = MenuPage.PICTURE }, modifier = wide) { Text("Picture ›") }
                        MenuButton(onClick = { menuPage = MenuPage.SOUND_CONTROLS }, modifier = wide) { Text("Sound and controls ›") }
                        MenuButton(onClick = { menuPage = MenuPage.EMULATOR }, modifier = wide) { Text("Emulator ›") }
                        MenuButton(onClick = { menuPage = MenuPage.NETPLAY; if (netState == 0 && netHosts == null) findNetplayHosts() }, modifier = wide) {
                            Text(if (netPlaying) "Netplay: player ${NativePlayer.netplayPlayer() + 1} ›" else "Netplay ›")
                        }
                        MenuButton(onClick = {
                            if (netGuest) { notice = "During netplay only player 1 can restart the game."; return@MenuButton }
                            NativePlayer.reset()
                            // Player 2 gets the restarted game too (a state load sends it).
                            if (netPlaying) NativePlayer.saveState()?.let { NativePlayer.loadState(it) }
                            closeMenu()
                        }, modifier = wide) { Text("Restart game") }
                        MenuButton(onClick = { quit() }, modifier = wide) { Text("Quit to RetroForge") }
                    }
                    MenuPage.NETPLAY -> {
                        Text("Netplay", color = Color.White, fontWeight = FontWeight.Bold)
                        MenuButton(onClick = { menuPage = MenuPage.MAIN }, modifier = wide.focusRequester(first)) { Text("‹ Back") }
                        if (netStatus.isNotBlank() && netState != 0) {
                            Text(netStatus, color = if (netState == 5) Color(0xFFFF9A8B) else Color(0xFF9FE3B0), fontSize = 13.sp, textAlign = TextAlign.Center)
                        }
                        if (netState in 1..4) {
                            if (netState == 1) {
                                val addresses = Netplay.localAddresses()
                                Text(
                                    "On the same Wi-Fi, player 2 opens this game and picks Netplay → your device. " +
                                        "Or they join by address: ${addresses.firstOrNull() ?: "this device's IP"} (port ${Netplay.GAME_PORT}).",
                                    color = Color(0xFFAAAAB4), fontSize = 13.sp, textAlign = TextAlign.Center,
                                )
                                netInternet?.let { Text(it, color = Color(0xFFAAAAB4), fontSize = 13.sp, textAlign = TextAlign.Center) }
                            }
                            MenuButton(onClick = { leaveNetplay() }, modifier = wide) { Text("Leave netplay") }
                        } else {
                            Text(
                                "Play this game with a friend on another device, each with RetroForge and the same game file. " +
                                    "One hosts (player 1), the other joins (player 2).",
                                color = Color(0xFFAAAAB4), fontSize = 13.sp, textAlign = TextAlign.Center,
                            )
                            MenuButton(onClick = { hostNetplay() }, modifier = wide) { Text("Host this game") }
                            when {
                                netSearching -> Text("Looking for games on this Wi-Fi…", color = Color(0xFFAAAAB4), fontSize = 13.sp)
                                netHosts?.isEmpty() == true -> Text("Nobody's hosting on this Wi-Fi yet.", color = Color(0xFFAAAAB4), fontSize = 13.sp)
                            }
                            netHosts.orEmpty().forEach { host ->
                                val same = host.gameId == netGameId
                                MenuButton(onClick = {
                                    if (same) joinNetplay(host.address, host.port)
                                    else notice = "${host.player} is playing ${host.title}. Open that game to join."
                                }, modifier = wide) { Text(if (same) "Join ${host.player}" else "${host.player}: ${host.title}") }
                            }
                            MenuButton(onClick = { findNetplayHosts() }, modifier = wide) { Text("Look again") }
                            androidx.compose.material3.OutlinedTextField(
                                value = netAddress,
                                onValueChange = { netAddress = it.trim() },
                                singleLine = true,
                                label = { Text("Join by address (internet)") },
                                modifier = wide,
                            )
                            MenuButton(onClick = {
                                if (netAddress.isBlank()) return@MenuButton
                                val port = netAddress.substringAfterLast(':', "").toIntOrNull()
                                joinNetplay(if (port != null) netAddress.substringBeforeLast(':') else netAddress, port ?: Netplay.GAME_PORT)
                            }, modifier = wide) { Text("Join this address") }
                        }
                    }
                    MenuPage.PICTURE -> {
                        Text("Picture", color = Color.White, fontWeight = FontWeight.Bold)
                        MenuButton(onClick = { menuPage = MenuPage.MAIN }, modifier = wide.focusRequester(first)) { Text("‹ Back") }
                        MenuButton(onClick = { chooseShape(shape.next()) }, modifier = wide) { Text("Screen shape: ${shape.label}") }
                        MenuButton(onClick = { chooseFilter(filter.next()) }, modifier = wide) { Text("Screen filter: ${filter.label}") }
                        MenuButton(onClick = { chooseLook(!consoleLook) }, modifier = wide) { Text("Console look: ${if (consoleLook) "On" else "Off"}") }
                        if (Bezels.repoFor(system) != null) {
                            if (bezels.downloaded(system)) MenuButton(onClick = { chooseBezel(!bezelOn) }, modifier = wide) {
                                Text("Bezel (landscape): ${if (bezelOn) "On" else "Off"}")
                            } else MenuButton(onClick = { if (!bezelBusy) downloadBezels() }, modifier = wide) {
                                Text(if (bezelBusy) "Downloading bezels…" else "Download bezels (The Bezel Project)")
                            }
                            bezelStatus?.let { Text(it, color = Color(0xFF9FE3B0), fontSize = 13.sp, textAlign = TextAlign.Center) }
                        }
                        PlayerCores.widescreenOption(coreId)?.let { (key, wideValue, _) ->
                            val on = coreOptions.firstOrNull { it.key == key }?.value == wideValue
                            MenuButton(onClick = { chooseWidescreen() }, modifier = wide) { Text("Widescreen: ${if (on) "On" else "Off"}") }
                        }
                        if (PlayerCores.resolutionOption(coreId) != null) MenuButton(onClick = { chooseScale() }, modifier = wide) {
                            Text("Resolution: ${scale}×" + if (scale == 1) " (original)" else "")
                        }
                        if (coreId in PlayerCores.vulkanCores) MenuButton(onClick = { chooseGraphics() }, modifier = wide) {
                            Text("Graphics: ${PlayerCores.graphicsLabel(coreId, vulkanOn)}")
                        }
                        MenuButton(onClick = { chooseVr(!vr) }, modifier = wide) {
                            Text("Cardboard VR: ${if (vr) "On" else "Off"}" + if (PlayerCores.sideBySide(coreId)) " (3D)" else "")
                        }
                        MenuButton(onClick = {
                            showFps = !showFps
                            videoChoices.set("show_fps", if (showFps) "true" else null)
                        }, modifier = wide) { Text("Show FPS: ${if (showFps) "On" else "Off"}") }
                    }
                    MenuPage.SOUND_CONTROLS -> {
                        Text("Sound and controls", color = Color.White, fontWeight = FontWeight.Bold)
                        MenuButton(onClick = { menuPage = MenuPage.MAIN }, modifier = wide.focusRequester(first)) { Text("‹ Back") }
                        MenuButton(onClick = { chooseVolume() }, modifier = wide) { Text("Volume: ${(volume * 100).toInt()}%") }
                        if (hasTouch) MenuButton(onClick = { chooseTouch(!touchEnabled) }, modifier = wide) {
                            Text("Touch controls: ${if (touchEnabled) "On" else "Off"}")
                        }
                        MenuButton(onClick = { menuPage = MenuPage.CONTROLS }, modifier = wide) { Text("Remap buttons ›") }
                    }
                    MenuPage.EMULATOR -> {
                        Text("Emulator · $coreName", color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        MenuButton(onClick = { menuPage = MenuPage.MAIN }, modifier = wide.focusRequester(first)) { Text("‹ Back") }
                        MenuButton(onClick = {
                            coreOptions = NativePlayer.options()
                            menuPage = MenuPage.OPTIONS
                        }, modifier = wide) { Text("Emulator settings ›") }
                        MenuButton(onClick = { openCheats() }, modifier = wide) {
                            Text("Cheats ›" + cheats.count { it.enabled }.let { if (it > 0) "  ($it on)" else "" })
                        }
                        if (PlayerCores.choices(system).size > 1) {
                            MenuButton(onClick = { menuPage = MenuPage.EMULATORS }, modifier = wide) { Text("Switch emulator ›") }
                        }
                    }
                    MenuPage.CHEATS -> {
                        Text("Cheats", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(
                            "From libretro's cheat database (CC BY-SA 4.0). Switch one on and it works at once. Cheats can upset saves: save your game first.",
                            color = Color(0xFFAAAAB4), fontSize = 13.sp, textAlign = TextAlign.Center,
                        )
                        cheatStatus?.let { Text(it, color = Color(0xFF9FE3B0), fontSize = 13.sp, textAlign = TextAlign.Center) }
                        MenuButton(onClick = { menuPage = MenuPage.MAIN }, modifier = wide.focusRequester(first)) { Text("‹ Back") }
                        if (!fetchingCheats) cheats.forEachIndexed { i, cheat ->
                            MenuButton(onClick = {
                                updateCheats(cheats.toMutableList().also { it[i] = cheat.copy(enabled = !cheat.enabled) })
                            }, modifier = wide) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(cheat.description, textAlign = TextAlign.Center, fontSize = 14.sp)
                                    Text(if (cheat.enabled) "On" else "Off", color = if (cheat.enabled) Color(0xFF9FE3B0) else Color(0xFFFF8A1F), fontSize = 13.sp)
                                }
                            }
                            if (cheat.custom) {
                                MenuButton(onClick = { updateCheats(cheats.filterIndexed { j, _ -> j != i }) }, modifier = wide) {
                                    Text("Remove \"${cheat.description}\"", fontSize = 13.sp)
                                }
                            }
                        }
                        if (!fetchingCheats) {
                        Text("Add your own code", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                        com.retroforge.core.ui.ControllerTextField(newCode, { newCode = it.take(400) }, wide, label = { Text("Code (Game Genie, Action Replay …)") })
                        com.retroforge.core.ui.ControllerTextField(newName, { newName = it.take(60) }, wide, label = { Text("Name (optional)") })
                        MenuButton(onClick = {
                            val code = newCode.trim()
                            if (code.isNotEmpty()) {
                                updateCheats(cheats + Cheat(newName.trim().ifEmpty { "My code ${cheats.count { it.custom } + 1}" }, code, enabled = true, custom = true))
                                newCode = ""
                                newName = ""
                            }
                        }, modifier = wide) { Text("Add and switch on") }
                        }
                    }
                    MenuPage.OPTIONS -> {
                        Text("$coreName settings", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Select a setting to change it. Kept for this emulator.", color = Color(0xFFAAAAB4), fontSize = 13.sp, textAlign = TextAlign.Center)
                        MenuButton(onClick = { menuPage = MenuPage.MAIN }, modifier = wide.focusRequester(first)) { Text("‹ Back") }
                        if (coreOptions.isEmpty()) Text("This emulator has no settings.", color = Color(0xFFDDDDDD))
                        coreOptions.forEach { option ->
                            MenuButton(onClick = { chooseOption(option) }, modifier = wide) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(option.label, textAlign = TextAlign.Center, fontSize = 14.sp)
                                    Text(option.value, color = Color(0xFFFF8A1F), fontSize = 13.sp)
                                }
                            }
                        }
                    }
                    MenuPage.CONTROLS -> {
                        Text("Remap buttons", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(
                            "Select a button, then press the controller button you want for it. Kept for this system.",
                            color = Color(0xFFAAAAB4), fontSize = 13.sp, textAlign = TextAlign.Center,
                        )
                        MenuButton(onClick = { input.capture = null; waitingFor = null; menuPage = MenuPage.MAIN }, modifier = wide.focusRequester(first)) { Text("‹ Back") }
                        val buttons = gameButtons()
                        Text("Controller", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        buttons.forEach { (label, button) ->
                            MenuButton(onClick = { remapButton(button) }, modifier = wide) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(label, fontSize = 14.sp)
                                    Text(
                                        if (waitingFor == button) "Press a controller button…" else keyFor(button),
                                        color = if (waitingFor == button) Color(0xFF9FE3B0) else Color(0xFFFF8A1F), fontSize = 13.sp,
                                    )
                                }
                            }
                        }
                        if (remap.isNotEmpty()) MenuButton(onClick = { saveRemap(emptyMap()) }, modifier = wide) { Text("Controller back to normal") }
                        Text("On-screen buttons", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
                        Text("Select one to change which game button it presses.", color = Color(0xFFAAAAB4), fontSize = 13.sp, textAlign = TextAlign.Center)
                        val onScreen = padLayout().let { it.face + it.shoulders + it.triggers }
                        onScreen.forEach { (label, default) ->
                            val now = touchMap[label] ?: default
                            MenuButton(onClick = {
                                val next = buttons[(buttons.indexOfFirst { it.second == now } + 1) % buttons.size].second
                                saveTouchMap(if (next == default) touchMap - label else touchMap + (label to next))
                            }, modifier = wide) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("On-screen $label", fontSize = 14.sp)
                                    Text("presses " + (buttons.firstOrNull { it.second == now }?.first ?: "?"), color = Color(0xFFFF8A1F), fontSize = 13.sp)
                                }
                            }
                        }
                        if (touchMap.isNotEmpty()) MenuButton(onClick = { saveTouchMap(emptyMap()) }, modifier = wide) { Text("On-screen back to normal") }
                    }
                    MenuPage.EMULATORS -> {
                        Text("Switch emulator", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(
                            "The game restarts in the emulator you pick and your in-game save carries over. Save states belong to each emulator.",
                            color = Color(0xFFAAAAB4), fontSize = 13.sp, textAlign = TextAlign.Center,
                        )
                        notice?.let { Text(it, color = Color(0xFF9FE3B0), fontSize = 13.sp, textAlign = TextAlign.Center) }
                        MenuButton(onClick = { menuPage = MenuPage.MAIN }, modifier = wide.focusRequester(first)) { Text("‹ Back") }
                        PlayerCores.choices(system).forEach { core ->
                            MenuButton(onClick = { switchEmulator(core) }, modifier = wide) {
                                Text(
                                    core.name + (if (core.id == coreId) "  ✓" else "") + (if (core.needsBios) " (needs BIOS)" else ""),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
          }
        }
    }

    companion object {
        /** Controller keys that press a game button by default (see PlayerInput.buttonFor). */
        private val DEFAULT_KEYS = listOf(
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y,
            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2,
            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT,
        )

        private const val EXTRA_CORE_PATH = "core_path"
        private const val EXTRA_URI = "uri"
        private const val EXTRA_FILE_NAME = "file_name"
        private const val EXTRA_SYSTEM = "system"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_RESUME = "resume"
        private const val EXTRA_FAST_FORWARD_R2 = "fast_forward_r2"
        private const val EXTRA_MENU_COMBO = "menu_combo"
        private const val EXTRA_QUIT_COMBO = "quit_combo"
        private const val EXTRA_COPY_FIRST = "copy_first"
        private const val EXTRA_NETPLAY = "netplay"
        /** EXTRA_NETPLAY: host this game; "join:address:port" joins one. */
        const val NETPLAY_HOST = "host"
        /** EXTRA_NETPLAY: open the netplay page, looking for hosts on this Wi-Fi. */
        const val NETPLAY_FIND = "find"

        /** In the "views" preferences: copy games that must be files to the phone before playing, instead of streaming. */
        const val COPY_FIRST_PREF = "copy_games_first"

        fun intent(
            context: Context,
            corePath: String,
            uri: Uri,
            fileName: String,
            system: String,
            title: String,
            resume: Boolean,
            fastForwardOnR2: Boolean,
            menuCombo: HotkeyCombo,
            quitCombo: HotkeyCombo,
            copyFirst: Boolean = false,
            netplay: String? = null,
        ): Intent = Intent(context, PlayerActivity::class.java)
            .putExtra(EXTRA_COPY_FIRST, copyFirst)
            .apply { if (netplay != null) putExtra(EXTRA_NETPLAY, netplay) }
            .putExtra(EXTRA_CORE_PATH, corePath)
            .putExtra(EXTRA_URI, uri.toString())
            .putExtra(EXTRA_FILE_NAME, fileName)
            .putExtra(EXTRA_SYSTEM, system)
            .putExtra(EXTRA_TITLE, title)
            .putExtra(EXTRA_RESUME, resume)
            .putExtra(EXTRA_FAST_FORWARD_R2, fastForwardOnR2)
            .putExtra(EXTRA_MENU_COMBO, menuCombo.value)
            .putExtra(EXTRA_QUIT_COMBO, quitCombo.value)
    }
}

/** Menu entry with an unmistakable focus outline, for controllers and TV remotes. */
@Composable
private fun MenuButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        interactionSource = interaction,
        border = BorderStroke(if (focused) 3.dp else 1.dp, if (focused) Color(0xFFFF8A1F) else Color(0xFF55555F)),
        content = content,
    )
}
