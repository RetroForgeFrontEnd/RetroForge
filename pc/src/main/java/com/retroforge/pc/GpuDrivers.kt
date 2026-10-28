package com.retroforge.pc

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** What custom GPU drivers can do on this device. */
data class DriverAdvice(val canUseCustomDrivers: Boolean, val gpuName: String, val advice: String, val preferA8xx: Boolean = false)

/** One downloadable Turnip build. */
data class DriverFile(val source: String, val release: String, val fileName: String, val url: String, val size: Long, val published: String)

/**
 * Custom Vulkan drivers for Qualcomm Adreno GPUs: Turnip, from the Mesa project (MIT licence), packaged for
 * adrenotools by community builders. Apps with a GPU driver manager (Eden, Dolphin, NetherSX2 and others) load the
 * zip; RetroForge only downloads it where the user can pick it. Qualcomm's own proprietary drivers, which some of
 * the same pages also offer, are left out.
 */
object GpuDrivers {

    data class Source(val name: String, val repo: String)

    val SOURCES = listOf(
        Source("K11MCH1 AdrenoToolsDrivers", "K11MCH1/AdrenoToolsDrivers"),
        Source("MrPurple Turnip", "MrPurple666/purple-turnip"),
    )

    fun advice(identity: GpuIdentity): DriverAdvice {
        val name = identity.glRenderer ?: identity.kgslModel ?: identity.socModel ?: "Unknown GPU"
        val adreno = GpuClassifier.adrenoModel(identity)
        return when {
            adreno == null -> DriverAdvice(
                false, name,
                "Custom drivers (Turnip) only work on Qualcomm Adreno GPUs. This GPU uses the driver that came with the phone; " +
                    "system updates are the way to get a newer one. Emulators still run, using that driver.",
            )
            adreno >= 800 -> DriverAdvice(
                true, name,
                "Adreno $adreno: Turnip support for 8xx GPUs is new. Use builds marked a8xx, and keep the phone's own driver " +
                    "if a game runs worse.",
                preferA8xx = true,
            )
            adreno >= 600 -> DriverAdvice(
                true, name,
                "Adreno $adreno: Turnip often runs Switch, PS2, GameCube and PC games better than the phone's own driver. " +
                    "If a game breaks, switch back to the system driver in that emulator.",
            )
            else -> DriverAdvice(false, name, "Adreno $adreno is older than Turnip supports (Adreno 6xx and newer).")
        }
    }

    @Serializable
    private data class Asset(val name: String, val browser_download_url: String, val size: Long)

    @Serializable
    private data class Release(val tag_name: String, val name: String? = null, val published_at: String? = null, val assets: List<Asset> = emptyList())

    private val json = Json { ignoreUnknownKeys = true }

    /** Turnip zips from one source's GitHub releases JSON, newest first. */
    fun parse(source: Source, releasesJson: String): List<DriverFile> =
        json.decodeFromString<List<Release>>(releasesJson).flatMap { release ->
            release.assets
                .filter { it.name.endsWith(".zip", ignoreCase = true) && it.name.contains("turnip", ignoreCase = true) }
                .map { DriverFile(source.name, release.name ?: release.tag_name, it.name, it.browser_download_url, it.size, release.published_at.orEmpty().take(10)) }
        }

    /** The latest Turnip builds from every source (a few releases each). */
    fun fetch(userAgent: String): List<DriverFile> = SOURCES.flatMap { source ->
        runCatching { parse(source, get("https://api.github.com/repos/${source.repo}/releases?per_page=5", userAgent)) }.getOrDefault(emptyList())
    }.sortedByDescending { it.published }

    /** Downloads [file] into [target], replacing it only once the download is complete. */
    fun download(file: DriverFile, target: File, userAgent: String, onProgress: (Int) -> Unit) {
        val connection = (URL(file.url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("User-Agent", userAgent)
        }
        try {
            if (connection.responseCode != 200) throw IOException("the download answered ${connection.responseCode}")
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: file.size
            target.parentFile?.mkdirs()
            val part = File(target.path + ".part")
            connection.inputStream.use { input ->
                part.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        done += n
                        if (total > 0) onProgress((done * 100 / total).toInt())
                    }
                }
            }
            target.delete()
            if (!part.renameTo(target)) throw IOException("couldn't save ${target.name}")
        } finally {
            connection.disconnect()
        }
    }

    private fun get(url: String, userAgent: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Accept", "application/vnd.github+json")
        }
        try {
            if (connection.responseCode != 200) throw IOException("GitHub answered ${connection.responseCode}")
            return connection.inputStream.use { it.readBytes().decodeToString() }
        } finally {
            connection.disconnect()
        }
    }
}
