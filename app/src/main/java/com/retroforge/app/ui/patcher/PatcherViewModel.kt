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
import com.retroforge.patch.NamedBytes
import com.retroforge.patch.PatchError
import com.retroforge.patch.PatchException
import com.retroforge.patch.PatchInfo
import com.retroforge.patch.PatchNote
import com.retroforge.patch.PatchOutcome
import com.retroforge.patch.RomMatch
import com.retroforge.patch.RomPatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PickedRom(val pickedName: String, val file: NamedBytes, val crc32: Long)

data class PickedPatch(val pickedName: String, val file: NamedBytes, val info: PatchInfo)

/** A patched ROM held in memory until the user chooses where to save it. */
data class PendingOutput(val suggestedName: String, val bytes: ByteArray, val notes: Set<PatchNote>)

data class PatcherState(
    val rom: PickedRom? = null,
    val patch: PickedPatch? = null,
    val busy: Boolean = false,
    val pending: PendingOutput? = null,
    val message: String? = null,
    val messageIsError: Boolean = false,
    val match: RomMatch? = null,
)

private fun PatcherState.withMatch(): PatcherState =
    copy(match = if (rom != null && patch != null) RomPatcher.checkRom(rom.file.bytes, patch.info) else null)

/**
 * Reads the ROM and patch through the Storage Access Framework, applies the patch in memory and writes the
 * result only to a new document the user creates. The picked ROM is opened read-only and never written.
 */
class PatcherViewModel(private val resolver: ContentResolver) : ViewModel() {

    private val _state = MutableStateFlow(PatcherState())
    val state: StateFlow<PatcherState> = _state.asStateFlow()

    fun onRomPicked(uri: Uri) = load(uri) { name, bytes ->
        when (val result = ArchiveInput.readRom(name, bytes)) {
            is ArchiveResult.Ok -> _state.update {
                it.copy(rom = PickedRom(name, result.file, RomPatcher.crc32Of(result.file.bytes)), pending = null, message = null).withMatch()
            }
            else -> showError(describe(result, "ROM"))
        }
    }

    fun onPatchPicked(uri: Uri) = load(uri) { name, bytes ->
        when (val result = ArchiveInput.readPatch(name, bytes)) {
            is ArchiveResult.Ok -> RomPatcher.inspect(result.file.bytes).fold(
                onSuccess = { info -> _state.update { it.copy(patch = PickedPatch(name, result.file, info), pending = null, message = null).withMatch() } },
                onFailure = { error -> showError(describe((error as? PatchException)?.error)) },
            )
            else -> showError(describe(result, "patch"))
        }
    }

    /** Applies the patch; on success the screen asks the user where to save the new file. */
    fun applyPatch() {
        val current = _state.value
        val rom = current.rom ?: return
        val patch = current.patch ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            val outcome = withContext(Dispatchers.Default) { RomPatcher.apply(rom.file.bytes, patch.file.bytes) }
            when (outcome) {
                is PatchOutcome.Success -> _state.update {
                    it.copy(busy = false, pending = PendingOutput(RomPatcher.outputName(rom.file.name, patch.file.name), outcome.output, outcome.notes))
                }
                is PatchOutcome.Failure -> showError(describe(outcome.error))
            }
        }
    }

    fun onOutputChosen(uri: Uri?) {
        val pending = _state.value.pending ?: return
        if (uri == null) {
            _state.update { it.copy(message = "Not saved. The patched ROM is still ready if you want to choose a location.", messageIsError = false) }
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
                        if (PatchNote.SOURCE_NOT_VERIFIABLE in pending.notes) add("IPS patches can't confirm the base ROM; if the game misbehaves, try a different revision of the ROM.")
                    }
                    _state.update {
                        it.copy(busy = false, pending = null, messageIsError = false,
                            message = (listOf("Saved ${displayName(uri) ?: pending.suggestedName}.") + extra).joinToString(" "))
                    }
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
        fun factory(resolver: ContentResolver): ViewModelProvider.Factory = viewModelFactory {
            initializer { PatcherViewModel(resolver) }
        }

        fun describe(error: PatchError?): String = when (error) {
            PatchError.UnknownFormat, null -> "This isn't an IPS, BPS or UPS patch."
            is PatchError.Malformed -> "The patch file is malformed (${error.detail})."
            PatchError.PatchDamaged -> "The patch file is damaged or incomplete (its checksum doesn't match). Try downloading it again."
            is PatchError.WrongSourceRom -> "This patch was made for a different ROM (expected CRC32 ${hex(error.expectedCrc32)}, " +
                "${error.expectedSize} bytes; yours is ${hex(error.actualCrc32)}). Check the patch's readme for the exact ROM version it needs."
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
