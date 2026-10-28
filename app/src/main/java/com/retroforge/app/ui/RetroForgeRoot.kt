package com.retroforge.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
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
import com.retroforge.app.ui.links.LinksScreen
import com.retroforge.app.ui.setup.SetupScreen
import com.retroforge.app.ui.setup.SetupViewModel
import com.retroforge.app.ui.pc.PcScreen
import com.retroforge.app.ui.patcher.PatcherScreen
import com.retroforge.app.ui.patcher.PatcherViewModel
import com.retroforge.app.ui.pc.PcViewModel
import com.retroforge.app.ui.library.CoverPickerScreen
import com.retroforge.app.ui.library.GameDetailScreen
import com.retroforge.app.ui.library.PlaylistScreen
import com.retroforge.app.ui.manager.RomManagerScreen
import com.retroforge.app.ui.manager.RomManagerViewModel
import com.retroforge.app.ui.library.LibraryFoldersScreen
import com.retroforge.app.ui.library.LibraryViewModel
import com.retroforge.app.ui.library.RetroScreen
import com.retroforge.app.ui.library.SystemGamesScreen
import com.retroforge.app.ui.settings.AboutScreen
import com.retroforge.app.ui.settings.HotkeysScreen
import com.retroforge.app.ui.settings.TutorialScreen
import com.retroforge.app.ui.settings.LicencesScreen
import com.retroforge.app.ui.settings.PrivacyScreen
import com.retroforge.app.ui.settings.ThemesScreen
import com.retroforge.app.ui.settings.ThemesViewModel
import com.retroforge.app.ui.settings.SettingsScreen
import com.retroforge.app.ui.settings.SettingsViewModel
import com.retroforge.core.settings.ThemeMode
import com.retroforge.core.ui.NavCommand
import com.retroforge.core.ui.RetroForgeTheme
import com.retroforge.core.ui.SectionTab
import com.retroforge.core.ui.Wordmark
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme
import kotlinx.coroutines.flow.Flow

enum class Section(val route: String, val label: String, @DrawableRes val icon: Int) {
    HOME("home", "Home", R.drawable.ic_section_home),
    RETRO("retro", "Retro", R.drawable.ic_section_retro),
    HACKS("hacks", "ROM hacks", R.drawable.ic_section_hacks),
    PC("pc", "PC", R.drawable.ic_section_pc),
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
private const val TUTORIAL_ROUTE = "settings/tutorial"
private const val THEMES_ROUTE = "settings/themes"
private const val LICENCES_ROUTE = "settings/licences"
private const val PRIVACY_ROUTE = "settings/privacy"
private const val SYSTEM_ROUTE = "retro/system/{system}"
private const val GAME_ROUTE = "retro/game/{id}"
private const val COVER_ROUTE = "retro/game/{id}/cover"
private const val MANAGER_ROUTE = "retro/manager"
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
            route == SETUP_ROUTE || route == ABOUT_ROUTE || route == HOTKEYS_ROUTE || route == TUTORIAL_ROUTE || route == THEMES_ROUTE || route == LICENCES_ROUTE || route == PRIVACY_ROUTE -> Section.SETTINGS
            else -> Section.HOME
        }
        val libraryViewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(container.library, container.retroArch, container.covers, container.standalone, container.settings, container.retroArchHotkeys, container.cores, container.coreChoices))
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
                val content: @Composable (Modifier) -> Unit = { modifier ->
                    NavHost(nav, startDestination = Section.HOME.route, modifier = modifier) {
                        composable(Section.HOME.route) {
                            HomeScreen(
                                showPc = settings.showPcSection, library = libraryViewModel, onOpen = { nav.goTo(it) },
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
                        composable(MANAGER_DELETE_ROUTE) { entry ->
                            val managerViewModel: RomManagerViewModel = viewModel(factory = RomManagerViewModel.factory(container.library, container.romManager))
                            RomManagerScreen(managerViewModel, deleteGameId = entry.arguments?.getString("id")?.toLongOrNull() ?: -1)
                        }
                        composable(SETUP_ROUTE) {
                            val setupViewModel: SetupViewModel = viewModel(
                                factory = SetupViewModel.factory(container.library, container.retroArch, container.settings, container.contentResolver, container.playerSystemDir),
                            )
                            SetupScreen(setupViewModel, onDone = { nav.goTo(Section.RETRO) })
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
                            val patcherViewModel: PatcherViewModel = viewModel(factory = PatcherViewModel.factory(container.contentResolver))
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
                            PcScreen(pcViewModel, container.droidDeck)
                        }
                        composable(Section.LINKS.route) { LinksScreen() }
                        composable(Section.SETTINGS.route) {
                            val counts by container.library.systemCounts.collectAsStateWithLifecycle(initialValue = emptyList())
                            SettingsScreen(settingsViewModel,
                                playerSystems = counts.map { it.system to container.library.systemName(it.system) }, coreChoices = container.coreChoices, onOpenSetup = { nav.navigate(SETUP_ROUTE) }, onOpenAbout = { nav.navigate(ABOUT_ROUTE) }, onOpenHotkeys = { nav.navigate(HOTKEYS_ROUTE) }, onOpenTutorial = { nav.navigate(TUTORIAL_ROUTE) }, onOpenThemes = { nav.navigate(THEMES_ROUTE) },
                                onOpenLicences = { nav.navigate(LICENCES_ROUTE) }, onOpenPrivacy = { nav.navigate(PRIVACY_ROUTE) },
                            )
                        }
                        composable(ABOUT_ROUTE) { AboutScreen() }
                        composable(HOTKEYS_ROUTE) { HotkeysScreen(settingsViewModel) }
                        composable(TUTORIAL_ROUTE) { TutorialScreen() }
                        composable(LICENCES_ROUTE) { LicencesScreen() }
                        composable(PRIVACY_ROUTE) { PrivacyScreen() }
                        composable(THEMES_ROUTE) {
                            val themesViewModel: ThemesViewModel = viewModel(factory = ThemesViewModel.factory(container.settings, container.themes, container.contentResolver))
                            ThemesScreen(themesViewModel)
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
        Row(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            sections.forEach { tab(it, Modifier.weight(1f)) }
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
