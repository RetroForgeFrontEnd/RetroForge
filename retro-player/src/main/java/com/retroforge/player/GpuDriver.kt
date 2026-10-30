package com.retroforge.player

import com.retroforge.core.ui.str
import android.content.Context
import android.os.Build
import java.io.File
import java.util.zip.ZipFile

/**
 * The custom Vulkan driver (Turnip) chosen in Settings → GPU and drivers for RetroForge's own players, or none.
 * It lives unpacked in files/gpu-driver (meta.json and the driver library, as adrenotools packages it); both the
 * built-in player and the PC player read it from there.
 */
object GpuDriver {

    fun dir(context: Context) = File(context.filesDir, "gpu-driver")

    /** The chosen driver's library file name and its description, or null for the device's own driver. */
    fun current(context: Context): Pair<String, String>? {
        val meta = File(dir(context), "meta.json").takeIf { it.isFile }?.readText() ?: return null
        val library = field(meta, "libraryName") ?: return null
        if (!File(dir(context), library).isFile) return null
        val name = listOfNotNull(field(meta, "name"), field(meta, "driverVersion")).joinToString(", ")
        return library to name.ifEmpty { library }
    }

    /**
     * Hands the chosen driver to the native player (before a core opens). adrenotools' hook libraries are packaged
     * in the APK; they're copied out next to the driver, where they can be loaded. 64-bit ARM only.
     */
    fun applyTo(context: Context) {
        val (library, _) = current(context) ?: return NativePlayer.setGpuDriver("", "", "")
        if (Build.SUPPORTED_ABIS.firstOrNull() != "arm64-v8a") return NativePlayer.setGpuDriver("", "", "")
        val hooks = File(context.filesDir, "gpu-driver-hooks").apply { mkdirs() }
        val ok = runCatching { copyHooks(context, hooks) }.isSuccess
        if (!ok) return NativePlayer.setGpuDriver("", "", "")
        NativePlayer.setGpuDriver(hooks.path, dir(context).path, library)
    }

    // libhook_impl.so is loaded again in the driver's own linker namespace, which only searches this folder: the shared
    // C++ runtime it needs goes there too (without it Turnip never loaded and every core fell back to the device's driver).
    private val HOOKS = listOf("libhook_impl.so", "libmain_hook.so", "libfile_redirect_hook.so", "libgsl_alloc_hook.so", "libc++_shared.so")

    private fun copyHooks(context: Context, into: File) {
        val info = context.applicationInfo
        // Already on disk when the APK's native libraries are extracted.
        val extracted = File(info.nativeLibraryDir)
        val apks = listOf(info.sourceDir) + info.splitSourceDirs.orEmpty()
        val newest = apks.maxOf { File(it).lastModified() }
        for (name in HOOKS) {
            val out = File(into, name)
            if (out.isFile && out.lastModified() >= newest) continue
            File(extracted, name).takeIf { it.isFile }?.let { it.copyTo(out, overwrite = true); continue }
            val copied = apks.any { apk ->
                ZipFile(apk).use { zip ->
                    val entry = zip.getEntry("lib/arm64-v8a/$name") ?: return@use false
                    zip.getInputStream(entry).use { input -> out.outputStream().use { input.copyTo(it) } }
                    true
                }
            }
            if (!copied) throw IllegalStateException(str(R.string.gpu_driver_isn_t_in_the_app, name))
        }
    }

    private fun field(json: String, key: String) = Regex("\"$key\"\\s*:\\s*\"([^\"]*)\"").find(json)?.groupValues?.get(1)
}
