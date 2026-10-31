package com.retroforge.app.ui.library

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.retroforge.app.MainActivity
import com.retroforge.app.R
import com.retroforge.core.ui.str

/**
 * Keeps RetroForge running while a game on a network drive is copied to the phone before it opens in another app.
 * Without it, leaving RetroForge (to answer a text, say) lets Android, and Samsung's app freezer most of all, put it
 * to sleep, which cuts the link to the PC halfway through. The copy itself runs in [LibraryViewModel]; this only shows
 * its progress in a notification, which is what keeps the app awake.
 */
class GameCopyService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra(EXTRA_TITLE).orEmpty()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
        ServiceCompat.startForeground(this, ID_COPYING, copying(this, title, 0), type)
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "game_copy"
        private const val ID_COPYING = 4101
        private const val ID_READY = 4102
        private const val EXTRA_TITLE = "title"

        fun start(context: Context, title: String) {
            channel(context)
            runCatching { ContextCompat.startForegroundService(context, Intent(context, GameCopyService::class.java).putExtra(EXTRA_TITLE, title)) }
        }

        fun progress(context: Context, title: String, percent: Int) {
            manager(context).notify(ID_COPYING, copying(context, title, percent))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GameCopyService::class.java))
        }

        /** True while RetroForge is on screen, so the game may be opened straight away. */
        fun inFront(): Boolean = ActivityManager.RunningAppProcessInfo().also(ActivityManager::getMyMemoryState).importance <=
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND

        /**
         * The copy finished while RetroForge was in the background, where Android doesn't let it open another app:
         * a notification opens the game with a tap instead.
         */
        fun ready(context: Context, title: String, launch: Intent) {
            channel(context)
            val tap = PendingIntent.getActivity(context, ID_READY, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_section_retro)
                .setContentTitle(str(R.string.game_copy_ready, title))
                .setContentText(str(R.string.game_copy_tap_to_play))
                .setContentIntent(tap)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()
            runCatching { manager(context).notify(ID_READY, notification) }
        }

        private fun copying(context: Context, title: String, percent: Int): Notification {
            val open = PendingIntent.getActivity(
                context, ID_COPYING, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            return NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_section_retro)
                .setContentTitle(title)
                .setContentText(str(R.string.game_copy_copying, percent))
                .setProgress(100, percent, false)
                .setContentIntent(open)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .build()
        }

        private fun manager(context: Context) = context.getSystemService(NotificationManager::class.java)

        private fun channel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                manager(context).createNotificationChannel(
                    NotificationChannel(CHANNEL, str(R.string.game_copy_channel), NotificationManager.IMPORTANCE_DEFAULT).apply { setSound(null, null) },
                )
            }
        }
    }
}
