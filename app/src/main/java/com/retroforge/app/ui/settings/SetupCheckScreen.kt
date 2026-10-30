package com.retroforge.app.ui.settings

import com.retroforge.app.R

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.app.ui.ScreenFrame
import com.retroforge.core.library.LibraryRepository
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.FocusChip
import com.retroforge.core.ui.StatusPill
import com.retroforge.core.ui.StatusTone
import com.retroforge.library.BiosChecker
import com.retroforge.library.BiosNeed
import com.retroforge.player.CoreManager
import com.retroforge.player.CoreState
import com.retroforge.player.PlayerCores
import kotlinx.coroutines.launch
import java.io.File

/**
 * A check of every system in the library before playing: its games, whether the built-in player's emulator is ready,
 * and whether the BIOS it needs is where the player looks. One line per system, problems first.
 */
@Composable
fun SetupCheckScreen(library: LibraryRepository, cores: CoreManager, biosDir: File, onOpenSetup: () -> Unit) {
    val counts by library.systemCounts.collectAsStateWithLifecycle(initialValue = emptyList())
    val states by cores.states.collectAsStateWithLifecycle()
    val bios = remember { BiosChecker.bundled() }
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    val present = remember(refresh) { biosDir.walkTopDown().filter { it.isFile }.map { it.name.lowercase() }.toSet() }

    data class Line(val system: String, val name: String, val games: Int, val tone: StatusTone, val verdict: String, val detail: String, val coreId: String?)

    val lines = counts.map { count ->
        val core = PlayerCores.forSystem(count.system)
        val coreState = core?.let { states[it.id] ?: cores.state(it.id) }
        val rules = bios.rulesFor(listOf(count.system))
        // "Required" only when this system's emulator really needs it (PCSX ReARMed, for one, has its own replacement).
        val strict = core?.needsBios ?: true
        val missing = rules.filter { it.files.none { f -> f.baseName.lowercase() in present } }
        val missingRequired = missing.filter { strict && it.need == BiosNeed.REQUIRED }
        val missingOther = missing - missingRequired.toSet()
        val name = library.systemName(count.system)
        when {
            core == null -> Line(count.system, name, count.games, StatusTone.NEUTRAL, "Plays in another app",
                "Not in the built-in player yet: RetroArch or a standalone emulator (on each game's page).", null)
            coreState is CoreState.Failed -> Line(count.system, name, count.games, StatusTone.BAD, "Emulator didn't download",
                "${core.name}: ${coreState.reason}. Select Download again.", core.id)
            coreState is CoreState.Downloading -> Line(count.system, name, count.games, StatusTone.CAUTION, "Emulator downloading",
                "${core.name}: ${coreState.percent}%", core.id)
            coreState == CoreState.Missing -> Line(count.system, name, count.games, StatusTone.CAUTION, "Emulator not downloaded yet",
                "${core.name} downloads in the background, or select Download now.", core.id)
            missingRequired.isNotEmpty() -> Line(count.system, name, count.games, StatusTone.BAD, "BIOS missing",
                "Needs one of: " + missingRequired.flatMap { it.files.map { f -> f.baseName } }.distinct().joinToString() +
                    ". Put it in your BIOS folder and run Set up RetroForge, which copies it across.", null)
            missingOther.isNotEmpty() -> Line(count.system, name, count.games, StatusTone.GOOD, "Ready",
                "${core.name}. Optional BIOS for better results: " + missingOther.flatMap { it.files.map { f -> f.baseName } }.distinct().joinToString() + ".", null)
            else -> Line(count.system, name, count.games, StatusTone.GOOD, "Ready", "${core.name}, everything in place.", null)
        }
    }.sortedWith(compareBy({ it.tone != StatusTone.BAD }, { it.tone != StatusTone.CAUTION }, { it.name }))

    ScreenFrame(title = stringResource(R.string.settings_check_my_setup), subtitle = stringResource(R.string.setup_check_every_system_you_have_games_for)) {
        val problems = lines.count { it.tone == StatusTone.BAD || it.tone == StatusTone.CAUTION }
        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle(if (problems == 0) "All set" else "$problems ${if (problems == 1) "system needs" else "systems need"} a look")
            CardBody("${lines.size} systems, ${lines.sumOf { it.games }} games.")
        }
        lines.forEach { line ->
            FocusCard(modifier = Modifier.fillMaxWidth()) {
                CardTitle(line.name) { StatusPill(line.verdict, line.tone) }
                CardBody("${line.games} ${if (line.games == 1) "game" else "games"}. ${line.detail}")
                line.coreId?.let { id ->
                    FocusChip(stringResource(R.string.setup_check_download_now), onClick = { scope.launch { cores.ensure(id); refresh++ } })
                }
                if (line.verdict == "BIOS missing") FocusChip(stringResource(R.string.settings_set_up_retroforge), onClick = onOpenSetup)
            }
        }
    }
}
