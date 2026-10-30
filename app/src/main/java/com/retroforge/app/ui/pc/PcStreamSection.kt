package com.retroforge.app.ui.pc

import android.content.Intent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import com.limelight.nvstream.http.PairingManager.PairState
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.app.ui.ChipRow
import com.retroforge.core.ui.ControllerTextField
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.FocusChip
import com.retroforge.core.ui.StatusPill
import com.retroforge.core.ui.StatusTone
import com.retroforge.stream.FoundHost
import com.retroforge.stream.HostStatus
import com.retroforge.stream.PcStreaming
import com.retroforge.stream.StreamApp
import com.retroforge.stream.StreamHost
import kotlinx.coroutines.launch

/**
 * Streaming games from the user's own PC (running Sunshine, or GeForce Experience's GameStream) over Moonlight's
 * protocol: find or add the PC, pair it with a PIN, list its apps, and stream one full screen.
 */
@Composable
fun PcStreamSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val streaming = remember { PcStreaming(context) }
    var changes by remember { mutableIntStateOf(0) }
    val hosts = remember(changes) { streaming.hosts() }
    var address by remember { mutableStateOf("") }
    var looking by remember { mutableStateOf(false) }
    var found by remember { mutableStateOf<List<FoundHost>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    fun add(text: String) {
        message = "Asking $text…"
        scope.launch {
            message = runCatching { streaming.add(text) }.fold(
                onSuccess = { "Added ${it.name}. Pair it next." },
                onFailure = { "Nothing answered at $text. Is Sunshine running on the PC, on the same network? (${it.message})" },
            )
            changes++
        }
    }

    Text("Stream from your PC", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle("Your PC's games on this screen")
        CardBody(
            "Play games running on your own Windows, Linux or Mac computer here, with your controller or touch. The PC needs " +
                "Sunshine, a free streaming host (or an NVIDIA graphics card with GeForce Experience's GameStream), on the same " +
                "network. RetroForge streams with Moonlight's open-source code built in, so there's no other app to install here.",
        )
        ChipRow {
            FocusChip(if (looking) "Looking…" else "Look for PCs", enabled = !looking, onClick = {
                looking = true
                scope.launch {
                    found = runCatching { streaming.discover() }.getOrDefault(emptyList())
                    looking = false
                }
            })
            FocusChip("Stream settings", onClick = { runCatching { context.startActivity(streaming.settingsIntent()) } })
            FocusChip("Get Sunshine", onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, SUNSHINE_RELEASES.toUri())) }
            })
        }
        if (looking) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        found?.let { list ->
            val known = hosts.map { it.address }.toSet()
            val fresh = list.filterNot { it.address in known }
            if (list.isEmpty()) CardBody("No PCs answered. Type its address below instead (on the PC: Settings > Network shows its IP address).")
            else if (fresh.isEmpty()) CardBody("Every PC that answered is already in your list.")
            else ChipRow { fresh.forEach { pc -> FocusChip("Add ${pc.name}", onClick = { add(pc.address) }) } }
        }
        ControllerTextField(
            value = address, onValueChange = { address = it }, singleLine = true,
            label = { Text("PC's address, e.g. 192.168.1.20") }, modifier = Modifier.fillMaxWidth(),
        )
        FocusChip("Add this PC", enabled = address.isNotBlank(), onClick = { add(address) })
        message?.let { CardBody(it) }
    }
    hosts.forEach { host -> key(host.uuid) { HostCard(host, streaming, onChanged = { changes++ }) } }
}

@Composable
private fun HostCard(host: StreamHost, streaming: PcStreaming, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<HostStatus?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var pin by remember { mutableStateOf<String?>(null) }
    var apps by remember { mutableStateOf<List<StreamApp>?>(null) }
    var note by remember { mutableStateOf<String?>(null) }

    // A new host value (paired, unpaired) asks the PC again; the card and its messages stay.
    LaunchedEffect(refresh, host) { status = streaming.status(host) }

    FocusCard(modifier = Modifier.fillMaxWidth()) {
        val s = status
        CardTitle(host.name) {
            when {
                s == null -> StatusPill("Checking…", StatusTone.NEUTRAL)
                !s.online -> StatusPill("Offline", StatusTone.CAUTION)
                s.paired -> StatusPill("Ready", StatusTone.GOOD)
                else -> StatusPill("Not paired", StatusTone.NEUTRAL)
            }
        }
        CardBody("Address: ${host.address}${if (host.port != 47989) ":${host.port}" else ""}")
        if (s != null && !s.online) CardBody("It didn't answer. Check the PC is on, awake and running Sunshine.")
        pin?.let {
            CardBody(
                "PIN: $it\nOn the PC, open Sunshine's page (https://localhost:47990), go to PIN, and type $it with any device " +
                    "name. With GeForce Experience, type it in the box that appears. Waiting for the PC…",
            )
        }
        ChipRow {
            if (s?.online == true && !s.paired && pin == null) FocusChip("Pair", onClick = {
                val newPin = PcStreaming.newPin()
                pin = newPin
                note = null
                scope.launch {
                    val result = runCatching { streaming.pair(host, newPin) }
                    pin = null
                    note = result.fold(
                        onSuccess = { state ->
                            when (state) {
                                PairState.PAIRED -> "Paired."
                                PairState.PIN_WRONG -> "The PC says the PIN was wrong. Try again."
                                PairState.ALREADY_IN_PROGRESS -> "The PC is already pairing with something. Finish or cancel that, then try again."
                                else -> "Pairing didn't work. On the PC, check Sunshine is running and try again."
                            }
                        },
                        onFailure = { "Pairing stopped: ${it.message}" },
                    )
                    onChanged()
                }
            })
            if (s?.paired == true) FocusChip(if (apps == null) "Show games" else "Refresh games", onClick = {
                scope.launch {
                    note = null
                    apps = runCatching { streaming.apps(host) }.onFailure { note = "Couldn't get the list: ${it.message}" }.getOrNull()
                }
            })
            if (s?.online == true && s.runningAppId != 0) FocusChip("Quit the running game", onClick = {
                scope.launch {
                    note = runCatching { streaming.quit(host) }.fold(
                        onSuccess = { if (it) "The game on the PC was closed." else "The PC didn't close it." },
                        onFailure = { "The PC didn't close it: ${it.message}" },
                    )
                    refresh++
                }
            })
            FocusChip("Check again", onClick = { refresh++ })
            if (host.paired) FocusChip("Unpair", onClick = { scope.launch { streaming.unpair(host); onChanged() } })
            FocusChip("Forget", onClick = { streaming.remove(host); onChanged() })
        }
        note?.let { CardBody(it) }
        apps?.let { list ->
            if (list.isEmpty()) CardBody("The PC lists no apps. Add some in Sunshine's Applications page.")
            else {
                CardBody("Choose one to stream full screen. Press back (or Start+Select+L1+R1 on a controller) to stop streaming; the game keeps running on the PC until you quit it.")
                ChipRow {
                    list.forEach { app ->
                        val running = s?.runningAppId == app.id
                        FocusChip(if (running) "Resume ${app.name}" else app.name, onClick = {
                            runCatching { context.startActivity(streaming.streamIntent(host, app)) }
                                .onFailure { note = "Couldn't start the stream: ${it.message}" }
                        })
                    }
                }
            }
        }
    }
}

private const val SUNSHINE_RELEASES = "https://github.com/LizardByte/Sunshine/releases/latest"
