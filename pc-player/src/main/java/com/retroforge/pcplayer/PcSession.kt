package com.retroforge.pcplayer

import android.content.Context
import android.os.Build
import android.system.Os
import android.util.Log
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

    val xServer = XServer(screen)
    private var xConnector: XConnectorEpoll? = null
    private var shmConnector: XConnectorEpoll? = null
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
            "PCPLAYER_SELF_EXE" to "$wine/bin/wine",
            "ANDROID_SYSVSHM_SERVER" to imageFs + UnixSocketConfig.SYSVSHM_SERVER_PATH,
            "PREFIX" to "$imageFs/usr",
            "WINEPREFIX" to runtime.prefix.path,
            "WINEDEBUG" to "-all,err+module,err+loaddll",
            // No sound server yet: Wine's audio drivers are switched off so nothing waits on one.
            "WINEDLLOVERRIDES" to "winemenubuilder.exe=d;winealsa.drv=d;winepulse.drv=d",
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
        )
    }

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
        val screen = "${xServer.screenInfo.width}x${xServer.screenInfo.height}"
        // explorer /desktop gives Wine one desktop window the size of RetroForge's X screen; the game runs inside it.
        val command = listOf(
            "/system/bin/linker64", "${runtime.wineDir.path}/bin/wine",
            "explorer", "/desktop=shell,$screen", "D:\\${exe.name}",
        )
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
        sharedMemory?.deleteAll()
        sharedMemory = null
    }

    companion object {
        private const val TAG = "PcPlayer"
    }
}
