package com.retroforge.app.ui.files

import android.content.ContentResolver
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.retroforge.app.R
import com.retroforge.core.library.LibraryRepository
import com.retroforge.core.ui.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A folder the browser can open: a game collection, or one added in the browser. */
data class Place(val tree: Uri, val label: String, val added: Boolean)

enum class FileSort { NAME, SIZE, DATE }

/** What the browser is busy with, or has to say. */
sealed interface FileTask {
    data class Running(val moving: Boolean, val name: String, val fraction: Float?) : FileTask
    data class Done(val problems: List<String>) : FileTask
    data class Failed(val reason: String) : FileTask
}

/** A question waiting on the user. */
sealed interface FileAsk {
    data class Delete(val entries: List<FileEntry>) : FileAsk
    data class Rename(val entry: FileEntry) : FileAsk
    data object NewFolder : FileAsk
    /** The folder was granted read-only; picking it again can allow changes. */
    data class WriteAccess(val place: Place, val wrongPick: Boolean = false) : FileAsk
}

data class Clipboard(val entries: List<FileEntry>, val cut: Boolean)

class FileBrowserViewModel(
    library: LibraryRepository,
    private val resolver: ContentResolver,
    private val prefs: SharedPreferences,
) : ViewModel() {
    private val ops = FileOps(resolver)
    private val added = MutableStateFlow(prefs.getStringSet(ADDED_PLACES, emptySet()).orEmpty().toList().sorted())

    val places: StateFlow<List<Place>?> = combine(library.roots, added) { roots, extra ->
        val fromLibrary = roots.map { Uri.parse(it.treeUri) }
        (fromLibrary.map { Place(it, label(it), added = false) } +
            extra.map(Uri::parse).filter { it !in fromLibrary }.map { Place(it, label(it), added = true) })
            .distinctBy { it.tree }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val place = MutableStateFlow<Place?>(null)
    /** Folders opened inside the place: (document id, name), the place itself first. */
    val path = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val entries = MutableStateFlow<List<FileEntry>?>(null)
    val error = MutableStateFlow<String?>(null)
    val sort = MutableStateFlow(FileSort.valueOf(prefs.getString(SORT, null) ?: FileSort.NAME.name))
    val showHidden = MutableStateFlow(prefs.getBoolean(SHOW_HIDDEN, false))
    val filter = MutableStateFlow("")
    val selecting = MutableStateFlow(false)
    val selected = MutableStateFlow<Set<String>>(emptySet())
    val clipboard = MutableStateFlow<Clipboard?>(null)
    val task = MutableStateFlow<FileTask?>(null)
    val ask = MutableStateFlow<FileAsk?>(null)
    private var job: Job? = null
    @Volatile private var cancelled = false

    fun open(p: Place) {
        place.value = p
        path.value = listOf(ops.rootId(p.tree) to p.label)
        clearSelection()
        reload()
    }

    fun enter(folder: FileEntry) {
        path.value = path.value + (folder.id to folder.name)
        clearSelection()
        filter.value = ""
        reload()
    }

    /** One folder up; false at the top of the place. */
    fun up(): Boolean {
        if (path.value.size <= 1) return false
        path.value = path.value.dropLast(1)
        clearSelection()
        filter.value = ""
        reload()
        return true
    }

    fun goTo(depth: Int) {
        if (depth >= path.value.size - 1) return
        path.value = path.value.take(depth + 1)
        clearSelection()
        reload()
    }

    fun reload() {
        val p = place.value ?: return
        val folder = path.value.lastOrNull()?.first ?: return
        viewModelScope.launch {
            error.value = null
            entries.value = try {
                withContext(Dispatchers.IO) { ops.list(p.tree, folder) }
            } catch (e: SecurityException) {
                error.value = str(R.string.files_no_access)
                emptyList()
            } catch (e: Exception) {
                error.value = e.message ?: e.javaClass.simpleName
                emptyList()
            }
        }
    }

    fun setSort(s: FileSort) { sort.value = s; prefs.edit { putString(SORT, s.name) } }
    fun setShowHidden(show: Boolean) { showHidden.value = show; prefs.edit { putBoolean(SHOW_HIDDEN, show) } }

    fun toggle(entry: FileEntry) {
        selected.value = if (entry.id in selected.value) selected.value - entry.id else selected.value + entry.id
    }

    fun startSelecting(entry: FileEntry? = null) {
        selecting.value = true
        if (entry != null) selected.value = selected.value + entry.id
    }

    fun clearSelection() { selecting.value = false; selected.value = emptySet() }

    fun selectAll(visible: List<FileEntry>) { selected.value = visible.map { it.id }.toSet() }

    private fun chosen(): List<FileEntry> = entries.value.orEmpty().filter { it.id in selected.value }

    fun copy(cut: Boolean) {
        val picked = chosen().ifEmpty { return }
        clipboard.value = Clipboard(picked, cut)
        clearSelection()
    }

    fun clearClipboard() { clipboard.value = null }

    fun paste() {
        val clip = clipboard.value ?: return
        val p = place.value ?: return
        val folder = path.value.last().first
        cancelled = false
        job = viewModelScope.launch {
            task.value = FileTask.Running(clip.cut, clip.entries.first().name, null)
            task.value = try {
                val problems = withContext(Dispatchers.IO) {
                    ops.transfer(clip.entries, p.tree, folder, clip.cut, { done, total, name ->
                        task.value = FileTask.Running(clip.cut, name, if (total > 0) done.toFloat() / total else null)
                    }, { cancelled })
                }
                if (clip.cut) clipboard.value = null
                if (problems.isEmpty()) null else FileTask.Done(problems)
            } catch (e: FileOps.CancelledException) {
                FileTask.Done(listOf(str(R.string.files_stopped)))
            } catch (e: SecurityException) {
                ask.value = FileAsk.WriteAccess(p)
                null
            } catch (e: Exception) {
                FileTask.Failed(e.message ?: e.javaClass.simpleName)
            }
            reload()
        }
    }

    fun stop() { cancelled = true }
    fun dismissTask() { task.value = null }

    fun askDelete() { chosen().ifEmpty { return }.let { ask.value = FileAsk.Delete(it) } }
    fun askRename() { chosen().singleOrNull()?.let { ask.value = FileAsk.Rename(it) } }
    fun askNewFolder() { ask.value = FileAsk.NewFolder }
    fun dismissAsk() { ask.value = null }

    fun delete(list: List<FileEntry>) = write {
        val problems = list.mapNotNull { e -> runCatching { ops.delete(e) }.exceptionOrNull()?.let { if (it is SecurityException) throw it; "${e.name}: ${it.message}" } }
        clearSelection()
        problems
    }

    fun rename(entry: FileEntry, name: String) = write { ops.rename(entry, name.trim()); clearSelection(); emptyList() }

    fun newFolder(name: String) = write {
        val p = place.value!!
        ops.createFolder(p.tree, path.value.last().first, name.trim())
        emptyList()
    }

    /** Runs a change; a refusal from storage asks for write access instead. */
    private fun write(action: () -> List<String>) {
        val p = place.value ?: return
        ask.value = null
        viewModelScope.launch {
            try {
                val problems = withContext(Dispatchers.IO) { action() }
                if (problems.isNotEmpty()) task.value = FileTask.Done(problems)
            } catch (e: SecurityException) {
                ask.value = FileAsk.WriteAccess(p)
            } catch (e: Exception) {
                task.value = FileTask.Failed(e.message ?: e.javaClass.simpleName)
            }
            reload()
        }
    }

    /** The user picked a folder to add to the list. */
    fun addPlace(picked: Uri) {
        runCatching { resolver.takePersistableUriPermission(picked, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
            .recoverCatching { resolver.takePersistableUriPermission(picked, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        val list = (added.value + picked.toString()).distinct()
        added.value = list
        prefs.edit { putStringSet(ADDED_PLACES, list.toSet()) }
        open(Place(picked, label(picked), added = true))
    }

    fun removePlace(p: Place) {
        val list = added.value - p.tree.toString()
        added.value = list
        prefs.edit { putStringSet(ADDED_PLACES, list.toSet()) }
        // A game collection keeps its own access; only a folder added here gives it up.
        if (places.value.orEmpty().none { !it.added && it.tree == p.tree }) {
            runCatching { resolver.releasePersistableUriPermission(p.tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        }
        if (place.value?.tree == p.tree) { place.value = null; entries.value = null; path.value = emptyList() }
    }

    /** The user picked a folder to allow changes in [p]; it has to be the same one. */
    fun grantWrite(p: Place, picked: Uri) {
        if (DocumentsContract.getTreeDocumentId(picked) != DocumentsContract.getTreeDocumentId(p.tree) || picked.authority != p.tree.authority) {
            ask.value = FileAsk.WriteAccess(p, wrongPick = true)
            return
        }
        runCatching { resolver.takePersistableUriPermission(picked, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        ask.value = null
    }

    fun sorted(list: List<FileEntry>, sort: FileSort, hidden: Boolean, filter: String): List<FileEntry> {
        val shown = list.filter { (hidden || !it.name.startsWith(".")) && (filter.isBlank() || it.name.contains(filter.trim(), ignoreCase = true)) }
        val order = when (sort) {
            FileSort.NAME -> compareBy<FileEntry> { it.name.lowercase() }
            FileSort.SIZE -> compareByDescending<FileEntry> { it.size }.thenBy { it.name.lowercase() }
            FileSort.DATE -> compareByDescending<FileEntry> { it.modified }.thenBy { it.name.lowercase() }
        }
        return shown.sortedWith(compareBy<FileEntry> { !it.isFolder }.then(order))
    }

    companion object {
        private const val ADDED_PLACES = "file_browser_places"
        private const val SORT = "file_browser_sort"
        private const val SHOW_HIDDEN = "file_browser_hidden"

        /** "ManagerTest/roms", or "Retrobat (SD card)"; a whole drive is "Internal storage" or "SD card". */
        fun label(tree: Uri): String {
            val id = DocumentsContract.getTreeDocumentId(tree)
            val volume = id.substringBefore(':')
            val folder = id.substringAfter(':', "").trimEnd('/')
            val internal = volume == "primary"
            return when {
                folder.isEmpty() -> str(if (internal) R.string.files_internal_storage else R.string.files_sd_card)
                internal -> folder.split('/').takeLast(2).joinToString("/")
                else -> folder.split('/').takeLast(2).joinToString("/") + " (" + str(R.string.files_sd_card) + ")"
            }
        }

        fun factory(library: LibraryRepository, resolver: ContentResolver, prefs: SharedPreferences): ViewModelProvider.Factory = viewModelFactory {
            initializer { FileBrowserViewModel(library, resolver, prefs) }
        }
    }
}
