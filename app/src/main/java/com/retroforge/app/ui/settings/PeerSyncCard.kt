package com.retroforge.app.ui.settings

import com.retroforge.app.R
import com.retroforge.core.ui.str
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.retroforge.app.player.FoundPeer
import com.retroforge.app.player.NotPairedException
import com.retroforge.app.player.PeerSync
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
 * Settings → Playing: the built-in player's saves and memory cards swapped with the user's other devices on the same
 * Wi-Fi, no network drive needed. Devices pair once with a code; after that newer saves go both ways whenever
 * RetroForge opens on both.
 */
@Composable
fun PeerSyncCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val changes = PeerSync.changes
    val on = remember(changes) { PeerSync.isOn(context) }
    val peers = remember(changes) { PeerSync.peers(context) }
    var code by remember { mutableStateOf<String?>(null) }
    var found by remember { mutableStateOf<List<FoundPeer>?>(null) }
    var pairingWith by remember { mutableStateOf<FoundPeer?>(null) }
    var typed by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }

    // The code only works while it's on screen.
    DisposableEffect(Unit) { onDispose { PeerSync.closePairing() } }
    // A device paired with this one while the code was showing: the code is used up.
    LaunchedEffect(peers.size) { code = null }

    fun work(block: suspend () -> String?) {
        busy = true
        scope.launch {
            status = runCatching { block() }.getOrElse { str(R.string.peer_sync_failed, it.message) }
            busy = false
        }
    }

    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.peer_sync_title)) { StatusPill(if (on) stringResource(R.string.settings_on) else stringResource(R.string.player_off), if (on) StatusTone.GOOD else StatusTone.NEUTRAL) }
        CardBody(stringResource(R.string.peer_sync_body))
        if (on) CardBody(stringResource(R.string.peer_sync_this_device, PeerSync.myName(context)))
        status?.let { CardBody(it) }
        ChipRow {
            FocusChip(if (on) stringResource(R.string.save_sync_turn_off) else stringResource(R.string.peer_sync_turn_on), onClick = {
                PeerSync.setOn(context, !on)
                code = null; found = null; pairingWith = null; status = null
            })
            if (on) {
                FocusChip(stringResource(R.string.peer_sync_show_code), onClick = {
                    code = PeerSync.openPairing()
                    status = if (code == null) str(R.string.peer_sync_not_running) else null
                })
                FocusChip(stringResource(R.string.peer_sync_pair_with), enabled = !busy, onClick = {
                    pairingWith = null
                    work {
                        found = withContext(Dispatchers.IO) { PeerSync.find(context, 2500) }.filter { f -> peers.none { it.id == f.id } }
                        if (found.isNullOrEmpty()) str(R.string.peer_sync_none_found) else null
                    }
                })
                if (peers.isNotEmpty()) FocusChip(stringResource(R.string.peer_sync_swap_now), enabled = !busy, onClick = {
                    work {
                        val results = withContext(Dispatchers.IO) { PeerSync.syncAll(context) }
                        if (results.isEmpty()) str(R.string.peer_sync_none_open)
                        else results.joinToString("\n") { r ->
                            val report = r.report
                            when {
                                r.error is NotPairedException -> str(R.string.peer_sync_unpaired, r.name)
                                r.error != null -> str(R.string.peer_sync_with_failed, r.name, r.error.message)
                                report!!.pulled + report.pushed == 0 -> str(R.string.peer_sync_in_step, r.name)
                                else -> str(R.string.peer_sync_toast, r.name, report.pulled, report.pushed) +
                                    if (report.conflicts > 0) str(R.string.save_sync_had_changed_on_both_sides_the, report.conflicts) else ""
                            }
                        }
                    }
                })
            }
        }
    }
    if (!on) return
    code?.let {
        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle(it)
            CardBody(stringResource(R.string.peer_sync_code_body, PeerSync.myName(context)))
        }
    }
    found?.takeIf { pairingWith == null }?.forEach { f ->
        FocusCard(modifier = Modifier.fillMaxWidth(), onClick = { pairingWith = f; typed = "" }) {
            Text(f.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            CardBody(stringResource(R.string.peer_sync_tap_to_pair, f.host))
        }
    }
    pairingWith?.let { f ->
        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle(stringResource(R.string.peer_sync_pairing_with, f.name))
            CardBody(stringResource(R.string.peer_sync_enter_code, f.name))
            ControllerTextField(value = typed, onValueChange = { v -> typed = v.filter(Char::isDigit).take(6) }, singleLine = true,
                label = { Text(stringResource(R.string.peer_sync_code)) }, modifier = Modifier.fillMaxWidth())
            ChipRow {
                FocusChip(stringResource(R.string.peer_sync_pair), enabled = !busy && typed.length == 6, onClick = {
                    work {
                        if (withContext(Dispatchers.IO) { PeerSync.pair(context, f, typed) }) {
                            pairingWith = null
                            found = null
                            str(R.string.peer_sync_paired, f.name)
                        } else str(R.string.peer_sync_wrong_code)
                    }
                })
                FocusChip(stringResource(R.string.peer_sync_cancel), onClick = { pairingWith = null })
            }
        }
    }
    if (peers.isNotEmpty()) CardBody(stringResource(R.string.peer_sync_paired_devices))
    peers.forEach { peer ->
        FocusCard(modifier = Modifier.fillMaxWidth()) {
            Text(peer.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            ChipRow { FocusChip(stringResource(R.string.peer_sync_forget), onClick = { PeerSync.forget(context, peer) }) }
        }
    }
}
