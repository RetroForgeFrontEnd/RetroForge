package com.retroforge.pcplayer.steam

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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Downloads a Steam game in the background (a foreground service with a progress notification, so Android lets
 * it run for the hour or more a big game takes), one game at a time. [state] says how it's going; [onFinished]
 * hears when a game is ready.
 */
class SteamDownloadService : Service() {

    data class State(val appId: Int, val name: String, val progress: Float, val step: String, val error: String? = null, val finished: Boolean = false)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var lastNotified = -1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val appId = intent?.getIntExtra(EXTRA_APP_ID, 0) ?: 0
        val name = intent?.getStringExtra(EXTRA_NAME).orEmpty()
        val folder = intent?.getStringExtra(EXTRA_FOLDER)?.let(::File)
        if (appId == 0 || folder == null || state.value?.let { !it.finished && it.error == null } == true) return START_NOT_STICKY

        createChannel()
        val first = State(appId, name, 0f, "Starting")
        mutableState.value = first
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification(first), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification(first))
        }
        holdLocks()
        scope.launch {
            val result = runCatching {
                SteamDownloads(SteamAccount(this@SteamDownloadService)).download(appId, folder) { progress, step ->
                    update(State(appId, name, progress, step))
                } ?: throw IllegalStateException("Sign in to Steam first")
            }
            val end = result.fold(
                onSuccess = { State(appId, name, 1f, "Ready to play", finished = true) },
                onFailure = {
                    Log.w(TAG, "Download of $appId failed", it)
                    State(appId, name, mutableState.value?.progress ?: 0f, "Stopped", error = it.message ?: "The download failed")
                },
            )
            mutableState.value = end
            if (end.finished) onFinished?.invoke(appId, name, folder)
            releaseLocks()
            stopForeground(STOP_FOREGROUND_DETACH)
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(end))
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun update(next: State) {
        mutableState.value = next
        val percent = (next.progress * 100).toInt()
        if (percent != lastNotified) {
            lastNotified = percent
            getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(next))
        }
    }

    private fun notification(s: State): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) Notification.Builder(this, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(this)
        val percent = (s.progress * 100).toInt()
        return builder
            .setSmallIcon(if (s.finished || s.error != null) android.R.drawable.stat_sys_download_done else android.R.drawable.stat_sys_download)
            .setContentTitle(s.name)
            .setContentText(s.error?.let { "Download stopped: $it" } ?: if (s.finished) "Downloaded from Steam. Ready to play." else "${s.step} · $percent%")
            .setOnlyAlertOnce(true)
            .setOngoing(!s.finished && s.error == null)
            .apply { if (!s.finished && s.error == null) setProgress(100, percent, percent == 0) }
            .apply { packageManager.getLaunchIntentForPackage(packageName)?.let { launch ->
                setContentIntent(android.app.PendingIntent.getActivity(this@SteamDownloadService, 0, launch, android.app.PendingIntent.FLAG_IMMUTABLE))
            } }
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(NotificationChannel(CHANNEL, "Steam downloads", NotificationManager.IMPORTANCE_LOW))
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

        private val mutableState = MutableStateFlow<State?>(null)
        val state: StateFlow<State?> = mutableState

        /** Called (on a background thread) when a game has finished downloading. */
        @Volatile
        var onFinished: ((appId: Int, name: String, folder: File) -> Unit)? = null

        fun start(context: Context, appId: Int, name: String, folder: File) {
            val intent = Intent(context, SteamDownloadService::class.java)
                .putExtra(EXTRA_APP_ID, appId).putExtra(EXTRA_NAME, name).putExtra(EXTRA_FOLDER, folder.path)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        }
    }
}
