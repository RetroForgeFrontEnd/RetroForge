package com.retroforge.app.ui.patcher

import com.retroforge.app.R
import com.retroforge.core.ui.str
import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.retroforge.patch.ArchiveInput
import com.retroforge.patch.ArchiveResult
import com.retroforge.patch.ChainOutcome
import com.retroforge.patch.NamedBytes
import com.retroforge.patch.PatchError
import com.retroforge.patch.PatchException
import com.retroforge.patch.PatchInfo
import com.retroforge.patch.PatchNote
import com.retroforge.patch.RomMatch
import com.retroforge.patch.RomPatcher
import com.retroforge.patch.TextFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PickedRom(val pickedName: String, val file: NamedBytes, val crc32: Long, val uri: Uri? = null)

data class PickedPatch(val pickedName: String, val file: NamedBytes, val info: PatchInfo, val readmes: List<TextFile>)

/** A patched ROM held in memory until the user chooses where to save it. */
data class PendingOutput(val suggestedName: String, val bytes: ByteArray, val notes: Set<PatchNote>)

data class PatcherState(
    val rom: PickedRom? = null,
    /** Applied top to bottom; each patch goes onto the result of the one before it. */
    val patches: List<PickedPatch> = emptyList(),
    val busy: Boolean = false,
    val pending: PendingOutput? = null,
    val message: String? = null,
    val messageIsError: Boolean = false,
    /** How the ROM matches the first patch. Later patches can only be checked once earlier ones are applied. */
    val match: RomMatch? = null,
    val expandedReadme: Int? = null,
    /** An archive holding several patches, waiting for the user to pick one. */
    val choice: PatchChoice? = null,
)

class PatchChoice(val archiveName: String, val bytes: ByteArray, val candidates: List<String>)

private fun PatcherState.withMatch(): PatcherState {
    val first = patches.firstOrNull()
    return copy(
        match = if (rom != null && first != null) RomPatcher.checkRom(rom.file.bytes, first.info) else null,
        pending = null,
        expandedReadme = expandedReadme?.takeIf { it < patches.size },
    )
}

/**
 * Reads the ROM and patches through the Storage Access Framework, applies the patches in memory and writes the
 * result only to a new document the user creates. The picked ROM is opened read-only and never written.
 */
/** [onSaved]: called after a patched copy is saved, so the library can pick it up (it rescans the game folders). */
class PatcherViewModel(private val resolver: ContentResolver, private val onSaved: () -> Unit = {}) : ViewModel() {

    private val _state = MutableStateFlow(PatcherState())
    val state: StateFlow<PatcherState> = _state.asStateFlow()

    fun onRomPicked(uri: Uri) = load(uri) { name, bytes ->
        when (val result = ArchiveInput.readRom(name, bytes)) {
            is ArchiveResult.Ok -> _state.update {
                it.copy(rom = PickedRom(name, result.file, RomPatcher.crc32Of(result.file.bytes), uri), message = null).withMatch()
            }
            else -> showError(describe(result, "ROM"))
        }
    }

    fun onPatchPicked(uri: Uri) = load(uri) { name, bytes -> addPatch(name, bytes, entryName = null) }

    /** Takes a patch the hack page downloaded, if one is waiting. */
    fun takePending() {
        val (name, bytes) = PendingPatch.file ?: return
        PendingPatch.file = null
        viewModelScope.launch(Dispatchers.Default) { addPatch(name, bytes, entryName = null) }
    }

    fun onPatchChosen(entryName: String) {
        val choice = _state.value.choice ?: return
        _state.update { it.copy(choice = null) }
        viewModelScope.launch(Dispatchers.Default) { addPatch(choice.archiveName, choice.bytes, entryName) }
    }

    fun cancelChoice() = _state.update { it.copy(choice = null) }

    private fun addPatch(name: String, bytes: ByteArray, entryName: String?) {
        when (val result = ArchiveInput.readPatch(name, bytes, entryName)) {
            is ArchiveResult.Ok -> RomPatcher.inspect(result.file.bytes).fold(
                onSuccess = { info ->
                    val picked = PickedPatch(name, result.file, info, result.readmes)
                    _state.update { it.copy(patches = it.patches + picked, message = null).withMatch() }
                },
                onFailure = { error -> showError(describe((error as? PatchException)?.error)) },
            )
            is ArchiveResult.Ambiguous -> _state.update { it.copy(choice = PatchChoice(name, bytes, result.candidates), message = null) }
            else -> showError(describe(result, "patch"))
        }
    }

    fun removePatch(index: Int) = _state.update { state ->
        state.copy(patches = state.patches.filterIndexed { i, _ -> i != index }, message = null).withMatch()
    }

    fun movePatchUp(index: Int) {
        if (index <= 0) return
        _state.update { state ->
            val reordered = state.patches.toMutableList().apply { add(index - 1, removeAt(index)) }
            state.copy(patches = reordered, message = null, expandedReadme = null).withMatch()
        }
    }

    fun toggleReadme(index: Int) = _state.update { it.copy(expandedReadme = if (it.expandedReadme == index) null else index) }

    /** Applies the patches in order; on success the screen asks the user where to save the new file. */
    fun applyPatches() {
        val current = _state.value
        val rom = current.rom ?: return
        val patches = current.patches.takeIf { it.isNotEmpty() } ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            val outcome = withContext(Dispatchers.Default) { RomPatcher.applyChain(rom.file.bytes, patches.map { it.file.bytes }) }
            when (outcome) {
                is ChainOutcome.Success -> _state.update {
                    val name = RomPatcher.outputName(rom.file.name, patches.map { patch -> patch.file.name })
                    it.copy(busy = false, pending = PendingOutput(name, outcome.output, outcome.stepNotes.flatten().toSet()))
                }
                is ChainOutcome.Failure -> {
                    val failed = patches[outcome.step]
                    val prefix = if (patches.size > 1) str(R.string.patcher_patch, outcome.step + 1, failed.file.name) else ""
                    val hint = if (outcome.step > 0 && outcome.error is PatchError.WrongSourceRom) {
                        str(R.string.patcher_it_may_need_a_different_order)
                    } else ""
                    showError(prefix + describe(outcome.error) + hint)
                }
            }
        }
    }

    fun onOutputChosen(uri: Uri?) {
        val pending = _state.value.pending ?: return
        if (uri == null) {
            _state.update { it.copy(message = str(R.string.patcher_not_saved_select_create_patched_copy), messageIsError = false) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val written = withContext(Dispatchers.IO) {
                runCatching { resolver.openOutputStream(uri, "wt")?.use { it.write(pending.bytes) } ?: error(str(R.string.patcher_could_not_open_the_file_for)) }
            }
            written.fold(
                onSuccess = {
                    val extra = buildList {
                        if (PatchNote.COPIER_HEADER_REMOVED in pending.notes) add(str(R.string.patcher_your_rom_s_512_byte_copier))
                        if (PatchNote.SOURCE_NOT_VERIFIABLE in pending.notes) add(str(R.string.patcher_ips_patches_can_t_confirm_the))
                    }
                    _state.update {
                        it.copy(busy = false, pending = null, messageIsError = false,
                            message = (listOf(str(R.string.patcher_saved_if_it_s_in_one, displayName(uri) ?: pending.suggestedName)) + extra).joinToString(" "))
                    }
                    onSaved()
                },
                onFailure = { showError(str(R.string.patcher_couldn_t_save_the_patched_rom, it.message)) },
            )
        }
    }

    private fun load(uri: Uri, onLoaded: (String, ByteArray) -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            val name = displayName(uri) ?: "file"
            val bytes = withContext(Dispatchers.IO) {
                runCatching { resolver.openInputStream(uri)?.use { ArchiveInput.readLimited(it, name) } }.getOrNull()
            }
            if (bytes == null) {
                showError(str(R.string.patcher_couldn_t_read_it_may_be, name))
                return@launch
            }
            withContext(Dispatchers.Default) { onLoaded(name, bytes) }
            _state.update { it.copy(busy = false) }
        }
    }

    private fun displayName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    private fun showError(text: String) = _state.update { it.copy(busy = false, message = text, messageIsError = true) }

    companion object {
        fun factory(resolver: ContentResolver, onSaved: () -> Unit = {}): ViewModelProvider.Factory = viewModelFactory {
            initializer { PatcherViewModel(resolver, onSaved) }
        }

        fun describe(error: PatchError?): String = when (error) {
            PatchError.UnknownFormat, null -> str(R.string.patcher_this_isn_t_an_ips_bps)
            is PatchError.Malformed -> str(R.string.patcher_the_patch_file_is_malformed, error.detail)
            PatchError.PatchDamaged -> str(R.string.patcher_the_patch_file_is_damaged_or)
            is PatchError.WrongSourceRom -> str(R.string.patcher_this_patch_was_made_for_a, hex(error.expectedCrc32), error.expectedSize, hex(error.actualCrc32))
            PatchError.OutputMismatch -> str(R.string.patcher_the_patch_applied_but_the_result)
            is PatchError.TooLarge -> str(R.string.patcher_the_result_would_be_too_large, error.bytes)
        }

        fun describe(result: ArchiveResult, what: String): String = when (result) {
            is ArchiveResult.Ok -> ""
            is ArchiveResult.NothingSuitable -> str(R.string.patcher_doesn_t_contain_a_file, result.archiveName, what)
            is ArchiveResult.Ambiguous -> str(R.string.patcher_contains_several_possible_s_extract_the, result.archiveName, what, result.candidates.joinToString())
            is ArchiveResult.Unreadable -> str(R.string.network_drives_provider_couldn_t_open, result.archiveName, result.detail)
            is ArchiveResult.TooLarge -> str(R.string.patcher_is_too_large_to_open_over, result.name)
        }

        fun hex(crc: Long) = "%08X".format(crc)
    }
}
