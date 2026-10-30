package com.retroforge.app.ui.pc

import com.retroforge.app.R
import com.retroforge.core.ui.str
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.retroforge.app.BuildConfig
import com.retroforge.library.GamePort
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/** One file of a port's latest GitHub release. */
data class ReleaseFile(val name: String, val url: String, val size: Long)

/**
 * Direct downloads of a port's latest official GitHub release: the Android APK for this phone (then Android's
 * installer, where the user confirms), or the Windows version unzipped into Download/RetroForge/Ports for GameNative.
 */
object PortDownloads {

    private val agent = "RetroForge/${BuildConfig.VERSION_NAME}"

    /** The latest release's files, from GitHub's API. */
    fun latest(port: GamePort): List<ReleaseFile> = latest(port.repo)

    /** The latest release's files of a GitHub repository ("owner/name" or its address). */
    fun latest(repository: String): List<ReleaseFile> {
        val repo = repository.removePrefix("https://github.com/").trimEnd('/')
        val json = JSONObject(get("https://api.github.com/repos/$repo/releases/latest").decodeToString())
        val assets = json.optJSONArray("assets") ?: return emptyList()
        return (0 until assets.length()).map { assets.getJSONObject(it) }
            .map { ReleaseFile(it.getString("name"), it.getString("browser_download_url"), it.optLong("size")) }
    }

    /** The APK for this phone: one named for its processor, else one without a processor in its name. */
    fun pickApk(files: List<ReleaseFile>): ReleaseFile? {
        val apks = files.filter { it.name.endsWith(".apk", ignoreCase = true) }
        val abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty().lowercase()
        val tags = mapOf("arm64-v8a" to listOf("arm64", "aarch64", "v8a"), "x86_64" to listOf("x86_64", "x64"), "armeabi-v7a" to listOf("armeabi", "v7a", "arm32"))
        val mine = tags[abi].orEmpty()
        val others = tags.filterKeys { it != abi }.values.flatten()
        return apks.firstOrNull { apk -> mine.any { apk.name.contains(it, ignoreCase = true) } }
            ?: apks.firstOrNull { apk -> others.none { apk.name.contains(it, ignoreCase = true) } }
            ?: apks.firstOrNull()
    }

    /** The Windows version: a zip with "win" in its name. */
    fun pickWindows(files: List<ReleaseFile>): ReleaseFile? =
        files.firstOrNull { it.name.endsWith(".zip", ignoreCase = true) && Regex("""win(dows|64|32)?\b|-win|_win""", RegexOption.IGNORE_CASE).containsMatchIn(it.name) }

    /** Downloads [file] and opens Android's installer for it. */
    fun installApk(context: Context, file: ReleaseFile, onProgress: (Int) -> Unit) {
        val apk = File(context.cacheDir, "ports/${file.name.replace(Regex("""[^\w.-]"""), "_")}")
        download(file, apk, onProgress)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.ports", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    /** Downloads the Windows zip and unpacks it into Download/RetroForge/Ports/<port>. Returns where. */
    fun unpackWindows(context: Context, port: GamePort, file: ReleaseFile, onProgress: (Int) -> Unit): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) throw IOException(str(R.string.port_downloads_needs_android_10_or_newer))
        val zip = File(context.cacheDir, "ports/${file.name.replace(Regex("""[^\w.-]"""), "_")}")
        download(file, zip, onProgress)
        val folder = "${Environment.DIRECTORY_DOWNLOADS}/RetroForge/Ports/${port.name.replace(":", " -").replace(Regex("""[\\/*?"<>|]"""), "_")} (Windows)"
        val resolver = context.contentResolver
        try {
            ZipInputStream(zip.inputStream().buffered()).use { input ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    if (entry.isDirectory) continue
                    // Paths stay inside the port's folder.
                    val path = entry.name.replace('\\', '/').split('/').filter { it.isNotBlank() && it != ".." && it != "." }
                    if (path.isEmpty()) continue
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, path.last())
                        put(MediaStore.Downloads.RELATIVE_PATH, (listOf(folder) + path.dropLast(1)).joinToString("/"))
                    }
                    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IOException("couldn't create ${path.last()}")
                    resolver.openOutputStream(uri)?.use { input.copyTo(it) } ?: throw IOException("couldn't write ${path.last()}")
                }
            }
        } finally {
            zip.delete()
        }
        return "Download/RetroForge/Ports/${folder.substringAfterLast('/')}"
    }

    fun download(file: ReleaseFile, target: File, onProgress: (Int) -> Unit) {
        val connection = open(file.url)
        try {
            if (connection.responseCode != 200) throw IOException(str(R.string.port_downloads_the_download_answered, connection.responseCode))
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
            if (!part.renameTo(target)) throw IOException(str(R.string.bezels_couldn_t_save, target.name))
        } finally {
            connection.disconnect()
        }
    }

    private fun get(url: String): ByteArray {
        val connection = open(url).apply { setRequestProperty("Accept", "application/vnd.github+json") }
        try {
            if (connection.responseCode != 200) throw IOException(str(R.string.bezels_github_answered, connection.responseCode))
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private fun open(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 60_000
        setRequestProperty("User-Agent", agent)
    }
}
