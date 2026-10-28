package com.retroforge.app.ui.settings

import com.retroforge.core.ui.ThemeBackdrop
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.HorizontalPager
import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.app.ui.ScreenFrame
import com.retroforge.core.library.SafScanNode
import com.retroforge.core.settings.SettingsRepository
import com.retroforge.core.settings.ThemeMode
import com.retroforge.core.settings.ThemeStore
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.FocusChip
import com.retroforge.core.ui.RetroForgeTheme
import com.retroforge.core.ui.StatusPill
import com.retroforge.core.ui.StatusTone
import com.retroforge.core.ui.Wordmark
import com.retroforge.library.ScanNode
import com.retroforge.theme.EsThemeColors
import com.retroforge.theme.ImportReport
import com.retroforge.theme.ThemeFormatException
import com.retroforge.theme.ThemeSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A converted theme waiting for the user to look at and save. */
data class PendingImport(val theme: ThemeSpec?, val report: ImportReport, val source: String)

class ThemesViewModel(private val settings: SettingsRepository, private val store: ThemeStore, private val resolver: ContentResolver) : ViewModel() {
    val current = settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val userThemes: StateFlow<List<ThemeSpec>> = store.userThemes
    val builtIn = store.builtIn
    val message = MutableStateFlow<Pair<String, Boolean>?>(null)
    val pending = MutableStateFlow<PendingImport?>(null)
    val working = MutableStateFlow(false)

    fun use(theme: ThemeSpec) { viewModelScope.launch { settings.setThemeId(theme.id) } }
    fun setMode(mode: ThemeMode) { viewModelScope.launch { settings.setThemeMode(mode) } }

    fun delete(theme: ThemeSpec) {
        store.delete(theme.id)
        if (current.value?.themeId == theme.id) use(store.builtIn.first())
    }

    /** A RetroForge theme file (.json) the user picked. */
    fun importFile(uri: Uri) {
        viewModelScope.launch {
            message.value = try {
                val text = withContext(Dispatchers.IO) {
                    resolver.openInputStream(uri)?.use { it.readBytes() }?.takeIf { it.size <= 64 * 1024 }?.toString(Charsets.UTF_8)
                } ?: throw ThemeFormatException("couldn't read the file, or it's larger than 64 KB")
                val saved = store.importFile(text)
                "Added \"${saved.name}\"." to false
            } catch (e: ThemeFormatException) {
                "That isn't a RetroForge theme: ${e.message}" to true
            }
        }
    }

    /** Reads the colour variables of an ES-DE / RetroBat / Batocera theme folder (read-only). */
    fun importEsFolder(tree: Uri) {
        viewModelScope.launch {
            working.value = true
            pending.value = withContext(Dispatchers.IO) {
                val root = SafScanNode.root(resolver, tree, "theme")
                val name = runCatching { tree.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') }.getOrNull()?.ifBlank { null } ?: "Imported theme"
                val xmls = mutableListOf<String>()
                var licence: String? = null
                fun walk(node: ScanNode, depth: Int) {
                    for (child in runCatching { node.children() }.getOrDefault(emptyList())) {
                        if (xmls.size >= 200) return
                        val lower = child.name.lowercase()
                        when {
                            child.isDirectory && depth < 3 -> walk(child, depth + 1)
                            lower.endsWith(".xml") && child.size in 1..512 * 1024 -> runCatching { child.open().use { xmls += it.readBytes().toString(Charsets.UTF_8) } }
                            depth == 0 && (lower.startsWith("licen") || lower.startsWith("readme")) && child.size < 256 * 1024 ->
                                licence = licence ?: runCatching { child.open().use { EsThemeColors.licenseOf(it.readBytes().toString(Charsets.UTF_8)) } }.getOrNull()
                        }
                    }
                }
                walk(root, 0)
                val (theme, report) = EsThemeColors.import(name, xmls, licence)
                PendingImport(theme, report, name)
            }
            working.value = false
        }
    }

    fun savePending() {
        val p = pending.value?.theme ?: return
        val saved = store.save(p)
        pending.value = null
        use(saved)
        message.value = "Added and switched to \"${saved.name}\"." to false
    }

    fun cancelPending() { pending.value = null }

    companion object {
        fun factory(settings: SettingsRepository, store: ThemeStore, resolver: ContentResolver): ViewModelProvider.Factory = viewModelFactory {
            initializer { ThemesViewModel(settings, store, resolver) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemesScreen(viewModel: ThemesViewModel) {
    val current by viewModel.current.collectAsStateWithLifecycle()
    val user by viewModel.userThemes.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val working by viewModel.working.collectAsStateWithLifecycle()
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(viewModel::importFile) }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(viewModel::importEsFolder) }
    val mode = current?.themeMode ?: ThemeMode.DARK
    val dark = mode != ThemeMode.LIGHT

    ScreenFrame(title = "Themes", subtitle = "Each preview below is drawn in that theme. Themes change colours, shapes, type and how game lists look.") {
        message?.let { (text, isError) ->
            StatusPill(if (isError) "Problem" else "Done", if (isError) StatusTone.BAD else StatusTone.GOOD)
            CardBody(text)
        }

        CardTitle("Brightness")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ThemeMode.entries.forEach { m ->
                FocusChip((if (m == mode) "● " else "") + m.name.lowercase().replaceFirstChar { it.uppercase() }, onClick = { viewModel.setMode(m) })
            }
        }
        CardBody("Some themes are dark-only or light-only and keep their look either way.")

        pending?.let { p -> PendingCard(p, dark, viewModel) }
        if (working) CardBody("Reading the theme's colours …")

        // Swipe (or press left/right) through every theme; each page is drawn in its own theme.
        val all = viewModel.builtIn + user
        val startPage = all.indexOfFirst { it.id == current?.themeId }.coerceAtLeast(0)
        val pager = rememberPagerState(initialPage = startPage) { all.size }
        val scope = rememberCoroutineScope()
        HorizontalPager(state = pager, contentPadding = PaddingValues(horizontal = 24.dp), pageSpacing = 16.dp, modifier = Modifier.fillMaxWidth()) { page ->
            ThemePreview(all[page], dark)
        }
        val shown = all.getOrNull(pager.currentPage)
        if (shown != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FocusChip("‹", enabled = pager.currentPage > 0, onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } })
                Text("${pager.currentPage + 1} / ${all.size}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 10.dp))
                FocusChip("›", enabled = pager.currentPage < all.lastIndex, onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } })
            }
            Text(shown.name, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
            if (shown.description.isNotEmpty()) CardBody(shown.description)
            CardBody(listOfNotNull(shown.author.ifEmpty { null }, shown.license, shown.importedFrom).joinToString(" · "))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (shown.id == current?.themeId) StatusPill("In use", StatusTone.GOOD)
                else FocusChip("Use this theme", onClick = { viewModel.use(shown) })
                if (shown in user) FocusChip("Delete", onClick = { viewModel.delete(shown) })
            }
        }

        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle("Add a theme")
            CardBody("A RetroForge theme is one small .json file (format in docs/retroforge-theme-format.md). Themes are only data: nothing in them runs.")
            CardBody(
                "From another frontend: choose a theme folder from another frontend (EmulationStation-style themes). RetroForge reads its colours only " +
                    "(layouts, carousels and artwork don't carry over) and never changes the folder. Check the theme's licence before sharing.",
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FocusChip("Import a theme file", onClick = { pickFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) })
            FocusChip("Colours from another frontend's theme", onClick = { pickFolder.launch(null) })
        }
    }
}

@Composable
private fun PendingCard(p: PendingImport, dark: Boolean, viewModel: ThemesViewModel) {
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle("From \"${p.source}\"")
        if (p.theme == null) {
            StatusPill("No colours found", StatusTone.CAUTION)
            CardBody("This theme doesn't define its colours as variables RetroForge can read.")
        } else {
            CardBody("Imported: " + p.report.used.joinToString { (role, from) -> "$role ← $from" })
        }
        CardBody("Not imported: " + p.report.notImported.joinToString())
        CardBody("Licence: " + (p.report.license ?: "not stated; ask the author before sharing it"))
    }
    p.theme?.let { ThemePreview(it, dark) }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (p.theme != null) FocusChip("Save and use", onClick = viewModel::savePending)
        FocusChip(if (p.theme != null) "Cancel" else "OK", onClick = viewModel::cancelPending)
    }
}

/** A mock-up of the home screen drawn with the real components in [theme]'s look, backdrop and fonts. */
@Composable
fun ThemePreview(theme: ThemeSpec, dark: Boolean) {
    RetroForgeTheme(theme = theme, dark = dark) {
        val colors = MaterialTheme.colorScheme
        Box(Modifier.fillMaxWidth().height(340.dp).clip(MaterialTheme.shapes.large)) {
            ThemeBackdrop {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Wordmark(fontSize = 22.sp)
                    // Hero banner.
                    Box(
                        Modifier.fillMaxWidth().height(96.dp).clip(MaterialTheme.shapes.medium)
                            .background(Brush.linearGradient(listOf(colors.primary.copy(alpha = 0.55f), colors.secondary.copy(alpha = 0.35f), colors.surface))),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text("JUMP BACK IN", style = MaterialTheme.typography.labelMedium, color = colors.primary)
                            Text("Super Game", style = MaterialTheme.typography.headlineSmall, color = colors.onBackground, maxLines = 1)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Kart", "Quest", "Racer").forEachIndexed { i, label ->
                            FocusCard(modifier = Modifier.weight(1f)) {
                                Box(Modifier.fillMaxWidth().height(28.dp).clip(MaterialTheme.shapes.small).background(if (i == 0) colors.primary else colors.surfaceVariant))
                                Text(label, style = MaterialTheme.typography.titleSmall, color = colors.onSurface, modifier = Modifier.padding(top = 6.dp))
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusPill("Ready", StatusTone.GOOD)
                        Box(Modifier.background(colors.primary, MaterialTheme.shapes.small).padding(horizontal = 12.dp, vertical = 5.dp)) {
                            Text("Play", style = MaterialTheme.typography.labelLarge, color = colors.onPrimary)
                        }
                    }
                }
            }
        }
    }
}
