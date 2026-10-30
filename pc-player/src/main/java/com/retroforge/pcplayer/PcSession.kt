package com.retroforge.pcplayer

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.system.Os
import android.util.Log
import com.retroforge.pcplayer.net.PcLan
import com.winlator.cmod.alsaserver.ALSAClientConnectionHandler
import com.winlator.cmod.alsaserver.ALSARequestHandler
import com.winlator.cmod.sysvshm.SysVSHMConnectionHandler
import com.winlator.cmod.sysvshm.SysVSHMRequestHandler
import com.winlator.cmod.sysvshm.SysVSharedMemory
import com.winlator.cmod.xconnector.UnixSocketConfig
import com.winlator.cmod.xconnector.XConnectorEpoll
import com.winlator.cmod.xserver.SHMSegmentManager
import com.winlator.cmod.xserver.ScreenInfo
import com.winlator.cmod.xserver.XClientConnectionHandler
import com.winlator.cmod.xserver.XClientRequestHandler
import com.winlator.cmod.xserver.XServer
import java.io.File
import java.io.IOException
import java.util.zip.ZipFile
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * One running Windows game: RetroForge's X server (Winlator's, drawn by [com.winlator.cmod.widget.XServerView]),
 * its shared-memory server, and Wine started on the game's .exe.
 *
 * Android won't run programs from an app's own storage directly, so Wine is started as
 * `/system/bin/linker64 wine ...` and libpcexec.so (preloaded into each Wine process) does the same for every
 * program Wine starts. The game's folder is Windows drive D: (see [gameFolder]).
 */
class PcSession(private val context: Context, private val runtime: PcRuntime, val exe: File, screen: ScreenInfo) {

    val xServer = XServer(screen).apply { winHandler = com.winlator.cmod.winhandler.WinHandler() }
    private var xConnector: XConnectorEpoll? = null
    private var shmConnector: XConnectorEpoll? = null
    private var alsaConnector: XConnectorEpoll? = null
    private var sharedMemory: SysVSharedMemory? = null
    private var process: Process? = null
    val log = File(runtime.root, "last-run.log")
    /** The program actually run: [exe], or the game an Unreal Engine starter .exe would start (see [unrealGame]). */
    val program: File = unrealGame(exe) ?: exe
    /** Drive D:, the game's own top folder, so paths the game reaches with ..\ from its .exe's folder still work. */
    private val folder: File = gameFolder(exe)
    /** The custom Vulkan driver chosen in Settings (its folder and library), or null for the device's own. */
    private val customDriver: Pair<String, String>? = customDriver(runtime.root)

    /** Whether this is Steam's own client (Steam method 2) rather than a game. */
    private val steamClient = isSteamClient(program)
    /** The on-screen gamepad, seen by the game as an Xbox 360 controller once [VirtualPad.connected] is set. */
    val pad = VirtualPad(File(runtime.root, "pad.mem"))

    /** Called with Wine's exit code when the game's Wine process ends. */
    var onExit: ((Int) -> Unit)? = null

    fun start() {
        val rootPath = runtime.imageFs.path
        val memory = SysVSharedMemory()
        sharedMemory = memory
        shmConnector = XConnectorEpoll(
            UnixSocketConfig.createSocket(rootPath, UnixSocketConfig.SYSVSHM_SERVER_PATH),
            SysVSHMConnectionHandler(memory), SysVSHMRequestHandler(),
        ).also { it.start() }
        xServer.setSHMSegmentManager(SHMSegmentManager(memory))
        xConnector = XConnectorEpoll(
            UnixSocketConfig.createSocket(rootPath, UnixSocketConfig.XSERVER_PATH),
            XClientConnectionHandler(xServer), XClientRequestHandler(),
        ).apply {
            setInitialInputBufferCapacity(262144)
            setCanReceiveAncillaryMessages(true)
            start()
        }
        // Sound: Wine plays through ALSA, whose android_aserver plugin (in the image) sends it here; played with AAudio.
        alsaConnector = XConnectorEpoll(
            UnixSocketConfig.createSocket(rootPath, UnixSocketConfig.ALSA_SERVER_PATH),
            ALSAClientConnectionHandler(false), ALSARequestHandler(),
        ).apply {
            setMultithreadedClients(true)
            start()
        }
        mapDrives()
        val wineRunning = wineRunning()
        if (!wineRunning) {
            clearStaleSync()
            registerTahoma()
        }
        launchWine()
    }

    /** Whether a wineserver is running (a game from before still closing, say): then the prefix is in use. */
    private fun wineRunning() = File("/proc").listFiles().orEmpty().any { dir ->
        dir.name.all(Char::isDigit) && runCatching { File(dir, "cmdline").readText() }.getOrDefault("").contains("wineserver")
    }

    /**
     * Wine's userspace sync state (usr/tmp/ntsync_userspace.*.shm) outlives a session Android killed outright; the next
     * Wine finds it and waits forever at start. With no wineserver running nothing is using it, so it goes.
     */
    private fun clearStaleSync() {
        File(runtime.imageFs, "usr/tmp").listFiles { f -> f.name.startsWith("ntsync_userspace") }.orEmpty()
            .forEach { it.delete() }
    }

    private fun mapDrives() {
        val devices = File(runtime.prefix, "dosdevices")
        devices.mkdirs()
        fun link(name: String, target: String) {
            val file = File(devices, name)
            runCatching { Os.remove(file.path) }
            Os.symlink(target, file.path)
        }
        link("c:", "../drive_c")
        link("d:", folder.path)
        link("z:", runtime.imageFs.path)
        runCatching { Os.remove(File(devices, "e:").path) }
    }

    private fun environment(): Map<String, String> {
        val imageFs = runtime.imageFs.path
        val wine = runtime.wineDir.path
        val tmp = "$imageFs/usr/tmp"
        return mapOf(
            "HOME" to runtime.home.path,
            "USER" to "xuser",
            "TMPDIR" to tmp,
            "XDG_RUNTIME_DIR" to tmp,
            "DISPLAY" to ":0",
            "LANG" to "en_US.UTF-8",
            "PATH" to "$wine/bin:$imageFs/usr/bin:/system/bin",
            "LD_LIBRARY_PATH" to "$imageFs/usr/lib:$wine/lib:/system/lib64",
            "LD_PRELOAD" to "$imageFs/usr/lib/libandroid-sysvshm.so:${preloadLibrary("libpcexec.so").path}:${preloadLibrary("libpcpad.so").path}",
            // The on-screen gamepad's state, read by libpcpad.so in Wine's controller driver.
            "PCPAD_FILE" to pad.file.path,
            // Play together's virtual LAN (read by libpcexec.so while it exists), and a seed for the made-up MAC
            // addresses it gives adapters whose real one Android hides, different on each device.
            "PCNET_FILE" to PcLan.configFile(runtime.root).path,
            "PCNET_MACSEED" to (Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: Build.MODEL),
            "ANDROID_SYSVSHM_SERVER" to imageFs + UnixSocketConfig.SYSVSHM_SERVER_PATH,
            "ANDROID_ALSA_SERVER" to imageFs + UnixSocketConfig.ALSA_SERVER_PATH,
            "ALSA_CONFIG_PATH" to "$imageFs/usr/share/alsa/alsa.conf:$imageFs/usr/etc/alsa/conf.d/android_aserver.conf",
            "ALSA_PLUGIN_DIR" to "$imageFs/usr/lib/alsa-lib",
            "PREFIX" to "$imageFs/usr",
            "WINEPREFIX" to runtime.prefix.path,
            "WINEDEBUG" to "-all,err+module,err+loaddll",
            // Sound goes through ALSA (above); Wine's PulseAudio driver is off so it doesn't look for a server first.
            // DXVK's Direct3D DLLs and Mesa's OpenGL (installed in the prefix) before Wine's own.
            // Steam's own client loads Valve's steamclient DLLs: the prefix's built-in ones only forward to a Linux
            // Steam, and with none here Steam stops at once.
            // (Wine's own Direct3D instead where the GPU's Vulkan can't run DXVK; see dxvkUnsupported.)
            "WINEDLLOVERRIDES" to "winemenubuilder.exe=d;winepulse.drv=d;" +
                (if (dxvkUnsupported(runtime.root)) "d3d8,d3d9,d3d10core,d3d11,dxgi=b;opengl32=n,b" else "d3d8,d3d9,d3d10core,d3d11,dxgi,opengl32=n,b") +
                if (steamClient) ";steamclient,steamclient64=n" else "",
            // Mesa's OpenGL draws through Vulkan (zink); GameNative's settings for the wrapper driver.
            "GALLIUM_DRIVER" to "zink",
            "LIBGL_KOPPER_DISABLE" to "true",
            // On the Xclipse wrapper zink's default descriptors fail to bind image memory (black screen), and its
            // command reordering drops draws (CTR Native lost Crash's face).
            "ZINK_DESCRIPTORS" to "lazy",
            "ZINK_DEBUG" to "compact,noreorder",
            // Games' own SDL2 reads Xbox pads through Raw Input / HIDAPI / Windows.Gaming.Input when it can, none of
            // which carry the virtual pad under Wine; XInput does (Wine's winexinput driver feeds it).
            "SDL_JOYSTICK_RAWINPUT" to "0",
            "SDL_JOYSTICK_HIDAPI" to "0",
            "SDL_JOYSTICK_WGI" to "0",
            "PROTON_DISABLE_LSTEAMCLIENT" to if (steamClient) "1" else "",
            "DXVK_LOG_LEVEL" to "warn",
            "DXVK_LOG_PATH" to "none",
            "WINE_NO_DUPLICATE_EXPLORER" to "1",
            "WINE_DISABLE_FULLSCREEN_HACK" to "1",
            "WINEESYNC" to "1",
            "HODLL" to "libwow64fex.dll",
            "FONTCONFIG_PATH" to "$imageFs/usr/etc/fonts",
            "XDG_DATA_DIRS" to "$imageFs/usr/share",
            "XDG_CONFIG_DIRS" to "$imageFs/usr/etc/xdg",
            "VK_ICD_FILENAMES" to "$imageFs/usr/share/vulkan/icd.d/wrapper_icd.aarch64.json",
            // A Turnip driver chosen in Settings → GPU and drivers: the Vulkan wrapper loads it through adrenotools (in
            // the image) instead of the device's own driver. Qualcomm chips only; nothing changes elsewhere.
            "ADRENOTOOLS_DRIVER_PATH" to (customDriver?.let { it.first + "/" } ?: ""),
            "ADRENOTOOLS_HOOKS_PATH" to (customDriver?.let { "$imageFs/usr/lib" } ?: ""),
            "ADRENOTOOLS_DRIVER_NAME" to (customDriver?.second ?: ""),
            "SSL_CERT_FILE" to "$imageFs/usr/etc/tls/cert.pem",
            "MESA_SHADER_CACHE_DIR" to "$imageFs/usr/tmp/mesa",
            // GameNative's defaults for the Vulkan wrapper.
            "WRAPPER_MAX_IMAGE_COUNT" to "0",
            "MESA_VK_WSI_PRESENT_MODE" to "mailbox",
            // GameNative's default FEX settings: x86 memory ordering where it matters, code-change tracking,
            // and its fixes for Mono/.NET's JIT.
            "FEX_TSOENABLED" to "1",
            "FEX_VECTORTSOENABLED" to "0",
            "FEX_HALFBARRIERTSOENABLED" to "1",
            "FEX_MEMCPYSETTSOENABLED" to "0",
            "FEX_X87REDUCEDPRECISION" to "0",
            "FEX_MULTIBLOCK" to "1",
            "FEX_MAXINST" to "5000",
            "FEX_SMALLTSCSCALE" to "1",
            "FEX_SMCCHECKS" to "mtrack",
            "FEX_VOLATILEMETADATA" to "1",
            "FEX_MONOHACKS" to "1",
            // .NET games abort loading Windows' ICU, which this Wine only partly has; Windows' older NLS language
            // functions (which Wine has) work instead. ("Invariant" mode avoids ICU too, but games that ask for a
            // real culture such as "en" then fail: Warside did.)
            "DOTNET_SYSTEM_GLOBALIZATION_USENLS" to "1",
            // .NET 7+ writes compiled code through one mapping and runs it through another; FEX misses the change
            // and spins on stale code (Warside hung loading). One mapping for both fixes it.
            "DOTNET_EnableWriteXorExecute" to "0",
        ) + extraEnvironment()
    }

    /** For testing: KEY=VALUE lines in files/pc/extra.env are added to (or replace) Wine's environment. */
    private fun extraEnvironment(): Map<String, String> = runCatching {
        File(runtime.root, "extra.env").readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") && '=' in it }
            .associate { it.substringBefore('=') to it.substringAfter('=') }
    }.getOrDefault(emptyMap())

    /**
     * One of RetroForge's preloaded libraries (libpcexec.so, libpcpad.so) as a file Wine's processes can preload.
     * The app's native libraries stay inside the APK (they aren't unpacked on install), so it's copied out of the
     * APK into the runtime folder, again whenever the app is updated.
     */
    private fun preloadLibrary(name: String): File {
        val out = File(runtime.root, name)
        val info = context.applicationInfo
        val apks = listOf(info.sourceDir) + info.splitSourceDirs.orEmpty()
        val newest = apks.maxOf { File(it).lastModified() }
        if (out.isFile && out.lastModified() >= newest) return out
        File(info.nativeLibraryDir, name).takeIf { it.isFile }?.let { return it.copyTo(out, overwrite = true) }
        for (apk in apks) ZipFile(apk).use { zip ->
            val entry = Build.SUPPORTED_ABIS.firstNotNullOfOrNull { zip.getEntry("lib/$it/$name") } ?: return@use
            zip.getInputStream(entry).use { input -> out.outputStream().use { input.copyTo(it) } }
            return out
        }
        throw IOException("RetroForge's $name is missing from the app")
    }

    private fun launchWine() {
        blackDesktop()
        val screen = "${xServer.screenInfo.width}x${xServer.screenInfo.height}"
        // explorer /desktop gives Wine one desktop window the size of RetroForge's X screen; the game runs inside it
        // (without it, SDL games such as CTR Native can't open a window). Naming it anything but "shell" leaves out
        // the taskbar, and blackDesktop() makes it black, so nothing shows around a 4:3 game or while it loads.
        val command = listOf(
            "/system/bin/linker64", "${runtime.wineDir.path}/bin/wine",
            "explorer", "/desktop=RetroForge,$screen", "D:\\" + program.relativeTo(folder).path.replace('/', '\\'),
        ) + gameArguments(program)
        log.delete()
        val builder = ProcessBuilder(command)
            .directory(program.parentFile)
            .redirectErrorStream(true)
            .redirectOutput(log)
        builder.environment().apply {
            clear()
            putAll(environment())
        }
        Log.i(TAG, "Starting ${command.joinToString(" ")}")
        val started = builder.start()
        process = started
        thread(name = "pc-player-wait", isDaemon = true) {
            val code = started.waitFor()
            Log.i(TAG, "Wine exited with $code")
            onExit?.invoke(code)
        }
    }

    /**
     * Windows' UI font, Tahoma (Wine's own copy), where DirectWrite finds it: in C:\windows\Fonts and the Windows NT
     * font list. The prefix lists it only in the Windows 9x list, so DirectWrite has no Tahoma; Chromium-based windows
     * (Steam's) then fall back to the system font, which is Tahoma again, and recurse until the stack runs out.
     */
    private fun registerTahoma() = runCatching {
        val fonts = File(runtime.prefix, "drive_c/windows/Fonts").apply { mkdirs() }
        for (name in listOf("tahoma.ttf", "tahomabd.ttf")) {
            val copy = File(fonts, name)
            if (!copy.isFile) File(runtime.wineDir, "share/wine/fonts/$name").takeIf { it.isFile }?.copyTo(copy)
        }
        val reg = File(runtime.prefix, "system.reg")
        val text = reg.readText()
        // The registry file writes each \ in a key's name as \\.
        val section = text.indexOf("[Software\\\\Microsoft\\\\Windows NT\\\\CurrentVersion\\\\Fonts]")
        if (section < 0) return@runCatching
        val sectionEnd = text.indexOf("\n[", section).let { if (it < 0) text.length else it }
        if (text.substring(section, sectionEnd).contains("\"Tahoma (TrueType)\"")) return@runCatching
        var at = text.indexOf('\n', section) + 1
        if (text.startsWith("#time=", at)) at = text.indexOf('\n', at) + 1
        reg.writeText(
            text.substring(0, at) +
                "\"Tahoma (TrueType)\"=\"tahoma.ttf\"\n\"Tahoma Bold (TrueType)\"=\"tahomabd.ttf\"\n" +
                text.substring(at),
        )
    }

    /** Sets Wine's desktop colour (blue in the prefix as downloaded) to black in user.reg; Wine isn't running yet. */
    private fun blackDesktop() = runCatching {
        val reg = File(runtime.prefix, "user.reg")
        val text = reg.readText()
        val black = text.replace(Regex("(?m)^\"Background\"=\"[0-9 ]+\"$"), "\"Background\"=\"0 0 0\"")
        if (black != text) reg.writeText(black)
    }

    /**
     * Pauses (or resumes) the game: its Windows processes (Wine's own services and the X server keep running) are
     * stopped with SIGSTOP until resumed with SIGCONT.
     */
    fun setPaused(paused: Boolean) {
        val signal = if (paused) SIGSTOP else SIGCONT
        val me = android.os.Process.myPid()
        File("/proc").listFiles()?.forEach { dir ->
            val pid = dir.name.toIntOrNull() ?: return@forEach
            if (pid == me) return@forEach
            val cmdline = runCatching { File(dir, "cmdline").readText() }.getOrNull() ?: return@forEach
            if (cmdline.contains(".exe", ignoreCase = true) && !cmdline.contains("C:\\windows\\", ignoreCase = true)) {
                android.os.Process.sendSignal(pid, signal)
            }
        }
    }

    /** Ends the game: asks wineserver to stop every Wine process, then stops the servers. */
    fun stop() {
        setPaused(false)
        runCatching {
            val kill = ProcessBuilder("/system/bin/linker64", "${runtime.wineDir.path}/bin/wineserver", "-k")
            kill.environment().apply { clear(); putAll(environment()) }
            kill.start().waitFor(5, TimeUnit.SECONDS)
        }
        process?.destroy()
        process = null
        xConnector?.stop()
        xConnector = null
        shmConnector?.stop()
        shmConnector = null
        alsaConnector?.stop()
        alsaConnector = null
        sharedMemory?.deleteAll()
        sharedMemory = null
    }

    companion object {
        private const val TAG = "PcPlayer"
        private const val SIGCONT = 18
        private const val SIGSTOP = 19

        /**
         * Extra arguments some engines need here. Godot games (an .exe with a .pck beside it) ask for OpenGL, which
         * Godot on Windows gets through ANGLE on Direct3D 11, and there's no Direct3D layer yet; Godot's own
         * Vulkan renderer works on the Vulkan wrapper, so they're started on that.
         */
        /**
         * Unreal Engine games ship a small starter .exe in the game's folder that starts the real one
         * (<Project>/Binaries/Win64/<Project>-Win64-Shipping.exe) and exits at once, which would look like the game
         * closing. The real one is run instead; the game's folder stays drive D: so its paths still work.
         */
        fun unrealGame(exe: File): File? {
            if (exe.length() > 20L * 1024 * 1024) return null
            return exe.parentFile?.listFiles { f -> f.isDirectory && f.name != "Engine" }.orEmpty()
                .flatMap { File(it, "Binaries/Win64").listFiles { f -> f.name.endsWith("-Shipping.exe", true) }.orEmpty().asList() }
                .firstOrNull()
        }

        /** Folders games keep their .exe in, below the game's own folder (Binaries\Win32 for Unreal Engine 3, bin\x64...). */
        private val PROGRAM_FOLDERS = setOf(
            "binaries", "win32", "win64", "bin", "bin32", "bin64", "x86", "x64", "win_x86", "win_x64", "system", "system32", "exe",
        )

        /**
         * The game's own top folder for [exe]: up from its folder past folders like Binaries\Win32 (XCOM: Enemy Unknown
         * runs Binaries\Win32\XComGame.exe and reads ..\..\XComGame), and past an Unreal Engine project folder to the
         * folder holding Engine. Wine can't go above a drive's top (D:\..\ is D:\), so D: has to be this folder.
         */
        fun gameFolder(exe: File): File {
            var dir = exe.parentFile!!
            while (dir.name.lowercase() in PROGRAM_FOLDERS && dir.parentFile != null) dir = dir.parentFile!!
            val parent = dir.parentFile
            if (dir != exe.parentFile && parent != null && File(parent, "Engine").isDirectory) dir = parent
            return dir
        }

        /**
         * Steam's own client (steam.exe beside its package folder) starts without its updater: under Wine the updater
         * can't check free space (Wine refuses its \\?\D:\. path) and gives up; the client files are put in place whole.
         * Its Chromium-based browser (the sign-in and library screens) runs without Chromium's own sandbox, which
         * can't work under Wine; Android's app sandbox still holds everything the player runs. It draws without the
         * GPU: its GPU process spins under emulation until Steam gives up on the browser as not responding. And it
         * starts quietly, without its library window: the store and library pages run a phone out of memory under
         * emulation, and RetroForge shows the library itself.
         */
        /**
         * Whether this device's Vulkan driver can't run DXVK (it needs Vulkan 1.3 and features older Adreno drivers
         * lack, even when Android lists 1.3), found the first time a game tried: then games use Wine's own Direct3D.
         */
        fun dxvkUnsupported(root: File) = File(root, noDxvkFlag(root)).isFile

        fun markDxvkUnsupported(root: File) = runCatching { File(root, noDxvkFlag(root)).writeText("DXVK found no usable Vulkan GPU\n") }

        /** Recorded per driver: the device's own may not run DXVK where Turnip does. */
        private fun noDxvkFlag(root: File) = customDriver(root)?.let { "no-dxvk-${it.second}" } ?: "no-dxvk"

        /**
         * The Turnip driver chosen in Settings → GPU and drivers (files/gpu-driver beside the runtime's files/pc, as
         * adrenotools packages it: meta.json naming the library), on a Qualcomm chip; null otherwise.
         */
        fun customDriver(runtimeRoot: File): Pair<String, String>? {
            if (!Build.HARDWARE.startsWith("qcom", true) && !(Build.VERSION.SDK_INT >= 31 && Build.SOC_MANUFACTURER.equals("QTI", true))) return null
            val dir = File(runtimeRoot.parentFile, "gpu-driver")
            val meta = File(dir, "meta.json").takeIf { it.isFile }?.readText() ?: return null
            val library = Regex("\"libraryName\"\\s*:\\s*\"([^\"]+)\"").find(meta)?.groupValues?.get(1) ?: return null
            return if (File(dir, library).isFile) dir.path to library else null
        }

        /** DXVK's message when the Vulkan driver gives it no GPU it can use. */
        fun dxvkFoundNoGpu(log: File) = runCatching { log.readText().contains("DXVK: No adapters found") }.getOrDefault(false)

        /** Steam's own client: steam.exe beside the package folder its files came from. */
        fun isSteamClient(exe: File) = exe.name.equals("steam.exe", true) && File(exe.parentFile, "package").isDirectory

        fun gameArguments(exe: File): List<String> = when {
            File(exe.parentFile, exe.nameWithoutExtension + ".pck").isFile ->
                listOf("--rendering-driver", "vulkan", "--rendering-method", "mobile")
            isSteamClient(exe) ->
                listOf("-inhibitbootstrap", "-no-cef-sandbox", "-cef-disable-gpu", "-cef-disable-gpu-compositing", "-silent")
            else -> emptyList()
        }
    }
}
