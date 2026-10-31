package com.retroforge.app.ui.settings

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.retroforge.app.R
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.FocusChip
import com.retroforge.core.ui.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Closes other apps that sit idle in the background, so a game has more memory. Android only lets an app do this for
 * apps it isn't using right now (not the home screen, music that's playing, or anything with a notification running),
 * and it may start some of them again later on its own.
 */
object FreeMemory {
    /** Available memory in MB. */
    fun availableMb(context: Context): Long {
        val info = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java).getMemoryInfo(info)
        return info.availMem / 1_000_000
    }

    /** Asks Android to close every other app's background processes; returns how many apps were asked. */
    fun closeBackgroundApps(context: Context): Int {
        val am = context.getSystemService(ActivityManager::class.java)
        val pm = context.packageManager
        val launchers = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.packageName }
        // Never our own players, which a game may be using.
        val keep = setOf(context.packageName, SwitchPlayer.PACKAGE, "com.retroforge.ps3player", "com.retroforge.ps4player")
        val apps = (launchers - keep).distinct()
        apps.forEach { runCatching { am.killBackgroundProcesses(it) } }
        return apps.size
    }
}

@Composable
fun FreeMemoryCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.free_memory_title))
        CardBody(stringResource(R.string.free_memory_body))
        FocusChip(stringResource(R.string.free_memory_button), enabled = !busy, onClick = {
            busy = true
            scope.launch {
                status = withContext(Dispatchers.Default) {
                    val before = FreeMemory.availableMb(context)
                    val asked = FreeMemory.closeBackgroundApps(context)
                    kotlinx.coroutines.delay(1500) // Android takes a moment to hand the memory back
                    val after = FreeMemory.availableMb(context)
                    str(R.string.free_memory_done, asked, (after - before).coerceAtLeast(0), after)
                }
                busy = false
            }
        })
        status?.let { CardBody(it) }
    }
}
