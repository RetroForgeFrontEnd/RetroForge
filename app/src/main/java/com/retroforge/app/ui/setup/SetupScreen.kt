package com.retroforge.app.ui.setup

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
import com.retroforge.library.BiosStatus
import com.retroforge.retro.RetroArchLauncher

/** Where RetroArch for Android looks for BIOS by default; offered as the picker's starting point. */
private val RETROARCH_SYSTEM_HINT: Uri =
    DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:RetroArch/system")

@Composable
fun SetupScreen(viewModel: SetupViewModel, onOpenSort: () -> Unit = {}, onDone: () -> Unit) {
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

    ScreenFrame(title = "Set up RetroForge", subtitle = "Three steps from your game folder to playing; most of it happens by itself. You can come back here any time from Settings.") {
        val s = summary

        // 1. Games
        FocusCard(modifier = Modifier.fillMaxWidth(), onClick = { pickGames.launch(null) }) {
            CardTitle("1. Your games")
            if (s == null || s.folders == 0) {
                StatusPill("To do", StatusTone.CAUTION)
                CardBody("Select to choose your games folder, e.g. an existing \"roms\" folder from another frontend. RetroForge only reads it.")
            } else {
                StatusPill("${plural(s.games, "game")} in ${plural(s.cores.size + s.unsupported.size, "system")}", StatusTone.GOOD)
                CardBody("Select to add another folder.")
            }
        }
        // Tidy-up offer: games lying loose instead of in their system's folder.
        val loose by androidx.compose.runtime.produceState(0, s?.games) { value = viewModel.looseGames() }
        if (loose > 0) {
            FocusCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenSort) {
                CardTitle("Tidy your games folder?") { StatusPill("${plural(loose, "game")} not in a system folder", StatusTone.CAUTION) }
                CardBody(
                    "They play fine where they are. Other frontends (RetroArch, ES-DE, RetroBat) want each game in its system's " +
                        "folder, though. Select to see them in the ROM Manager: it shows every move first, and History can undo it.",
                )
            }
        }

        // 2. Emulators: the built-in player's download by themselves; RetroArch only for systems it can't run yet.
        val coreStates by viewModel.coreStates.collectAsStateWithLifecycle()
        val systemIds = s?.systemIds.orEmpty()
        val builtIn = systemIds.mapNotNull { id -> com.retroforge.player.PlayerCores.forSystem(id)?.let { id to it } }
        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle("2. Emulators")
            when {
                s == null || systemIds.isEmpty() -> CardBody("Add your games first. RetroForge then gets the emulators they need by itself.")
                builtIn.isEmpty() -> CardBody("RetroForge's built-in player doesn't run these systems yet; see below.")
                else -> {
                    val cores = builtIn.map { it.second }.distinctBy { it.id }
                    val ready = cores.count { (coreStates[it.id] ?: viewModel.coreState(it.id)) == com.retroforge.player.CoreState.Ready }
                    if (ready == cores.size) StatusPill("Ready", StatusTone.GOOD)
                    else StatusPill("Getting ${ready + 1} of ${cores.size}…", StatusTone.CAUTION)
                    CardBody("Built in, nothing to install: ${builtIn.map { viewModel.systemName(it.first) }.sorted().joinToString()}.")
                    CardBody("RetroForge downloads these emulators for you in the background.")
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
                if (i == null) StatusPill("Not installed", StatusTone.CAUTION) else StatusPill("Installed ${i.versionName.orEmpty()}".trim(), StatusTone.GOOD)
                CardBody("${needRetroArch.map(viewModel::systemName).sorted().joinToString()} play in RetroArch, a free emulator app RetroForge starts for you.")
                if (i == null) {
                    CardBody("Install it from Google Play or its website, then open it once and allow it to access files.")
                } else {
                    CardBody(
                        "RetroArch keeps its emulators (\"cores\") in its own private storage, so only RetroArch can download them: " +
                            "in RetroArch, Main Menu → Online Updater → Core Downloader. Each game's page names the core it needs.",
                    )
                }
            }
            if (install == null) {
                ChipRow {
                    FocusChip("Get it on Google Play", onClick = {
                        val market = Intent(Intent.ACTION_VIEW, "market://details?id=com.retroarch".toUri())
                        try {
                            context.startActivity(market)
                        } catch (_: ActivityNotFoundException) {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=com.retroarch".toUri())) }
                        }
                    })
                    FocusChip("RetroArch website", onClick = {
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
            CardTitle("3. BIOS files")
            CardBody(
                "Some systems need BIOS files from the original console. RetroForge doesn't provide them. If you have your own, " +
                    "select this to choose the folder they're in (for example another frontend's \"bios\" folder) and RetroForge will check them.",
            )
            if (settings?.biosFolderUri != null && bios.reports == null && !bios.checking) CardBody("Select to check your BIOS folder again.")
        }
        if (settings?.biosFolderUri != null) FocusChip("Choose another BIOS folder", onClick = { pickBios.launch(null) })
        if (bios.checking) LinearProgressIndicator(Modifier.fillMaxWidth())
        bios.error?.let { CardBody(it) }
        bios.reports?.let { reports ->
            if (reports.isEmpty()) CardBody("None of the systems in your library need BIOS files.")
            reports.forEach { report ->
                FocusCard(modifier = Modifier.fillMaxWidth()) {
                    CardTitle(report.rule.systems.joinToString(" / ") { viewModel.systemName(it) })
                    val need = when (report.rule.need) {
                        BiosNeed.REQUIRED -> "required"
                        BiosNeed.RECOMMENDED -> "recommended"
                        BiosNeed.OPTIONAL -> "optional"
                    }
                    when (report.status) {
                        BiosStatus.OK -> StatusPill("Found ($need)", StatusTone.GOOD)
                        BiosStatus.UNVERIFIED -> StatusPill("Found, version not recognised ($need)", StatusTone.CAUTION)
                        BiosStatus.PARTIAL -> StatusPill("Some files missing ($need)", StatusTone.CAUTION)
                        BiosStatus.MISSING -> StatusPill("Missing ($need)", if (report.rule.need == BiosNeed.REQUIRED) StatusTone.BAD else StatusTone.NEUTRAL)
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
                    CardTitle("Copy them to RetroArch")
                    CardBody(
                        "RetroArch reads BIOS from its own \"system\" folder (normally RetroArch/system on internal storage). " +
                            "Select to copy the files above there under the names the cores expect. Files already there are left alone; nothing is deleted.",
                    )
                    if (settings?.retroArchSystemUri == null) CardBody("You'll be asked to choose that folder once. Open RetroArch at least once first so it exists.")
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
                        if (r.copied.isNotEmpty()) CardBody("Copied: ${r.copied.joinToString()}")
                        if (r.alreadyThere.isNotEmpty()) CardBody("Already there, left alone: ${r.alreadyThere.joinToString()}")
                        r.failed.forEach { (name, why) -> CardBody("Couldn't copy $name: $why") }
                    }
                }
                if (settings?.retroArchSystemUri != null) {
                    FocusChip("Choose a different RetroArch folder", onClick = { pickRetroArchFolder.launch(RETROARCH_SYSTEM_HINT) })
                }
            }
        }

        FocusCard(modifier = Modifier.fillMaxWidth(), onClick = { viewModel.finish(); onDone() }) {
            CardTitle("Done")
            CardBody("You can skip any step and come back later from Settings → Set up RetroForge.")
        }
    }
}
