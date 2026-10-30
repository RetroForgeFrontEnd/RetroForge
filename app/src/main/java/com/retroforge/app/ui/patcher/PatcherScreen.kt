package com.retroforge.app.ui.patcher

import com.retroforge.core.ui.str
import com.retroforge.app.R

import androidx.compose.ui.res.stringResource

import com.retroforge.app.ui.ChipRow
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
        title = stringResource(R.string.library_rom_patcher),
        subtitle = stringResource(R.string.patcher_apply_ips_bps_or_ups_patches),
    ) {
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())

        FocusCard(modifier = Modifier.fillMaxWidth(), onClick = { pickRom.launch(arrayOf("*/*")) }) {
            CardTitle(stringResource(R.string.patcher_1_original_rom))
            val rom = state.rom
            if (rom == null) {
                CardBody(stringResource(R.string.patcher_select_to_choose_a_rom_zip))
            } else {
                CardBody(if (rom.file.name == rom.pickedName) rom.pickedName else stringResource(R.string.patcher_from, rom.file.name, rom.pickedName))
                CardBody(stringResource(R.string.patcher_bytes_crc32, rom.file.bytes.size, hex(rom.crc32)))
            }
        }

        CardTitle(if (state.patches.size > 1) stringResource(R.string.patcher_2_patches_applied_top_to_bottom) else stringResource(R.string.patcher_2_patch))
        state.patches.forEachIndexed { index, patch ->
            PatchCard(index, patch, state, viewModel)
        }
        state.choice?.let { choice ->
            FocusCard(modifier = Modifier.fillMaxWidth()) {
                CardTitle(stringResource(R.string.patcher_which_patch_from, choice.archiveName))
                CardBody(stringResource(R.string.patcher_this_archive_holds_patches_if_a, choice.candidates.size))
            }
            choice.candidates.forEachIndexed { i, candidate ->
                FocusChip(
                    candidate,
                    onClick = { viewModel.onPatchChosen(candidate) },
                    modifier = if (i == 0) Modifier.focusRequester(firstChoice) else Modifier,
                )
            }
            FocusChip(stringResource(R.string.network_drives_cancel), onClick = viewModel::cancelChoice)
        }

        FocusCard(modifier = Modifier.fillMaxWidth().focusRequester(addPatch), onClick = { pickPatch.launch(arrayOf("*/*")) }) {
            CardTitle(if (state.patches.isEmpty()) stringResource(R.string.patcher_add_a_patch) else stringResource(R.string.patcher_add_another_patch_on_top))
            CardBody(
                if (state.patches.isEmpty()) str(R.string.patcher_select_to_choose_a_ips_bps)
                else str(R.string.patcher_for_add_on_hacks_whose_readme),
            )
        }

        val ready = state.rom != null && state.patches.isNotEmpty() && !state.busy && state.match != RomMatch.MISMATCH
        FocusCard(modifier = Modifier.fillMaxWidth(), onClick = if (ready) viewModel::applyPatches else null) {
            CardTitle(stringResource(R.string.patcher_3_create_patched_copy))
            CardBody(
                when {
                    state.rom == null || state.patches.isEmpty() -> str(R.string.patcher_choose_a_rom_and_at_least)
                    state.match == RomMatch.MISMATCH -> str(R.string.patcher_unavailable_the_first_patch_needs_a)
                    state.patches.size > 1 -> str(R.string.patcher_select_to_apply_all_patches_in, state.patches.size)
                    else -> str(R.string.patcher_select_to_patch_then_choose_where)
                },
            )
        }

        state.message?.let { message ->
            FocusCard(modifier = Modifier.fillMaxWidth()) {
                StatusPill(if (state.messageIsError) str(R.string.play_problem) else str(R.string.setup_done), if (state.messageIsError) StatusTone.BAD else StatusTone.GOOD)
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
                if (patch.file.name != patch.pickedName) append(str(R.string.patcher_from_2, patch.pickedName))
            },
        )
        patch.info.expectedSourceCrc32?.let { CardBody(stringResource(R.string.patcher_needs_rom_crc32_bytes, hex(it), patch.info.expectedSourceSize.toString())) }
        if (index == 0) {
            when (state.match) {
                RomMatch.MATCH -> StatusPill(stringResource(R.string.patcher_rom_matches_this_patch), StatusTone.GOOD)
                RomMatch.MATCH_WITHOUT_HEADER -> StatusPill(stringResource(R.string.patcher_matches_once_the_512_byte_header), StatusTone.GOOD)
                RomMatch.MISMATCH -> StatusPill(stringResource(R.string.patcher_wrong_rom_for_this_patch), StatusTone.BAD)
                RomMatch.UNVERIFIABLE -> StatusPill(stringResource(R.string.patcher_can_t_verify_the_rom_ips), StatusTone.CAUTION)
                null -> Unit
            }
        } else if (patch.info.format != PatchFormat.IPS) {
            CardBody(stringResource(R.string.patcher_checked_against_the_result_of_the))
        }
        patch.info.metadata?.takeIf { it.isNotBlank() }?.let { CardBody(stringResource(R.string.patcher_patch_info, it.trim().take(300))) }

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
                    expanded -> str(R.string.patcher_select_to_collapse_the_readme)
                    lines.size > README_PREVIEW_LINES -> str(R.string.patcher_readme_select_to_read_all_of, readme.name)
                    else -> "Readme: ${readme.name}"
                },
            )
        } else {
            CardBody(stringResource(R.string.patcher_no_readme_found_check_the_patch))
        }
    }
    ChipRow {
        if (index > 0) FocusChip(stringResource(R.string.patcher_move_up), onClick = { viewModel.movePatchUp(index) })
        FocusChip(stringResource(R.string.hacks_remove), onClick = { viewModel.removePatch(index) })
    }
}
