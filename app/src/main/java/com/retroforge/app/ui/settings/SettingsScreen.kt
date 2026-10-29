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
                            val of = if (p.systemsTotal > 0) " (${p.systemsDone * 100 / p.systemsTotal}%, ${p.systemsDone} of ${p.systemsTotal} systems)" else ""
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
                    CardBody("HD textures that make N64 and PSP games look sharper than the original. Download free N64 packs, or add your own.")
                }

                viewPrefs?.let { prefs ->
                    var scanOnStart by remember { mutableStateOf(prefs.getBoolean(com.retroforge.app.ui.library.LibraryViewModel.SCAN_ON_START, false)) }
                    FocusCard(modifier = Modifier.fillMaxWidth(), onClick = {
                        scanOnStart = !scanOnStart
                        prefs.edit().putBoolean(com.retroforge.app.ui.library.LibraryViewModel.SCAN_ON_START, scanOnStart).apply()
                    }) {
                        CardTitle("Look for new games when RetroForge starts") { StatusPill(if (scanOnStart) "On" else "Off", if (scanOnStart) StatusTone.GOOD else StatusTone.NEUTRAL) }
                        CardBody("Each time RetroForge opens, it checks the game folders on this phone, so games you add show up by themselves. Network drives aren't checked (too slow over Wi-Fi): rescan those in Retro → Game folders.")
                    }
                    var showAll by remember { mutableStateOf(prefs.getBoolean(com.retroforge.app.ui.library.LibraryViewModel.SHOW_ALL_SYSTEMS, false)) }
                    FocusCard(modifier = Modifier.fillMaxWidth(), onClick = {
                        showAll = !showAll
                        prefs.edit().putBoolean(com.retroforge.app.ui.library.LibraryViewModel.SHOW_ALL_SYSTEMS, showAll).apply()
                    }) {
                        CardTitle("Show systems you have no games for") { StatusPill(if (showAll) "On" else "Off", if (showAll) StatusTone.GOOD else StatusTone.NEUTRAL) }
                        CardBody("On: the Retro tab lists every system RetroForge supports. Off: only the systems you have games for.")
                    }
                }
                FocusCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { viewModel.setShowPcSection(!settings.showPcSection) },
                ) {
                    CardTitle("Show the PC tab") { StatusPill(if (settings.showPcSection) "On" else "Off", if (settings.showPcSection) StatusTone.GOOD else StatusTone.NEUTRAL) }
                    CardBody("Turn off to hide the PC tab, if you only play retro games or this device can't run PC games.")
                }

            }
            "playing" -> {
                coreChoices?.let { BuiltInPlayerCard(playerSystems, it, settings.resumeGames, viewModel::setResumeGames, library) }

                EmulatorAppsCard()

                viewPrefs?.let { DualScreenCard(it) }

                FocusCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenGpu) {
                    CardTitle("Graphics chip and drivers")
                    CardBody("Shows which graphics chip this device has. On Snapdragon phones, it also downloads Turnip drivers, which can make emulator apps like Dolphin, Eden and NetherSX2 run better.")
                }

                NetplayCard()

                HotkeysCard(settings.retroArchHotkeys, HotkeyCombo.of(settings.menuCombo), HotkeyCombo.of(settings.quitCombo), viewModel::setRetroArchHotkeys, onOpenHotkeys)
                if (settings.retroArchHotkeys) {
                    FocusCard(modifier = Modifier.fillMaxWidth(), onClick = { viewModel.setFastForwardOnR2(!settings.fastForwardOnR2) }) {
                        CardTitle("Hold R2 to fast-forward") { StatusPill(if (settings.fastForwardOnR2) "On" else "Off", if (settings.fastForwardOnR2) StatusTone.GOOD else StatusTone.NEUTRAL) }
                        CardBody("While you hold R2, the game runs fast, to skip slow text and cutscenes. Only on systems whose controllers had no R2 (Game Boy, GBA, NES, SNES, Mega Drive …).")
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
                    CardBody("Change RetroForge's colours, fonts and layout, with previews (brightness: ${settings.themeMode.name.lowercase()}). You can also import a theme, or the colours of one you use in ES-DE or RetroBat.")
                }

                viewPrefs?.let { prefs ->
                    var sounds by remember { mutableStateOf(prefs.getBoolean(com.retroforge.app.UI_SOUNDS, true)) }
                    FocusCard(modifier = Modifier.fillMaxWidth(), onClick = {
                        sounds = !sounds
                        prefs.edit().putBoolean(com.retroforge.app.UI_SOUNDS, sounds).apply()
                        com.retroforge.core.ui.UiSounds.enabled = sounds
                    }) {
                        CardTitle("Interface sounds") { StatusPill(if (sounds) "On" else "Off", if (sounds) StatusTone.GOOD else StatusTone.NEUTRAL) }
                        CardBody("Small sounds as you move around with a controller or remote, and when you select something or go back.")
                    }
                }
                FocusCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenApps) {
                    CardTitle("Android apps and home screen")
                    CardBody("Open your other apps and Android's settings from RetroForge, or make RetroForge the home screen (good for handhelds and TV boxes).")
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
                    CardBody("Who made it, what it does, and where the name comes from.")
                }

                FocusCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenCheck) {
                    CardTitle("Check my setup")
                    CardBody("Checks every system you have games for: is its emulator ready, are its BIOS files in place, were games found? Problems are listed first.")
                }

                FocusCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenSetup) {
                    CardTitle("Set up RetroForge")
                    CardBody("Step by step: pick your games folder, get the emulators your systems need (downloaded for you) and add your BIOS files.")
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
        CardTitle("Box art from SteamGridDB")
        if (saved.isNullOrBlank()) {
            StatusPill("No API key", StatusTone.NEUTRAL)
            CardBody(
                "Thousands of fan-made covers to choose from. To use them, make a free SteamGridDB account, tap \"Get a free key\", " +
                    "then copy the key and paste it here. It stays on this device and is only sent to SteamGridDB.",
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
        CardTitle("Other emulator apps")
        CardBody(
            "As well as the built-in player, games can open in RetroArch or in free emulator apps you install yourself (pick one on " +
                "a game's page). Obtainium installs them from their official releases and keeps them up to date.",
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
        CardTitle("RetroArch buttons")
        if (enabled) {
            StatusPill("On", StatusTone.GOOD)
            CardBody("In games started from RetroForge: ${quit.label} quits back to RetroForge, ${menu.label} opens RetroArch's menu (save states, disc swap under Disc Control, shaders).")
        } else {
            StatusPill("Off: RetroArch's own settings", StatusTone.NEUTRAL)
        }
        CardBody(
            "Button combos that work in games RetroForge opens in RetroArch. Your RetroArch settings aren't changed: RetroForge " +
                "adds a small file in Documents/RetroForge with these buttons. Other emulator apps use their own menus and buttons.",
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
        CardTitle("Box art from ScreenScraper")
        if (!available) {
            StatusPill("Not available in this build", StatusTone.NEUTRAL)
            CardBody("ScreenScraper needs RetroForge's own developer ID, which ScreenScraper issues on request. It will work once that's added.")
        } else if (savedUser.isNullOrBlank()) {
            StatusPill("No account (lower limits)", StatusTone.NEUTRAL)
        } else {
            StatusPill("Signed in as $savedUser", StatusTone.GOOD)
        }
        CardBody(
            "Optional: sign in with a free ScreenScraper account to download more covers each day. Your login stays on this " +
                "device and is only sent to ScreenScraper. Use \"Change cover\" on a game to look it up.",
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

/** Netplay: the name other players see, and how many frames late buttons are applied (more for slow connections). */
@Composable
private fun NetplayCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var name by remember { mutableStateOf(com.retroforge.app.player.Netplay.name(context)) }
    var delay by remember { mutableStateOf(com.retroforge.app.player.Netplay.delay(context)) }
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle("Play together (netplay)")
        CardBody(
            "Play a game with a friend on another device: open a game and choose Play together, or use Netplay in the game's " +
                "menu. Same Wi-Fi, or over the internet (RetroForge asks your router to let your friend in).",
        )
        ControllerTextField(
            value = name,
            onValueChange = { name = it.take(24); com.retroforge.app.player.Netplay.setName(context, name) },
            singleLine = true,
            label = { Text("Your name for other players") },
            modifier = Modifier.fillMaxWidth(),
        )
        FocusChip("Input delay: $delay frames", onClick = {
            val all = com.retroforge.app.player.Netplay.delays
            delay = all[(all.indexOf(delay) + 1) % all.size]
            com.retroforge.app.player.Netplay.setDelay(context, delay)
        })
        CardBody("Buttons work this many frames late on both devices, so they arrive in time. 2 or 3 on the same Wi-Fi; 4 to 6 over the internet. The host's setting is used.")
    }
}
