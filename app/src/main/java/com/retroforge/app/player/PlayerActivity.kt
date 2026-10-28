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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.retroforge.player.NativePlayer
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
    private var filter by mutableStateOf(com.retroforge.player.ScreenFilter.SHARP)
    private var pictureRect by mutableStateOf(Rect(0, 0, 0, 0))
    private enum class MenuPage { MAIN, OPTIONS, EMULATORS }
    private var menuPage by mutableStateOf(MenuPage.MAIN)
    private var fastForwardOn by mutableStateOf(false)
    private var coreOptions by mutableStateOf<List<CoreOption>>(emptyList())
    @Volatile private var switching = false
    private lateinit var coreChoices: CoreChoices
    private lateinit var cores: CoreManager
    private lateinit var prefs: SharedPreferences
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    @Volatile private var picture = Rect(0, 0, 0, 0)

    private val worker = Executors.newSingleThreadExecutor()
    private val surfaceLock = Any()
    private var surface: Surface? = null
    @Volatile private var loaded = false
    @Volatile private var quitting = false
    private var foreground = false
    private var hasTouch = true

    private fun controllerConnected() = android.view.InputDevice.getDeviceIds().any { id ->
        val device = android.view.InputDevice.getDevice(id) ?: return@any false
        !device.isVirtual && (device.sources and android.view.InputDevice.SOURCE_GAMEPAD == android.view.InputDevice.SOURCE_GAMEPAD ||
            device.sources and android.view.InputDevice.SOURCE_JOYSTICK == android.view.InputDevice.SOURCE_JOYSTICK)
    }

    private lateinit var system: String
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
        cores = CoreManager(File(filesDir, "cores"), "RetroForge/${com.retroforge.app.BuildConfig.VERSION_NAME}")
        shape = ScreenShape.of(prefs.getString("shape.$system", null))
        touchEnabled = prefs.getBoolean("touch.$system", true)
        consoleLook = prefs.getBoolean("look.$system", true)
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

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                PlayerScreen(PadLayout.forSystem(system), extras.getString(EXTRA_TITLE).orEmpty())
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
        // The user's emulator settings for this core, then the ones that follow the screen's orientation.
        val options = PlayerCores.defaultOptions(coreId) + savedOptions() + PlayerCores.orientationOptions(coreId, isLandscape())
        val error = NativePlayer.openCore(corePath, systemDir.path, savesDir.path, options.keys.toTypedArray(), options.values.toTypedArray())
        if (error != null) return fail(error)
        val info = NativePlayer.coreInfo()
        val ok = try {
            loadContent(uri, fileName, info.needsFile, info.extensions)
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
        NativePlayer.setVolume(volume)
        PlayerCores.portDevice(coreId)?.let { NativePlayer.setPortDevice(0, it) }
        NativePlayer.start()
        runOnUiThread {
            video = av
            applyViewport()
            phase = Phase.Playing
            NativePlayer.setPaused(!foreground || menuOpen)
            surface?.let { setFrameRate(it, av) }
        }
    }

    /**
     * Gives the core the game the way it wants it: in memory straight from the folder when it can take that,
     * otherwise as a file copied into the cache. A .zip or .7z is unpacked first unless the core reads it itself.
     */
    private fun loadContent(uri: Uri, fileName: String, needsFile: Boolean, extensions: Set<String>): Boolean {
        val cache = File(cacheDir, "player").apply { deleteRecursively(); mkdirs() }
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext == "zip" && "zip" !in extensions) {
            val file = contentResolver.openInputStream(uri)?.use { stream ->
                ZipInputStream(stream).use { zip ->
                    val entries = generateSequence { zip.nextEntry }.filter { !it.isDirectory }
                    val entry = entries.firstOrNull { it.name.substringAfterLast('.').lowercase() in extensions }
                        ?: throw IOException("the zip has no file this emulator can open")
                    File(cache, entry.name.substringAfterLast('/')).also { out -> out.outputStream().use { zip.copyTo(it) } }
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
                File(cache, entry.name.substringAfterLast('/')).also { out ->
                    seven.getInputStream(entry).use { input -> out.outputStream().use { input.copyTo(it) } }
                }
            }
            archive.delete()
            return NativePlayer.loadGame(file.path, -1, !needsFile)
        }
        val named = File(cache, fileName)
        if (needsFile) {
            // Copied once into RetroForge's cache and reused next time; only the two most recent games are kept.
            val root = File(externalCacheDir ?: cacheDir, "games")
            val dir = File(root, Integer.toHexString(uri.toString().hashCode()))
            GameFiles.prune(root, keep = 2, current = dir)
            val path = GameFiles(contentResolver, dir).expose(uri, fileName) { percent ->
                runOnUiThread { loadingDetail = "Preparing the game for the first time… $percent%" }
            }
            return NativePlayer.loadGame(path, -1, false)
        }
        return contentResolver.openFileDescriptor(uri, "r")?.use { pfd -> NativePlayer.loadGame(named.path, pfd.fd, false) }
            ?: throw IOException("couldn't open it")
    }

    private fun fail(message: String) = runOnUiThread { phase = Phase.Failed(message) }

    private fun sram() = File(savesDir, "$saveBase.srm")
    // States belong to one emulator (another can't load them), so their names include it. In-game saves (.srm) are shared.
    private fun autoState() = File(statesDir, "$saveBase.$coreId.auto.state")
    private fun slotState() = File(statesDir, "$saveBase.$coreId.state")

    private fun savedOptions(): Map<String, String> = prefs.all
        .filterKeys { it.startsWith("opt.$coreId.") }
        .mapNotNull { (k, v) -> (v as? String)?.let { k.removePrefix("opt.$coreId.") to it } }
        .toMap()

    private fun chooseOption(option: CoreOption) {
        if (option.choices.isEmpty()) return
        val next = option.choices[(option.choices.indexOf(option.value) + 1) % option.choices.size]
        prefs.edit().putString("opt.$coreId.${option.key}", next).apply()
        NativePlayer.setOption(option.key, next)
        coreOptions = NativePlayer.options()
        refreshVideo(10) // some settings change the picture (borders, layouts)
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
        val options = PlayerCores.orientationOptions(coreId, newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE)
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

    override fun onResume() {
        super.onResume()
        foreground = true
        if (loaded && !menuOpen) NativePlayer.setPaused(false)
    }

    override fun onPause() {
        super.onPause()
        foreground = false
        if (loaded && !quitting) {
            input.releaseAll()
            NativePlayer.setPaused(true)
            // Android may end the player while it's in the background; keep the save and the resume point.
            worker.execute { if (!quitting) persist() }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            worker.shutdown()
            // The player process exists for this one game. Ending it gives the next game a clean core. Not when switching
            // emulator: the old core is already closed, and the new player may already be starting in this process.
            if (!switching) Process.killProcess(Process.myPid())
        }
    }

    // ---------- input ----------

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (menuOpen) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_MODE, KeyEvent.KEYCODE_MENU -> {
                    if (event.action == KeyEvent.ACTION_UP) {
                        if (menuPage != MenuPage.MAIN) menuPage = MenuPage.MAIN else closeMenu()
                    }
                    return true
                }
                KeyEvent.KEYCODE_BUTTON_A -> return super.dispatchKeyEvent(
                    KeyEvent(event.downTime, event.eventTime, event.action, KeyEvent.KEYCODE_DPAD_CENTER, event.repeatCount, event.metaState, event.deviceId, event.scanCode, event.flags, event.source),
                )
            }
            return super.dispatchKeyEvent(event)
        }
        if (event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == KeyEvent.KEYCODE_BUTTON_MODE || event.keyCode == KeyEvent.KEYCODE_MENU) {
            if (event.action == KeyEvent.ACTION_UP) {
                if (phase is Phase.Failed) finish() else openMenu()
            }
            return true
        }
        if (phase == Phase.Playing && input.onKey(event)) {
            showTouch = false
            return true
        }
        return super.dispatchKeyEvent(event)
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
        // With the console look, the picture keeps a margin for the screen surround.
        val inset = if (consoleLook) (bezelMargin(!isLandscape()).value * resources.displayMetrics.density).toInt() else 0
        val fitted = Viewport.fit(shape, surfaceWidth - 2 * inset, surfaceHeight - 2 * inset, av)
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

    private fun chooseTouch(on: Boolean) {
        touchEnabled = on
        prefs.edit().putBoolean("touch.$system", on).apply()
    }

    private fun setFrameRate(surface: Surface, av: Video) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && av.fps > 0) {
            runCatching { surface.setFrameRate(av.fps, Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE) }
        }
    }

    /** Touches on the picture itself go to the core as a touchscreen (the DS's lower screen, for example). */
    @SuppressLint("ClickableViewAccessibility")
    private fun pointerListener(view: SurfaceView) = view.setOnTouchListener { v, event ->
        val rect = picture
        if (!loaded || rect.w <= 0 || rect.h <= 0) return@setOnTouchListener false
        val down = event.actionMasked != MotionEvent.ACTION_UP && event.actionMasked != MotionEvent.ACTION_CANCEL
        NativePlayer.setPointer((event.x - rect.x) / rect.w, (event.y - rect.y) / rect.h, down)
        true
    }

    @Composable
    private fun PlayerScreen(layout: PadLayout, title: String) {
        // No background here: the game surface sits behind the window, and anything painted would cover it.
        Box(Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val portrait = maxHeight > maxWidth
                // Compact controls when the full-width picture wouldn't leave room for the normal ones (the DS's two screens).
                val aspect = video?.aspect ?: (4f / 3f)
                val compact = portrait && maxHeight - maxWidth / aspect < touchControlsHeight(layout, compact = false)
                val controls: @Composable (Modifier) -> Unit = { modifier ->
                    if (touchEnabled && showTouch && phase == Phase.Playing && !menuOpen) {
                        TouchControls(
                            layout, onMask = { input.setTouch(it) }, onMenu = { openMenu() }, modifier = modifier,
                            compact = compact, landscape = !portrait,
                            onAnalog = { stick, x, y -> if (loaded) NativePlayer.setAnalog(0, stick, (x * 32767).toInt(), (y * 32767).toInt()) },
                        )
                    }
                }
                val skin = if (consoleLook) ConsoleSkin.forSystem(system) else null
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
                    skin?.let { ConsoleFrame(it, pictureRect, Modifier.fillMaxSize()) }
                    CompositionLocalProvider(LocalConsoleSkin provides skin) { controls(Modifier.fillMaxSize()) }
                }
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
            Column(
                Modifier.width(320.dp).heightIn(max = 560.dp).background(Color(0xFF1C1C22), RoundedCornerShape(16.dp))
                    .verticalScroll(rememberScrollState()).padding(20.dp),
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
                            worker.execute {
                                val file = slotState()
                                val done = file.isFile && NativePlayer.loadState(file.readBytes())
                                runOnUiThread { if (done) closeMenu() else notice = "No saved state yet" }
                            }
                        }, modifier = wide) { Text("Load state") }
                        MenuButton(onClick = {
                            fastForwardOn = !fastForwardOn
                            NativePlayer.setFastForward(fastForwardOn)
                        }, modifier = wide) { Text("Fast-forward: ${if (fastForwardOn) "On" else "Off"}") }
                        MenuButton(onClick = { chooseShape(shape.next()) }, modifier = wide) { Text("Screen shape: ${shape.label}") }
                        MenuButton(onClick = { chooseVolume() }, modifier = wide) { Text("Volume: ${(volume * 100).toInt()}%") }
                        MenuButton(onClick = { chooseFilter(filter.next()) }, modifier = wide) { Text("Screen filter: ${filter.label}") }
                        MenuButton(onClick = { chooseLook(!consoleLook) }, modifier = wide) { Text("Console look: ${if (consoleLook) "On" else "Off"}") }
                        if (hasTouch) MenuButton(onClick = { chooseTouch(!touchEnabled) }, modifier = wide) {
                            Text("Touch controls: ${if (touchEnabled) "On" else "Off"}")
                        }
                        MenuButton(onClick = {
                            coreOptions = NativePlayer.options()
                            menuPage = MenuPage.OPTIONS
                        }, modifier = wide) { Text("Emulator settings ›") }
                        if (PlayerCores.choices(system).size > 1) {
                            MenuButton(onClick = { menuPage = MenuPage.EMULATORS }, modifier = wide) { Text("Switch emulator ›") }
                        }
                        MenuButton(onClick = {
                            NativePlayer.reset()
                            closeMenu()
                        }, modifier = wide) { Text("Restart game") }
                        MenuButton(onClick = { quit() }, modifier = wide) { Text("Quit to RetroForge") }
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

    companion object {
        private const val EXTRA_CORE_PATH = "core_path"
        private const val EXTRA_URI = "uri"
        private const val EXTRA_FILE_NAME = "file_name"
        private const val EXTRA_SYSTEM = "system"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_RESUME = "resume"
        private const val EXTRA_FAST_FORWARD_R2 = "fast_forward_r2"
        private const val EXTRA_MENU_COMBO = "menu_combo"
        private const val EXTRA_QUIT_COMBO = "quit_combo"

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
        ): Intent = Intent(context, PlayerActivity::class.java)
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
