package com.retroforge.app.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import com.retroforge.app.ui.home.HomeScreen
import com.retroforge.app.ui.pc.PcScreen
import com.retroforge.app.ui.patcher.PatcherScreen
import com.retroforge.app.ui.patcher.PatcherViewModel
import com.retroforge.app.ui.pc.PcViewModel
import com.retroforge.app.ui.retro.RetroScreen
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
    PC("pc", "PC", R.drawable.ic_section_pc),
    SETTINGS("settings", "Settings", R.drawable.ic_section_settings),
}

private const val PATCHER_ROUTE = "retro/patcher"

@Composable
fun RetroForgeRoot(container: AppContainer, sectionCommands: Flow<NavCommand>) {
    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container.settings))
    val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }

    RetroForgeTheme(dark = dark) {
        val nav = rememberNavController()
        val sections = Section.entries.filter { it != Section.PC || settings.showPcSection }
        val backStack by nav.currentBackStackEntryAsState()
        val route = backStack?.destination?.route
        val current = sections.firstOrNull { it.route == route } ?: if (route == PATCHER_ROUTE) Section.RETRO else Section.HOME

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
                            HomeScreen(showPc = settings.showPcSection, onOpen = { nav.goTo(it) })
                        }
                        composable(Section.RETRO.route) { RetroScreen(onOpenPatcher = { nav.navigate(PATCHER_ROUTE) }) }
                        composable(PATCHER_ROUTE) {
                            val patcherViewModel: PatcherViewModel = viewModel(factory = PatcherViewModel.factory(container.contentResolver))
                            PatcherScreen(patcherViewModel)
                        }
                        composable(Section.PC.route) {
                            val pcViewModel: PcViewModel = viewModel(factory = PcViewModel.factory(container.pcStatus))
                            PcScreen(pcViewModel, container.droidDeck)
                        }
                        composable(Section.SETTINGS.route) { SettingsScreen(settingsViewModel) }
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
    val tab: @Composable (Section) -> Unit = { section ->
        SectionTab(
            label = section.label,
            icon = painterResource(section.icon),
            selected = section == current,
            onClick = { onSelect(section) },
            modifier = if (section == current) Modifier.focusRequester(initialFocus) else Modifier,
        )
    }
    if (vertical) {
        Column(
            modifier = Modifier.fillMaxHeight().background(MaterialTheme.colorScheme.surface).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Wordmark(short = true, fontSize = 24.sp)
            Spacer(Modifier.height(16.dp))
            sections.forEach { tab(it) }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            sections.forEach { tab(it) }
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
