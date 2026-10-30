package com.retroforge.app.ui.apps

import com.retroforge.app.R

import androidx.compose.ui.res.stringResource

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.provider.MediaStore
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** An app on the device that can be opened. */
private class AndroidApp(val label: String, val packageName: String, val icon: ImageBitmap?)

/**
 * The device's own apps, laid out like a phone's home screen: the time, a search bar, a dock of the apps used most
 * (or pinned with a long press), then every app as an icon grid, and Android's settings at the bottom. Used for
 * launcher mode (RetroForge as the home screen) and for anyone who wants their other apps a tap away.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppsScreen() {
    val context = LocalContext.current
    val apps by produceState<List<AndroidApp>?>(null) { value = withContext(Dispatchers.IO) { loadApps(context) } }
    var query by remember { mutableStateOf("") }
    var changes by remember { mutableIntStateOf(0) }
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(15_000) } }

    fun open(app: AndroidApp) {
        AppUsage.opened(context, app.packageName)
        changes++
        context.packageManager.getLaunchIntentForPackage(app.packageName)?.let { start(context, it) }
    }
    fun togglePin(app: AndroidApp) { AppUsage.togglePin(context, app.packageName); changes++ }

    ScreenFrame(title = null) {
        // The time, like a phone's home screen.
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(now), fontSize = 56.sp, fontWeight = FontWeight.Light,
                color = MaterialTheme.colorScheme.onBackground)
            Text(DateFormat.getDateInstance(DateFormat.FULL).format(now), style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ControllerTextField(value = query, onValueChange = { query = it }, singleLine = true, label = { Text(stringResource(R.string.apps_search_apps)) },
            modifier = Modifier.fillMaxWidth())
        val list = apps
        if (list == null) {
            CardBody(stringResource(R.string.apps_loading_your_apps))
            return@ScreenFrame
        }
        if (query.isNotBlank()) {
            AppGrid(list.filter { it.label.contains(query.trim(), ignoreCase = true) }, ::open, ::togglePin)
            return@ScreenFrame
        }
        // The dock: pinned apps, then the most used, then the usual phone apps until there's some history.
        val dock = remember(list, changes) { AppUsage.dock(context, list.map { it.packageName }).mapNotNull { p -> list.firstOrNull { it.packageName == p } } }
        if (dock.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), RoundedCornerShape(28.dp)).padding(vertical = 12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    dock.forEach { AppIcon(it, showLabel = false, onOpen = ::open, onLongPress = ::togglePin) }
                }
            }
            Text(stringResource(R.string.apps_long_press_an_app_to_pin), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
        AppGrid(list, ::open, ::togglePin)
        Text(stringResource(R.string.apps_android), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FocusChip(stringResource(R.string.apps_android_settings), onClick = { start(context, Intent(Settings.ACTION_SETTINGS)) })
            FocusChip(stringResource(R.string.apps_wi_fi), onClick = { start(context, Intent(Settings.ACTION_WIFI_SETTINGS)) })
            FocusChip(stringResource(R.string.apps_bluetooth_controllers), onClick = { start(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) })
            FocusChip(stringResource(R.string.apps_display), onClick = { start(context, Intent(Settings.ACTION_DISPLAY_SETTINGS)) })
            FocusChip(stringResource(R.string.apps_sound), onClick = { start(context, Intent(Settings.ACTION_SOUND_SETTINGS)) })
        }
        LauncherModeCard()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppGrid(apps: List<AndroidApp>, onOpen: (AndroidApp) -> Unit, onLongPress: (AndroidApp) -> Unit) {
    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        apps.forEach { AppIcon(it, showLabel = true, onOpen = onOpen, onLongPress = onLongPress) }
    }
}

/** One app as a phone shows it: a big icon and its name, no card around it. Long press pins it to the dock. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppIcon(app: AndroidApp, showLabel: Boolean, onOpen: (AndroidApp) -> Unit, onLongPress: (AndroidApp) -> Unit) {
    Column(
        Modifier.width(76.dp).combinedClickable(onClick = { onOpen(app) }, onLongClick = { onLongPress(app) }),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        app.icon?.let { Image(it, contentDescription = app.label, modifier = Modifier.size(56.dp)) }
        if (showLabel) {
            Text(app.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** How often each app is opened from here, and the ones pinned to the dock (kept in RetroForge's own settings). */
private object AppUsage {
    private const val PREFS = "android_apps"
    private const val DOCK = 5

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun opened(context: Context, packageName: String) {
        val p = prefs(context)
        p.edit().putInt("count:$packageName", p.getInt("count:$packageName", 0) + 1).apply()
    }

    fun togglePin(context: Context, packageName: String) {
        val p = prefs(context)
        val pinned = pinned(context).toMutableList()
        if (!pinned.remove(packageName)) pinned.add(0, packageName)
        p.edit().putString("pinned", pinned.take(DOCK).joinToString(",")).apply()
    }

    private fun pinned(context: Context) = prefs(context).getString("pinned", "").orEmpty().split(',').filter { it.isNotBlank() }

    /** Pinned apps first, then the most opened, then the phone's usual apps (dialler, messages, browser, camera, store). */
    fun dock(context: Context, installed: List<String>): List<String> {
        val p = prefs(context)
        val used = installed.map { it to p.getInt("count:$it", 0) }.filter { it.second > 0 }.sortedByDescending { it.second }.map { it.first }
        return (pinned(context) + used + usual(context)).filter { it in installed }.distinct().take(DOCK)
    }

    private fun usual(context: Context): List<String> {
        val pm = context.packageManager
        fun handler(intent: Intent) = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            ?.takeIf { it != "android" }
        return listOfNotNull(
            handler(Intent(Intent.ACTION_DIAL)),
            handler(Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:"))),
            handler(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://example.com"))),
            handler(Intent(MediaStore.ACTION_IMAGE_CAPTURE)),
            "com.android.vending",
        )
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
        CardTitle(stringResource(R.string.apps_use_retroforge_as_the_home_screen)) { StatusPill(if (on) stringResource(R.string.apps_offered_to_android) else stringResource(R.string.player_off), if (on) StatusTone.GOOD else StatusTone.NEUTRAL) }
        CardBody(
            stringResource(R.string.apps_for_handhelds_and_tv_boxes_the),
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
            val icon = runCatching { info.loadIcon(pm).toBitmap(144, 144, Bitmap.Config.ARGB_8888).asImageBitmap() }.getOrNull()
            AndroidApp(info.loadLabel(pm).toString(), info.activityInfo.packageName, icon)
        }
        .sortedBy { it.label.lowercase() }
}

private fun start(context: Context, intent: Intent) {
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
