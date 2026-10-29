package com.retroforge.app.ui.patcher

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.app.ui.ScreenFrame
import com.retroforge.app.ui.patcher.PatcherViewModel.Companion.hex
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.FocusChip
import com.retroforge.core.ui.StatusPill
import com.retroforge.core.ui.StatusTone
import com.retroforge.patch.PatchFormat
import com.retroforge.patch.RomMatch

private const val README_PREVIEW_LINES = 4

@Composable
fun PatcherScreen(viewModel: PatcherViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.takePending() }
    val pickRom = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(viewModel::onRomPicked) }
    val pickPatch = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(viewModel::onPatchPicked) }
    // Opens in the original ROM's folder, so the patched copy lands next to it (and in the library).
    val romUri = state.rom?.uri
    val saveOutput = rememberLauncherForActivityResult(object : ActivityResultContracts.CreateDocument("application/octet-stream") {
        override fun createIntent(context: android.content.Context, input: String) = super.createIntent(context, input).apply {
            romUri?.let { putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, it) }
        }
    }) {
        viewModel.onOutputChosen(it)
    }
    LaunchedEffect(state.pending) {
        state.pending?.let { saveOutput.launch(it.suggestedName) }
    }

    // Controller users would otherwise be left at the top of the screen after a list change.
    val keyboardMode = LocalInputModeManager.current.inputMode == InputMode.Keyboard
    val firstChoice = remember { FocusRequester() }
    val addPatch = remember { FocusRequester() }
    LaunchedEffect(state.choice) {
        if (keyboardMode && state.choice != null) runCatching { firstChoice.requestFocus() }
    }
    LaunchedEffect(state.patches.size) {
        if (keyboardMode && state.patches.isNotEmpty()) runCatching { addPatch.requestFocus() }
    }

    ScreenFrame(
        title = "ROM Patcher",
        subtitle = "Apply IPS, BPS or UPS patches. Your original ROM is never changed; the result is saved as a new file you choose.",
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

        CardTitle(if (state.patches.size > 1) "2. Patches, applied top to bottom" else "2. Patch")
        state.patches.forEachIndexed { index, patch ->
            PatchCard(index, patch, state, viewModel)
        }
        state.choice?.let { choice ->
            FocusCard(modifier = Modifier.fillMaxWidth()) {
                CardTitle("Which patch from ${choice.archiveName}?")
                CardBody("This archive holds ${choice.candidates.size} patches. If a hack's readme names one, choose that.")
            }
            choice.candidates.forEachIndexed { i, candidate ->
                FocusChip(
                    candidate,
                    onClick = { viewModel.onPatchChosen(candidate) },
                    modifier = if (i == 0) Modifier.focusRequester(firstChoice) else Modifier,
                )
            }
            FocusChip("Cancel", onClick = viewModel::cancelChoice)
        }

        FocusCard(modifier = Modifier.fillMaxWidth().focusRequester(addPatch), onClick = { pickPatch.launch(arrayOf("*/*")) }) {
            CardTitle(if (state.patches.isEmpty()) "Add a patch" else "Add another patch on top")
            CardBody(
                if (state.patches.isEmpty()) "Select to choose a .ips, .bps or .ups file, or a .zip containing one."
                else "For add-on hacks whose readme says to apply another patch first.",
            )
        }

        val ready = state.rom != null && state.patches.isNotEmpty() && !state.busy && state.match != RomMatch.MISMATCH
        FocusCard(modifier = Modifier.fillMaxWidth(), onClick = if (ready) viewModel::applyPatches else null) {
            CardTitle("3. Create patched copy")
            CardBody(
                when {
                    state.rom == null || state.patches.isEmpty() -> "Choose a ROM and at least one patch first."
                    state.match == RomMatch.MISMATCH -> "Unavailable: the first patch needs a different ROM."
                    state.patches.size > 1 -> "Select to apply all ${state.patches.size} patches in order, then choose where to save."
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

@Composable
private fun PatchCard(index: Int, patch: PickedPatch, state: PatcherState, viewModel: PatcherViewModel) {
    val readme = patch.readmes.firstOrNull()
    val expanded = state.expandedReadme == index
    FocusCard(modifier = Modifier.fillMaxWidth(), onClick = readme?.let { { viewModel.toggleReadme(index) } }) {
        CardTitle(if (state.patches.size > 1) "${index + 1}. ${patch.file.name}" else patch.file.name)
        CardBody(
            buildString {
                append(patch.info.format.label)
                if (patch.file.name != patch.pickedName) append(" · from ${patch.pickedName}")
            },
        )
        patch.info.expectedSourceCrc32?.let { CardBody("Needs ROM CRC32 ${hex(it)} (${patch.info.expectedSourceSize} bytes)") }
        if (index == 0) {
            when (state.match) {
                RomMatch.MATCH -> StatusPill("ROM matches this patch", StatusTone.GOOD)
                RomMatch.MATCH_WITHOUT_HEADER -> StatusPill("Matches once the 512-byte header is removed", StatusTone.GOOD)
                RomMatch.MISMATCH -> StatusPill("Wrong ROM for this patch", StatusTone.BAD)
                RomMatch.UNVERIFIABLE -> StatusPill("Can't verify the ROM (IPS)", StatusTone.CAUTION)
                null -> Unit
            }
        } else if (patch.info.format != PatchFormat.IPS) {
            CardBody("Checked against the result of the patches above when applied.")
        }
        patch.info.metadata?.takeIf { it.isNotBlank() }?.let { CardBody("Patch info: ${it.trim().take(300)}") }

        if (readme != null) {
            val lines = readme.text.trim().lines()
            Text(
                text = if (expanded) readme.text.trim() else lines.take(README_PREVIEW_LINES).joinToString("\n"),
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp),
            )
            CardBody(
                when {
                    expanded -> "Select to collapse the readme."
                    lines.size > README_PREVIEW_LINES -> "Readme: ${readme.name}. Select to read all of it."
                    else -> "Readme: ${readme.name}"
                },
            )
        } else {
            CardBody("No readme found. Check the patch's download page for the ROM version and patch order it needs.")
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (index > 0) FocusChip("Move up", onClick = { viewModel.movePatchUp(index) })
        FocusChip("Remove", onClick = { viewModel.removePatch(index) })
    }
}
