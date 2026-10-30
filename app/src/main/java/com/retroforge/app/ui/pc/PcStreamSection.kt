package com.retroforge.app.ui.pc

import com.retroforge.core.ui.str
import com.retroforge.app.R

import androidx.compose.ui.res.stringResource

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
        message = str(R.string.pc_stream_asking, text)
        scope.launch {
            message = runCatching { streaming.add(text) }.fold(
                onSuccess = { str(R.string.pc_stream_added_pair_it_next, it.name) },
                onFailure = { str(R.string.pc_stream_nothing_answered_at_is_sunshine_running, text, it.message) },
            )
            changes++
        }
    }

    Text(stringResource(R.string.pc_stream_stream_from_your_pc), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.pc_stream_your_pc_s_games_on_this))
        CardBody(
            stringResource(R.string.pc_stream_play_games_running_on_your_own),
        )
        ChipRow {
            FocusChip(if (looking) stringResource(R.string.library_looking) else stringResource(R.string.pc_stream_look_for_pcs), enabled = !looking, onClick = {
                looking = true
                scope.launch {
                    found = runCatching { streaming.discover() }.getOrDefault(emptyList())
                    looking = false
                }
            })
            FocusChip(stringResource(R.string.pc_stream_stream_settings), onClick = { runCatching { context.startActivity(streaming.settingsIntent()) } })
            FocusChip(stringResource(R.string.pc_stream_get_sunshine), onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, SUNSHINE_RELEASES.toUri())) }
            })
        }
        if (looking) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        found?.let { list ->
            val known = hosts.map { it.address }.toSet()
            val fresh = list.filterNot { it.address in known }
            if (list.isEmpty()) CardBody(stringResource(R.string.pc_stream_no_pcs_answered_type_its_address))
            else if (fresh.isEmpty()) CardBody(stringResource(R.string.pc_stream_every_pc_that_answered_is_already))
            else ChipRow { fresh.forEach { pc -> FocusChip(stringResource(R.string.pc_stream_add, pc.name), onClick = { add(pc.address) }) } }
        }
        ControllerTextField(
            value = address, onValueChange = { address = it }, singleLine = true,
            label = { Text(stringResource(R.string.pc_stream_pc_s_address_e_g_192)) }, modifier = Modifier.fillMaxWidth(),
        )
        FocusChip(stringResource(R.string.pc_stream_add_this_pc), enabled = address.isNotBlank(), onClick = { add(address) })
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
                s == null -> StatusPill(stringResource(R.string.pc_stream_checking), StatusTone.NEUTRAL)
                !s.online -> StatusPill(stringResource(R.string.pc_stream_offline), StatusTone.CAUTION)
                s.paired -> StatusPill(stringResource(R.string.pc_stream_ready), StatusTone.GOOD)
                else -> StatusPill(stringResource(R.string.pc_stream_not_paired), StatusTone.NEUTRAL)
            }
        }
        CardBody(stringResource(R.string.pc_stream_address_else, host.address, if (host.port != 47989) ":${host.port}" else ""))
        if (s != null && !s.online) CardBody(stringResource(R.string.pc_stream_it_didn_t_answer_check_the))
        pin?.let {
            CardBody(
                stringResource(R.string.pc_stream_pin_on_the_pc_open_sunshine, it, it),
            )
        }
        ChipRow {
            if (s?.online == true && !s.paired && pin == null) FocusChip(stringResource(R.string.pc_stream_pair), onClick = {
                val newPin = PcStreaming.newPin()
                pin = newPin
                note = null
                scope.launch {
                    val result = runCatching { streaming.pair(host, newPin) }
                    pin = null
                    note = result.fold(
                        onSuccess = { state ->
                            when (state) {
                                PairState.PAIRED -> str(R.string.pc_stream_paired)
                                PairState.PIN_WRONG -> str(R.string.pc_stream_the_pc_says_the_pin_was)
                                PairState.ALREADY_IN_PROGRESS -> str(R.string.pc_stream_the_pc_is_already_pairing_with)
                                else -> str(R.string.pc_stream_pairing_didn_t_work_on_the)
                            }
                        },
                        onFailure = { str(R.string.pc_stream_pairing_stopped, it.message) },
                    )
                    onChanged()
                }
            })
            if (s?.paired == true) FocusChip(if (apps == null) stringResource(R.string.pc_stream_show_games) else stringResource(R.string.pc_stream_refresh_games), onClick = {
                scope.launch {
                    note = null
                    apps = runCatching { streaming.apps(host) }.onFailure { note = str(R.string.pc_stream_couldn_t_get_the_list, it.message) }.getOrNull()
                }
            })
            if (s?.online == true && s.runningAppId != 0) FocusChip(stringResource(R.string.pc_stream_quit_the_running_game), onClick = {
                scope.launch {
                    note = runCatching { streaming.quit(host) }.fold(
                        onSuccess = { if (it) str(R.string.pc_stream_the_game_on_the_pc_was) else str(R.string.pc_stream_the_pc_didn_t_close_it) },
                        onFailure = { str(R.string.pc_stream_the_pc_didn_t_close_it_2, it.message) },
                    )
                    refresh++
                }
            })
            FocusChip(stringResource(R.string.rom_manager_check_again), onClick = { refresh++ })
            if (host.paired) FocusChip(stringResource(R.string.pc_stream_unpair), onClick = { scope.launch { streaming.unpair(host); onChanged() } })
            FocusChip(stringResource(R.string.pc_stream_forget), onClick = { streaming.remove(host); onChanged() })
        }
        note?.let { CardBody(it) }
        apps?.let { list ->
            if (list.isEmpty()) CardBody(stringResource(R.string.pc_stream_the_pc_lists_no_apps_add))
            else {
                CardBody(stringResource(R.string.pc_stream_choose_one_to_stream_full_screen))
                ChipRow {
                    list.forEach { app ->
                        val running = s?.runningAppId == app.id
                        FocusChip(if (running) stringResource(R.string.pc_stream_resume, app.name) else app.name, onClick = {
                            runCatching { context.startActivity(streaming.streamIntent(host, app)) }
                                .onFailure { note = str(R.string.pc_stream_couldn_t_start_the_stream, it.message) }
                        })
                    }
                }
            }
        }
    }
}

private const val SUNSHINE_RELEASES = "https://github.com/LizardByte/Sunshine/releases/latest"
