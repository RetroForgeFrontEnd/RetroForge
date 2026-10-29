package com.retroforge.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.retroforge.app.AppContainer
import com.retroforge.app.R
import com.retroforge.app.ui.hacks.CatalogueSourcesScreen
import com.retroforge.app.ui.hacks.HackDetailScreen
import com.retroforge.app.ui.hacks.HacksScreen
import com.retroforge.app.ui.hacks.HacksViewModel
import com.retroforge.app.ui.home.HomeScreen
import com.retroforge.app.ui.library.CoverPickerScreen
import com.retroforge.app.ui.library.GameDetailScreen
import com.retroforge.app.ui.library.LibraryFoldersScreen
import com.retroforge.app.ui.library.LibraryViewModel
import com.retroforge.app.ui.library.PlaylistScreen
import com.retroforge.app.ui.library.RetroScreen
import com.retroforge.app.ui.library.SystemGamesScreen
import com.retroforge.app.ui.links.LinksScreen
import com.retroforge.app.ui.manager.RomManagerScreen
import com.retroforge.app.ui.manager.RomManagerViewModel
import com.retroforge.app.ui.patcher.PatcherScreen
import com.retroforge.app.ui.patcher.PatcherViewModel
import com.retroforge.app.ui.pc.PcScreen
import com.retroforge.app.ui.pc.PcViewModel
import com.retroforge.app.ui.settings.AboutScreen
import com.retroforge.app.ui.settings.GpuDriversScreen
import com.retroforge.app.ui.settings.HotkeysScreen
import com.retroforge.app.ui.settings.LicencesScreen
import com.retroforge.app.ui.settings.PrivacyScreen
import com.retroforge.app.ui.settings.SettingsScreen
import com.retroforge.app.ui.settings.SettingsViewModel
import com.retroforge.app.ui.settings.ThemesScreen
import com.retroforge.app.ui.settings.ThemesViewModel
import com.retroforge.app.ui.settings.TutorialScreen
import com.retroforge.app.ui.setup.SetupScreen
import com.retroforge.app.ui.setup.SetupViewModel
import com.retroforge.core.settings.ThemeMode
import com.retroforge.core.ui.NavCommand
import com.retroforge.core.ui.RetroForgeTheme
import com.retroforge.core.ui.SectionTab
import com.retroforge.core.ui.Wordmark
import kotlinx.coroutines.flow.Flow

enum class Section(val route: String, val label: String, @DrawableRes val icon: Int) {
    HOME("home", "Home", R.drawable.ic_section_home),
    RETRO("retro", "Retro", R.drawable.ic_section_retro),
    HACKS("hacks", "Hacks", R.drawable.ic_section_hacks),
    PC("pc", "PC", R.drawable.ic_section_pc),
    MEDIA("media", "Media", R.drawable.ic_section_media),
    VR("vr", "VR", R.drawable.ic_section_vr),
    APPS("apps", "Android", R.drawable.ic_section_apps),
    LINKS("links", "Links", R.drawable.ic_section_links),
    SETTINGS("settings", "Settings", R.drawable.ic_section_settings),
}

private const val PATCHER_ROUTE = "retro/patcher"
private const val HACK_DETAIL_ROUTE = "hacks/detail/{source}/{hack}"
private const val HACK_SOURCES_ROUTE = "hacks/sources"
private const val FOLDERS_ROUTE = "retro/folders"
private const val SETUP_ROUTE = "setup"
private const val ABOUT_ROUTE = "settings/about"
private const val HOTKEYS_ROUTE = "settings/hotkeys"
private const val GPU_ROUTE = "settings/gpu"
private const val TEXTURES_ROUTE = "settings/textures"
private const val CHECK_ROUTE = "settings/check"
private const val TUTORIAL_ROUTE = "settings/tutorial"
private const val THEMES_ROUTE = "settings/themes"
private const val LICENCES_ROUTE = "settings/licences"
private const val PRIVACY_ROUTE = "settings/privacy"
private const val SYSTEM_ROUTE = "retro/system/{system}"
private const val GAME_ROUTE = "retro/game/{id}"
private const val COVER_ROUTE = "retro/game/{id}/cover"
private const val EDIT_ROUTE = "retro/game/{id}/edit"
private const val MANAGER_ROUTE = "retro/manager"
private const val MANAGER_SORT_ROUTE = "retro/manager/sort"
private const val PLAYLIST_ROUTE = "retro/playlist/{id}"
private const val MANAGER_DELETE_ROUTE = "retro/manager/delete/{id}"

@Composable
fun RetroForgeRoot(container: AppContainer, sectionCommands: Flow<NavCommand>) {
    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container.settings))
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    val userThemes by container.themes.userThemes.collectAsStateWithLifecycle()
    val theme = remember(settings.themeId, userThemes) { container.themes.find(settings.themeId) }

    RetroForgeTheme(theme = theme, dark = dark) {
        // null until settings have loaded, so returning users never see the notice flash up.
        val acknowledged by settingsViewModel.noticeAcknowledged.collectAsStateWithLifecycle()
        if (acknowledged == null || acknowledged!! < LEGAL_NOTICE_VERSION) {
            Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                if (acknowledged != null) {
                    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                        LegalNoticeScreen(onAcknowledge = { settingsViewModel.acknowledgeNotice(LEGAL_NOTICE_VERSION) })
                    }
                }
            }
            return@RetroForgeTheme
        }
        val nav = rememberNavController()
        // Back on Home asks before closing RetroForge, so a stray Back (or B on a controller) doesn't drop out of the app.
        val atHome = nav.currentBackStackEntryAsState().value?.destination?.route == Section.HOME.route
        var askClose by remember { mutableStateOf(false) }
        androidx.activity.compose.BackHandler(enabled = atHome && nav.previousBackStackEntry == null) { askClose = true }
        if (askClose) {
            val activity = androidx.activity.compose.LocalActivity.current
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { askClose = false },
                title = { androidx.compose.material3.Text("Close RetroForge?") },
                confirmButton = { androidx.compose.material3.TextButton(onClick = { askClose = false; activity?.finish() }) { androidx.compose.material3.Text("Close") } },
                dismissButton = { androidx.compose.material3.TextButton(onClick = { askClose = false }) { androidx.compose.material3.Text("Stay") } },
            )
        }
        // First run (after the notice): offer the setup wizard once; afterwards it's in Settings.
        val setupDone by settingsViewModel.setupDone.collectAsStateWithLifecycle()
        LaunchedEffect(setupDone) {
            if (setupDone == false) {
                settingsViewModel.markSetupOffered()
                nav.navigate(SETUP_ROUTE)
            }
        }
        val sections = Section.entries.filter { it != Section.PC || settings.showPcSection }
        val backStack by nav.currentBackStackEntryAsState()
        val route = backStack?.destination?.route
        val current = sections.firstOrNull { it.route == route } ?: when {
            route == PATCHER_ROUTE || route?.startsWith("retro/") == true -> Section.RETRO
            route?.startsWith("hacks/") == true -> Section.HACKS
            route == SETUP_ROUTE || route == ABOUT_ROUTE || route == HOTKEYS_ROUTE || route == GPU_ROUTE || route == TEXTURES_ROUTE || route == CHECK_ROUTE || route == TUTORIAL_ROUTE || route == THEMES_ROUTE || route == LICENCES_ROUTE || route == PRIVACY_ROUTE -> Section.SETTINGS
            else -> Section.HOME
        }
        val libraryViewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(container.library, container.retroArch, container.covers, container.standalone, container.settings, container.retroArchHotkeys, container.cores, container.coreChoices, container.systemArt, container.contentResolver, container.viewPrefs, container.canvasArt))
        val hacksViewModel: HacksViewModel = viewModel(
            factory = HacksViewModel.factory(container.catalogues, container.catalogueFetcher, container.contentResolver),
        )

        val latestSections by rememberUpdatedState(sections)
        val latestCurrent by rememberUpdatedState(current)
        LaunchedEffect(sectionCommands) {
            sectionCommands.collect { command ->
                val index = latestSections.indexOf(latestCurrent).coerceAtLeast(0)
                val step = when (command) {
                    NavCommand.PREVIOUS_SECTION -> -1
                    NavCommand.NEXT_SECTION -> 1
                    else -> return@collect
                }
                nav.goTo(latestSections[(index + step + latestSections.size) % latestSections.size])
            }
        }
        LaunchedEffect(settings.showPcSection) {
            if (!settings.showPcSection && latestCurrent == Section.PC) nav.goTo(Section.HOME)
        }

        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
                // Inner pages (anything that isn't a main tab) get a close button, so touch users never need the
                // phone's back gesture. A controller's B does the same.
                val inner = route != null && sections.none { it.route == route }
                // Main tabs other than Home get one too, back to Home; a page's own steps can take it over.
                val closeOverride = remember { mutableStateOf<(() -> Unit)?>(null) }
                val content: @Composable (Modifier) -> Unit = { modifier ->
                  Box(modifier) {
                   androidx.compose.runtime.CompositionLocalProvider(LocalCloseOverride provides closeOverride) {
                    NavHost(nav, startDestination = Section.HOME.route, modifier = Modifier.fillMaxSize()) {
                        composable(Section.HOME.route) {
                            HomeScreen(
                                showPc = settings.showPcSection, library = libraryViewModel, onOpen = { nav.goTo(it) },
                                onOpenApps = { nav.goTo(Section.APPS) },
                                onOpenGame = { nav.navigate("retro/game/$it") }, onOpenSystem = { nav.navigate("retro/system/$it") },
                                onOpenPlaylist = { nav.navigate("retro/playlist/$it") },
                            )
                        }
                        composable(Section.RETRO.route) {
                            RetroScreen(
                                libraryViewModel,
                                onOpenFolders = { nav.navigate(FOLDERS_ROUTE) },
                                onOpenSystem = { nav.navigate("retro/system/$it") },
                                onOpenGame = { nav.navigate("retro/game/$it") },
                                onOpenPatcher = { nav.navigate(PATCHER_ROUTE) },
                                onOpenManager = { nav.navigate(MANAGER_ROUTE) },
                                onOpenPlaylist = { nav.navigate("retro/playlist/$it") },
                            )
                        }
                        composable(FOLDERS_ROUTE) { LibraryFoldersScreen(libraryViewModel) }
                        composable(PLAYLIST_ROUTE) { entry ->
                            PlaylistScreen(
                                libraryViewModel,
                                entry.arguments?.getString("id")?.toLongOrNull() ?: -1,
                                onOpenGame = { nav.navigate("retro/game/$it") },
                                onDeleted = { nav.popBackStack() },
                            )
                        }
                        composable(MANAGER_ROUTE) {
                            val managerViewModel: RomManagerViewModel = viewModel(factory = RomManagerViewModel.factory(container.library, container.romManager))
                            RomManagerScreen(managerViewModel)
                        }
                        composable(MANAGER_SORT_ROUTE) {
                            val managerViewModel: RomManagerViewModel = viewModel(factory = RomManagerViewModel.factory(container.library, container.romManager))
                            LaunchedEffect(Unit) { managerViewModel.tab.value = com.retroforge.app.ui.manager.ManagerTab.SORT }
                            RomManagerScreen(managerViewModel)
                        }
                        composable(MANAGER_DELETE_ROUTE) { entry ->
                            val managerViewModel: RomManagerViewModel = viewModel(factory = RomManagerViewModel.factory(container.library, container.romManager))
                            RomManagerScreen(managerViewModel, deleteGameId = entry.arguments?.getString("id")?.toLongOrNull() ?: -1)
                        }
                        composable(SETUP_ROUTE) {
                            val setupViewModel: SetupViewModel = viewModel(
                                factory = SetupViewModel.factory(container.library, container.retroArch, container.settings, container.contentResolver, container.playerSystemDir, container.cores),
                            )
                            val setupCounts by container.library.systemCounts.collectAsStateWithLifecycle(initialValue = emptyList())
                            SetupScreen(setupViewModel, onOpenSort = { nav.navigate(MANAGER_SORT_ROUTE) }, systems = setupCounts.map { it.system to container.library.systemName(it.system) }, onDone = {
                                // Take setup off the stack first, or Home's saved stack would bring it back.
                                nav.popBackStack(SETUP_ROUTE, inclusive = true)
                                nav.goTo(Section.RETRO)
                            })
                        }
                        composable(SYSTEM_ROUTE) { entry ->
                            SystemGamesScreen(libraryViewModel, entry.arguments?.getString("system").orEmpty(), onOpenGame = { nav.navigate("retro/game/$it") })
                        }
                        composable(GAME_ROUTE) { entry ->
                            GameDetailScreen(
                                libraryViewModel,
                                entry.arguments?.getString("id")?.toLongOrNull() ?: -1,
                                onOpenGame = { nav.navigate("retro/game/$it") },
                                onChangeCover = { nav.navigate("retro/game/$it/cover") },
                                onDelete = { nav.navigate("retro/manager/delete/$it") },
                                onEditDetails = { nav.navigate("retro/game/$it/edit") },
                            )
                        }
                        composable(EDIT_ROUTE) { entry ->
                            com.retroforge.app.ui.library.EditDetailsScreen(
                                libraryViewModel,
                                entry.arguments?.getString("id")?.toLongOrNull() ?: -1,
                                onDone = { nav.popBackStack() },
                            )
                        }
                        composable(COVER_ROUTE) { entry ->
                            CoverPickerScreen(
                                libraryViewModel,
                                entry.arguments?.getString("id")?.toLongOrNull() ?: -1,
                                hasSteamGridDbKey = !settings.steamGridDbKey.isNullOrBlank(),
                                onOpenSettings = { nav.goTo(Section.SETTINGS) },
                                onDone = { nav.popBackStack() },
                            )
                        }
                        composable(PATCHER_ROUTE) {
                            val patcherViewModel: PatcherViewModel = viewModel(factory = PatcherViewModel.factory(container.contentResolver, onSaved = container::rescanAll))
                            PatcherScreen(patcherViewModel)
                        }
                        composable(Section.HACKS.route) {
                            HacksScreen(
                                hacksViewModel,
                                libraryViewModel,
                                onOpenGame = { nav.navigate("retro/game/$it") },
                                onOpenRetro = { nav.goTo(Section.RETRO) },
                                onOpenHack = { key -> nav.navigate("hacks/detail/$key") },
                                onOpenSources = { nav.navigate(HACK_SOURCES_ROUTE) },
                            )
                        }
                        composable(HACK_DETAIL_ROUTE) { entry ->
                            val key = "${entry.arguments?.getString("source")}/${entry.arguments?.getString("hack")}"
                            HackDetailScreen(
                                hacksViewModel,
                                key,
                                onOpenPatcher = { nav.navigate(PATCHER_ROUTE) },
                                onOpenHack = { other -> nav.navigate("hacks/detail/$other") },
                            )
                        }
                        composable(HACK_SOURCES_ROUTE) { CatalogueSourcesScreen(hacksViewModel) }
                        composable(Section.PC.route) {
                            val pcViewModel: PcViewModel = viewModel(factory = PcViewModel.factory(container.pcStatus))
                            PcScreen(pcViewModel, container.droidDeck, container.library)
                        }
                        composable(Section.LINKS.route) { LinksScreen() }
                        composable(Section.SETTINGS.route) {
                            val counts by container.library.systemCounts.collectAsStateWithLifecycle(initialValue = emptyList())
                            SettingsScreen(settingsViewModel,
                                playerSystems = counts.map { it.system to container.library.systemName(it.system) }, coreChoices = container.coreChoices, viewPrefs = container.viewPrefs, onOpenSetup = { nav.navigate(SETUP_ROUTE) }, onOpenAbout = { nav.navigate(ABOUT_ROUTE) }, onOpenHotkeys = { nav.navigate(HOTKEYS_ROUTE) }, onOpenGpu = { nav.navigate(GPU_ROUTE) }, onOpenTexturePacks = { nav.navigate(TEXTURES_ROUTE) }, onOpenCheck = { nav.navigate(CHECK_ROUTE) }, library = container.library, onOpenApps = { nav.goTo(Section.APPS) }, onOpenTutorial = { nav.navigate(TUTORIAL_ROUTE) }, onOpenThemes = { nav.navigate(THEMES_ROUTE) },
                                onOpenLicences = { nav.navigate(LICENCES_ROUTE) }, onOpenPrivacy = { nav.navigate(PRIVACY_ROUTE) },
                            )
                        }
                        composable(ABOUT_ROUTE) { AboutScreen() }
                        composable(HOTKEYS_ROUTE) { HotkeysScreen(settingsViewModel) }
                        composable(GPU_ROUTE) { GpuDriversScreen() }
                        composable(TEXTURES_ROUTE) { com.retroforge.app.ui.settings.TexturePacksScreen() }
                        composable(CHECK_ROUTE) {
                            com.retroforge.app.ui.settings.SetupCheckScreen(container.library, container.cores, container.playerSystemDir, onOpenSetup = { nav.navigate(SETUP_ROUTE) })
                        }
                        composable(Section.APPS.route) { com.retroforge.app.ui.apps.AppsScreen() }
                        composable(Section.VR.route) {
                            com.retroforge.app.ui.vr.VrScreen(container.viewPrefs, container.library, onOpenGame = { nav.navigate("retro/game/$it") }, onOpenMedia = { nav.goTo(Section.MEDIA) })
                        }
                        composable(Section.MEDIA.route) { com.retroforge.app.ui.media.MediaScreen(container.viewPrefs) }
                        composable(TUTORIAL_ROUTE) { TutorialScreen() }
                        composable(LICENCES_ROUTE) { LicencesScreen() }
                        composable(PRIVACY_ROUTE) { PrivacyScreen() }
                        composable(THEMES_ROUTE) {
                            val themesViewModel: ThemesViewModel = viewModel(factory = ThemesViewModel.factory(container.settings, container.themes, container.contentResolver))
                            ThemesScreen(themesViewModel)
                        }
                    }
                   }
                    val override = closeOverride.value
                    if (inner || override != null || current != Section.HOME) CloseButton(Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 12.dp)) {
                        com.retroforge.core.ui.UiSounds.back()
                        when {
                            override != null -> override()
                            inner -> nav.popBackStack()
                            else -> nav.goTo(Section.HOME)
                        }
                    }
                  }
                }
                if (maxWidth >= 600.dp) {
                    Row(Modifier.fillMaxSize()) {
                        SectionNav(sections, current, vertical = true, onSelect = { nav.goTo(it) })
                        content(Modifier.weight(1f).fillMaxHeight())
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        content(Modifier.weight(1f).fillMaxWidth())
                        SectionNav(sections, current, vertical = false, onSelect = { nav.goTo(it) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionNav(sections: List<Section>, current: Section, vertical: Boolean, onSelect: (Section) -> Unit) {
    val initialFocus = remember { FocusRequester() }
    val inputMode = LocalInputModeManager.current.inputMode
    // Without a touchscreen nothing is focused at launch, so D-pad users need a starting point.
    LaunchedEffect(Unit) {
        if (inputMode == InputMode.Keyboard) runCatching { initialFocus.requestFocus() }
    }
    val tab: @Composable (Section, Modifier) -> Unit = { section, modifier ->
        SectionTab(
            label = section.label,
            icon = painterResource(section.icon),
            selected = section == current,
            onClick = { onSelect(section) },
            modifier = if (section == current) modifier.focusRequester(initialFocus) else modifier,
            compact = !vertical,
        )
    }
    if (vertical) {
        Column(
            // Scrolls when the screen is short (a phone turned sideways), so every section stays reachable.
            modifier = Modifier.fillMaxHeight().background(MaterialTheme.colorScheme.surface).verticalScroll(rememberScrollState()).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Wordmark(short = true, fontSize = 24.sp)
            Spacer(Modifier.height(16.dp))
            sections.forEach { tab(it, Modifier) }
        }
    } else {
        // Out of the way while typing: the keyboard would push the bar up over the text field.
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        if (WindowInsets.isImeVisible) return
        BoxWithConstraints(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
            // Too many tabs for the width (8 on a phone): they keep a readable size and the bar scrolls sideways.
            val fits = maxWidth / sections.size >= 64.dp
            Row(
                modifier = Modifier.fillMaxWidth().then(if (fits) Modifier else Modifier.horizontalScroll(rememberScrollState()))
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                sections.forEach { tab(it, if (fits) Modifier.weight(1f) else Modifier.width(76.dp)) }
            }
        }
    }
}

private fun NavHostController.goTo(section: Section) {
    navigate(section.route) {
        popUpTo(Section.HOME.route) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Red ✕ in the corner of every page but Home: goes back one step (from a main tab, to Home). */
@Composable
private fun CloseButton(modifier: Modifier, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color(0xFFD9363E))
            .border(if (focused) 3.dp else 0.dp, Color.White, CircleShape)
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClickLabel = "Back", onClick = onClick)
            .semantics { contentDescription = "Back" },
        contentAlignment = Alignment.Center,
    ) {
        Text("✕", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

