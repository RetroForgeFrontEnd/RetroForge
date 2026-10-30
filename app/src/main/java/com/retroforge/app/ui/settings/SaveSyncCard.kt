package com.retroforge.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.retroforge.app.network.NetworkDrives
import com.retroforge.app.player.SaveSync
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.app.ui.ChipRow
import com.retroforge.core.ui.ControllerTextField
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.FocusChip
import com.retroforge.core.ui.StatusPill
import com.retroforge.core.ui.StatusTone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Save syncing: the built-in player's saves (and states) kept the same on every device, through a folder on a network
 * drive or an Android folder a cloud app keeps in step. Saves come in when a game starts and go out when it's quit.
 */
@Composable
fun SaveSyncCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { SaveSync.settings(context) }
    var changes by remember { mutableIntStateOf(0) }
    val on = remember(changes) { prefs.getBoolean("on", false) }
    val kind = remember(changes) { prefs.getString("kind", null) }
    val states = remember(changes) { prefs.getBoolean("states", true) }
    val drives = remember { NetworkDrives.all(context) }
    var folderName by remember { mutableStateOf(prefs.getString("folder", null) ?: "RetroForge saves") }
    var progress by remember { mutableStateOf<Float?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    fun edit(block: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).apply()
        changes++
    }

    fun syncNow() {
        val sync = SaveSync.of(context) ?: return
        progress = 0f
        status = "Syncing your saves…"
        scope.launch {
            status = withContext(Dispatchers.IO) {
                runCatching { sync.sync(onProgress = { p -> progress = p }) }.fold(
                    onSuccess = { r ->
                        if (r.pulled + r.pushed == 0) "Everything's already in step."
                        else "Done: ${r.pulled} brought to this device, ${r.pushed} sent to the sync folder." +
                            if (r.conflicts > 0) " ${r.conflicts} had changed on both sides: the newer was kept, the other is in the \"sync conflicts\" folder." else ""
                    },
                    onFailure = { "Couldn't sync: ${it.message}" },
                )
            }
            progress = null
        }
    }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }
        edit { putBoolean("on", true); putString("kind", "folder"); putString("uri", uri.toString()) }
        syncNow()
    }

    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle("Save syncing") { StatusPill(if (on) "On" else "Off", if (on) StatusTone.GOOD else StatusTone.NEUTRAL) }
        CardBody(
            "Keeps your in-game saves (and save states) the same on all your devices. Choose a folder that every device can " +
                "reach: one on a network drive (your PC), or a folder on this device that a cloud app keeps in step " +
                "(Syncthing, FolderSync, Autosync for Google Drive, Nextcloud, OneDrive …). A game's saves come in when it " +
                "starts and go back when you quit. If a save changed on two devices, the newer one is kept and the other is " +
                "put in the \"sync conflicts\" folder. Nothing is ever deleted. Built-in player only.",
        )
        val where = when {
            !on -> null
            kind == "network" -> drives.firstOrNull { it.id == prefs.getString("share", null) }?.let { "${it.name} → ${prefs.getString("folder", "")}" }
            kind == "folder" -> prefs.getString("uri", null)?.let { Uri.parse(it).lastPathSegment?.substringAfter(':')?.ifBlank { "the folder you chose" } }
            else -> null
        }
        where?.let { CardBody("Syncing with: $it") }
        progress?.let { p ->
            CardBody("${(p * 100).toInt()}%")
            LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
        }
        status?.let { CardBody(it) }
        if (drives.isNotEmpty()) {
            ControllerTextField(
                value = folderName, onValueChange = { folderName = it }, singleLine = true,
                label = { Text("Folder on the network drive") }, modifier = Modifier.fillMaxWidth(),
            )
            ChipRow {
                drives.forEach { share ->
                    FocusChip("Use ${share.name}", onClick = {
                        edit {
                            putBoolean("on", true); putString("kind", "network"); putString("share", share.id)
                            putString("folder", folderName.trim().trim('/', '\\'))
                        }
                        syncNow()
                    })
                }
            }
        }
        ChipRow {
            FocusChip(if (kind == "folder" && on) "Choose a different folder" else "Use a folder on this device", onClick = { pickFolder.launch(null) })
            if (on) {
                FocusChip("Sync now", enabled = progress == null, onClick = ::syncNow)
                FocusChip(if (states) "Save states too: on" else "Save states too: off", onClick = { edit { putBoolean("states", !states) } })
                FocusChip("Turn off", onClick = { edit { putBoolean("on", false) }; status = null })
            }
        }
        if (drives.isEmpty()) CardBody("To sync through your PC, add it first under Games and library → Game folders → Network drives.")
    }
}
