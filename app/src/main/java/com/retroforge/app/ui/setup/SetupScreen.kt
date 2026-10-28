package com.retroforge.app.ui.setup

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
import com.retroforge.core.ui.StatusPill
import com.retroforge.core.ui.StatusTone
import com.retroforge.library.BiosNeed
import com.retroforge.library.BiosStatus
import com.retroforge.retro.RetroArchLauncher

/** Where RetroArch for Android looks for BIOS by default; offered as the picker's starting point. */
private val RETROARCH_SYSTEM_HINT: Uri =
    DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:RetroArch/system")

@Composable
fun SetupScreen(viewModel: SetupViewModel, onDone: () -> Unit) {
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val install by viewModel.retroArchInstall.collectAsStateWithLifecycle()
    val bios by viewModel.bios.collectAsStateWithLifecycle()
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    val pickGames = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(viewModel::addGameFolder) }
    val pickBios = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(viewModel::onBiosFolderPicked) }
    val pickRetroArchFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(viewModel::onRetroArchFolderPicked) }

    ScreenFrame(title = "Set up RetroForge", subtitle = "Four steps to go from your game folder to playing. You can come back here any time from Settings.") {
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

        // 2. RetroArch
        FocusCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (install == null) {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, RetroArchLauncher.DOWNLOAD_URL.toUri()))
                    } catch (_: ActivityNotFoundException) {
                        // No browser: the address is shown on the card.
                    }
                } else {
                    viewModel.openRetroArch()
                }
            },
        ) {
            CardTitle("2. RetroArch")
            val i = install
            if (i == null) {
                StatusPill("Not installed", StatusTone.CAUTION)
                CardBody("RetroForge plays games through RetroArch, a free emulator app. Select to open its official download page (${RetroArchLauncher.DOWNLOAD_URL}). Open it once after installing and allow it to access files.")
            } else {
                StatusPill("Installed ${i.versionName.orEmpty()}".trim(), StatusTone.GOOD)
                CardBody("Select to open RetroArch.")
            }
        }

        // 3. Cores
        FocusCard(modifier = Modifier.fillMaxWidth(), onClick = if (install != null) viewModel::openRetroArch else null) {
            CardTitle("3. Emulator cores")
            when {
                s == null || s.cores.isEmpty() -> CardBody("Add your games first; RetroForge will then list the cores they need.")
                else -> {
                    StatusPill("${plural(s.cores.size, "core")} for your library", StatusTone.NEUTRAL)
                    CardBody("In RetroArch: Main Menu → Online Updater → Core Downloader, then download each of these. RetroForge can't see inside RetroArch, so it can't tick them off for you.")
                    s.cores.forEach { need ->
                        Text(
                            "• ${need.core.name}: ${need.systems.joinToString()} (${plural(need.games, "game")})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    if (s.unsupported.isNotEmpty()) {
                        CardBody("Not playable from RetroForge yet: ${s.unsupported.take(15).joinToString()}${if (s.unsupported.size > 15) " …" else ""}")
                    }
                }
            }
        }

        // 4. BIOS
        FocusCard(modifier = Modifier.fillMaxWidth(), onClick = { pickBios.launch(null) }) {
            CardTitle("4. BIOS files")
            CardBody(
                "Some systems need BIOS files from the original console. RetroForge doesn't provide them. If you have your own, " +
                    "select this to choose the folder they're in (for example another frontend's \"bios\" folder) and RetroForge will check them.",
            )
            if (settings?.biosFolderUri != null && bios.reports == null && !bios.checking) CardBody("Select to check your BIOS folder again.")
        }
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
                    if (report.status == BiosStatus.MISSING) CardBody("Needs ${report.rule.files.joinToString(if (report.rule.mode == "any") " or " else " and ") { it.name }}.")
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
