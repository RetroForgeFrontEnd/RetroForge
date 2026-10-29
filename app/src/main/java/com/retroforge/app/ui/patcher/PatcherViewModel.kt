package com.retroforge.app.ui.patcher

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
                    val prefix = if (patches.size > 1) "Patch ${outcome.step + 1} (${failed.file.name}): " else ""
                    val hint = if (outcome.step > 0 && outcome.error is PatchError.WrongSourceRom) {
                        " It may need a different order or a different patch before it; check its readme."
                    } else ""
                    showError(prefix + describe(outcome.error) + hint)
                }
            }
        }
    }

    fun onOutputChosen(uri: Uri?) {
        val pending = _state.value.pending ?: return
        if (uri == null) {
            _state.update { it.copy(message = "Not saved. Select \"Create patched copy\" again to choose a location.", messageIsError = false) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val written = withContext(Dispatchers.IO) {
                runCatching { resolver.openOutputStream(uri, "wt")?.use { it.write(pending.bytes) } ?: error("could not open the file for writing") }
            }
            written.fold(
                onSuccess = {
                    val extra = buildList {
                        if (PatchNote.COPIER_HEADER_REMOVED in pending.notes) add("Your ROM's 512-byte copier header was removed so the patch could apply.")
                        if (PatchNote.SOURCE_NOT_VERIFIABLE in pending.notes) add("IPS patches can't confirm the base ROM; if the game misbehaves, check the readme for the exact ROM and patch order.")
                    }
                    _state.update {
                        it.copy(busy = false, pending = null, messageIsError = false,
                            message = (listOf("Saved ${displayName(uri) ?: pending.suggestedName}. If it's in one of your game folders it appears in the library in a moment.") + extra).joinToString(" "))
                    }
                    onSaved()
                },
                onFailure = { showError("Couldn't save the patched ROM: ${it.message}") },
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
                showError("Couldn't read $name. It may be too large (over 256 MB) or no longer accessible.")
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
            PatchError.UnknownFormat, null -> "This isn't an IPS, BPS or UPS patch."
            is PatchError.Malformed -> "The patch file is malformed (${error.detail})."
            PatchError.PatchDamaged -> "The patch file is damaged or incomplete (its checksum doesn't match). Try downloading it again."
            is PatchError.WrongSourceRom -> "This patch was made for a different ROM (expected CRC32 ${hex(error.expectedCrc32)}, " +
                "${error.expectedSize} bytes; got ${hex(error.actualCrc32)})."
            PatchError.OutputMismatch -> "The patch applied but the result failed its checksum, so it wasn't saved."
            is PatchError.TooLarge -> "The result would be too large (${error.bytes} bytes)."
        }

        fun describe(result: ArchiveResult, what: String): String = when (result) {
            is ArchiveResult.Ok -> ""
            is ArchiveResult.NothingSuitable -> "${result.archiveName} doesn't contain a $what file."
            is ArchiveResult.Ambiguous -> "${result.archiveName} contains several possible ${what}s (${result.candidates.joinToString()}). Extract the one you want and pick it directly."
            is ArchiveResult.Unreadable -> "Couldn't open ${result.archiveName}: ${result.detail}"
            is ArchiveResult.TooLarge -> "${result.name} is too large to open (over 256 MB)."
        }

        fun hex(crc: Long) = "%08X".format(crc)
    }
}
