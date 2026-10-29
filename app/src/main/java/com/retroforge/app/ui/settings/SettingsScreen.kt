package com.retroforge.app.ui.settings

import com.retroforge.app.ui.ChipRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import android.content.Intent
import com.retroforge.core.ui.ControllerTextField
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.core.net.toUri
import com.retroforge.artwork.SteamGridDb
import com.retroforge.artwork.ScreenScraper
import com.retroforge.app.BuildConfig
import com.retroforge.retro.StandaloneLauncher
import com.retroforge.retro.HotkeyCombo
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import com.retroforge.core.ui.FocusChip
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.app.ui.ScreenFrame
import com.retroforge.app.ui.legalNoticeParagraphs
import com.retroforge.core.settings.ThemeMode
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.StatusPill
import com.retroforge.core.ui.StatusTone

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onOpenSetup: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenHotkeys: () -> Unit,
    onOpenGpu: () -> Unit,
    onOpenTexturePacks: () -> Unit = {},
    onOpenCheck: () -> Unit = {},
    library: com.retroforge.core.library.LibraryRepository? = null,
    onOpenApps: () -> Unit = {},
    onOpenTutorial: () -> Unit,
    onOpenThemes: () -> Unit,
    onOpenLicences: () -> Unit,
    onOpenPrivacy: () -> Unit,
    playerSystems: List<Pair<String, String>> = emptyList(),
    coreChoices: com.retroforge.player.CoreChoices? = null,
    viewPrefs: android.content.SharedPreferences? = null,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    // Settings in a few groups, each on its own page, so nothing is far down a long list. Back returns to the groups.
    var group by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    com.retroforge.app.ui.CloseStep(enabled = group != null) { group = null }
    val groups = listOf(
        "games" to ("Games and library" to "Game folders and network drives, ES-DE collections, texture packs, scanning, the Retro and PC tabs."),
        "playing" to ("Playing" to "The built-in player, saves and backups, emulator apps, dual screens, GPU drivers, RetroArch hotkeys."),
        "covers" to ("Covers" to "SteamGridDB and ScreenScraper accounts for box art."),
        "looks" to ("Looks and sounds" to "Themes, interface sounds, Android apps and the home screen."),
        "help" to ("Help and about" to "How to use RetroForge, set up, check my setup, report a problem, about, licences."),
    )

    ScreenFrame(title = groups.firstOrNull { it.first == group }?.second?.first ?: "Settings", compact = true) {
        if (group == null) {
            groups.forEach { (id, text) ->
                FocusCard(modifier = Modifier.fillMaxWidth(), onClick = { group = id }) {
                    CardTitle(text.first)
                    CardBody(text.second)
                }
            }
            return@ScreenFrame
        }
        FocusChip("‹ All settings", onClick = { group = null })
        when (group) {
            "games" -> {
                com.retroforge.app.ui.NetworkDrivesCard("Games", onUse = library?.let { lib ->
                    { uri, progress ->
                        // A short running report while it scans: which system, and how far.
                        val root = lib.addFolderAndWait(uri) { p ->
                            val system = p.currentSystem?.let { " ${lib.systemName(it)}" }.orEmpty()
                            val of = if (p.systemsTotal > 0) " (${p.systemsDone} of ${p.systemsTotal} systems)" else ""
                            progress("Looking in$system… ${p.filesSeen} files checked$of", if (p.systemsTotal > 0) p.systemsDone.toFloat() / p.systemsTotal else null)
                        }
                        when {
                            root == null -> "Added. It's still scanning: see Retro → Game folders for progress."
                            root.gameCount == 0 -> "Added ${root.name}, but no games were found. Pick the folder holding your system folders (snes, psx …)."
                            else -> "Added ${root.name}: ${root.gameCount} game${if (root.gameCount == 1) "" else "s"} found. They're in the Retro tab now."
                        }
                    }
                })

                library?.let { EsdeImportCard(it) }

                FocusCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenTexturePacks) {
                    CardTitle("Texture packs")
                    CardBody("High-resolution textures for N64 and PSP games: download free N64 packs, or add your own. Select to open.")
                }

                viewPrefs?.let { prefs ->
                    var scanOnStart by remember { mutableStateOf(prefs.getBoolean(com.retroforge.app.ui.library.LibraryViewModel.SCAN_ON_START, false)) }
                    FocusCard(modifier = Modifier.fillMaxWidth(), onClick = {
                        scanOnStart = !scanOnStart
                        prefs.edit().putBoolean(com.retroforge.app.ui.library.LibraryViewModel.SCAN_ON_START, scanOnStart).apply()
                    }) {
                        CardTitle("Look for new games when RetroForge starts")
                        StatusPill(if (scanOnStart) "On" else "Off", if (scanOnStart) StatusTone.GOOD else StatusTone.NEUTRAL)
                        CardBody("Rescans the game folders on this device each time the app opens, so games you add show up by themselves. Network drives are left out (too slow over Wi-Fi): rescan those in Retro → Game folders.")
                    }
                    var showAll by remember { mutableStateOf(prefs.getBoolean(com.retroforge.app.ui.library.LibraryViewModel.SHOW_ALL_SYSTEMS, false)) }
                    FocusCard(modifier = Modifier.fillMaxWidth(), onClick = {
                        showAll = !showAll
                        prefs.edit().putBoolean(com.retroforge.app.ui.library.LibraryViewModel.SHOW_ALL_SYSTEMS, showAll).apply()
                    }) {
                        CardTitle("Retro tab: show systems without games")
                        StatusPill(if (showAll) "On" else "Off", if (showAll) StatusTone.GOOD else StatusTone.NEUTRAL)
                        CardBody("Off: the Retro tab shows only the systems you have games for. On: every system RetroForge knows, for browsing.")
                    }
                }
                FocusCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { viewModel.setShowPcSection(!settings.showPcSection) },
                ) {
                    CardTitle("Show PC section")
                    StatusPill(if (settings.showPcSection) "On" else "Off", if (settings.showPcSection) StatusTone.GOOD else StatusTone.NEUTRAL)
                    CardBody("Hide the PC section if you only play retro games or this device cannot run PC games.")
                }

            }
            "playing" -> {
                coreChoices?.let { BuiltInPlayerCard(playerSystems, it, settings.resumeGames, viewModel::setResumeGames, library) }

                EmulatorAppsCard()

                viewPrefs?.let { DualScreenCard(it) }

                FocusCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenGpu) {
                    CardTitle("GPU and drivers")
                    CardBody("See which graphics chip this device has, and download custom Turnip drivers for emulators like Eden, Dolphin and NetherSX2 (Snapdragon only).")
                }

                HotkeysCard(settings.retroArchHotkeys, HotkeyCombo.of(settings.menuCombo), HotkeyCombo.of(settings.quitCombo), viewModel::setRetroArchHotkeys, onOpenHotkeys)
                if (settings.retroArchHotkeys) {
                    FocusCard(modifier = Modifier.fillMaxWidth(), onClick = { viewModel.setFastForwardOnR2(!settings.fastForwardOnR2) }) {
                        CardTitle("Hold R2 to fast-forward")
                        StatusPill(if (settings.fastForwardOnR2) "On" else "Off", if (settings.fastForwardOnR2) StatusTone.GOOD else StatusTone.NEUTRAL)
                        CardBody("For systems whose controllers had no R2 (Game Boy, GBA, NES, SNES, Mega Drive …): skip through slow dialogue. Select to switch.")
                    }
                }

            }
            "covers" -> {
                SteamGridDbKeyCard(settings.steamGridDbKey, viewModel::setSteamGridDbKey)

                ScreenScraperCard(settings.screenScraperUser, viewModel::setScreenScraperLogin)

            }
            "looks" -> {
                FocusCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenThemes) {
                    CardTitle("Themes")
                    CardBody("Colours, shapes, type and game-list layout, with previews. Brightness: ${settings.themeMode.name.lowercase()}. Import your own, or a theme's colours from another frontend.")
                }

                viewPrefs?.let { prefs ->
                    var sounds by remember { mutableStateOf(prefs.getBoolean(com.retroforge.app.UI_SOUNDS, true)) }
                    FocusCard(modifier = Modifier.fillMaxWidth(), onClick = {
                        sounds = !sounds
                        prefs.edit().putBoolean(com.retroforge.app.UI_SOUNDS, sounds).apply()
                        com.retroforge.core.ui.UiSounds.enabled = sounds
                    }) {
                        CardTitle("Interface sounds")
                        StatusPill(if (sounds) "On" else "Off", if (sounds) StatusTone.GOOD else StatusTone.NEUTRAL)
                        CardBody("A soft tick when moving with a controller or remote, and a chime when selecting or going back.")
                    }
                }
                FocusCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenApps) {
                    CardTitle("Android apps and home screen")
                    CardBody("Open your other apps and Android's settings, and make RetroForge the device's home screen (for handhelds and TV boxes).")
                }

            }
            else -> {
                FocusCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenTutorial) {
                    CardTitle("How to use RetroForge")
                    CardBody("A short guide to every feature: playing, emulators, hotkeys, covers, playlists, the ROM Manager and more.")
                    CardBody("With a controller: D-pad to move, A to select, B to go back, L1/R1 to switch tabs.")
                }

                FocusCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenAbout) {
                    CardTitle("About RetroForge")
                    CardBody("Version ${BuildConfig.VERSION_NAME}, made by Darren Glancy.")
                    CardBody("Who made it, what RetroForge does, and what the name stands for (sort of).")
                }

                FocusCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenCheck) {
                    CardTitle("Check my setup")
                    CardBody("Every system you have games for, checked: emulator ready, BIOS in place, games found. Problems first.")
                }

                FocusCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenSetup) {
                    CardTitle("Set up RetroForge")
                    CardBody("Your games folder, the emulators your systems need (downloaded for you) and your BIOS files.")
                }

                FocusCard(modifier = Modifier.fillMaxWidth()) {
                    CardTitle("Games, BIOS and the law")
                    legalNoticeParagraphs.forEach { CardBody(it) }
                }

                SupportCard()
                ReportProblemCard()
                ChipRow {
                    FocusChip("Licences", onClick = onOpenLicences)
                    FocusChip("Privacy", onClick = onOpenPrivacy)
                }

            }
        }
    }
}

@Composable
private fun SteamGridDbKeyCard(saved: String?, onSave: (String?) -> Unit) {
    val context = LocalContext.current
    var draft by remember(saved) { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle("Custom covers (SteamGridDB)")
        if (saved.isNullOrBlank()) {
            StatusPill("No API key", StatusTone.NEUTRAL)
            CardBody(
                "SteamGridDB has thousands of community-made covers. Create a free account, then use \"Get a free key\" to " +
                    "copy your API key and paste it here. It stays on this device and is sent only to SteamGridDB.",
            )
        } else {
            StatusPill("API key saved (…${saved.takeLast(4)})", StatusTone.GOOD)
            CardBody("Use \"Change cover\" on any game to browse SteamGridDB covers.")
        }
        ControllerTextField(
            value = draft,
            onValueChange = { draft = it.trim() },
            singleLine = true,
            label = { Text(if (saved.isNullOrBlank()) "Paste API key" else "Replace API key") },
            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        ChipRow {
            FocusChip(if (showKey) "Hide key" else "Show key", onClick = { showKey = !showKey })
            FocusChip("Save key", onClick = { onSave(draft); draft = "" }, enabled = draft.length >= 16)
            FocusChip("Get a free key", onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, SteamGridDb.KEY_PAGE.toUri())) } })
            if (!saved.isNullOrBlank()) FocusChip("Remove key", onClick = { onSave(null) })
        }
    }
}

@Composable
private fun EmulatorAppsCard() {
    val context = LocalContext.current
    fun open(url: String) { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } }
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle("Emulator apps")
        CardBody(
            "Besides the built-in player, RetroForge can play games in RetroArch or in free standalone emulators you install yourself (choose one on any game's page). " +
                "Obtainium installs them from their official releases and keeps them updated.",
        )
        CardBody(
            "To add a whole set at once: get the Obtainium Emulation Pack file, then in Obtainium open Import/Export → Obtainium Import and pick the file.",
        )
        ChipRow {
            FocusChip("Get Obtainium", onClick = { open(StandaloneLauncher.OBTAINIUM_PAGE) })
            FocusChip("Emulation Pack", onClick = { open(EMULATION_PACK_PAGE) })
        }
    }
}

/** Community list of emulators for Obtainium (Unlicense). */
private const val EMULATION_PACK_PAGE = "https://github.com/RJNY/Obtainium-Emulation-Pack/releases/latest"

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HotkeysCard(enabled: Boolean, menu: HotkeyCombo, quit: HotkeyCombo, onChange: (Boolean, HotkeyCombo, HotkeyCombo) -> Unit, onShowAll: () -> Unit) {
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle("RetroArch hotkeys")
        if (enabled) {
            StatusPill("On", StatusTone.GOOD)
            CardBody("In games started from RetroForge: ${quit.label} quits back to RetroForge, ${menu.label} opens RetroArch's menu (save states, disc swap under Disc Control, shaders).")
        } else {
            StatusPill("Off: RetroArch's own settings", StatusTone.NEUTRAL)
        }
        CardBody(
            "RetroForge doesn't change RetroArch's own settings. It gives RetroArch a small file in Documents/RetroForge that sets these buttons " +
                "and then loads your RetroArch settings. Standalone emulators use their own menus and hotkeys.",
        )
        if (enabled) {
            CardBody("Menu:")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HotkeyCombo.entries.filter { it != HotkeyCombo.NONE && it != quit }.forEach { c ->
                    FocusChip((if (c == menu) "● " else "") + c.label, onClick = { onChange(true, c, quit) })
                }
            }
            CardBody("Quit to RetroForge:")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HotkeyCombo.entries.filter { it != HotkeyCombo.NONE && it != menu }.forEach { c ->
                    FocusChip((if (c == quit) "● " else "") + c.label, onClick = { onChange(true, menu, c) })
                }
            }
        }
        ChipRow {
            FocusChip("Show all hotkeys", onClick = onShowAll)
            FocusChip(if (enabled) "Turn hotkeys off" else "Turn hotkeys on", onClick = { onChange(!enabled, menu, quit) })
        }
    }
}

@Composable
private fun ScreenScraperCard(savedUser: String?, onSave: (String?, String?) -> Unit) {
    val context = LocalContext.current
    var user by remember(savedUser) { mutableStateOf(savedUser.orEmpty()) }
    var password by remember(savedUser) { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    val available = BuildConfig.SCREENSCRAPER_DEV_ID.isNotBlank()
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle("Covers from ScreenScraper")
        if (!available) {
            StatusPill("Not available in this build", StatusTone.NEUTRAL)
            CardBody("ScreenScraper needs RetroForge's own developer ID, which ScreenScraper issues on request. It will work once that's added.")
        } else if (savedUser.isNullOrBlank()) {
            StatusPill("No account (lower limits)", StatusTone.NEUTRAL)
        } else {
            StatusPill("Signed in as $savedUser", StatusTone.GOOD)
        }
        CardBody(
            "Optional: your free ScreenScraper account raises your daily limit. It stays on this device and is sent only to ScreenScraper. " +
                "Use \"Change cover\" on a game to look it up.",
        )
        ControllerTextField(value = user, onValueChange = { user = it.trim() }, singleLine = true, label = { Text("ScreenScraper username") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        ControllerTextField(
            value = password, onValueChange = { password = it }, singleLine = true, label = { Text("Password") },
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        ChipRow {
            FocusChip(if (showPassword) "Hide password" else "Show password", onClick = { showPassword = !showPassword })
            FocusChip("Save", onClick = { onSave(user, password); password = "" }, enabled = user.isNotBlank() && password.isNotEmpty())
            FocusChip("Create a free account", onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, ScreenScraper.SIGN_UP.toUri())) } })
            if (!savedUser.isNullOrBlank()) FocusChip("Sign out", onClick = { onSave(null, null) })
        }
    }
}

/** Opens the project's issue page with the app and device details filled in (no personal data). */
@Composable
private fun ReportProblemCard() {
    val context = LocalContext.current
    val url = BuildConfig.BUG_REPORT_URL
    FocusCard(modifier = Modifier.fillMaxWidth(), onClick = {
        val body = "What happened:\n\nWhat you expected:\n\nSteps to repeat it:\n\n---\n" +
            "RetroForge ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
            "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})"
        val intent = if (url.isNotBlank()) {
            Intent(Intent.ACTION_VIEW, (url.trimEnd('/') + "/new?labels=bug&body=" + java.net.URLEncoder.encode(body, "UTF-8")).toUri())
        } else {
            // No issue tracker in this build: share the report by email, message or anything else.
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, "RetroForge problem report").putExtra(Intent.EXTRA_TEXT, body),
                "Send the report with…",
            )
        }
        runCatching { context.startActivity(intent) }
    }) {
        CardTitle("Report a problem")
        CardBody(
            if (url.isNotBlank()) "Opens a bug report on GitHub with your RetroForge version and device model filled in. Nothing else is sent; you see everything before posting."
            else "Writes a short report with your RetroForge version and phone model, for you to send by email or message. Nothing is sent until you send it.",
        )
    }
}

/** View preference: DS games put their touch screen on a second display. */
const val DUAL_SCREEN_DS = "dual_screen_ds"

/** View preference: the second display shows the open game while browsing. */
const val DUAL_SCREEN_BROWSE = "dual_screen_browse"

/** Two-screen devices (AYN Thor, AYANEO Pocket DS …): what the second screen is used for. */
@Composable
private fun DualScreenCard(prefs: android.content.SharedPreferences) {
    val context = LocalContext.current
    val second = remember {
        context.getSystemService(android.hardware.display.DisplayManager::class.java)
            .getDisplays(android.hardware.display.DisplayManager.DISPLAY_CATEGORY_PRESENTATION).firstOrNull()
    }
    var ds by remember { mutableStateOf(prefs.getBoolean(DUAL_SCREEN_DS, true)) }
    var browse by remember { mutableStateOf(prefs.getBoolean(DUAL_SCREEN_BROWSE, false)) }
    FocusCard(modifier = Modifier.fillMaxWidth(), onClick = {
        ds = !ds
        prefs.edit().putBoolean(DUAL_SCREEN_DS, ds).apply()
    }) {
        CardTitle("Dual screen")
        StatusPill(if (second != null) "Second screen found: ${second.name}" else "No second screen on this device", if (second != null) StatusTone.GOOD else StatusTone.NEUTRAL)
        StatusPill("DS games on both screens: ${if (ds) "On" else "Off"}", if (ds) StatusTone.GOOD else StatusTone.NEUTRAL)
        StatusPill("Game box on the second screen while browsing (experimental): ${if (browse) "On" else "Off"}", if (browse) StatusTone.GOOD else StatusTone.NEUTRAL)
        com.retroforge.core.ui.FocusChip("Switch the game box", onClick = {
            browse = !browse
            prefs.edit().putBoolean(DUAL_SCREEN_BROWSE, browse).apply()
        })
        CardBody(
            "For two-screen devices like the AYN Thor and AYANEO Pocket DS. On: a DS game's top screen fills the main display and " +
                "its touch screen goes to the second one. Off: both DS screens on the main display. Select to switch.",
        )
    }
}

