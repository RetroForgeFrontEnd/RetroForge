package com.retroforge.app.ui.patcher

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.app.ui.ScreenFrame
import com.retroforge.app.ui.patcher.PatcherViewModel.Companion.hex
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.StatusPill
import com.retroforge.core.ui.StatusTone
import com.retroforge.patch.PatchFormat
import com.retroforge.patch.RomMatch

@Composable
fun PatcherScreen(viewModel: PatcherViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pickRom = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(viewModel::onRomPicked) }
    val pickPatch = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(viewModel::onPatchPicked) }
    val saveOutput = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {
        viewModel.onOutputChosen(it)
    }
    LaunchedEffect(state.pending) {
        state.pending?.let { saveOutput.launch(it.suggestedName) }
    }

    ScreenFrame(
        title = "ROM Patcher",
        subtitle = "Apply an IPS, BPS or UPS patch. Your original ROM is never changed; the result is saved as a new file you choose.",
    ) {
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())

        FocusCard(modifier = Modifier.fillMaxWidth(), onClick = { pickRom.launch(arrayOf("*/*")) }) {
            CardTitle("1. Original ROM")
            val rom = state.rom
            if (rom == null) {
                CardBody("Select to choose a ROM (.zip and .7z are fine).")
            } else {
                CardBody(if (rom.file.name == rom.pickedName) rom.pickedName else "${rom.file.name} (from ${rom.pickedName})")
                CardBody("${rom.file.bytes.size} bytes · CRC32 ${hex(rom.crc32)}")
            }
        }

        FocusCard(modifier = Modifier.fillMaxWidth(), onClick = { pickPatch.launch(arrayOf("*/*")) }) {
            CardTitle("2. Patch")
            val patch = state.patch
            if (patch == null) {
                CardBody("Select to choose a .ips, .bps or .ups file, or a .zip containing one.")
            } else {
                CardBody("${patch.file.name} · ${patch.info.format.label}")
                patch.info.expectedSourceCrc32?.let { CardBody("Needs ROM CRC32 ${hex(it)} (${patch.info.expectedSourceSize} bytes)") }
                if (patch.info.format == PatchFormat.IPS) CardBody("IPS patches don't record which ROM they need.")
            }
            when (state.match) {
                RomMatch.MATCH -> StatusPill("ROM matches this patch", StatusTone.GOOD)
                RomMatch.MATCH_WITHOUT_HEADER -> StatusPill("Matches once the 512-byte header is removed", StatusTone.GOOD)
                RomMatch.MISMATCH -> StatusPill("Wrong ROM for this patch", StatusTone.BAD)
                RomMatch.UNVERIFIABLE -> StatusPill("Can't verify the ROM (IPS)", StatusTone.CAUTION)
                null -> Unit
            }
        }

        val ready = state.rom != null && state.patch != null && !state.busy && state.match != RomMatch.MISMATCH
        FocusCard(modifier = Modifier.fillMaxWidth(), onClick = if (ready) viewModel::applyPatch else null) {
            CardTitle("3. Create patched copy")
            CardBody(
                when {
                    state.rom == null || state.patch == null -> "Choose a ROM and a patch first."
                    state.match == RomMatch.MISMATCH -> "Unavailable: this patch needs a different ROM."
                    else -> "Select to patch, then choose where to save the new file."
                },
            )
        }

        state.message?.let { message ->
            FocusCard(modifier = Modifier.fillMaxWidth()) {
                StatusPill(if (state.messageIsError) "Problem" else "Done", if (state.messageIsError) StatusTone.BAD else StatusTone.GOOD)
                CardBody(message)
            }
        }
    }
}
