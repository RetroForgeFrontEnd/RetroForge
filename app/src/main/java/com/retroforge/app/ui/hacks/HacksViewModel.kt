package com.retroforge.app.ui.hacks

import com.retroforge.app.R
import com.retroforge.core.ui.str
import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.retroforge.hacks.AddResult
import com.retroforge.hacks.CatalogueParser
import com.retroforge.hacks.CatalogueStore
import com.retroforge.hacks.ListedHack
import com.retroforge.hacks.LoadedCatalogue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HacksState(
    val loading: Boolean = true,
    val catalogues: List<LoadedCatalogue> = emptyList(),
    val hacks: List<ListedHack> = emptyList(),
    val problems: List<String> = emptyList(),
    /** null = all systems. */
    val systemFilter: String? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val messageIsError: Boolean = false,
) {
    val systems: List<String> get() = hacks.map { it.entry.system }.distinct().sortedBy { systemOrder(it) }
    val visible: Map<String, List<ListedHack>>
        get() = hacks.filter { systemFilter == null || it.entry.system == systemFilter }
            .groupBy { it.entry.system }
            .toSortedMap(compareBy<String> { systemOrder(it) }.thenBy { it })

    fun find(key: String): ListedHack? = hacks.firstOrNull { it.key == key }

    /**
     * Resolves a `requires` id within [sourceId]'s own catalogue. If that entry was merged away as a duplicate of
     * another catalogue's listing for the same page, the listing that is shown is returned instead.
     */
    fun findRequirement(sourceId: String, requiredId: String): ListedHack? {
        find("$sourceId/$requiredId")?.let { return it }
        val entry = catalogues.firstOrNull { it.catalogue.id == sourceId }?.catalogue?.hacks?.firstOrNull { it.id == requiredId }
            ?: return null
        val page = CatalogueStore.pageKey(entry.pageUrl)
        return hacks.firstOrNull { CatalogueStore.pageKey(it.entry.pageUrl) == page }
    }
}

class HacksViewModel(
    private val store: CatalogueStore,
    private val fetcher: CatalogueFetcher,
    private val resolver: ContentResolver,
) : ViewModel() {

    private val _state = MutableStateFlow(HacksState())
    val state: StateFlow<HacksState> = _state.asStateFlow()

    init {
        reload()
    }

    fun setFilter(system: String?) = _state.update { it.copy(systemFilter = system) }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    fun addFromFile(uri: Uri) = runBusy {
        val text = runCatching {
            resolver.openInputStream(uri)?.use { input ->
                val bytes = input.readNBytesCapped(CatalogueParser.MAX_BYTES + 1)
                if (bytes.size > CatalogueParser.MAX_BYTES) null else bytes.toString(Charsets.UTF_8)
            }
        }.getOrNull()
        if (text == null) str(R.string.hacks_couldn_t_read_that_file_or) to true else describe(store.add(text, url = null))
    }

    fun addFromUrl(url: String) = runBusy {
        when (val result = fetcher.fetch(url.trim())) {
            is CatalogueFetcher.Result.Ok -> describe(store.add(result.text, url.trim()))
            is CatalogueFetcher.Result.Failed -> result.reason to true
        }
    }

    fun refresh(sourceId: String) {
        val url = store.sources().firstOrNull { it.id == sourceId }?.url ?: return
        addFromUrl(url)
    }

    fun remove(sourceId: String) = runBusy {
        store.remove(sourceId)
        str(R.string.hacks_catalogue_removed) to false
    }

    private fun describe(result: AddResult): Pair<String, Boolean> = when (result) {
        is AddResult.Added -> buildString {
            append(str(R.string.hacks_added_2, result.source.name))
            if (result.warnings.isNotEmpty()) {
                append(if (result.warnings.size == 1) str(R.string.hacks_1_entry_was_skipped) else str(R.string.hacks_entries_were_skipped, result.warnings.size))
                append(result.warnings.take(3).joinToString(" "))
            }
        } to false
        is AddResult.Rejected -> result.reason to true
    }

    private fun runBusy(action: () -> Pair<String, Boolean>) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            val (message, isError) = withContext(Dispatchers.IO) { action() }
            _state.update { it.copy(busy = false, message = message, messageIsError = isError) }
            reload()
        }
    }

    private fun reload() {
        viewModelScope.launch {
            val (loaded, problems) = withContext(Dispatchers.IO) { store.load() }
            _state.update { state ->
                val hacks = CatalogueStore.merge(loaded)
                state.copy(
                    loading = false,
                    catalogues = loaded,
                    hacks = hacks,
                    problems = problems,
                    systemFilter = state.systemFilter?.takeIf { filter -> hacks.any { it.entry.system == filter } },
                )
            }
        }
    }

    companion object {
        fun factory(store: CatalogueStore, fetcher: CatalogueFetcher, resolver: ContentResolver): ViewModelProvider.Factory =
            viewModelFactory { initializer { HacksViewModel(store, fetcher, resolver) } }
    }
}

private fun java.io.InputStream.readNBytesCapped(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(16 * 1024)
    while (out.size() < limit) {
        val read = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (read < 0) break
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}

private val knownSystems = listOf(
    "nes" to "NES", "snes" to "SNES", "n64" to "Nintendo 64", "gb" to "Game Boy", "gbc" to "Game Boy Color",
    "gba" to "Game Boy Advance", "nds" to "Nintendo DS", "mastersystem" to "Master System", "megadrive" to "Mega Drive / Genesis",
    "gamegear" to "Game Gear", "megacd" to "Mega CD", "psx" to "PlayStation", "pcengine" to "PC Engine",
)

fun systemName(id: String): String = knownSystems.firstOrNull { it.first == id }?.second ?: id.uppercase()

private fun systemOrder(id: String): Int = knownSystems.indexOfFirst { it.first == id }.let { if (it < 0) Int.MAX_VALUE else it }
