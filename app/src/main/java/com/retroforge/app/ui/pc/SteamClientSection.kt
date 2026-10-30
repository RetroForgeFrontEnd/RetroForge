package com.retroforge.app.ui.pc

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
        CardTitle("Steam method 2: Steam client")
        CardBody(
            "For games that won't start without Valve's own Steam: their copy protection (like XCOM: Enemy Unknown's) " +
                "asks Steam itself to start them, and they quit. RetroForge spots this and uses this method for them from then on; " +
                "you can also choose it per game with \"Steam method for a game\" above.",
        )
        CardBody(
            "It runs Valve's Windows Steam quietly in the background, so these games take a few minutes longer to start " +
                "and need plenty of memory (8 GB devices manage; close other apps first).",
        )
        val (label, tone) = when {
            ClientSetup.job != null -> "Setting up…" to StatusTone.NEUTRAL
            signedIn -> "Ready, signed in" to StatusTone.GOOD
            installed -> "Set up, not signed in yet" to StatusTone.CAUTION
            else -> "Not set up" to StatusTone.NEUTRAL
        }
        StatusPill(label, tone)
        if (ClientSetup.job != null) {
            LinearProgressIndicator(progress = { ClientSetup.progress }, modifier = Modifier.fillMaxWidth())
            ClientSetup.step?.let { CardBody("$it… ${(ClientSetup.progress * 100).toInt()}%") }
        } else {
            ClientSetup.step?.let { CardBody(it) }
            ChipRow {
                if (!installed) {
                    FocusChip("Set up the Steam client (about 360 MB from Valve)", onClick = {
                        ClientSetup.step = null
                        ClientSetup.job = ClientSetup.scope.launch {
                            ClientSetup.step = runCatching {
                                client.install { progress, step -> ClientSetup.progress = progress; ClientSetup.step = step }
                            }.fold({ "Steam client set up. Now sign in once." }, { "Couldn't set it up: ${it.message}" })
                            ClientSetup.job = null
                            changes++
                        }
                    })
                } else {
                    if (!signedIn) FocusChip("Open Steam to sign in", onClick = {
                        context.startActivity(
                            com.retroforge.pcplayer.PcPlayerActivity.intent(context, client.exe, "Steam client")
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    })
                    FocusChip(if (confirmRemove) "Remove it? Select again" else "Remove the Steam client", onClick = {
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
                    "Steam's sign-in window takes a few minutes to appear. Scan its QR code with the Steam app on another device " +
                        "(it can't scan its own screen), or type with ☰ → Keyboard, and keep Remember me ticked.",
                )
            }
        }
    }
}
