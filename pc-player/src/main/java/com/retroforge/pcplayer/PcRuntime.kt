package com.retroforge.pcplayer

import android.content.Context
import android.system.Os
import com.github.luben.zstd.ZstdInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.tukaani.xz.XZInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

sealed interface RuntimeState {
    data object Missing : RuntimeState
    /** [step] is what's happening ("Downloading Wine"), [percent] is for the whole install. */
    data class Working(val step: String, val percent: Int) : RuntimeState
    data object Ready : RuntimeState
    data class Failed(val reason: String) : RuntimeState
}

/**
 * The Windows runtime RetroForge's PC player runs games in, downloaded on first use into RetroForge's private
 * storage (about 280 MB to download, about 1.8 GB unpacked):
 *
 * - a Linux userland built for Android (bionic): X11 and Vulkan libraries, fonts, the Vulkan "wrapper" driver;
 * - Proton 11 (Valve's Wine) built for Android's arm64 with ARM64EC, so Windows x86-64 code runs through FEX;
 * - FEX's Windows DLLs (libarm64ecfex.dll for 64-bit programs, libwow64fex.dll for 32-bit ones).
 *
 * These are GameNative's builds (GPL-3.0 app; Wine/Proton LGPL-2.1+, FEX MIT, Mesa MIT), fetched from GameNative's
 * download server. See docs/pc-player-plan.md for licences and for moving them to RetroForge's own releases.
 */
class PcRuntime(context: Context) {

    val root = File(context.filesDir, "pc")
    val imageFs = File(root, "imagefs")
    val wineDir = File(imageFs, "opt/proton")
    val home = File(imageFs, "home/xuser")
    val prefix = File(home, ".wine")
    private val cache = File(context.cacheDir, "pc-runtime")
    private val marker = File(root, "runtime.version")

    private val _state = MutableStateFlow(if (isReady()) RuntimeState.Ready else RuntimeState.Missing)
    val state: StateFlow<RuntimeState> = _state.asStateFlow()
    private val lock = Mutex()

    fun isReady() = marker.isFile && marker.readText().trim() == VERSION

    /** Downloads and unpacks whatever isn't there yet. True when the runtime is ready. */
    suspend fun ensure(): Boolean = withContext(Dispatchers.IO) {
        lock.withLock {
            if (isReady()) {
                _state.value = RuntimeState.Ready
                return@withLock true
            }
            try {
                install()
                _state.value = RuntimeState.Ready
                true
            } catch (e: IOException) {
                _state.value = RuntimeState.Failed(e.message ?: "The PC runtime couldn't be installed")
                false
            }
        }
    }

    private fun install() {
        marker.delete()
        if (root.exists()) root.deleteRecursively()
        imageFs.mkdirs()
        cache.mkdirs()
        val total = PARTS.sumOf { it.size }.toDouble()
        var done = 0L
        for (part in PARTS) {
            val file = File(cache, part.file)
            if (!(file.isFile && file.length() == part.size)) {
                download(part, file) { read -> progress("Downloading ${part.label}", (done + read) / total * DOWNLOAD_SHARE) }
            }
            done += part.size
        }
        done = 0L
        for (part in PARTS) {
            val target = File(imageFs, part.into)
            target.mkdirs()
            val file = File(cache, part.file)
            unpack(file, part.zstd, target) { read ->
                progress("Unpacking ${part.label}", DOWNLOAD_SHARE + (done + read) / total * (100 - DOWNLOAD_SHARE - 3))
            }
            done += part.size
        }
        progress("Setting up Windows", 97.0)
        setUpPrefix()
        cache.deleteRecursively()
        marker.writeText(VERSION)
    }

    /** Wine's starting prefix (C: drive and registry) comes inside the Proton package; FEX's DLLs go in system32. */
    private fun setUpPrefix() {
        val pack = File(wineDir, "prefixPack.txz")
        if (!pack.isFile) throw IOException("The Wine package had no starting prefix")
        prefix.mkdirs()
        unpack(pack, zstd = false, into = home) {}
        pack.delete()
        val system32 = File(prefix, "drive_c/windows/system32")
        system32.mkdirs()
        for (dll in listOf("libarm64ecfex.dll", "libwow64fex.dll")) {
            val from = File(imageFs, "opt/fex/system32/$dll")
            if (!from.isFile) throw IOException("FEX's $dll is missing")
            from.copyTo(File(system32, dll), overwrite = true)
        }
        // The Vulkan wrapper's manifest names the Winlator package's path; point it at ours.
        File(imageFs, "usr/share/vulkan/icd.d/wrapper_icd.aarch64.json").writeText(
            """{"file_format_version":"1.0.0","ICD":{"library_path":"${File(imageFs, "usr/lib/libvulkan_wrapper.so").path}","api_version":"1.3.0"}}"""
        )
        File(imageFs, "usr/tmp").mkdirs()
    }

    private fun progress(step: String, percent: Double) {
        _state.value = RuntimeState.Working(step, percent.toInt().coerceIn(0, 99))
    }

    private fun download(part: Part, file: File, onRead: (Long) -> Unit) {
        val connection = (URL(part.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
        }
        try {
            if (connection.responseCode != 200) throw IOException("The download server answered ${connection.responseCode} for ${part.label}")
            val partial = File(file.path + ".part")
            CountingStream(connection.inputStream, onRead).use { input -> partial.outputStream().use { input.copyTo(it, 1 shl 16) } }
            if (partial.length() != part.size) throw IOException("${part.label} didn't download completely")
            if (!partial.renameTo(file)) throw IOException("Couldn't save ${part.label}")
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        /** Wine and FEX here are built for 64-bit ARM; x86 devices and emulators can't run them. */
        val deviceSupported: Boolean get() = android.os.Build.SUPPORTED_64_BIT_ABIS.firstOrNull() == "arm64-v8a"

        /** Bumped when the parts change, so the runtime is installed again. */
        const val VERSION = "1-proton-11.0-2-arm64ec-9"
        private const val DOWNLOAD_SHARE = 60.0
        private const val SERVER = "https://downloads.gamenative.app"

        private class Part(val label: String, val url: String, val file: String, val size: Long, val zstd: Boolean, val into: String)

        private val PARTS = listOf(
            Part("the Linux image", "$SERVER/imagefs_bionic.txz", "imagefs_bionic.txz", 183_506_500, zstd = false, into = ""),
            Part("Wine (Proton 11)", "$SERVER/proton-11.0-2-arm64ec-9.wcp", "proton.wcp", 96_016_250, zstd = true, into = "opt/proton"),
            Part("FEX", "$SERVER/FEXCore-2610-gn-5.wcp", "fex.wcp", 1_332_620, zstd = false, into = "opt/fex"),
            Part("the Vulkan driver", "$SERVER/graphics_driver/wrapper-gamenative-20260925.tzst", "wrapper.tzst", 774_855, zstd = true, into = ""),
        )

        /** Unpacks a .tar.xz or .tar.zst into [into], keeping symlinks. Entries with absolute or ".." paths are skipped. */
        fun unpack(archive: File, zstd: Boolean, into: File, onRead: (Long) -> Unit) {
            val base = into.absoluteFile
            CountingStream(archive.inputStream(), onRead).use { counted ->
                val raw = BufferedInputStream(counted, 1 shl 16)
                val decoded: InputStream = if (zstd) ZstdInputStream(raw) else XZInputStream(raw)
                TarArchiveInputStream(decoded).use { tar ->
                    while (true) {
                        val entry = tar.nextEntry ?: break
                        val name = entry.name.removePrefix("./")
                        // Some archives were made on a Mac and carry "._" resource-fork files.
                        if (name.isEmpty() || name.substringAfterLast('/').startsWith("._")) continue
                        if (name.startsWith("/") || name.split('/').any { it == ".." }) continue
                        val out = File(base, name)
                        when {
                            entry.isDirectory -> out.mkdirs()
                            entry.isSymbolicLink -> {
                                out.parentFile?.mkdirs()
                                out.delete()
                                try {
                                    Os.symlink(entry.linkName, out.path)
                                } catch (e: Exception) {
                                    throw IOException("Couldn't make the link $name: ${e.message}")
                                }
                            }
                            entry.isLink -> {
                                out.parentFile?.mkdirs()
                                File(base, entry.linkName.removePrefix("./")).takeIf { it.isFile }?.copyTo(out, overwrite = true)
                            }
                            entry.isFile -> {
                                out.parentFile?.mkdirs()
                                out.delete()
                                out.outputStream().use { tar.copyTo(it, 1 shl 16) }
                                if (entry.mode and 0b001_001_001 != 0) out.setExecutable(true, false)
                            }
                        }
                    }
                }
            }
        }
    }

    private class CountingStream(input: InputStream, private val onRead: (Long) -> Unit) : FilterInputStream(input) {
        private var count = 0L
        private var reported = 0L
        override fun read(): Int = super.read().also { if (it >= 0) progress(1) }
        override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) progress(it.toLong()) }
        private fun progress(n: Long) {
            count += n
            if (count - reported >= 256 * 1024) {
                reported = count
                onRead(count)
            }
        }
    }
}
