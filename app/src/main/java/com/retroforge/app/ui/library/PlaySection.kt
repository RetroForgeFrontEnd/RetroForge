package com.retroforge.app.ui.library

import com.retroforge.app.R

import androidx.compose.ui.res.stringResource

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retroforge.app.player.Playtime
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.core.library.GameEntity
import com.retroforge.core.library.RootEntity
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.FocusChip
import com.retroforge.core.ui.StatusPill
import com.retroforge.core.ui.StatusTone
import com.retroforge.library.StandaloneEmulator
import com.retroforge.library.StandaloneOption
import com.retroforge.player.CoreState
import com.retroforge.player.PlayerCores
import com.retroforge.retro.LaunchPlan
import com.retroforge.retro.LaunchProblem
import com.retroforge.retro.RetroArchCores
import com.retroforge.retro.RetroArchLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
fun PlaySection(viewModel: LibraryViewModel, game: GameEntity, root: RootEntity?) {
    val context = LocalContext.current
    // Re-check after returning from RetroArch or the store, in case RetroArch was just installed.
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumes++ }
    val plan = remember(game, root, resumes) { viewModel.launchPlan(game, root) }
    val play by remember(game.id) { viewModel.playFor(game.id) }.collectAsStateWithLifecycle(initialValue = null)
    var problem by remember(game.id) { mutableStateOf<LaunchProblem?>(null) }
    val choices by viewModel.emulatorChoices.collectAsStateWithLifecycle()
    val standalone = remember(game.system) { viewModel.standaloneOptions(game.system) }
    val chosen = (choices[game.system]?.let { id -> standalone.firstOrNull { it.second.id == id } }
        ?.takeIf { remember(it, resumes) { viewModel.installedComponent(it.second) } != null })
        // Newer systems: an installed standalone app plays them better than the experimental built-in player.
        ?: if (game.system in com.retroforge.player.PlayerCores.experimentalSystems && choices[game.system] == null)
            standalone.firstOrNull { viewModel.installedComponent(it.second) != null } else null

    val builtIn = remember(game.id, game.fileName) { viewModel.builtInCore(game) }?.takeIf { root != null }
    val retroArchChosen = choices[game.system] == RETROARCH_CHOICE
    // The built-in player is the main Play button wherever it can run the game, unless another emulator was chosen.
    val builtInMain = builtIn != null && chosen == null && !retroArchChosen
    if (builtIn != null && root != null && builtInMain) BuiltInCard(viewModel, game, root, builtIn, main = true) { problem = it }

    // The system's chosen standalone emulator comes first, as the main Play button.
    if (chosen != null) {
        val (option, emulator) = chosen
        FocusCard(modifier = Modifier.fillMaxWidth(), onClick = { problem = viewModel.playStandalone(game, root, option, emulator) }) {
            CardTitle(stringResource(R.string.play_play))
            CardBody("In ${option.label}, your choice for ${viewModel.systemName(game.system)}.")
        }
    }

    when (plan) {
        is LaunchPlan.Ready -> {
            plan.cores.forEachIndexed { index, core ->
                FocusCard(modifier = Modifier.fillMaxWidth(), onClick = { problem = viewModel.play(game, plan, core) }) {
                    CardTitle(if (index == 0 && chosen == null && !builtInMain) "Play" else "Play in RetroArch with ${core.name}")
                    if (index == 0) CardBody("In RetroArch with the ${core.name} core.")
                    if (core.id in RetroArchCores.needsBios) CardBody(stringResource(R.string.play_this_core_needs_bios_files_in))
                }
            }
            FocusCard(modifier = Modifier.fillMaxWidth()) {
                play?.let { CardBody("Played ${plural(it.playCount, "time")}, last on ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it.lastPlayedAt))}.") }
                CardBody(
                    "First time with a core? If RetroArch says the core is missing, open RetroArch → Online Updater → Core Downloader and get " +
                        "\"${plan.cores.first().name}\". To come back here, quit the game from RetroArch's menu.",
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FocusChip(stringResource(R.string.play_open_retroarch), onClick = { viewModel.openRetroArch(plan) })
                if (builtIn != null && !retroArchChosen) FocusChip(stringResource(R.string.play_use_retroarch_by_default), onClick = { viewModel.setEmulatorChoice(game.system, RETROARCH_CHOICE) })
            }
        }
        // With the built-in player (or a chosen emulator app) there, RetroArch is optional: no "can't play" card.
        is LaunchPlan.NotPossible if builtIn != null || chosen != null -> Unit
        is LaunchPlan.NotPossible -> FocusCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = if (plan.reason == LaunchProblem.RetroArchNotInstalled) {
                {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, RetroArchLauncher.DOWNLOAD_URL.toUri()))
                    } catch (_: ActivityNotFoundException) {
                        problem = LaunchProblem.StartFailed("No browser installed. Get RetroArch from ${RetroArchLauncher.DOWNLOAD_URL}")
                    }
                }
            } else null,
        ) {
            CardTitle(stringResource(R.string.play_play)) { StatusPill(stringResource(R.string.play_can_t_play_yet), StatusTone.CAUTION) }
            CardBody(describe(plan.reason, viewModel))
        }
    }
    if (builtIn != null && root != null && !builtInMain) {
        BuiltInCard(viewModel, game, root, builtIn, main = false) { problem = it }
        if (retroArchChosen || chosen != null) FocusChip(stringResource(R.string.play_use_the_built_in_player_by), onClick = { viewModel.setEmulatorChoice(game.system, null) })
    }
    if (standalone.isNotEmpty()) StandaloneSection(viewModel, game, root, standalone, choices[game.system], resumes) { problem = it }

    problem?.let { p ->
        FocusCard(modifier = Modifier.fillMaxWidth()) {
            StatusPill(stringResource(R.string.play_problem), StatusTone.BAD)
            CardBody(describe(p, viewModel))
        }
    }
}

private const val RETROARCH_CHOICE = "RETROARCH"

@Composable
private fun BuiltInCard(
    viewModel: LibraryViewModel,
    game: GameEntity,
    root: RootEntity,
    core: PlayerCores.PlayerCore,
    main: Boolean,
    onProblem: (LaunchProblem?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val states by viewModel.coreStates.collectAsStateWithLifecycle()
    val state = states[core.id] ?: viewModel.coreState(core.id)
    // Read again each time this page comes back, e.g. after quitting the game.
    var played by remember { mutableLongStateOf(0L) }
    LifecycleResumeEffect(game.id) {
        val job = scope.launch {
            played = withContext(Dispatchers.IO) { Playtime(File(context.filesDir, "player/playtime.properties")).get(game.system, game.fileName) }
        }
        onPauseOrDispose { job.cancel() }
    }
    FocusCard(modifier = Modifier.fillMaxWidth(), onClick = {
        onProblem(null)
        scope.launch {
            val intent = viewModel.builtInIntent(context, game, root, core)
            if (intent != null) context.startActivity(intent)
            else onProblem(LaunchProblem.StartFailed("Couldn't download the ${core.name} emulator. Check the internet connection and try again."))
        }
    }) {
        CardTitle(if (main) "Play" else "Play in RetroForge")
        CardBody(
            when (state) {
                CoreState.Ready -> "In RetroForge's built-in player, with ${core.name}."
                is CoreState.Downloading -> "Getting the ${core.name} emulator… ${state.percent}%"
                CoreState.Missing -> "In RetroForge's built-in player. The ${core.name} emulator (under 2 MB) downloads first."
                is CoreState.Failed -> "Couldn't download the ${core.name} emulator last time (${state.reason}). Select to try again."
            },
        )
        if (core.needsBios) CardBody("${core.name} needs this system's BIOS file.")
        if (game.system == "saturn") CardBody(stringResource(R.string.play_saturn_in_the_built_in_player))
        if (played > 0) CardBody("Time played here: ${Playtime.format(played)}.")
    }
    if (main && com.retroforge.app.player.Netplay.supports(game.system)) {
        // Netplay: the same game on two devices, one hosting (player 1), the other joining (player 2).
        fun start(how: String) {
            onProblem(null)
            scope.launch {
                val intent = viewModel.builtInIntent(context, game, root, core, netplay = how)
                if (intent != null) context.startActivity(intent)
                else onProblem(LaunchProblem.StartFailed("Couldn't download the ${core.name} emulator. Check the internet connection and try again."))
            }
        }
        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle(stringResource(R.string.play_play_together))
            CardBody(stringResource(R.string.play_two_players_on_two_devices_each))
            com.retroforge.app.ui.ChipRow {
                com.retroforge.core.ui.FocusChip(stringResource(R.string.play_host), onClick = { start(com.retroforge.app.player.PlayerActivity.NETPLAY_HOST) })
                com.retroforge.core.ui.FocusChip(stringResource(R.string.play_join), onClick = { start(com.retroforge.app.player.PlayerActivity.NETPLAY_FIND) })
            }
        }
    }
}

private fun describe(problem: LaunchProblem, viewModel: LibraryViewModel): String = when (problem) {
    LaunchProblem.RetroArchNotInstalled ->
        "RetroForge plays games through RetroArch, a free app installed separately. Select to open RetroArch's official download page (${RetroArchLauncher.DOWNLOAD_URL})."
    is LaunchProblem.SystemNotSupported ->
        "RetroArch can't start ${viewModel.systemName(problem.system)} games from RetroForge. Use one of the emulator apps below (Get installs it), or Settings → Help and about → How to use RetroForge → Other emulators."
    LaunchProblem.LocationNotSupported ->
        "This game is in a location RetroArch can't open directly (for example a cloud drive). Games on internal storage, an SD card or a USB drive work."
    is LaunchProblem.StartFailed -> problem.detail
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StandaloneSection(
    viewModel: LibraryViewModel,
    game: GameEntity,
    root: RootEntity?,
    options: List<Pair<StandaloneOption, StandaloneEmulator>>,
    choice: String?,
    resumes: Int,
    onProblem: (LaunchProblem?) -> Unit,
) {
    val multiFile = game.fileName.substringAfterLast('.').lowercase() in setOf("cue", "m3u", "gdi", "ccd", "toc")
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.play_other_emulators))
        CardBody("Free standalone apps that play ${viewModel.systemName(game.system)} games. RetroForge doesn't include them: install the ones you want.")
        if (multiFile) CardBody(stringResource(R.string.play_this_game_is_made_of_several))
        if (!viewModel.hasObtainium()) CardBody(stringResource(R.string.play_tip_obtainium_installs_emulators_from_their))
    }
    options.forEach { (option, emulator) ->
        val installed = remember(emulator.id, resumes) { viewModel.installedComponent(emulator) } != null
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (installed) {
                FocusChip("Play with ${option.label}", onClick = { onProblem(viewModel.playStandalone(game, root, option, emulator)) })
                if (choice == emulator.id) FocusChip(stringResource(R.string.play_stop_using_by_default), onClick = { viewModel.setEmulatorChoice(game.system, null) })
                else FocusChip(stringResource(R.string.play_use_by_default), onClick = { viewModel.setEmulatorChoice(game.system, emulator.id) })
            } else {
                FocusChip("Get ${emulator.name} (free)", onClick = {
                    if (!viewModel.getEmulator(emulator)) onProblem(LaunchProblem.StartFailed("Couldn't open ${emulator.page}"))
                })
            }
        }
    }
}
