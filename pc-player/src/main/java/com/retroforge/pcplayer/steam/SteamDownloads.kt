package com.retroforge.pcplayer.steam

import android.util.Log
import `in`.dragonbra.javasteam.depotdownloader.DepotDownloader
import `in`.dragonbra.javasteam.depotdownloader.IDownloadListener
import `in`.dragonbra.javasteam.depotdownloader.data.AppItem
import `in`.dragonbra.javasteam.depotdownloader.data.DownloadItem
import `in`.dragonbra.javasteam.steam.handlers.steamapps.PICSRequest
import `in`.dragonbra.javasteam.steam.handlers.steamapps.SteamApps
import `in`.dragonbra.javasteam.steam.steamclient.SteamClient
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Downloads a Steam game the signed-in account owns, straight from Steam's servers, into a folder of RetroForge's
 * PC games: the Windows, 64-bit, English build of its public branch (JavaSteam's depot downloader does the
 * manifests, chunks, decryption and writing). Run again on the same folder it carries on, or updates.
 */
class SteamDownloads(private val account: SteamAccount) {

    /** How much the game takes once downloaded (Windows, English, public branch), from Steam's app info. */
    suspend fun size(appId: Int): Long? = account.withSignIn { client, _ -> windowsDepotsSize(client, appId) }

    /**
     * Downloads [appId] into [folder]. [onProgress] gets 0–1, what's happening and the game's size in bytes. Throws when Steam refuses or the
     * download fails; null when not signed in.
     */
    suspend fun download(appId: Int, folder: File, onProgress: (Float, String, Long) -> Unit): Unit? =
        account.withLicenses { client, licenses ->
            val total = windowsDepotsSize(client, appId).coerceAtLeast(1)
            folder.mkdirs()
            val done = ConcurrentHashMap<Int, Long>()
            var failure: Throwable? = null
            val listener = object : IDownloadListener {
                override fun onStatusUpdate(message: String) {
                    if (message.startsWith("Downloading manifest")) onProgress(progress(done, total), "Getting the file list", total)
                }

                override fun onChunkCompleted(depotId: Int, depotPercentComplete: Float, compressedBytes: Long, uncompressedBytes: Long) {
                    done[depotId] = uncompressedBytes
                    onProgress(progress(done, total), "Downloading", total)
                }

                override fun onDepotCompleted(depotId: Int, compressedBytes: Long, uncompressedBytes: Long) {
                    done[depotId] = uncompressedBytes
                }

                override fun onDownloadFailed(item: DownloadItem, error: Throwable) {
                    failure = error
                }
            }
            DepotDownloader(client, licenses, maxDownloads = 4, maxFileWrites = 1).use { downloader ->
                downloader.addListener(listener)
                downloader.add(
                    AppItem(
                        appId = appId,
                        installDirectory = folder.path,
                        os = "windows",
                        osArch = "64",
                        language = "english",
                    ),
                )
                downloader.finishAdding()
                downloader.getCompletion().await()
            }
            failure?.let { throw IOException(it.message ?: "The download failed", it) }
            Log.i(TAG, "App $appId downloaded into $folder")
            Unit
        }

    private fun progress(done: Map<Int, Long>, total: Long) = (done.values.sum().toFloat() / total).coerceIn(0f, 1f)

    /** Total size of the depots a Windows, English, 64-bit download takes (public branch manifests). */
    private suspend fun windowsDepotsSize(client: SteamClient, appId: Int): Long {
        val apps = client.getHandler(SteamApps::class.java) ?: throw IOException("Steam's app handler is missing")
        val token = runCatching { withTimeout(TIMEOUT) { apps.picsGetAccessTokens(appId, null).await() }.appTokens[appId] }.getOrNull() ?: 0L
        val info = withTimeout(TIMEOUT) { apps.picsGetProductInfo(PICSRequest(appId, token), null, false).await() }
            .results.firstNotNullOfOrNull { it.apps[appId] }?.keyValues ?: throw IOException("Steam has no app info for $appId")
        return info["depots"].children.filter { it.name?.toIntOrNull() != null }.sumOf { depot ->
            val config = depot["config"]
            val os = config["oslist"].value.orEmpty().lowercase()
            val language = config["language"].value.orEmpty().lowercase()
            val arch = config["osarch"].value.orEmpty()
            val wanted = (os.isEmpty() || "windows" in os) && (language.isEmpty() || language == "english") && (arch.isEmpty() || arch == "64")
            if (!wanted) 0L else depot["manifests"]["public"]["size"].value?.toLongOrNull() ?: 0L
        }
    }

    private companion object {
        const val TAG = "PcSteamDownloads"
        const val TIMEOUT = 60_000L
    }
}
