package com.retroforge.pcplayer

import android.content.Context
import android.os.Build
import android.system.Os
import android.util.Log
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
 * program Wine starts. The game's folder is Windows drive D:.
 */
class PcSession(private val context: Context, private val runtime: PcRuntime, val exe: File, screen: ScreenInfo) {

    val xServer = XServer(screen).apply { winHandler = com.winlator.cmod.winhandler.WinHandler() }
    private var xConnector: XConnectorEpoll? = null
    private var shmConnector: XConnectorEpoll? = null
    private var alsaConnector: XConnectorEpoll? = null
    private var sharedMemory: SysVSharedMemory? = null
    private var process: Process? = null
    val log = File(runtime.root, "last-run.log")

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
        launchWine()
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
        link("d:", exe.parentFile!!.path)
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
            "LD_PRELOAD" to "$imageFs/usr/lib/libandroid-sysvshm.so:${execHelper().path}",
            "ANDROID_SYSVSHM_SERVER" to imageFs + UnixSocketConfig.SYSVSHM_SERVER_PATH,
            "ANDROID_ALSA_SERVER" to imageFs + UnixSocketConfig.ALSA_SERVER_PATH,
            "ALSA_CONFIG_PATH" to "$imageFs/usr/share/alsa/alsa.conf:$imageFs/usr/etc/alsa/conf.d/android_aserver.conf",
            "ALSA_PLUGIN_DIR" to "$imageFs/usr/lib/alsa-lib",
            "PREFIX" to "$imageFs/usr",
            "WINEPREFIX" to runtime.prefix.path,
            "WINEDEBUG" to "-all,err+module,err+loaddll",
            // Sound goes through ALSA (above); Wine's PulseAudio driver is off so it doesn't look for a server first.
            // DXVK's Direct3D DLLs and Mesa's OpenGL (installed in the prefix) before Wine's own.
            "WINEDLLOVERRIDES" to "winemenubuilder.exe=d;winepulse.drv=d;d3d8,d3d9,d3d10core,d3d11,dxgi,opengl32=n,b",
            // Mesa's OpenGL draws through Vulkan (zink); GameNative's settings for the wrapper driver.
            "GALLIUM_DRIVER" to "zink",
            "LIBGL_KOPPER_DISABLE" to "true",
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
     * libpcexec.so as a file Wine's processes can preload. The app's native libraries stay inside the APK (they
     * aren't unpacked on install), so it's copied out of the APK into the runtime folder, again whenever the app
     * is updated.
     */
    private fun execHelper(): File {
        val out = File(runtime.root, "libpcexec.so")
        val info = context.applicationInfo
        val apks = listOf(info.sourceDir) + info.splitSourceDirs.orEmpty()
        val newest = apks.maxOf { File(it).lastModified() }
        if (out.isFile && out.lastModified() >= newest) return out
        File(info.nativeLibraryDir, "libpcexec.so").takeIf { it.isFile }?.let { return it.copyTo(out, overwrite = true) }
        for (apk in apks) ZipFile(apk).use { zip ->
            val entry = Build.SUPPORTED_ABIS.firstNotNullOfOrNull { zip.getEntry("lib/$it/libpcexec.so") } ?: return@use
            zip.getInputStream(entry).use { input -> out.outputStream().use { input.copyTo(it) } }
            return out
        }
        throw IOException("RetroForge's libpcexec.so is missing from the app")
    }

    private fun launchWine() {
        blackDesktop()
        val screen = "${xServer.screenInfo.width}x${xServer.screenInfo.height}"
        // explorer /desktop gives Wine one desktop window the size of RetroForge's X screen; the game runs inside it
        // (without it, SDL games such as CTR Native can't open a window). Naming it anything but "shell" leaves out
        // the taskbar, and blackDesktop() makes it black, so nothing shows around a 4:3 game or while it loads.
        val command = listOf(
            "/system/bin/linker64", "${runtime.wineDir.path}/bin/wine",
            "explorer", "/desktop=RetroForge,$screen", "D:\\${exe.name}",
        ) + gameArguments(exe)
        log.delete()
        val builder = ProcessBuilder(command)
            .directory(exe.parentFile)
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

    /** Sets Wine's desktop colour (blue in the prefix as downloaded) to black in user.reg; Wine isn't running yet. */
    private fun blackDesktop() = runCatching {
        val reg = File(runtime.prefix, "user.reg")
        val text = reg.readText()
        val black = text.replace(Regex("(?m)^\"Background\"=\"[0-9 ]+\"$"), "\"Background\"=\"0 0 0\"")
        if (black != text) reg.writeText(black)
    }

    /** Ends the game: asks wineserver to stop every Wine process, then stops the servers. */
    fun stop() {
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

        /**
         * Extra arguments some engines need here. Godot games (an .exe with a .pck beside it) ask for OpenGL, which
         * Godot on Windows gets through ANGLE on Direct3D 11, and there's no Direct3D layer yet; Godot's own
         * Vulkan renderer works on the Vulkan wrapper, so they're started on that.
         */
        fun gameArguments(exe: File): List<String> =
            if (File(exe.parentFile, exe.nameWithoutExtension + ".pck").isFile) {
                listOf("--rendering-driver", "vulkan", "--rendering-method", "mobile")
            } else emptyList()
    }
}
