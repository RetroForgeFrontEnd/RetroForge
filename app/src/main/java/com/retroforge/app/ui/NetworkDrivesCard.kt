package com.retroforge.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
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
fun NetworkDrivesCard(what: String) {
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

    fun draft() = NetworkShare(NetworkDrives.newId(), name.trim().ifBlank { folder.trim().substringAfterLast('/') }, host.trim(),
        folder.trim(), user.trim(), password)

    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle("$what on a network drive")
        CardBody(
            "Play ${what.lowercase()} straight from a shared folder on a PC or NAS. Add the share here, then choose a folder as " +
                "usual: it's listed in Android's folder picker under RetroForge's network drives. Over Wi-Fi, so a fast " +
                "connection helps.",
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
            ControllerTextField(value = host, onValueChange = { host = it }, singleLine = true,
                label = { Text("PC or NAS name or address (e.g. 192.168.1.20)") }, modifier = Modifier.fillMaxWidth())
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
                FocusChip("Test and save", enabled = host.isNotBlank() && folder.isNotBlank(), onClick = {
                    val share = draft()
                    status = "Connecting to ${share.address}…"
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { runCatching { NetworkDrives.test(share) } }
                        result.onSuccess { count ->
                            NetworkDrives.save(context, share)
                            status = "Connected: ${share.address} has $count items. Choose it in the folder picker now."
                            adding = false; host = ""; folder = ""; user = ""; password = ""; name = ""
                            changes++
                        }.onFailure {
                            NetworkDrives.remove(context, share.id)
                            status = "Couldn't connect: ${it.message ?: it.javaClass.simpleName}. Check the address, folder name and login."
                        }
                    }
                })
                FocusChip("Cancel", onClick = { adding = false; status = null })
            }
        }
        status?.let { CardBody(it) }
    }
}
