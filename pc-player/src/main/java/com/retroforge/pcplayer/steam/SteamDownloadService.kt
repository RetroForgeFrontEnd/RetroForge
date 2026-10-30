package com.retroforge.pcplayer.steam

import com.retroforge.pcplayer.R
import com.retroforge.core.ui.str
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File

/**
 * Downloads a Steam game in the background (a foreground service with a progress notification, so Android lets
 * it run for the hour or more a big game takes), one game at a time. It runs in its own process (a big download
 * needs the memory), so how it's going is kept in a small status file the PC tab reads with [read].
 */
class SteamDownloadService : Service() {

    data class State(
        val appId: Int,
        val name: String,
        val folder: String,
        val progress: Float,
        val step: String,
        val error: String? = null,
        val finished: Boolean = false,
        /** When this was written; a running download that stopped writing died with its process. */
        val updated: Long = System.currentTimeMillis(),
    ) {
        val running get() = !finished && error == null && System.currentTimeMillis() - updated < STALE_MS
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var lastNotified = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // The notification's Cancel button: stop the download (what's downloaded stays, to carry on from later).
        if (intent?.action == ACTION_CANCEL) {
            job?.cancel()
            return START_NOT_STICKY
        }
        val appId = intent?.getIntExtra(EXTRA_APP_ID, 0) ?: 0
        val name = intent?.getStringExtra(EXTRA_NAME).orEmpty()
        val folder = intent?.getStringExtra(EXTRA_FOLDER)?.let(::File)
        val allowMobile = intent?.getBooleanExtra(EXTRA_ALLOW_MOBILE, false) == true
        if (appId == 0 || folder == null || downloading) return START_NOT_STICKY
        downloading = true

        createChannel()
        val first = State(appId, name, folder.path, 0f, str(R.string.pc_player_starting))
        save(first)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification(first), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification(first))
        }
        holdLocks()
        job = scope.launch {
            val result = runCatching {
                // Wi-Fi only unless the player allowed mobile data: without Wi-Fi the download waits, and when Wi-Fi
                // drops mid-way it stops and waits, then carries on from where it was once Wi-Fi is back.
                while (true) {
                    while (!allowMobile && !onWifi()) {
                        update(State(appId, name, folder.path, lastProgress, str(R.string.steam_download_service_waiting_for_wi_fi)))
                        delay(NETWORK_CHECK_MS)
                    }
                    var wifiLost = false
                    coroutineScope {
                        val download = launch {
                            SteamDownloads(SteamAccount(this@SteamDownloadService)).download(appId, folder) { progress, step, total ->
                                totalBytes = total
                                update(State(appId, name, folder.path, progress, step))
                            } ?: throw IllegalStateException(str(R.string.steam_download_service_sign_in_to_steam_first))
                        }
                        val watcher = if (allowMobile) null else launch {
                            while (true) {
                                delay(NETWORK_CHECK_MS)
                                if (!onWifi()) {
                                    wifiLost = true
                                    download.cancel()
                                    break
                                }
                            }
                        }
                        download.join()
                        watcher?.cancel()
                    }
                    if (!wifiLost) break
                }
            }
            val end = result.fold(
                onSuccess = { State(appId, name, folder.path, 1f, str(R.string.steam_download_service_ready_to_play), finished = true) },
                onFailure = {
                    if (it is kotlinx.coroutines.CancellationException) {
                        return@fold State(appId, name, folder.path, lastProgress, str(R.string.steam_download_service_stopped), error = str(R.string.steam_download_service_cancelled_select_the_game_to_carry))
                    }
                    Log.w(TAG, "Download of $appId failed", it)
                    State(appId, name, folder.path, lastProgress, str(R.string.steam_download_service_stopped), error = it.message ?: str(R.string.steam_download_service_the_download_failed))
                },
            )
            save(end)
            downloading = false
            releaseLocks()
            stopForeground(STOP_FOREGROUND_DETACH)
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(end))
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private var lastProgress = 0f
    private var totalBytes = 0L
    private var job: kotlinx.coroutines.Job? = null
    private var lastSaved = 0L

    /** Whether the phone is on Wi-Fi (or another network that isn't charged by the byte, like Ethernet). */
    private fun onWifi(): Boolean {
        val connectivity = getSystemService(android.net.ConnectivityManager::class.java)
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }

    private var lastStep = ""

    /** Progress: the notification on each new percent or step, the status file every couple of seconds. */
    private fun update(next: State) {
        lastProgress = next.progress
        val percent = (next.progress * 100).toInt()
        if (percent != lastNotified || next.step != lastStep) {
            lastNotified = percent
            lastStep = next.step
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(next))
        }
        if (next.updated - lastSaved > 2_000) save(next)
    }

    private fun save(state: State) {
        lastSaved = state.updated
        val file = statusFile(this)
        file.parentFile?.mkdirs()
        val part = File(file.path + ".part")
        part.writeText(
            JSONObject().put("appId", state.appId).put("name", state.name).put("folder", state.folder)
                .put("progress", state.progress.toDouble()).put("step", state.step).put("error", state.error ?: "")
                .put("finished", state.finished).put("updated", state.updated).toString(),
        )
        part.renameTo(file)
    }

    private fun notification(s: State): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(this)
        val percent = (s.progress * 100).toInt()
        return builder
            .setSmallIcon(if (s.finished || s.error != null) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_sys_download)
            .setContentTitle(s.name)
            .setContentText(
                s.error?.let { str(R.string.steam_download_service_download_stopped, it) } ?: if (s.finished) str(R.string.steam_download_service_downloaded_from_steam_ready_to_play)
                else "${s.step} · $percent%" + (totalBytes.takeIf { it > 0 }?.let { str(R.string.steam_download_service_of_1f_gb).format(it / 1_000_000_000.0) } ?: ""),
            )
            .setOnlyAlertOnce(true)
            .setOngoing(!s.finished && s.error == null)
            .apply { if (!s.finished && s.error == null) setProgress(100, percent, percent == 0) }
            .apply {
                if (!s.finished && s.error == null) {
                    val cancel = Intent(this@SteamDownloadService, SteamDownloadService::class.java).setAction(ACTION_CANCEL)
                    addAction(
                        Notification.Action.Builder(
                            null, str(R.string.steam_download_service_cancel),
                            android.app.PendingIntent.getService(this@SteamDownloadService, 1, cancel, android.app.PendingIntent.FLAG_IMMUTABLE),
                        ).build(),
                    )
                }
            }
            // Tapping it opens RetroForge's PC tab, where the game is.
            .apply { packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
                launch.putExtra(EXTRA_OPEN_SECTION, "pc").addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                setContentIntent(
                    android.app.PendingIntent.getActivity(
                        this@SteamDownloadService, 0, launch,
                        android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                )
            } }
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, str(R.string.steam_download_service_steam_downloads), NotificationManager.IMPORTANCE_LOW))
    }

    /** Keeps the CPU and Wi-Fi awake while downloading, so it doesn't stall when the screen goes off. */
    private fun holdLocks() {
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RetroForge:SteamDownload").apply { acquire(6 * 60 * 60 * 1000L) }
        @Suppress("DEPRECATION")
        wifiLock = (applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
            .createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "RetroForge:SteamDownload").apply { acquire() }
    }

    private fun releaseLocks() {
        runCatching { wakeLock?.takeIf { it.isHeld }?.release() }
        runCatching { wifiLock?.takeIf { it.isHeld }?.release() }
    }

    override fun onDestroy() {
        releaseLocks()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PcSteamDownload"
        private const val CHANNEL = "steam_downloads"
        private const val NOTIFICATION_ID = 7301
        private const val EXTRA_APP_ID = "app_id"
        private const val EXTRA_NAME = "name"
        private const val EXTRA_FOLDER = "folder"
        private const val EXTRA_ALLOW_MOBILE = "allow_mobile"
        private const val NETWORK_CHECK_MS = 3_000L

        private const val STALE_MS = 2 * 60 * 1000L
        private const val ACTION_CANCEL = "com.retroforge.pcplayer.steam.CANCEL"
        /** RetroForge's main screen opens the section named by this extra (MainActivity.EXTRA_OPEN_SECTION). */
        private const val EXTRA_OPEN_SECTION = "open_section"

        /** Whether this process is downloading (one game at a time). */
        @Volatile
        private var downloading = false

        private fun statusFile(context: Context) = File(context.filesDir, "pc/steam_download.json")

        /** The latest download's status (from any process), or null if there's never been one. */
        fun read(context: Context): State? = runCatching {
            val o = JSONObject(statusFile(context).readText())
            State(
                o.getInt("appId"), o.getString("name"), o.getString("folder"), o.getDouble("progress").toFloat(), o.getString("step"),
                o.optString("error").ifEmpty { null }, o.optBoolean("finished"), o.getLong("updated"),
            )
        }.getOrNull()

        /** Downloads a game; [allowMobile] lets it use mobile data when there's no Wi-Fi (otherwise it waits for Wi-Fi). */
        fun start(context: Context, appId: Int, name: String, folder: File, allowMobile: Boolean = false) {
            val intent = Intent(context, SteamDownloadService::class.java)
                .putExtra(EXTRA_APP_ID, appId).putExtra(EXTRA_NAME, name).putExtra(EXTRA_FOLDER, folder.path)
                .putExtra(EXTRA_ALLOW_MOBILE, allowMobile)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        }
    }
}
