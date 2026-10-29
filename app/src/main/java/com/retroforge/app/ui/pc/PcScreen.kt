package com.retroforge.app.ui.pc

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.app.ui.ScreenFrame
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.StatusPill
import com.retroforge.core.ui.StatusTone
import com.retroforge.pc.DroidDeckBridge
import com.retroforge.pc.PcStatus
import com.retroforge.pc.PcSupportLevel

@Composable
fun PcScreen(viewModel: PcViewModel, bridge: DroidDeckBridge, library: com.retroforge.core.library.LibraryRepository) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    ScreenFrame(title = "PC", subtitle = "Ports of your games, and Windows games through a PC game runtime (a separate app).") {
        PortsSection(library)
        Text("Windows games", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        val loaded = status
        if (loaded == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator()
                Text("Checking this device…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            DeviceCard(loaded)
            DroidDeckCard(loaded, bridge)
            FocusCard(modifier = Modifier.fillMaxWidth()) {
                CardTitle("Launching PC games from RetroForge") { StatusPill("Not built yet", StatusTone.NEUTRAL) }
                CardBody(
                    "Starting a specific Steam game from RetroForge will be added once a test on real hardware " +
                        "confirms the runtime's launch request works. Only Steam games can be started this way; " +
                        "GOG, Epic and Amazon games stay inside the runtime.",
                )
            }
        }
    }
}

/** Other free PC-game apps, for devices DroidDeck doesn't support. Links to their official releases only. */
@Composable
private fun OtherPcAppsCard() {
    val context = LocalContext.current
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle("Other PC game apps")
        CardBody(
            "GameNative (Steam, Epic, GOG and Amazon games you own) and Winlator (Windows games you have installed) are free, separately " +
                "developed apps. Their recent versions and community builds are reported to run some games on Mali and Xclipse GPUs " +
                "too, usually with lower compatibility than on Snapdragon. RetroForge doesn't start their games yet and hasn't tested them.",
        )
    }
    androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
        com.retroforge.core.ui.FocusChip("GameNative releases", onClick = {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, GAMENATIVE_RELEASES.toUri())) }
        })
        com.retroforge.core.ui.FocusChip("Winlator releases", onClick = {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, WINLATOR_RELEASES.toUri())) }
        })
    }
}

private const val GAMENATIVE_RELEASES = "https://github.com/utkarshdalal/GameNative/releases/latest"
private const val WINLATOR_RELEASES = "https://github.com/brunodev85/winlator/releases"

@Composable
private fun DeviceCard(status: PcStatus) {
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle("This device")
        val (label, tone) = when (status.support.level) {
            PcSupportLevel.SUPPORTED -> "Supported GPU" to StatusTone.GOOD
            PcSupportLevel.EXPERIMENTAL -> "Experimental GPU" to StatusTone.CAUTION
            PcSupportLevel.UNSUPPORTED -> "DroidDeck not supported" to StatusTone.CAUTION
            PcSupportLevel.UNKNOWN -> "GPU not identified" to StatusTone.NEUTRAL
        }
        StatusPill(label, tone)
        CardBody(status.support.reason)
        CardBody("GPU: ${status.gpu.glRenderer ?: "unknown"}")
        status.gpu.socModel?.let { CardBody("Chip: $it") }
        CardBody("Based on DroidDeck's published requirements; a supported GPU does not guarantee every game runs.")
    }
}

@Composable
private fun DroidDeckCard(status: PcStatus, bridge: DroidDeckBridge) {
    val context = LocalContext.current
    var openError by remember { mutableStateOf<String?>(null) }
    val install = status.droidDeck
    if (install == null && status.support.level == PcSupportLevel.UNSUPPORTED) {
        // No point sending people to install a runtime their device can't run.
        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle("PC game runtime")
            CardBody(
                "DroidDeck, the PC runtime RetroForge works with, needs a Qualcomm Snapdragon chip (Adreno GPU). " +
                    "Your retro games work as normal.",
            )
        }
        OtherPcAppsCard()
    } else if (install == null) {
        FocusCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                openError = try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, DroidDeckBridge.RELEASES_URL.toUri()))
                    null
                } catch (_: ActivityNotFoundException) {
                    "No browser is installed. Open ${DroidDeckBridge.RELEASES_URL} on another device."
                }
            },
        ) {
            CardTitle("PC game runtime") { StatusPill("Not installed", StatusTone.NEUTRAL) }
            CardBody(
                "RetroForge works with DroidDeck, a free, separately developed app. Install it only from its official releases page: ${DroidDeckBridge.RELEASES_URL}. " +
                    "Select this card to open it in a browser.",
            )
            openError?.let { CardBody(it) }
        }
    } else {
        val openIntent = bridge.openAppIntent()
        FocusCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = openIntent?.let { intent -> { context.startActivity(intent) } },
        ) {
            CardTitle("PC game runtime") { StatusPill("Installed ${install.versionName.orEmpty()}".trim(), StatusTone.GOOD) }
            CardBody(
                if (install.acceptsLaunchIntent) "It accepts game launch requests from other apps."
                else "This version does not accept launch requests from other apps.",
            )
            if (openIntent != null) CardBody("Select this card to open it.")
        }
    }
}
