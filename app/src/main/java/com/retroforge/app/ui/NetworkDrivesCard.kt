package com.retroforge.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.retroforge.app.network.NetworkDrives
import com.retroforge.app.network.NetworkShare
import com.retroforge.core.ui.ControllerTextField
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.FocusChip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Network drives built into RetroForge: shared folders on a PC or NAS (SMB). Once added, a share is listed in Android's
 * folder picker as one of RetroForge's drives, so it can be a game folder or a films folder like any other.
 * [what] is "Games" or "Films", for the wording.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NetworkDrivesCard(what: String, onUse: (suspend (android.net.Uri) -> String)? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var changes by remember { mutableIntStateOf(0) }
    val shares = remember(changes) { NetworkDrives.all(context) }
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var folder by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var found by remember { mutableStateOf<List<com.retroforge.app.network.FoundComputer>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var folders by remember { mutableStateOf<List<String>?>(null) }
    var folderStatus by remember { mutableStateOf<String?>(null) }
    // The computer's shared folders, to pick from instead of typing a name.
    fun listFolders() {
        val target = host.trim()
        if (target.isBlank()) return
        folders = null
        folderStatus = "Looking for shared folders on $target…"
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { NetworkDrives.sharedFolders(target, user.trim(), password) } }
            result.onSuccess { list ->
                folders = list
                folderStatus = if (list.isEmpty()) "$target doesn't share any folders yet. Share one first (Settings → How to use RetroForge → network drives)." else null
                if (list.size == 1 && folder.isBlank()) folder = list.single()
            }.onFailure {
                folderStatus = if (it is java.net.SocketTimeoutException || it is java.net.ConnectException || it.cause is java.net.ConnectException)
                    "Couldn't reach $target. Check it's on, and on the same network as this device."
                else "Couldn't list its folders: ${it.message} Enter the username and password, then Show shared folders."
            }
        }
    }

    fun draft() = NetworkShare(NetworkDrives.newId(), name.trim().ifBlank { folder.trim().substringAfterLast('/') }, host.trim(),
        folder.trim(), user.trim(), password)

    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle("$what on a network drive")
        CardBody(
            "Play ${what.lowercase()} straight from a shared folder on a PC or NAS, over Wi-Fi. Add a network drive, tap Find " +
                "on my network, pick your computer and one of its shared folders, then Add: RetroForge does the rest. " +
                "How to share a folder on the PC: Settings → Help and about → How to use RetroForge.",
        )
        if (shares.isNotEmpty()) CardBody("Added: " + shares.joinToString { "${it.name} (${it.address})" })
        if (!adding) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FocusChip("Add a network drive", onClick = { adding = true; status = null })
                shares.forEach { share ->
                    FocusChip("Remove ${share.name}", onClick = { NetworkDrives.remove(context, share.id); changes++ })
                }
            }
        } else {
            // Find the PC or NAS instead of typing its address.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FocusChip(if (searching) "Searching your network…" else "Find on my network", enabled = !searching, onClick = {
                    searching = true
                    scope.launch {
                        found = com.retroforge.app.network.NetworkSearch.find(context)
                        searching = false
                    }
                })
                found.orEmpty().forEach { computer ->
                    FocusChip((if (host == computer.address) "● " else "") + (computer.name?.let { "$it (${computer.address})" } ?: computer.address),
                        onClick = { host = computer.address; if (name.isBlank()) name = computer.name ?: ""; listFolders() })
                }
            }
            if (found?.isEmpty() == true) CardBody("Nothing sharing files found on this Wi-Fi. Check the PC is on, on the same network, and has a shared folder.")
            ControllerTextField(value = host, onValueChange = { host = it }, singleLine = true,
                label = { Text("PC or NAS name or address (e.g. 192.168.1.20)") }, modifier = Modifier.fillMaxWidth())
            if (host.isNotBlank()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FocusChip("Show shared folders", onClick = { listFolders() })
                    folders.orEmpty().forEach { f ->
                        FocusChip((if (folder == f) "● " else "") + f, onClick = { folder = f; if (name.isBlank()) name = f })
                    }
                }
                if (onUse != null && folders?.isNotEmpty() == true) {
                    CardBody("Pick a shared folder above, then add it. For a folder inside a share, type it after a slash (Retrobat/roms).")
                    androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                }
                folderStatus?.let { CardBody(it) }
            }
            ControllerTextField(value = folder, onValueChange = { folder = it }, singleLine = true,
                label = { Text("Shared folder (e.g. Games, or Media/Films)") }, modifier = Modifier.fillMaxWidth())
            ControllerTextField(value = user, onValueChange = { user = it }, singleLine = true,
                label = { Text("Username (blank for a guest share)") }, modifier = Modifier.fillMaxWidth())
            ControllerTextField(value = password, onValueChange = { password = it }, singleLine = true, label = { Text("Password") },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth())
            ControllerTextField(value = name, onValueChange = { name = it }, singleLine = true,
                label = { Text("Name to show (optional)") }, modifier = Modifier.fillMaxWidth())
            CardBody("The password stays on this device and is only sent to that PC or NAS.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FocusChip(if (showPassword) "Hide password" else "Show password", onClick = { showPassword = !showPassword })
                FocusChip(if (onUse != null) "Add to my ${what.lowercase()}" else "Test and save", enabled = host.isNotBlank() && folder.isNotBlank(), onClick = {
                    val share = draft()
                    status = "Connecting to ${share.address}…"
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { runCatching { NetworkDrives.test(share) } }
                        result.onSuccess { count ->
                            NetworkDrives.save(context, share)
                            status = if (onUse != null) "Connected to ${share.address}. Looking for your ${what.lowercase()}… (a big collection can take a few minutes)"
                            else "Connected: ${share.address} has $count items. Choose it in the folder picker now."
                            // The confirmation once it's scanned: how many games (or films) were found there.
                            if (onUse != null) status = onUse(NetworkDrives.folderUri(context, share))
                            adding = false; host = ""; folder = ""; user = ""; password = ""; name = ""
                            changes++
                        }.onFailure {
                            NetworkDrives.remove(context, share.id)
                            status = "Couldn't connect: ${it.message ?: it.javaClass.simpleName}"
                        }
                    }
                })
                FocusChip("Cancel", onClick = { adding = false; status = null })
            }
        }
        status?.let { CardBody(it) }
    }
}
