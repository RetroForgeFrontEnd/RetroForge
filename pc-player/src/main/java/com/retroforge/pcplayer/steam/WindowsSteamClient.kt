package com.retroforge.pcplayer.steam

import com.retroforge.pcplayer.R
import com.retroforge.core.ui.str
import android.content.Context
import com.retroforge.pcplayer.PcRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext

/**
 * Steam method 2, the Steam client: Valve's own Windows Steam, run quietly in RetroForge's PC player for the games
 * that won't start without it (Steam's CEG copy protection, as in XCOM: Enemy Unknown, makes a game hand itself back
 * to Steam and quit). Method 1, RetroForge Steam (SteamAccount, with Goldberg's stand-in beside each game), is quicker
 * and lighter and suits most games.
 *
 * Installing downloads Valve's client packages from Valve's own update server and unpacks them in Wine's C: drive,
 * as Steam's updater would: the updater itself can't run under Wine (Wine refuses the \\?\C:\. path it checks free
 * space with, so it gives up). Nothing of Valve's is in RetroForge.
 */
class WindowsSteamClient(context: Context) {

    private val runtime = PcRuntime(context)

    /** Where Steam lives: C:\Program Files (x86)\Steam, as on Windows. */
    val dir = File(runtime.prefix, "drive_c/Program Files (x86)/Steam")
    val exe get() = File(dir, "steam.exe")
    private val installedManifest get() = File(dir, "package/$MANIFEST.installed")

    val installed get() = exe.isFile && installedManifest.isFile

    /** Whether Steam remembers an account (signed in once in its own window, with "Remember me"). */
    val signedIn get() = File(dir, "config/loginusers.vdf").let { it.isFile && it.readText().contains("\"AccountName\"") }

    /** Removes the Steam client (its files and sign-in); games and their saves stay. */
    fun remove() {
        dir.deleteRecursively()
    }

    /**
     * Puts a game RetroForge downloaded into Steam's library so `steam.exe -applaunch` can start it: a link from
     * steamapps/common to the game's folder, and an app manifest with the depots RetroForge downloaded (from its
     * .DepotDownloader folder). Steam checks the build itself and fetches only what changed, if anything.
     */
    fun registerGame(appId: Int, name: String, folder: File) {
        val apps = File(dir, "steamapps")
        val common = File(apps, "common").apply { mkdirs() }
        val link = File(common, folder.name)
        if (!link.exists()) runCatching { android.system.Os.symlink(folder.path, link.path) }
        val depots = File(folder, ".DepotDownloader").listFiles { f -> f.name.endsWith(".manifest") }.orEmpty()
            .mapNotNull { f -> f.name.removeSuffix(".manifest").split('_').takeIf { it.size == 2 && it.all { p -> p.toLongOrNull() != null } } }
        val manifest = buildString {
            appendLine("\"AppState\"")
            appendLine("{")
            appendLine("\t\"appid\"\t\t\"$appId\"")
            appendLine("\t\"Universe\"\t\t\"1\"")
            appendLine("\t\"name\"\t\t\"${name.replace("\"", "")}\"")
            appendLine("\t\"StateFlags\"\t\t\"4\"")
            appendLine("\t\"installdir\"\t\t\"${folder.name}\"")
            appendLine("\t\"LastUpdated\"\t\t\"${System.currentTimeMillis() / 1000}\"")
            appendLine("\t\"buildid\"\t\t\"0\"")
            appendLine("\t\"InstalledDepots\"")
            appendLine("\t{")
            for ((depot, manifestId) in depots) {
                appendLine("\t\t\"$depot\"")
                appendLine("\t\t{")
                appendLine("\t\t\t\"manifest\"\t\t\"$manifestId\"")
                appendLine("\t\t}")
            }
            appendLine("\t}")
            appendLine("\t\"UserConfig\" { \"language\" \"english\" }")
            appendLine("}")
        }
        File(apps, "appmanifest_$appId.acf").writeText(manifest)
    }

    /** One of Valve's client packages: a zip, its size and its SHA-256. */
    private data class Package(val file: String, val size: Long, val sha256: String)

    /**
     * Downloads (or updates) and unpacks Valve's Steam client. [onProgress] gets 0–1 and what's happening. Packages
     * already downloaded and intact aren't fetched again. Throws when a download fails or doesn't match Valve's
     * checksum.
     */
    suspend fun install(onProgress: (Float, String) -> Unit) = withContext(Dispatchers.IO) {
        onProgress(0f, str(R.string.windows_steam_client_asking_valve_for_the_steam_client))
        val manifest = get(URL(HOST + MANIFEST)).toString(Charsets.UTF_8)
        val packages = packages(manifest)
        if (packages.isEmpty()) throw IOException(str(R.string.windows_steam_client_valve_s_list_of_steam_client))
        val total = packages.sumOf { it.size }.coerceAtLeast(1)
        val packageDir = File(dir, "package").apply { mkdirs() }
        var done = 0L
        for (pkg in packages) {
            coroutineContext.ensureActive()
            val zip = File(packageDir, pkg.file)
            if (!(zip.length() == pkg.size && sha256(zip) == pkg.sha256)) {
                download(URL(HOST + pkg.file), zip) { read -> onProgress(0.9f * (done + read) / total, str(R.string.windows_steam_client_downloading_steam)) }
                if (sha256(zip) != pkg.sha256) {
                    zip.delete()
                    throw IOException(str(R.string.windows_steam_client_didn_t_match_valve_s_checksum, pkg.file))
                }
            }
            done += pkg.size
            onProgress(0.9f * done / total, str(R.string.windows_steam_client_downloading_steam))
        }
        packages.forEachIndexed { i, pkg ->
            onProgress(0.9f + 0.1f * i / packages.size, str(R.string.windows_steam_client_unpacking_steam))
            val zip = File(packageDir, pkg.file)
            unzip(zip, dir)
            zip.delete() // unpacked; it would only take space (an update downloads what changed again)
        }
        installedManifest.writeText(manifest)
        raiseWebHelperStack(dir)
        onProgress(1f, str(R.string.windows_steam_client_steam_client_ready))
    }

    companion object {
        private const val HOST = "https://client-update.steamstatic.com/"

        /** The 64-bit client (Valve's 32-bit one is no longer updated, and it refuses to start). */
        private const val MANIFEST = "steam_client_win64"

        /** The packages for every Windows PC: each top-level entry's own file, not its region or Windows 7 variants. */
        private fun packages(manifest: String): List<Package> {
            val found = mutableListOf<Package>()
            var depth = 0
            var fields = mutableMapOf<String, String>()
            for (raw in manifest.lines()) {
                val line = raw.trim()
                when {
                    line == "{" -> depth++
                    line == "}" -> {
                        if (depth == 2) {
                            val file = fields["file"]
                            val size = fields["size"]?.toLongOrNull()
                            val sha = fields["sha2"]
                            if (file != null && size != null && sha != null) found += Package(file, size, sha)
                            fields = mutableMapOf()
                        }
                        depth--
                    }
                    depth == 2 -> Regex("\"(\\w+)\"\\s+\"([^\"]*)\"").matchEntire(line)?.let { fields[it.groupValues[1]] = it.groupValues[2] }
                }
            }
            return found
        }

        /**
         * steamwebhelper.exe (Steam's Chromium-based windows) gets an 8 MB main-thread stack instead of 1 MB: the
         * emulated x86 code needs more than on a PC, and Chromium's main thread runs out. A header field only.
         */
        fun raiseWebHelperStack(dir: File) {
            val helper = File(dir, "bin/cef/cef.win64/steamwebhelper.exe")
            if (!helper.isFile) return
            RandomAccessFile(helper, "rw").use { f ->
                f.seek(0x3c)
                val pe = Integer.reverseBytes(f.readInt()).toLong()
                f.seek(pe + 24)
                if (java.lang.Short.reverseBytes(f.readShort()).toInt() != 0x20b) return // not a 64-bit PE
                f.seek(pe + 24 + 72)
                if (java.lang.Long.reverseBytes(f.readLong()) >= STACK) return
                f.seek(pe + 24 + 72)
                f.writeLong(java.lang.Long.reverseBytes(STACK))
            }
        }

        private const val STACK = 8L * 1024 * 1024

        private fun connect(url: URL): HttpURLConnection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            if (responseCode != 200) throw IOException(str(R.string.windows_steam_client_valve_s_server_answered_for, responseCode, url.path))
        }

        private fun get(url: URL): ByteArray = connect(url).run { inputStream.use { it.readBytes() } }

        private fun download(url: URL, to: File, onRead: (Long) -> Unit) {
            val part = File(to.path + ".part")
            connect(url).inputStream.use { input ->
                part.outputStream().use { out ->
                    val buffer = ByteArray(1 shl 16)
                    var read = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        read += n
                        onRead(read)
                    }
                }
            }
            if (!part.renameTo(to)) throw IOException(str(R.string.pc_runtime_couldn_t_save, to.name))
        }

        private fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(1 shl 16)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        private fun unzip(zip: File, into: File) {
            val root = into.canonicalFile
            ZipInputStream(zip.inputStream().buffered()).use { input ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    val target = File(into, entry.name.replace('\\', '/'))
                    if (!target.canonicalPath.startsWith(root.path + File.separator)) continue // stays inside Steam's folder
                    if (entry.isDirectory) {
                        target.mkdirs()
                    } else {
                        target.parentFile?.mkdirs()
                        target.outputStream().use { input.copyTo(it) }
                    }
                }
            }
        }
    }
}
