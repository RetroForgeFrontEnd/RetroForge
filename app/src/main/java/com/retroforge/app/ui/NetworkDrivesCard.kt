package com.retroforge.app.ui

import com.retroforge.core.ui.pluralStr
import com.retroforge.core.ui.str
import com.retroforge.app.R

import androidx.compose.ui.res.stringResource

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
fun NetworkDrivesCard(what: String, onUse: (suspend (android.net.Uri, (String, Float?) -> Unit) -> String)? = null) {
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
    // The pop-up while games are being added: what it's doing, how far (0..1) and roughly how long is left.
    var popup by remember { mutableStateOf<Triple<String, Float?, String?>?>(null) }
    var popupHidden by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<List<com.retroforge.app.network.FoundComputer>?>(null) }
    var searching by remember { mutableStateOf(false) }
    var folders by remember { mutableStateOf<List<String>?>(null) }
    var folderStatus by remember { mutableStateOf<String?>(null) }
    // The computer's shared folders, to pick from instead of typing a name.
    fun listFolders() {
        val target = host.trim()
        if (target.isBlank()) return
        folders = null
        folderStatus = str(R.string.network_drives_looking_for_shared_folders_on, target)
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { NetworkDrives.sharedFolders(target, user.trim(), password) } }
            result.onSuccess { list ->
                folders = list
                folderStatus = if (list.isEmpty()) str(R.string.network_drives_doesn_t_share_any_folders_yet, target) else null
                if (list.size == 1 && folder.isBlank()) folder = list.single()
            }.onFailure {
                folderStatus = if (it is java.net.SocketTimeoutException || it is java.net.ConnectException || it.cause is java.net.ConnectException)
                    str(R.string.network_drives_couldn_t_reach_check_it_s, target)
                else str(R.string.network_drives_couldn_t_list_its_folders_enter, it.message)
            }
        }
    }

    fun draft() = NetworkShare(NetworkDrives.newId(), name.trim().ifBlank { folder.trim().substringAfterLast('/') }, host.trim(),
        folder.trim(), user.trim(), password)

    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.network_drives_on_a_network_drive, what))
        CardBody(
            stringResource(R.string.network_drives_play_kept_in_a_shared_folder, what.lowercase()),
        )
        if (shares.isNotEmpty()) CardBody("Added: " + shares.joinToString { "${it.name} (${it.address})" })
        if (!adding) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FocusChip(stringResource(R.string.network_drives_add_a_network_drive), onClick = { adding = true; status = null })
                shares.forEach { share ->
                    FocusChip(stringResource(R.string.network_drives_remove, share.name), onClick = { NetworkDrives.remove(context, share.id); changes++ })
                }
                FocusChip(stringResource(R.string.network_drives_get_tailscale), onClick = {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://play.google.com/store/apps/details?id=com.tailscale.ipn"))
                    runCatching { context.startActivity(intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                })
            }
            CardBody(stringResource(R.string.network_drives_away_from_home))
        } else {
            // Find the PC or NAS instead of typing its address.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FocusChip(if (searching) stringResource(R.string.network_drives_searching_your_network) else stringResource(R.string.network_drives_find_on_my_network), enabled = !searching, onClick = {
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
            if (found?.isEmpty() == true) CardBody(stringResource(R.string.network_drives_nothing_sharing_files_found_on_this))
            ControllerTextField(value = host, onValueChange = { host = it }, singleLine = true,
                label = { Text(stringResource(R.string.network_drives_pc_or_nas_name_or_address)) }, modifier = Modifier.fillMaxWidth())
            if (host.isNotBlank()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FocusChip(stringResource(R.string.network_drives_show_shared_folders), onClick = { listFolders() })
                    folders.orEmpty().forEach { f ->
                        FocusChip((if (folder == f) "● " else "") + f, onClick = { folder = f; if (name.isBlank()) name = f })
                    }
                }
                if (onUse != null && folders?.isNotEmpty() == true) {
                    CardBody(stringResource(R.string.network_drives_pick_a_shared_folder_above_then))
                    androidx.compose.foundation.layout.Spacer(Modifier.height(8.dp))
                }
                folderStatus?.let { CardBody(it) }
            }
            ControllerTextField(value = folder, onValueChange = { folder = it }, singleLine = true,
                label = { Text(stringResource(R.string.network_drives_shared_folder_e_g_games_or)) }, modifier = Modifier.fillMaxWidth())
            ControllerTextField(value = user, onValueChange = { user = it }, singleLine = true,
                label = { Text(stringResource(R.string.network_drives_username_blank_for_a_guest_share)) }, modifier = Modifier.fillMaxWidth())
            ControllerTextField(value = password, onValueChange = { password = it }, singleLine = true, label = { Text(stringResource(R.string.settings_password)) },
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth())
            ControllerTextField(value = name, onValueChange = { name = it }, singleLine = true,
                label = { Text(stringResource(R.string.network_drives_name_to_show_optional)) }, modifier = Modifier.fillMaxWidth())
            CardBody(stringResource(R.string.network_drives_the_password_stays_on_this_device))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FocusChip(if (showPassword) stringResource(R.string.network_drives_hide_password) else stringResource(R.string.network_drives_show_password), onClick = { showPassword = !showPassword })
                FocusChip(if (onUse != null) stringResource(R.string.network_drives_add_to_my, what.lowercase()) else stringResource(R.string.network_drives_test_and_save), enabled = host.isNotBlank() && folder.isNotBlank(), onClick = {
                    val share = draft()
                    status = str(R.string.network_drives_connecting_to, share.address)
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { runCatching { NetworkDrives.test(share) } }
                        result.onSuccess { count ->
                            NetworkDrives.save(context, share)
                            status = if (onUse != null) str(R.string.network_drives_connected_to_looking_for_your_a, share.address, what.lowercase())
                            else str(R.string.network_drives_connected_has_items_choose_it_in, share.address, count)
                            // The confirmation once it's scanned: how many games (or films) were found there.
                            if (onUse != null) {
                                val started = System.currentTimeMillis()
                                popupHidden = false
                                popup = Triple(str(R.string.network_drives_connecting_to, share.address), null, null)
                                status = onUse(NetworkDrives.folderUri(context, share)) { text, fraction ->
                                    val left = fraction?.takeIf { it > 0.05f }?.let { f ->
                                        val seconds = ((System.currentTimeMillis() - started) / 1000f * (1 - f) / f).toInt()
                                        if (seconds < 60) str(R.string.network_drives_less_than_a_minute_left) else pluralStr(R.plurals.network_drives_about_minutes_left, (seconds + 30) / 60, (seconds + 30) / 60)
                                    }
                                    if (!popupHidden) popup = Triple(text, fraction, left)
                                }
                                popup = null
                            }
                            adding = false; host = ""; folder = ""; user = ""; password = ""; name = ""
                            changes++
                        }.onFailure {
                            NetworkDrives.remove(context, share.id)
                            status = str(R.string.network_drives_couldn_t_connect, it.message ?: it.javaClass.simpleName)
                        }
                    }
                })
                FocusChip(stringResource(R.string.network_drives_cancel), onClick = { adding = false; status = null })
            }
        }
        status?.let { CardBody(it) }
    }
    popup?.let { (text, fraction, left) ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.network_drives_adding_your, what.lowercase())) },
            text = {
                androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text)
                    if (fraction != null) androidx.compose.material3.LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                    else androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(left ?: stringResource(R.string.network_drives_working_out_how_long), style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
                }
            },
            // It carries on in the background; the result shows on the card when it's done.
            confirmButton = { androidx.compose.material3.TextButton(onClick = { popup = null; popupHidden = true }) { Text(stringResource(R.string.network_drives_hide)) } },
        )
    }
}
