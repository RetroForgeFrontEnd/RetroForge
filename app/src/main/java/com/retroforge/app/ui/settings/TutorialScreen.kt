package com.retroforge.app.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.app.ui.ScreenFrame
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.FocusChip

/** One topic: a title and its steps. Kept in step with docs/USER_GUIDE.md. */
private class Topic(val title: String, val steps: List<String>)

private val topics = listOf(
    Topic("Getting started", listOf(
        "Settings → Set up RetroForge walks you through it: pick your games folder, install RetroArch, get the cores your games need and check your BIOS files.",
        "Your games folder can be a \"roms\" folder from another frontend (with snes, nes, psx … inside) or a single system folder, on the phone, an SD card or a USB drive.",
        "RetroForge comes with no games and no BIOS. Use your own.",
    )),
    Topic("Finding and playing games", listOf(
        "Retro shows your systems, Recently played, Favourites, Playlists and a search box.",
        "Open a game and press Play. Cartridge systems (Game Boy, GBA, NES, SNES, Mega Drive, DS and more) play in RetroForge's built-in player: its emulators download by themselves in the background.",
        "Pause a game (the ☰ button, a swipe in from either edge, Back, or Home/Guide on a controller) for save states, fast-forward, screen shape, screen filter, console look, touch controls, emulator settings and Switch emulator.",
        "Disc systems (PlayStation, PSP, Dreamcast …) play in RetroArch or a standalone emulator. If RetroArch says a core is missing: in RetroArch open Online Updater → Core Downloader and get the one named on the game's page.",
        "Some systems need BIOS files. Settings → Set up RetroForge checks yours and copies them where the built-in player and RetroArch look.",
    )),
    Topic("Other emulators", listOf(
        "A game's page lists free standalone emulators for its system (DuckStation, PPSSPP, Dolphin, NetherSX2 …).",
        "\"Get\" opens Obtainium (if installed) or the emulator's official page. RetroForge never installs apps itself.",
        "Once installed: \"Play with …\" starts the game in it, and \"Use by default\" makes the main Play button use it for that system.",
        "Games made of several files (.cue, .m3u) usually also need your games folder added inside the emulator.",
    )),
    Topic("Hotkeys, fast-forward and resume", listOf(
        "In RetroArch games: Start + Select quits back to RetroForge; Down + Select opens RetroArch's menu (save states, Disc Control, shaders). Change them in Settings → RetroArch hotkeys.",
        "Hold R2 to fast-forward on systems whose controllers had no R2 (Game Boy, GBA, NES, SNES, Mega Drive …): handy for slow dialogue.",
        "Carry on where you left off: quitting saves a state, and the next launch continues from it. Switch it off in Settings.",
        "Settings → Hotkeys lists every hotkey. Standalone emulators use their own menus.",
    )),
    Topic("Multi-disc games", listOf(
        "A game with an .m3u playlist shows once and plays as one game.",
        "To change disc in RetroArch: open the menu, Disc Control → Eject, choose the next disc in Current Disc Index, then Insert.",
        "No .m3u yet? ROM manager → Multi-disc makes them for you.",
    )),
    Topic("Covers", listOf(
        "Open a game and choose Change cover.",
        "Box art: free, from libretro's thumbnail library. A system's list also has \"Download box art\" for every game without artwork.",
        "SteamGridDB: thousands of community covers. Add your own free API key in Settings.",
        "ScreenScraper: front and 3D boxes, looked up for one game when you ask. An account (Settings) raises your daily limit.",
        "Your own image: any picture on the device, for example a scan saved from The Cover Project.",
    )),
    Topic("Favourites and playlists", listOf(
        "On a game's page: \"Add to favourites\", or \"Add to a playlist\" to tick existing playlists or make a new one.",
        "Both show on the Retro screen. Open a playlist to reorder, remove, rename or delete it; the games themselves are never touched.",
    )),
    Topic("ROM hacks and patches", listOf(
        "ROM hacks shows hacks you already have and hacks from catalogues, with links to their official pages.",
        "ROM Patcher (on the Retro screen) applies IPS, BPS or UPS patches, one after another, to make a new patched copy. Your original ROM is never changed.",
    )),
    Topic("ROM manager", listOf(
        "The only part of RetroForge that changes your files, and only after showing you exactly what it will do.",
        "Duplicates: finds the same game in several files and checks whether they hold the same data.",
        "Compress: discs to CHD, cartridges to .zip. Each new file is checked before the originals are set aside.",
        "Multi-disc: makes .m3u playlists. Delete a game: from its page.",
        "Nothing is deleted straight away: files go to _duplicates, _originals or _deleted, and History can undo every change. Free up space is the only step that deletes for good.",
        "If another frontend uses the same folder on a PC, close it first.",
    )),
    Topic("SD cards and controllers", listOf(
        "Games and BIOS can live on an SD card or USB drive: add that folder like any other.",
        "Everything works with a controller: D-pad to move, A to select, B to go back, L1/R1 to switch sections.",
    )),
    Topic("PC games: how it works", listOf(
        "RetroForge doesn't run Windows games itself. A free PC game app does, installed separately: DroidDeck on Snapdragon devices, or GameNative and Winlator on others.",
        "The PC section checks your device's graphics chip and shows which of these apps suit it, with links to their official release pages.",
        "Use only games you own. RetroForge never shares your store logins or game files.",
    )),
    Topic("PC games with DroidDeck (Snapdragon)", listOf(
        "Needs a Qualcomm Snapdragon chip with an Adreno 730 or newer GPU (many AYN and Retroid handhelds and Snapdragon phones). The PC section tells you if yours qualifies.",
        "PC → PC game runtime → open the official releases page, download the latest DroidDeck APK and install it (Android asks you to allow installing from your browser once).",
        "Open DroidDeck, sign in to Steam inside it and install a game you own. Back in RetroForge, the PC section shows DroidDeck as installed and opens it for you.",
    )),
    Topic("PC games with GameNative (Snapdragon, Exynos, Mali)", listOf(
        "GameNative is free and open source (GPL-3.0). It plays Steam, Epic, GOG and Amazon games you own, and games you add from a folder. Tested here: TCG Card Shop Simulator reached its main menu on a Galaxy S22 (Exynos).",
        "Install: PC → GameNative releases, download the latest APK (about 575 MB) and install it.",
        "Store games: sign in to Steam (scan the QR code with the Steam app), Epic, GOG or Amazon inside GameNative and download a game.",
        "Your own game folder: copy the game's folder to the phone (a file manager or USB cable), then in GameNative tap Skip login, then +, and choose that folder. GameNative asks for All files access: allow it so it can read the folder.",
        "If Play says the executable couldn't be chosen: Edit container → Executable Path → pick the game's .exe (not UnityCrashHandler or setup files), save, and press Play again.",
        "The first start downloads Proton (the Windows layer) and sets things up: a minute or two. Later starts are quicker.",
        "Mods (for example a large BepInEx folder) can be left out to save space: the game runs without them.",
    )),
)

@Composable
fun TutorialScreen() {
    val context = LocalContext.current
    fun open(url: String) { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) } }
    var openTopic by remember { mutableStateOf<String?>(null) }

    ScreenFrame(title = "How to use RetroForge", subtitle = "Grab a drink and a snack (as Russ from Retro Game Corps would say), and pick a topic.") {
        topics.forEach { topic ->
            val expanded = openTopic == topic.title
            FocusCard(modifier = Modifier.fillMaxWidth(), onClick = { openTopic = if (expanded) null else topic.title }) {
                CardTitle((if (expanded) "▾ " else "▸ ") + topic.title)
                if (expanded) topic.steps.forEach { CardBody("• $it") }
            }
        }

        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle("Learn more about Android emulation")
            CardBody(
                "Retro Game Corps (Russ) writes some of the best guides around. They're his own work, so they open on his site. " +
                    "More channels are in the Links tab.",
            )
        }
        FocusChip("Android Emulation Starter Guide", onClick = { open(STARTER_GUIDE) })
        FocusChip("Dual-Screen Android Handheld Guide", onClick = { open(DUAL_SCREEN_GUIDE) })
        FocusChip("All Retro Game Corps guides", onClick = { open(ALL_GUIDES) })
    }
}

private const val STARTER_GUIDE = "https://retrogamecorps.com/2022/03/13/android-emulation-starter-guide/"
private const val DUAL_SCREEN_GUIDE = "https://retrogamecorps.com/2025/10/27/dual-screen-android-handheld-guide/"
private const val ALL_GUIDES = "https://retrogamecorps.com/guides/"
