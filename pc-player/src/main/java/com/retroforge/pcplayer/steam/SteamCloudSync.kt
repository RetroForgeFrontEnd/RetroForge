package com.retroforge.pcplayer.steam

import android.util.Log
import `in`.dragonbra.javasteam.enums.EOSType
import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.steam.handlers.steamapps.PICSRequest
import `in`.dragonbra.javasteam.steam.handlers.steamapps.SteamApps
import `in`.dragonbra.javasteam.steam.handlers.steamcloud.AppFileChangeList
import `in`.dragonbra.javasteam.steam.handlers.steamcloud.SteamCloud
import `in`.dragonbra.javasteam.steam.steamclient.SteamClient
import `in`.dragonbra.javasteam.types.KeyValue
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeout
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.Date
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

/**
 * Steam Cloud saves for a Steam game played in RetroForge's PC player: before the game starts, saves that are newer
 * in the cloud (from a PC, a Steam Deck, another phone) are downloaded; when it closes, saves changed here are
 * uploaded. The same files Steam itself syncs, in the same places:
 *  - Steam Auto-Cloud files, named like "%WinAppDataLocal%MyGame/Saved/slot1.sav", go in Wine's user folders (or the
 *    game's folder for %GameInstall%). Which files count comes from the game's cloud rules ("ufs") in Steam's app info.
 *  - Files the game saves through Steam's own API ("remote storage") go in Steam's userdata folder, where the Steam
 *    emulator (see [SteamGameSetup]) keeps them: userdata/<account>/<app>/remote.
 *
 * When a save changed both here and in the cloud since the last sync, the newer one wins and the other is kept
 * beside it as "<name>.rfbak".
 */
class SteamCloudSync(
    private val account: SteamAccount,
    private val runtimeRoot: File,
    /** Wine's C: drive. */
    private val driveC: File,
    private val gameDir: File,
    private val appId: Int,
) {
    data class Result(val downloaded: Int = 0, val uploaded: Int = 0, val deleted: Int = 0, val conflicts: Int = 0)

    private class Rule(val root: String, val path: String, val pattern: Regex, val recursive: Boolean)

    private val stateFile = File(runtimeRoot, "steamcloud/$appId.json")
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

    /** Before playing: brings down saves that are newer in the cloud. Null when not signed in to Steam. */
    suspend fun download(onStep: (String) -> Unit = {}): Result? = account.withSignIn { client, _ ->
        val cloud = client.getHandler(SteamCloud::class.java) ?: throw IOException("Steam's cloud handler is missing")
        runCatching {
            cloud.signalAppLaunchIntent(appId, account.cloudClientId, account.machineName, true, EOSType.Win11).await()
        }
        val remote = withTimeout(TIMEOUT) { cloud.getAppFileListChange(appId, 0).await() }
        val state = loadState()
        var downloaded = 0
        var conflicts = 0
        for ((name, file) in remoteFiles(remote)) {
            val local = localFile(name) ?: continue
            val remoteSha = file.shaFile.hex()
            val localSha = if (local.isFile) sha1(local) else null
            when {
                localSha == remoteSha -> Unit
                localSha == null || localSha == state[name] -> {
                    onStep("Downloading your Steam Cloud saves")
                    fetch(cloud, name, local)
                    downloaded++
                }
                // Changed here and in the cloud: the newer one wins, the other is kept beside it.
                file.timestamp.time > local.lastModified() -> {
                    local.copyTo(File(local.path + ".rfbak"), overwrite = true)
                    fetch(cloud, name, local)
                    downloaded++
                    conflicts++
                }
                else -> {
                    conflicts++
                    continue
                }
            }
            state[name] = remoteSha
        }
        saveState(state)
        Log.i(TAG, "App $appId: ${remote.files.size} cloud files, $downloaded downloaded, $conflicts conflicts")
        Result(downloaded = downloaded, conflicts = conflicts)
    }

    /** After playing: sends up saves changed here (and removes ones deleted here). Null when not signed in. */
    suspend fun upload(onStep: (String) -> Unit = {}): Result? = account.withSignIn { client, _ ->
        val cloud = client.getHandler(SteamCloud::class.java) ?: throw IOException("Steam's cloud handler is missing")
        val remote = withTimeout(TIMEOUT) { cloud.getAppFileListChange(appId, 0).await() }
        val remoteShas = remoteFiles(remote).associate { (name, file) -> name to file.shaFile.hex() }
        val state = loadState()
        val local = localSaves(client, remote)
        // Different from the cloud and changed here since the last sync (or new). A save that only changed in the
        // cloud was brought down before the game started.
        val toUpload = local.filter { (name, file) ->
            val sha = sha1(file)
            sha != remoteShas[name] && (sha != state[name] || name !in remoteShas)
        }
        // Deleted here since the last sync, and unchanged in the cloud since then.
        val toDelete = state.keys.filter { name -> name !in local && remoteShas[name] != null && remoteShas[name] == state[name] && localFile(name)?.isFile == false }
        if (toUpload.isEmpty() && toDelete.isEmpty()) {
            cloud.signalAppExitSyncDone(appId, account.cloudClientId, true, false)
            return@withSignIn Result()
        }
        onStep("Saving to Steam Cloud")
        val batch = withTimeout(TIMEOUT) {
            cloud.beginAppUploadBatch(appId, account.machineName, toUpload.keys.toList(), toDelete, account.cloudClientId, 0L).await()
        }
        var ok = true
        var uploaded = 0
        for ((name, file) in toUpload) {
            val sha = sha1Bytes(file)
            val size = file.length().toInt()
            val info = withTimeout(TIMEOUT) {
                cloud.beginFileUpload(
                    appId = appId, fileSize = size, rawFileSize = size, fileSha = sha,
                    timestamp = Date(file.lastModified()), filename = name, uploadBatchId = batch.batchID,
                ).await()
            }
            // No blocks to send: the cloud already has these exact bytes.
            var sent = true
            RandomAccessFile(file, "r").use { input ->
                for (block in info.blockRequests) {
                    val body = block.explicitBodyData?.takeIf { it.isNotEmpty() } ?: ByteArray(block.blockLength).also {
                        input.seek(block.blockOffset)
                        input.readFully(it)
                    }
                    val headers = Headers.headersOf(*block.requestHeaders.flatMap { listOf(it.name, it.value) }.toTypedArray())
                    val request = Request.Builder().url(url(block.useHttps, block.urlHost, block.urlPath))
                        .headers(headers).put(body.toRequestBody()).build()
                    http.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            Log.w(TAG, "Upload of $name failed: ${response.code}")
                            sent = false
                        }
                    }
                    if (!sent) break
                }
            }
            if (info.blockRequests.isNotEmpty()) {
                withTimeout(TIMEOUT) { cloud.commitFileUpload(sent, appId, sha, name).await() }
            }
            if (sent) {
                state[name] = sha.hex()
                uploaded++
            } else ok = false
        }
        withTimeout(TIMEOUT) { cloud.completeAppUploadBatch(appId, batch.batchID, if (ok) EResult.OK else EResult.Fail).await() }
        if (ok) toDelete.forEach { state.remove(it) }
        saveState(state)
        cloud.signalAppExitSyncDone(appId, account.cloudClientId, ok, true)
        Log.i(TAG, "App $appId: $uploaded uploaded, ${toDelete.size} deleted, ok=$ok")
        Result(uploaded = uploaded, deleted = if (ok) toDelete.size else 0)
    }

    /** The cloud's files by full name (path prefix + file name), leaving out ones deleted from the cloud. */
    private fun remoteFiles(list: AppFileChangeList) = list.files
        .filter { !it.persistState.name.contains("Deleted", ignoreCase = true) }
        .map { file ->
            val prefix = list.pathPrefixes.getOrNull(file.pathPrefixIndex).takeIf { list.pathPrefixes.isNotEmpty() }.orEmpty()
            joinPrefix(prefix, file.filename) to file
        }

    /** Where a cloud file lives here; null for roots RetroForge doesn't place (Mac and Linux only, say). */
    private fun localFile(name: String): File? {
        val token = ROOT_TOKEN.find(name) ?: return File(remoteDir(), name)
        val root = rootDir(token.groupValues[1]) ?: return null
        return File(root, name.substring(token.range.last + 1).trimStart('/'))
    }

    private fun rootDir(root: String): File? {
        val user = File(driveC, "users/xuser")
        return when (root) {
            "GameInstall" -> gameDir
            "WinMyDocuments" -> File(user, "Documents")
            "WinAppDataLocal" -> File(user, "AppData/Local")
            "WinAppDataLocalLow" -> File(user, "AppData/LocalLow")
            "WinAppDataRoaming" -> File(user, "AppData/Roaming")
            "WinSavedGames" -> File(user, "Saved Games")
            "SteamUserData" -> remoteDir().parentFile
            else -> null
        }
    }

    /** Where the Steam emulator keeps the game's remote-storage files (as Steam does). */
    private fun remoteDir() = File(driveC, "Program Files (x86)/Steam/userdata/${account.steamId64 and 0xFFFFFFFFL}/$appId/remote")

    /**
     * The saves here that belong in the cloud: everything the game's cloud rules match, its remote-storage files,
     * and any file the cloud already has (so a save made under another rule still goes back up).
     */
    private suspend fun localSaves(client: SteamClient, remote: AppFileChangeList): Map<String, File> {
        val found = linkedMapOf<String, File>()
        for (rule in cloudRules(client)) {
            val dir = rootDir(rule.root)?.let { if (rule.path.isEmpty()) it else File(it, rule.path) } ?: continue
            if (!dir.isDirectory) continue
            val walk = if (rule.recursive) dir.walkTopDown() else dir.walkTopDown().maxDepth(1)
            walk.filter { it.isFile && !it.name.endsWith(".rfbak") && rule.pattern.matches(it.name) }.forEach { file ->
                val name = joinPrefix("%${rule.root}%" + rule.path.trim('/').let { if (it.isEmpty()) "" else "$it/" }, file.relativeTo(dir).invariantSeparatorsPath)
                found[name] = file
            }
        }
        remoteDir().takeIf { it.isDirectory }?.walkTopDown()?.filter { it.isFile }?.forEach { found[it.relativeTo(remoteDir()).invariantSeparatorsPath] = it }
        for ((name, _) in remoteFiles(remote)) {
            if (name !in found) localFile(name)?.takeIf { it.isFile }?.let { found[name] = it }
        }
        return found
    }

    /** The game's Steam Auto-Cloud rules ("ufs" → "savefiles" in its app info), Windows ones only. */
    private suspend fun cloudRules(client: SteamClient): List<Rule> {
        val apps = client.getHandler(SteamApps::class.java) ?: return emptyList()
        val token = runCatching { withTimeout(TIMEOUT) { apps.picsGetAccessTokens(appId, null).await() }.appTokens[appId] }.getOrNull() ?: 0L
        val info = runCatching {
            withTimeout(TIMEOUT) { apps.picsGetProductInfo(PICSRequest(appId, token), null, false).await() }
        }.getOrNull()?.results?.firstNotNullOfOrNull { it.apps[appId] }?.keyValues ?: return emptyList()
        val saveFiles = info["ufs"]["savefiles"]
        return saveFiles.children.mapNotNull { entry ->
            val platforms = entry["platforms"].children.map { it.value.orEmpty().lowercase() }
            if (platforms.isNotEmpty() && "windows" !in platforms && "all" !in platforms) return@mapNotNull null
            val root = entry["root"].value ?: return@mapNotNull null
            Rule(
                root = root,
                path = (entry["path"].value ?: "").replace('\\', '/').replace("{64BitSteamID}", account.steamId64.toString())
                    .replace("{Steam3AccountID}", (account.steamId64 and 0xFFFFFFFFL).toString()),
                pattern = glob(entry["pattern"].value ?: "*"),
                recursive = entry["recursive"].value == "1",
            )
        }.also { Log.i(TAG, "App $appId: ${it.size} cloud rules") }
    }

    /** Downloads one cloud file over HTTP into [target] (unzipping it when Steam sent it compressed). */
    private suspend fun fetch(cloud: SteamCloud, name: String, target: File) {
        val info = withTimeout(TIMEOUT) { cloud.clientFileDownload(appId, name).await() }
        if (info.encrypted) throw IOException("$name is encrypted in Steam Cloud")
        val headers = Headers.headersOf(*info.requestHeaders.flatMap { listOf(it.name, it.value) }.toTypedArray())
        val request = Request.Builder().url(url(info.useHttps, info.urlHost, info.urlPath)).headers(headers).build()
        target.parentFile?.mkdirs()
        val part = File(target.path + ".part")
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Steam Cloud download of $name failed (${response.code})")
            response.body.byteStream().use { body ->
                val input: InputStream = if (info.fileSize != info.rawFileSize) {
                    ZipInputStream(body).also { it.nextEntry ?: throw IOException("$name came compressed but empty") }
                } else body
                part.outputStream().use { input.copyTo(it) }
            }
        }
        if (part.length() != info.rawFileSize.toLong()) {
            part.delete()
            throw IOException("$name downloaded incomplete")
        }
        if (!part.renameTo(target)) {
            target.delete()
            part.renameTo(target)
        }
        target.setLastModified(info.timestamp.time)
    }

    private fun loadState(): MutableMap<String, String> = runCatching {
        val json = JSONObject(stateFile.readText())
        json.keys().asSequence().associateWith { json.getString(it) }.toMutableMap()
    }.getOrDefault(mutableMapOf())

    private fun saveState(state: Map<String, String>) {
        stateFile.parentFile?.mkdirs()
        stateFile.writeText(JSONObject(state).toString())
    }

    companion object {
        private const val TAG = "PcSteamCloud"
        private const val TIMEOUT = 30_000L
        private val ROOT_TOKEN = Regex("^%([A-Za-z]+)%")

        private fun url(https: Boolean, host: String, path: String) = (if (https) "https://" else "http://") + host + path

        /** "%Root%dir/" + "file", or "%Root%" + "file" with no separator when the prefix is just the root. */
        fun joinPrefix(prefix: String, file: String): String = when {
            prefix.isEmpty() -> file
            prefix.endsWith("/") || prefix.endsWith("%") -> prefix + file
            else -> "$prefix/$file"
        }

        /** A Steam cloud pattern ("*.sav", "slot?.dat", "*") as a regex, matching names case-insensitively. */
        fun glob(pattern: String): Regex = Regex(
            pattern.split('*').joinToString(".*") { part -> part.split('?').joinToString(".") { Regex.escape(it) } },
            RegexOption.IGNORE_CASE,
        )

        private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }

        private fun sha1Bytes(file: File): ByteArray {
            val digest = MessageDigest.getInstance("SHA-1")
            file.inputStream().use { input ->
                val buffer = ByteArray(1 shl 16)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
            return digest.digest()
        }

        private fun sha1(file: File) = sha1Bytes(file).hex()
    }
}
