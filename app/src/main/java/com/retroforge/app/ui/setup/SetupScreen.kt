package com.retroforge.app.ui.setup

import com.retroforge.app.R

import androidx.compose.ui.res.stringResource

import com.retroforge.app.ui.ChipRow
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.retroforge.retro.RetroArchCores
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.app.ui.ScreenFrame
import com.retroforge.app.ui.library.plural
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.FocusChip
import kotlinx.coroutines.launch
import com.retroforge.core.ui.StatusPill
import com.retroforge.core.ui.StatusTone
import com.retroforge.library.BiosNeed
import com.retroforge.library.BiosReport
import com.retroforge.library.BiosStatus
import com.retroforge.retro.RetroArchLauncher

/** Where RetroArch for Android looks for BIOS by default; offered as the picker's starting point. */
private val RETROARCH_SYSTEM_HINT: Uri =
    DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:RetroArch/system")

@Composable
fun SetupScreen(viewModel: SetupViewModel, onOpenSort: () -> Unit = {}, systems: List<Pair<String, String>> = emptyList(), onDone: () -> Unit) {
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val install by viewModel.retroArchInstall.collectAsStateWithLifecycle()
    val bios by viewModel.bios.collectAsStateWithLifecycle()
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    val pickGames = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(viewModel::addGameFolder) }
    val pickBios = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(viewModel::onBiosFolderPicked) }
    val biosScope = androidx.compose.runtime.rememberCoroutineScope()
    val pickRetroArchFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(viewModel::onRetroArchFolderPicked) }

    ScreenFrame(title = stringResource(R.string.settings_set_up_retroforge), subtitle = stringResource(R.string.setup_three_steps_from_your_game_folder)) {
        val s = summary

        // 1. Games
        FocusCard(modifier = Modifier.fillMaxWidth(), onClick = { pickGames.launch(null) }) {
            CardTitle(stringResource(R.string.setup_1_your_games))
            if (s == null || s.folders == 0) {
                StatusPill(stringResource(R.string.setup_to_do), StatusTone.CAUTION)
                CardBody(stringResource(R.string.setup_select_to_choose_your_games_folder))
            } else {
                StatusPill("${plural(s.games, "game")} in ${plural(s.cores.size + s.unsupported.size, "system")}", StatusTone.GOOD)
                CardBody(stringResource(R.string.setup_select_to_add_another_folder))
            }
        }
        // Tidy-up offer: games lying loose instead of in their system's folder.
        val loose by androidx.compose.runtime.produceState(0, s?.games) { value = viewModel.looseGames() }
        if (loose > 0) {
            FocusCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenSort) {
                CardTitle(stringResource(R.string.setup_tidy_your_games_folder)) { StatusPill("${plural(loose, "game")} not in a system folder", StatusTone.CAUTION) }
                CardBody(
                    stringResource(R.string.setup_they_play_fine_where_they_are),
                )
            }
        }

        // 2. Emulators: the built-in player's download by themselves; RetroArch only for systems it can't run yet.
        val coreStates by viewModel.coreStates.collectAsStateWithLifecycle()
        val systemIds = s?.systemIds.orEmpty()
        val builtIn = systemIds.mapNotNull { id -> com.retroforge.player.PlayerCores.forSystem(id)?.let { id to it } }
        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle(stringResource(R.string.setup_2_emulators))
            when {
                s == null || systemIds.isEmpty() -> CardBody(stringResource(R.string.setup_add_your_games_first_retroforge_then))
                builtIn.isEmpty() -> CardBody(stringResource(R.string.setup_retroforge_s_built_in_player_doesn))
                else -> {
                    val cores = builtIn.map { it.second }.distinctBy { it.id }
                    val ready = cores.count { (coreStates[it.id] ?: viewModel.coreState(it.id)) == com.retroforge.player.CoreState.Ready }
                    if (ready == cores.size) StatusPill(stringResource(R.string.pc_stream_ready), StatusTone.GOOD)
                    else StatusPill(stringResource(R.string.setup_getting_of, ready + 1, cores.size), StatusTone.CAUTION)
                    CardBody("Built in, nothing to install: ${builtIn.map { viewModel.systemName(it.first) }.sorted().joinToString()}.")
                    CardBody(stringResource(R.string.setup_retroforge_downloads_these_emulators_for_you))
                    val failed = cores.filter { (coreStates[it.id] ?: viewModel.coreState(it.id)) is com.retroforge.player.CoreState.Failed }
                    if (failed.isNotEmpty()) CardBody("Couldn't download ${failed.joinToString { it.name }} yet (no internet?). RetroForge tries again next time.")
                }
            }
        }

        // RetroArch, only when some systems need it.
        val needRetroArch = systemIds.filter { com.retroforge.player.PlayerCores.forSystem(it) == null && RetroArchCores.coresFor(it).isNotEmpty() }
        if (needRetroArch.isNotEmpty()) {
            FocusCard(modifier = Modifier.fillMaxWidth(), onClick = if (install != null) viewModel::openRetroArch else null) {
                CardTitle("RetroArch, for ${plural(needRetroArch.size, "more system")}")
                val i = install
                if (i == null) StatusPill(stringResource(R.string.pc_not_installed), StatusTone.CAUTION) else StatusPill("Installed ${i.versionName.orEmpty()}".trim(), StatusTone.GOOD)
                CardBody(stringResource(R.string.setup_play_in_retroarch_a_free_emulator, needRetroArch.map(viewModel::systemName).sorted().joinToString()))
                if (i == null) {
                    CardBody(stringResource(R.string.setup_install_it_from_google_play_or))
                } else {
                    CardBody(
                        stringResource(R.string.setup_retroarch_keeps_its_emulators_cores_in),
                    )
                }
            }
            if (install == null) {
                ChipRow {
                    FocusChip(stringResource(R.string.setup_get_it_on_google_play), onClick = {
                        val market = Intent(Intent.ACTION_VIEW, "market://details?id=com.retroarch".toUri())
                        try {
                            context.startActivity(market)
                        } catch (_: ActivityNotFoundException) {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=com.retroarch".toUri())) }
                        }
                    })
                    FocusChip(stringResource(R.string.setup_retroarch_website), onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, RetroArchLauncher.DOWNLOAD_URL.toUri())) }
                    })
                }
            }
        }
        if (s != null && s.unsupported.isNotEmpty()) {
            CardBody("Not playable from RetroForge yet: ${s.unsupported.take(15).joinToString()}${if (s.unsupported.size > 15) " …" else ""}")
        }

        // 4. BIOS
        FocusCard(modifier = Modifier.fillMaxWidth(), onClick = {
            // Check the folder already chosen; else look for one beside the games; else ask.
            if (settings?.biosFolderUri != null) viewModel.recheckBios()
            else biosScope.launch { if (!viewModel.useFoundBiosFolder()) pickBios.launch(null) }
        }) {
            CardTitle(stringResource(R.string.setup_3_bios_files))
            CardBody(
                stringResource(R.string.setup_some_systems_need_bios_files_from),
            )
        }
        if (settings?.biosFolderUri != null) {
            ChipRow {
                FocusChip(if (bios.reports == null) "Scan for BIOS files" else "Scan again", enabled = !bios.checking, onClick = { viewModel.recheckBios() })
                FocusChip(stringResource(R.string.setup_choose_another_bios_folder), onClick = { pickBios.launch(null) })
            }
        }
        if (bios.checking) {
            CardBody(stringResource(R.string.setup_scanning_your_bios_files, (bios.progress * 100).toInt()))
            LinearProgressIndicator(progress = { bios.progress }, modifier = Modifier.fillMaxWidth())
        }
        bios.error?.let { CardBody(it) }
        bios.reports?.let { reports ->
            if (reports.isEmpty()) CardBody(stringResource(R.string.setup_none_of_the_systems_in_your))
            else BiosSummary(reports, viewModel::systemName)
            reports.forEach { report ->
                FocusCard(modifier = Modifier.fillMaxWidth()) {
                    CardTitle(report.rule.systems.joinToString(" / ") { viewModel.systemName(it) })
                    val need = when (report.rule.need) {
                        BiosNeed.REQUIRED -> "required"
                        BiosNeed.RECOMMENDED -> "recommended"
                        BiosNeed.OPTIONAL -> "optional"
                    }
                    when (report.status) {
                        BiosStatus.OK -> StatusPill(stringResource(R.string.setup_found, need), StatusTone.GOOD)
                        BiosStatus.UNVERIFIED -> StatusPill(stringResource(R.string.setup_found_version_not_recognised, need), StatusTone.CAUTION)
                        BiosStatus.PARTIAL -> StatusPill(stringResource(R.string.setup_some_files_missing, need), StatusTone.CAUTION)
                        BiosStatus.MISSING -> StatusPill(stringResource(R.string.setup_missing_2, need), if (report.rule.need == BiosNeed.REQUIRED) StatusTone.BAD else StatusTone.NEUTRAL)
                    }
                    report.found.forEach { f ->
                        CardBody("${f.foundAs}${if (f.foundAs != f.wanted.baseName) " → ${f.wanted.name}" else ""}${if (f.verified) "" else " (unrecognised version)"}")
                    }
                    // A rule that takes any one of many versions (73 PS2 BIOS dumps) says so instead of listing them all.
                    if (report.status == BiosStatus.MISSING) CardBody(
                        if (report.rule.mode == "any" && report.rule.files.size > 4) "Needs one BIOS file for this system, any version (${report.rule.files.size} are recognised)."
                        else "Needs ${report.rule.files.joinToString(if (report.rule.mode == "any") " or " else " and ") { it.baseName }}.",
                    )
                    else if (report.missing.isNotEmpty() && report.rule.mode == "all") CardBody("Missing: ${report.missing.joinToString { it.name }}")
                    if (report.rule.note.isNotBlank()) CardBody(report.rule.note)
                }
            }
            if (reports.any { it.found.isNotEmpty() }) {
                FocusCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = if (bios.copying) null else {
                        {
                            val saved = settings?.retroArchSystemUri
                            if (saved != null) viewModel.copyBios(saved.toUri()) else pickRetroArchFolder.launch(RETROARCH_SYSTEM_HINT)
                        }
                    },
                ) {
                    CardTitle(stringResource(R.string.setup_copy_them_to_retroarch))
                    CardBody(
                        stringResource(R.string.setup_retroarch_reads_bios_from_its_own),
                    )
                    if (settings?.retroArchSystemUri == null) CardBody(stringResource(R.string.setup_you_ll_be_asked_to_choose))
                }
                bios.playerCopy?.takeIf { it.copied.isNotEmpty() || it.alreadyThere.isNotEmpty() || it.failed.isNotEmpty() }?.let { r ->
                    CardBody(
                        "Built-in player: " + listOfNotNull(
                            r.copied.size.takeIf { it > 0 }?.let { "$it BIOS file(s) copied" },
                            r.alreadyThere.size.takeIf { it > 0 }?.let { "$it already there" },
                            r.failed.size.takeIf { it > 0 }?.let { "$it couldn't be copied" },
                        ).joinToString(", ") + ". Nothing to do: RetroForge keeps its own copy.",
                    )
                }
                if (bios.copying) LinearProgressIndicator(Modifier.fillMaxWidth())
                bios.copyResult?.let { r ->
                    FocusCard(modifier = Modifier.fillMaxWidth()) {
                        StatusPill(if (r.failed.isEmpty()) "Done" else "Finished with problems", if (r.failed.isEmpty()) StatusTone.GOOD else StatusTone.CAUTION)
                        if (r.copied.isNotEmpty()) CardBody(stringResource(R.string.setup_copied, r.copied.joinToString()))
                        if (r.alreadyThere.isNotEmpty()) CardBody(stringResource(R.string.setup_already_there_left_alone, r.alreadyThere.joinToString()))
                        r.failed.forEach { (name, why) -> CardBody(stringResource(R.string.setup_couldn_t_copy, name, why)) }
                    }
                }
                if (settings?.retroArchSystemUri != null) {
                    FocusChip(stringResource(R.string.setup_choose_a_different_retroarch_folder), onClick = { pickRetroArchFolder.launch(RETROARCH_SYSTEM_HINT) })
                }
            }
        }

        // 4. Bezels round the game in landscape, for every system the scan found.
        com.retroforge.app.ui.settings.BezelsCard(systems, title = stringResource(R.string.setup_4_bezels_optional))

        FocusCard(modifier = Modifier.fillMaxWidth(), onClick = { viewModel.finish(); onDone() }) {
            CardTitle(stringResource(R.string.setup_done))
            CardBody(stringResource(R.string.setup_you_can_skip_any_step_and))
        }
    }
}

/** What the check found, at a glance: every BIOS your games need, or which systems still lack one and the file. */
@Composable
private fun BiosSummary(reports: List<BiosReport>, systemName: (String) -> String) {
    val missing = reports.filter { it.rule.need == BiosNeed.REQUIRED && (it.status == BiosStatus.MISSING || it.status == BiosStatus.PARTIAL) }
    val optional = reports.count { it.rule.need != BiosNeed.REQUIRED && it.status == BiosStatus.MISSING }
    val names = { r: BiosReport -> r.rule.systems.joinToString(" / ") { systemName(it) } }
    val context = androidx.compose.ui.platform.LocalContext.current
    // A short message when the check finishes, which can take minutes on a network drive.
    androidx.compose.runtime.LaunchedEffect(reports) {
        val text = if (missing.isEmpty()) "BIOS check done: you have every BIOS your games need."
        else "BIOS check done: ${missing.size} system${if (missing.size == 1) "" else "s"} missing a BIOS."
        android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_LONG).show()
    }
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        if (missing.isEmpty()) {
            CardTitle(stringResource(R.string.setup_you_have_every_bios_your_games)) { StatusPill(stringResource(R.string.setup_all_found), StatusTone.GOOD) }
            CardBody(
                if (optional == 0) "Every system in your library has what it needs."
                else "$optional optional one${if (optional == 1) "" else "s"} would make some systems more accurate; see below.",
            )
        } else {
            CardTitle("Missing BIOS for ${missing.size} system${if (missing.size == 1) "" else "s"}") { StatusPill(stringResource(R.string.setup_missing), StatusTone.BAD) }
            CardBody(stringResource(R.string.setup_games_for_these_won_t_start))
            missing.forEach { r ->
                val files = if (r.status == BiosStatus.PARTIAL) r.missing.map { it.baseName }
                else if (r.rule.mode == "any" && r.rule.files.size > 4) listOf("one BIOS file, any version")
                else r.rule.files.map { it.baseName }
                CardBody("• ${names(r)}: ${files.joinToString(if (r.rule.mode == "any") " or " else ", ")}")
            }
        }
    }
}
