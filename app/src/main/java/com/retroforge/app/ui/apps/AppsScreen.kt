package com.retroforge.app.ui.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.app.ui.ScreenFrame
import com.retroforge.core.ui.ControllerTextField
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.FocusChip
import com.retroforge.core.ui.StatusPill
import com.retroforge.core.ui.StatusTone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** An app on the device that can be opened. */
private class AndroidApp(val label: String, val packageName: String, val icon: ImageBitmap?)

/**
 * The device's own apps, for launcher mode (RetroForge as the home screen) and for anyone who wants their other apps
 * a tap away: Android settings at the top, then every app that can be opened, searchable.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppsScreen() {
    val context = LocalContext.current
    val apps by produceState<List<AndroidApp>?>(null) { value = withContext(Dispatchers.IO) { loadApps(context) } }
    var query by remember { mutableStateOf("") }

    ScreenFrame(title = "Android apps", subtitle = "Your other apps and Android's settings, without leaving RetroForge.") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FocusChip("Android settings", onClick = { start(context, Intent(Settings.ACTION_SETTINGS)) })
            FocusChip("Wi-Fi", onClick = { start(context, Intent(Settings.ACTION_WIFI_SETTINGS)) })
            FocusChip("Bluetooth (controllers)", onClick = { start(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) })
            FocusChip("Display", onClick = { start(context, Intent(Settings.ACTION_DISPLAY_SETTINGS)) })
            FocusChip("Sound", onClick = { start(context, Intent(Settings.ACTION_SOUND_SETTINGS)) })
        }
        LauncherModeCard()
        ControllerTextField(value = query, onValueChange = { query = it }, singleLine = true, label = { Text("Search apps") }, modifier = Modifier.fillMaxWidth())
        val list = apps
        if (list == null) {
            CardBody("Loading your apps…")
        } else {
            val shown = list.filter { query.isBlank() || it.label.contains(query.trim(), ignoreCase = true) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                shown.forEach { app ->
                    FocusCard(modifier = Modifier.width(104.dp), onClick = {
                        context.packageManager.getLaunchIntentForPackage(app.packageName)?.let { start(context, it) }
                    }) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            app.icon?.let { Image(it, contentDescription = null, modifier = Modifier.size(48.dp)) }
                            Text(
                                app.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Switches RetroForge's home-screen entry on or off, then shows Android's own "Home app" choice. */
@Composable
private fun LauncherModeCard() {
    val context = LocalContext.current
    val alias = remember { ComponentName(context, "com.retroforge.app.HomeLauncher") }
    var on by remember {
        mutableStateOf(context.packageManager.getComponentEnabledSetting(alias) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
    }
    FocusCard(modifier = Modifier.fillMaxWidth(), onClick = {
        on = !on
        context.packageManager.setComponentEnabledSetting(
            alias,
            if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP,
        )
        // Android decides the home app; this opens its choice so the user can pick RetroForge (or go back).
        start(context, Intent(Settings.ACTION_HOME_SETTINGS))
    }) {
        CardTitle("Use RetroForge as the home screen")
        StatusPill(if (on) "Offered to Android" else "Off", if (on) StatusTone.GOOD else StatusTone.NEUTRAL)
        CardBody(
            "For handhelds and TV boxes: the device starts in RetroForge and the home button comes back here. Select to switch, " +
                "then choose the home app in Android's list. To undo, select again and pick your old home app.",
        )
    }
}

private fun loadApps(context: Context): List<AndroidApp> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .filter { it.activityInfo.packageName != context.packageName }
        .distinctBy { it.activityInfo.packageName }
        .map { info ->
            val icon = runCatching { info.loadIcon(pm).toBitmap(96, 96, Bitmap.Config.ARGB_8888).asImageBitmap() }.getOrNull()
            AndroidApp(info.loadLabel(pm).toString(), info.activityInfo.packageName, icon)
        }
        .sortedBy { it.label.lowercase() }
}

private fun start(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
