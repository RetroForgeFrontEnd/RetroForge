package com.retroforge.app.ui.pc

import com.retroforge.app.R
import androidx.compose.ui.res.stringResource
import com.retroforge.core.ui.str
import android.content.Intent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.app.ui.ChipRow
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.FocusChip
import com.retroforge.core.ui.StatusPill
import com.retroforge.core.ui.StatusTone
import com.retroforge.pcplayer.steam.WindowsSteamClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** A Steam client set-up in progress lives here, not in the card, which goes when it scrolls away. */
private object ClientSetup {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    var job by mutableStateOf<Job?>(null)
    var progress by mutableStateOf(0f)
    var step by mutableStateOf<String?>(null)
}

/**
 * Steam method 2: Valve's own Windows Steam client, in RetroForge's PC player, for the games that won't start without
 * it. Set up here (downloaded from Valve), signed in once in Steam's own window, then it runs quietly whenever such a
 * game starts.
 */
@Composable
fun SteamClientSection() {
    val context = LocalContext.current
    val client = remember { WindowsSteamClient(context.applicationContext) }
    var changes by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { changes++ }
    val installed = remember(changes, ClientSetup.job) { client.installed }
    val signedIn = remember(changes, ClientSetup.job) { installed && client.signedIn }
    var confirmRemove by remember { mutableStateOf(false) }

    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.steam_client_steam_method_2_steam_client))
        CardBody(
            str(R.string.steam_client_for_games_that_won_t_start),
        )
        CardBody(
            str(R.string.steam_client_it_runs_valve_s_windows_steam),
        )
        val (label, tone) = when {
            ClientSetup.job != null -> str(R.string.steam_client_setting_up) to StatusTone.NEUTRAL
            signedIn -> str(R.string.steam_client_ready_signed_in) to StatusTone.GOOD
            installed -> str(R.string.steam_client_set_up_not_signed_in_yet) to StatusTone.CAUTION
            else -> str(R.string.steam_client_not_set_up) to StatusTone.NEUTRAL
        }
        StatusPill(label, tone)
        if (ClientSetup.job != null) {
            LinearProgressIndicator(progress = { ClientSetup.progress }, modifier = Modifier.fillMaxWidth())
            ClientSetup.step?.let { CardBody("$it… ${(ClientSetup.progress * 100).toInt()}%") }
        } else {
            ClientSetup.step?.let { CardBody(it) }
            ChipRow {
                if (!installed) {
                    FocusChip(stringResource(R.string.steam_client_set_up_the_steam_client_about), onClick = {
                        ClientSetup.step = null
                        ClientSetup.job = ClientSetup.scope.launch {
                            ClientSetup.step = runCatching {
                                client.install { progress, step -> ClientSetup.progress = progress; ClientSetup.step = step }
                            }.fold({ str(R.string.steam_client_steam_client_set_up_now_sign) }, { str(R.string.steam_client_couldn_t_set_it_up, it.message) })
                            ClientSetup.job = null
                            changes++
                        }
                    })
                } else {
                    if (!signedIn) FocusChip(stringResource(R.string.steam_client_open_steam_to_sign_in), onClick = {
                        context.startActivity(
                            com.retroforge.pcplayer.PcPlayerActivity.intent(context, client.exe, str(R.string.steam_client_steam_client))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    })
                    FocusChip(if (confirmRemove) stringResource(R.string.steam_client_remove_it_select_again) else stringResource(R.string.steam_client_remove_the_steam_client), onClick = {
                        if (!confirmRemove) {
                            confirmRemove = true
                        } else {
                            client.remove()
                            confirmRemove = false
                            changes++
                        }
                    })
                }
            }
            if (installed && !signedIn) {
                CardBody(
                    str(R.string.steam_client_steam_s_sign_in_window_takes),
                )
            }
        }
    }
}
