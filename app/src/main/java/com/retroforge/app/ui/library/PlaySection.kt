package com.retroforge.app.ui.library

import com.retroforge.core.ui.str
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
import com.retroforge.app.player.SaveStates
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
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
    val kioskGuard = com.retroforge.app.ui.rememberKioskGuard()
    val context = LocalContext.current
    // Re-check after returning from RetroArch or the store, in case RetroArch was just installed.
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumes++ }
    val plan = remember(game, root, resumes) { viewModel.launchPlan(game, root) }
    val play by remember(game.id) { viewModel.playFor(game.id) }.collectAsStateWithLifecycle(initialValue = null)
    var problem by remember(game.id) { mutableStateOf<LaunchProblem?>(null) }
    val choices by viewModel.emulatorChoices.collectAsStateWithLifecycle()
    val standalone = remember(game.system) { viewModel.standaloneOptions(game.system) }
    val builtIn = remember(game.id, game.fileName) { viewModel.builtInCore(game) }?.takeIf { root != null }
    val chosen = (choices[game.system]?.let { id -> standalone.firstOrNull { it.second.id == id } }
        ?.takeIf { remember(it, resumes) { viewModel.installedComponent(it.second) } != null })
        // Newer systems: an installed standalone app plays them better than the experimental built-in player. And where
        // nothing else can (Switch: no built-in player, RetroArch can't), an installed app is simply the Play button.
        ?: if (choices[game.system] == null &&
            (game.system in com.retroforge.player.PlayerCores.experimentalSystems || builtIn == null && plan is LaunchPlan.NotPossible)
        ) standalone.firstOrNull { viewModel.installedComponent(it.second) != null } else null
    val retroArchChosen = choices[game.system] == RETROARCH_CHOICE
    // The built-in player is the main Play button wherever it can run the game, unless another emulator was chosen.
    val builtInMain = builtIn != null && chosen == null && !retroArchChosen
    if (builtIn != null && root != null && builtInMain) BuiltInCard(viewModel, game, root, builtIn, main = true) { problem = it }

    // The system's chosen standalone emulator comes first, as the main Play button.
    if (chosen != null) {
        val (option, emulator) = chosen
        FocusCard(modifier = Modifier.fillMaxWidth(), onClick = { viewModel.playStandalone(context, game, root, option, emulator) { problem = it } }) {
            CardTitle(stringResource(R.string.play_play))
            CardBody(stringResource(
                if (choices[game.system] == emulator.id) R.string.play_in_your_choice_for else R.string.play_in_app_installed_for,
                option.label, viewModel.systemName(game.system),
            ))
        }
    }

    // Copy progress and problems right under the Play buttons, not below the long list of other emulators.
    val copying by viewModel.copying.collectAsStateWithLifecycle()
    copying?.takeIf { it.first == game.id }?.let { (_, percent) ->
        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardBody(stringResource(R.string.play_copying_from_network, percent))
            androidx.compose.material3.LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth())
        }
    }
    problem?.let { p ->
        FocusCard(modifier = Modifier.fillMaxWidth()) {
            StatusPill(stringResource(R.string.play_problem), StatusTone.BAD)
            CardBody(describe(p, viewModel))
        }
    }

    when (plan) {
        is LaunchPlan.Ready -> {
            plan.cores.forEachIndexed { index, core ->
                FocusCard(modifier = Modifier.fillMaxWidth(), onClick = { problem = viewModel.play(game, plan, core) }) {
                    CardTitle(if (index == 0 && chosen == null && !builtInMain) stringResource(R.string.play_play) else stringResource(R.string.play_play_in_retroarch_with, core.name))
                    if (index == 0) CardBody(stringResource(R.string.play_in_retroarch_with_the_core, core.name))
                    if (core.id in RetroArchCores.needsBios) CardBody(stringResource(R.string.play_this_core_needs_bios_files_in))
                }
            }
            FocusCard(modifier = Modifier.fillMaxWidth()) {
                play?.let { CardBody(stringResource(R.string.play_played_last_on, plural(it.playCount, "time"), DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it.lastPlayedAt)))) }
                CardBody(
                    stringResource(R.string.play_first_time_with_a_core_if, plan.cores.first().name),
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FocusChip(stringResource(R.string.play_open_retroarch), onClick = { viewModel.openRetroArch(plan) })
                if (builtIn != null && !retroArchChosen) FocusChip(stringResource(R.string.play_use_retroarch_by_default), onClick = { kioskGuard { viewModel.setEmulatorChoice(game.system, RETROARCH_CHOICE) } })
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
                        problem = LaunchProblem.StartFailed(str(R.string.play_no_browser_installed_get_retroarch_from, RetroArchLauncher.DOWNLOAD_URL))
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
        if (retroArchChosen || chosen != null) FocusChip(stringResource(R.string.play_use_the_built_in_player_by), onClick = { kioskGuard { viewModel.setEmulatorChoice(game.system, null) } })
    }
    if (standalone.isNotEmpty()) StandaloneSection(viewModel, game, root, standalone, choices[game.system], resumes) { problem = it }

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
            else onProblem(LaunchProblem.StartFailed(str(R.string.play_couldn_t_download_the_emulator_check, core.name)))
        }
    }) {
        CardTitle(if (main) stringResource(R.string.play_play) else stringResource(R.string.play_play_in_retroforge))
        CardBody(
            when (state) {
                CoreState.Ready -> str(R.string.play_in_retroforge_s_built_in_player, core.name)
                is CoreState.Downloading -> str(R.string.play_getting_the_emulator, core.name, state.percent)
                CoreState.Missing -> str(R.string.play_in_retroforge_s_built_in_player_2, core.name)
                is CoreState.Failed -> str(R.string.play_couldn_t_download_the_emulator_last, core.name, state.reason)
            },
        )
        if (core.needsBios) CardBody(stringResource(R.string.play_needs_this_system_s_bios_file, core.name))
        if (game.system == "saturn") CardBody(stringResource(R.string.play_saturn_in_the_built_in_player))
        if (played > 0) CardBody(stringResource(R.string.play_time_played_here, Playtime.format(played)))
    }
    SaveStatesCard(game, core) { state ->
        onProblem(null)
        scope.launch {
            val intent = viewModel.builtInIntent(context, game, root, core, loadState = state.file)
            if (intent != null) context.startActivity(intent)
            else onProblem(LaunchProblem.StartFailed(str(R.string.play_couldn_t_download_the_emulator_check, core.name)))
        }
    }
    if (main && com.retroforge.app.player.Netplay.supports(game.system)) {
        // Netplay: the same game on two devices, one hosting (player 1), the other joining (player 2).
        fun start(how: String) {
            onProblem(null)
            scope.launch {
                val intent = viewModel.builtInIntent(context, game, root, core, netplay = how)
                if (intent != null) context.startActivity(intent)
                else onProblem(LaunchProblem.StartFailed(str(R.string.play_couldn_t_download_the_emulator_check, core.name)))
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

/**
 * The game's save states from the built-in player, newest first, with their pictures: play from one (states made with
 * this emulator), rename or delete. Read again each time the page comes back.
 */
@Composable
private fun SaveStatesCard(game: GameEntity, core: PlayerCores.PlayerCore, onPlay: (SaveStates.State) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var states by remember(game.id) { mutableStateOf<List<SaveStates.State>>(emptyList()) }
    var changes by remember { mutableIntStateOf(0) }
    var renaming by remember { mutableStateOf<SaveStates.State?>(null) }
    var deleting by remember { mutableStateOf<File?>(null) }
    val dir = remember(game.system) { SaveStates.dir(context, game.system) }
    val base = game.fileName.substringBeforeLast('.')
    LifecycleResumeEffect(game.id, changes) {
        val job = scope.launch { states = withContext(Dispatchers.IO) { SaveStates.list(dir, base) } }
        onPauseOrDispose { job.cancel() }
    }
    if (states.isEmpty()) return
    val dates = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.states_title))
        CardBody(stringResource(R.string.states_card_body))
        states.forEach { s ->
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                if (s.picture.isFile) coil3.compose.AsyncImage(
                    s.picture, null,
                    Modifier.size(width = 112.dp, height = 84.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)),
                )
                androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                    CardTitle(SaveStates.label(context, s))
                    CardBody(dates.format(Date(s.modified)))
                    if (s.core != core.id) CardBody(stringResource(R.string.states_other_emulator, PlayerCores.byId(s.core)?.name ?: s.core))
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (s.core == core.id) FocusChip(stringResource(R.string.states_play_from_here), onClick = { onPlay(s) })
                FocusChip(stringResource(R.string.files_rename), onClick = { renaming = s })
                FocusChip(stringResource(if (deleting == s.file) R.string.states_delete_sure else R.string.files_delete), onClick = {
                    if (deleting == s.file) {
                        scope.launch { withContext(Dispatchers.IO) { SaveStates.delete(s) }; deleting = null; changes++ }
                    } else deleting = s.file
                })
            }
        }
    }
    renaming?.let { s ->
        var name by remember(s.file) { mutableStateOf(s.name ?: SaveStates.label(context, s)) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { renaming = null },
            title = { androidx.compose.material3.Text(stringResource(R.string.files_rename)) },
            text = {
                com.retroforge.core.ui.ControllerTextField(value = name, onValueChange = { name = it.take(60) }, singleLine = true,
                    label = { androidx.compose.material3.Text(stringResource(R.string.states_name)) }, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                FocusChip(stringResource(R.string.settings_save), onClick = {
                    scope.launch { withContext(Dispatchers.IO) { SaveStates.rename(s, name) }; renaming = null; changes++ }
                })
            },
            dismissButton = { FocusChip(stringResource(R.string.files_cancel), onClick = { renaming = null }) },
        )
    }
}

private fun describe(problem: LaunchProblem, viewModel: LibraryViewModel): String = when (problem) {
    LaunchProblem.RetroArchNotInstalled ->
        str(R.string.play_retroforge_plays_games_through_retroarch_a, RetroArchLauncher.DOWNLOAD_URL)
    is LaunchProblem.SystemNotSupported ->
        str(R.string.play_retroarch_can_t_start_games_from, viewModel.systemName(problem.system))
    LaunchProblem.LocationNotSupported ->
        str(R.string.play_this_game_is_in_a_location)
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
    val kioskGuard = com.retroforge.app.ui.rememberKioskGuard()
    val context = LocalContext.current
    val multiFile = game.fileName.substringAfterLast('.').lowercase() in setOf("cue", "m3u", "gdi", "ccd", "toc")
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.play_other_emulators))
        CardBody(stringResource(R.string.play_free_standalone_apps_that_play_games, viewModel.systemName(game.system)))
        if (multiFile) CardBody(stringResource(R.string.play_this_game_is_made_of_several))
        if (!viewModel.hasObtainium()) CardBody(stringResource(R.string.play_tip_obtainium_installs_emulators_from_their))
    }
    options.forEach { (option, emulator) ->
        val installed = remember(emulator.id, resumes) { viewModel.installedComponent(emulator) } != null
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (installed) {
                FocusChip(stringResource(R.string.play_play_with, option.label), onClick = { viewModel.playStandalone(context, game, root, option, emulator, onProblem) })
                if (choice == emulator.id) FocusChip(stringResource(R.string.play_stop_using_by_default), onClick = { kioskGuard { viewModel.setEmulatorChoice(game.system, null) } })
                else FocusChip(stringResource(R.string.play_use_by_default), onClick = { kioskGuard { viewModel.setEmulatorChoice(game.system, emulator.id) } })
            } else {
                FocusChip(stringResource(R.string.play_get_free, emulator.name), onClick = {
                    if (!viewModel.getEmulator(emulator)) onProblem(LaunchProblem.StartFailed(str(R.string.play_couldn_t_open, emulator.page)))
                })
            }
        }
    }
}
