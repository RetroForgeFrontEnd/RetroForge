package com.retroforge.app.ui.settings

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
    onOpenTutorial: () -> Unit,
    onOpenThemes: () -> Unit,
    onOpenLicences: () -> Unit,
    onOpenPrivacy: () -> Unit,
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    ScreenFrame(title = "Settings") {
        FocusCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenTutorial) {
            CardTitle("How to use RetroForge")
            CardBody("A short guide to every feature: playing, emulators, hotkeys, covers, playlists, the ROM manager and more.")
        }

        FocusCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenSetup) {
            CardTitle("Set up RetroForge")
            CardBody("Games folder, RetroArch, the cores your games need, and your BIOS files.")
        }

        FocusCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenThemes) {
            CardTitle("Themes")
            CardBody("Colours, shapes, type and game-list layout, with previews. Brightness: ${settings.themeMode.name.lowercase()}. Import your own or a theme's colours from ES-DE or RetroBat.")
        }

        FocusCard(
            modifier = Modifier.fillMaxWidth(),
            onClick = { viewModel.setShowPcSection(!settings.showPcSection) },
        ) {
            CardTitle("Show PC section")
            StatusPill(if (settings.showPcSection) "On" else "Off", if (settings.showPcSection) StatusTone.GOOD else StatusTone.NEUTRAL)
            CardBody("Hide the PC section if you only play retro games or this device cannot run PC games.")
        }

        SteamGridDbKeyCard(settings.steamGridDbKey, viewModel::setSteamGridDbKey)

        ScreenScraperCard(settings.screenScraperUser, viewModel::setScreenScraperLogin)

        EmulatorAppsCard()

        HotkeysCard(settings.retroArchHotkeys, HotkeyCombo.of(settings.menuCombo), HotkeyCombo.of(settings.quitCombo), viewModel::setRetroArchHotkeys, onOpenHotkeys)
        if (settings.retroArchHotkeys) {
            FocusCard(modifier = Modifier.fillMaxWidth(), onClick = { viewModel.setResumeGames(!settings.resumeGames) }) {
                CardTitle("Carry on where you left off")
                StatusPill(if (settings.resumeGames) "On" else "Off", if (settings.resumeGames) StatusTone.GOOD else StatusTone.NEUTRAL)
                CardBody(
                    "When you quit a game, RetroArch saves a state; next time you start it, it carries on from there. " +
                        "Uses RetroArch's auto save/load state. Select to switch.",
                )
            }
            FocusCard(modifier = Modifier.fillMaxWidth(), onClick = { viewModel.setFastForwardOnR2(!settings.fastForwardOnR2) }) {
                CardTitle("Hold R2 to fast-forward")
                StatusPill(if (settings.fastForwardOnR2) "On" else "Off", if (settings.fastForwardOnR2) StatusTone.GOOD else StatusTone.NEUTRAL)
                CardBody("For systems whose controllers had no R2 (Game Boy, GBA, NES, SNES, Mega Drive …): skip through slow dialogue. Select to switch.")
            }
        }

        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle("Games, BIOS and the law")
            legalNoticeParagraphs.forEach { CardBody(it) }
        }

        SupportCard()
        ReportProblemCard()
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FocusChip("Licences", onClick = onOpenLicences)
            FocusChip("Privacy", onClick = onOpenPrivacy)
        }

        FocusCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenAbout) {
            CardTitle("About RetroForge")
            CardBody("Version ${BuildConfig.VERSION_NAME}. Select to read what RetroForge is for.")
            CardBody("Controller: D-pad to move, A to select, B to go back, L1/R1 to switch sections.")
        }
    }
}

@Composable
private fun SteamGridDbKeyCard(saved: String?, onSave: (String?) -> Unit) {
    val context = LocalContext.current
    var draft by remember(saved) { mutableStateOf("") }
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle("Custom covers (SteamGridDB)")
        if (saved.isNullOrBlank()) {
            StatusPill("No API key", StatusTone.NEUTRAL)
            CardBody(
                "SteamGridDB has thousands of community-made covers. Create a free account, copy your API key from " +
                    "${SteamGridDb.KEY_PAGE} and paste it here. It stays on this device and is sent only to SteamGridDB.",
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
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FocusChip("Save key", onClick = { onSave(draft); draft = "" }, enabled = draft.length >= 16)
        FocusChip("Get a free key", onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, SteamGridDb.KEY_PAGE.toUri())) } })
        if (!saved.isNullOrBlank()) FocusChip("Remove key", onClick = { onSave(null) })
    }
}

@Composable
private fun EmulatorAppsCard() {
    val context = LocalContext.current
    fun open(url: String) { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } }
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle("Emulator apps")
        CardBody(
            "RetroForge plays games in RetroArch, or in free standalone emulators you install yourself (choose one on any game's page). " +
                "Obtainium installs them from their official releases and keeps them updated.",
        )
        CardBody(
            "To add a whole set at once: get the Obtainium Emulation Pack file, then in Obtainium open Import/Export → Obtainium Import and pick the file.",
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FocusChip("Get Obtainium", onClick = { open(StandaloneLauncher.OBTAINIUM_PAGE) })
        FocusChip("Emulation Pack", onClick = { open(EMULATION_PACK_PAGE) })
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
    }
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
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FocusChip("Show all hotkeys", onClick = onShowAll)
        FocusChip(if (enabled) "Turn hotkeys off" else "Turn hotkeys on", onClick = { onChange(!enabled, menu, quit) })
    }
}

@Composable
private fun ScreenScraperCard(savedUser: String?, onSave: (String?, String?) -> Unit) {
    val context = LocalContext.current
    var user by remember(savedUser) { mutableStateOf(savedUser.orEmpty()) }
    var password by remember(savedUser) { mutableStateOf("") }
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
            visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        FocusChip("Save", onClick = { onSave(user, password); password = "" }, enabled = user.isNotBlank() && password.isNotEmpty())
        FocusChip("Create a free account", onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, ScreenScraper.SIGN_UP.toUri())) } })
        if (!savedUser.isNullOrBlank()) FocusChip("Sign out", onClick = { onSave(null, null) })
    }
}

/** Opens the project's issue page with the app and device details filled in (no personal data). */
@Composable
private fun ReportProblemCard() {
    val context = LocalContext.current
    val url = BuildConfig.BUG_REPORT_URL
    if (url.isBlank()) return
    FocusCard(modifier = Modifier.fillMaxWidth(), onClick = {
        val body = "What happened:\n\nWhat you expected:\n\nSteps to repeat it:\n\n---\n" +
            "RetroForge ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
            "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})"
        val link = url.trimEnd('/') + "/new?labels=bug&body=" + java.net.URLEncoder.encode(body, "UTF-8")
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, link.toUri())) }
    }) {
        CardTitle("Report a problem")
        CardBody("Opens a bug report on GitHub with your RetroForge version and device model filled in. Nothing else is sent; you see everything before posting.")
    }
}
