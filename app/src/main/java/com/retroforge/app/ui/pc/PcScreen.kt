package com.retroforge.app.ui.pc

import com.retroforge.app.R

import androidx.compose.ui.res.stringResource

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

    ScreenFrame(title = stringResource(R.string.home_pc), subtitle = stringResource(R.string.pc_your_pc_games_cloud_gaming_ports)) {
        PcFold("Your PC games", "Steam, Epic, GOG and Amazon games through GameNative; Windows games through Winlator.", startOpen = true) {
            PcGamesSection(library)
            SteamSignInSection()
        }
        PcFold("Stream from your PC", "Play the games on your own PC from this device, with Moonlight and Sunshine.") {
            PcStreamSection()
        }
        Text(stringResource(R.string.pc_cloud_gaming), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        XboxCloudCard()
        CloudAppsCard()
        PcFold("Ports", "Games rebuilt for Android (and Windows-only ports) that use your own copy of the game.") {
            PortsSection(library)
        }
        PcFold("Free games", "${FreeGames.all.size} shareware, freeware and homebrew games to download and play.") {
            FreeGamesSection(library)
        }
        PcFold("Windows games runtime", "Whether this device can run Windows games, and the DroidDeck app.") {
            val loaded = status
            if (loaded == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.pc_checking_this_device), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                DeviceCard(loaded)
                DroidDeckCard(loaded, bridge)
            }
        }
    }
}

/** Other free PC-game apps, for devices DroidDeck doesn't support. Links to their official releases only. */
@Composable
private fun OtherPcAppsCard() {
    val context = LocalContext.current
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.pc_other_pc_game_apps))
        CardBody(
            stringResource(R.string.pc_gamenative_steam_epic_gog_and_amazon),
        )
    }
    com.retroforge.app.ui.ChipRow {
        com.retroforge.core.ui.FocusChip(stringResource(R.string.pc_gamenative_releases), onClick = {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, GAMENATIVE_RELEASES.toUri())) }
        })
        com.retroforge.core.ui.FocusChip(stringResource(R.string.pc_winlator_releases), onClick = {
            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, WINLATOR_RELEASES.toUri())) }
        })
    }
}

private const val GAMENATIVE_RELEASES = "https://github.com/utkarshdalal/GameNative/releases/latest"
private const val WINLATOR_RELEASES = "https://github.com/brunodev85/winlator/releases"

@Composable
private fun DeviceCard(status: PcStatus) {
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.pc_this_device))
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
        CardBody(stringResource(R.string.pc_based_on_droiddeck_s_published_requirements))
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
            CardTitle(stringResource(R.string.pc_pc_game_runtime))
            CardBody(
                stringResource(R.string.pc_droiddeck_the_pc_runtime_retroforge_works),
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
            CardTitle(stringResource(R.string.pc_pc_game_runtime)) { StatusPill(stringResource(R.string.pc_not_installed), StatusTone.NEUTRAL) }
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
            CardTitle(stringResource(R.string.pc_pc_game_runtime)) { StatusPill("Installed ${install.versionName.orEmpty()}".trim(), StatusTone.GOOD) }
            CardBody(
                if (install.acceptsLaunchIntent) "It accepts game launch requests from other apps."
                else "This version does not accept launch requests from other apps.",
            )
            if (openIntent != null) CardBody(stringResource(R.string.pc_select_this_card_to_open_it))
        }
    }
}
