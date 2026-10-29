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
    Topic("Play together (netplay)", listOf(
        "Two players on two devices play the same game, each with RetroForge and the same game file (same name and size). The host is player 1, the one who joins is player 2.",
        "To host: open the game and choose Play together → Host (or Netplay → Host this game in the game's menu). Your friend then joins.",
        "Same Wi-Fi: your friend opens the same game and chooses Play together → Join, then picks you from the list. Or Retro tab → Play together → Find games on this Wi-Fi.",
        "Over the internet: when you host, RetroForge asks your router to let your friend in and shows your internet address in Netplay. Your friend types it under Join by address. If your router doesn't allow it, forward port 55450 (TCP) to your device in the router's settings. Mobile data can't host.",
        "Input delay (Settings → Playing → Play together): buttons work a few frames late on both devices so they arrive in time. 2 or 3 on Wi-Fi, 4 to 6 over the internet.",
        "During netplay both devices run at the same speed: fast-forward is off, and only player 1 can load a state or restart. If the game gets out of step, player 1's game is sent again by itself.",
        "Play together shows on the games it suits: NES, SNES, Mega Drive, Master System, PC Engine, PlayStation, Atari, arcade (FinalBurn Neo, MAME, Neo Geo, CPS) and the two-player Game Boy. Handhelds, computers and 3D systems like N64, GameCube and PS2 don't have it: their emulators drift apart between devices.",
    )),
    Topic("Getting started", listOf(
        "Settings → Set up RetroForge walks you through it: pick your games folder, check your BIOS files and choose emulators. The built-in player's emulators download by themselves.",
        "Your games folder can be a \"roms\" folder from another frontend (RetroBat, ES-DE: snes, nes, psx … inside), a single system folder, or just a folder of games: RetroForge recognises loose games by their file type (.gba, .nds, .sfc, .z64 …).",
        "It can be on the phone, an SD card or a USB drive. RetroForge only reads it; nothing is changed unless you use the ROM Manager.",
        "New games: Retro → Game folders → Scan for new games finds them. Settings → Games and library can also look each time the app starts (folders on the device only; network drives are rescanned by hand).",
        "Settings → Check my setup shows every system at once: its emulator, BIOS and games, and what's missing.",
        "RetroForge comes with no games and no BIOS. Use your own.",
    )),
    Topic("Games and films on a network drive (PC or NAS)", listOf(
        "What it does: plays games and films kept on a computer or NAS at home, over Wi-Fi, without copying them to the phone. Nothing extra to install on the phone. The phone and the computer must be on the same network.",
        "STEP 1, on a Windows PC: share the folder. Right-click the folder (for example your roms folder) → Properties → Sharing tab → Advanced Sharing… → tick Share this folder. Note the share name (you can keep the folder's name). Click Permissions, make sure Everyone has Read ticked, then OK, OK.",
        "STEP 2, on the PC: let the folder be read. Same Properties window → Security tab → Edit… → Add… → type Everyone → Check Names → OK. With Everyone selected, tick Read & execute under Allow → OK → OK. RetroForge only ever reads, so Read is all it needs.",
        "STEP 2b, if some games then say ACCESS DENIED: those files kept their own permissions. Same Security tab → Advanced → tick Replace all child object permission entries with inheritable permission entries from this object → OK → Yes. It can take a few minutes on a big collection.",
        "STEP 3, choose how the phone signs in. Option A (simplest each time, safest): use the PC's own sign-in. If you sign in to Windows with a Microsoft account, that's its email address and its password (not your PIN: Windows won't accept a PIN over the network). Settings → Accounts → Your info on the PC shows which account you use.",
        "Option B (no login at all): on the PC open Control Panel → Network and Sharing Center → Change advanced sharing settings → All networks → Turn off password protected sharing → Save. Then leave the username and password empty in RetroForge. Anyone on your home Wi-Fi can then read the folders you've shared, so only do this at home.",
        "A NAS (Synology, QNAP and others) usually has shared folders already: use a NAS user's name and password, or leave them empty if the share allows guests.",
        "STEP 4, in RetroForge: Settings → Games on a network drive (or Media → Films on a network drive) → Add a network drive.",
        "Tap Find on my network. After a few seconds your computer appears as a button (its address, and its name when the network gives one): tap it and the address box fills itself. Nothing found? Check the computer is on and awake, on the same Wi-Fi as the phone, and that step 1 is done. You can also type the address yourself: on Windows it's under Settings → Network & internet → your connection → IPv4 address.",
        "Shared folder: the share name from step 1, for example roms. A folder inside a share goes after a slash, for example E/Games. Then the username and password from step 3 (or leave them empty for option B), and a name to show if you like. Tap Test and save.",
        "When it says Connected, it's saved. Now add it as a game folder: Retro → Game folders → Add a folder. In Android's folder picker tap the menu (☰, top left), scroll down past your Google Drive accounts to your network drive (shown with its address underneath), open it, go into the folder you want and tap Use this folder, then Allow.",
        "RetroForge scans it like any other folder. Your roms folder can have system folders (snes, psx …) inside it, or one folder further down. Cartridge games play straight from the network; CD and arcade games are copied to the phone the first time you play them (you'll see a percentage), then start from the copy.",
        "Films: Media → Choose another folder, then the same steps in the folder picker.",
        "If it says \"doesn't allow guests\": Windows' Guest account is off. Use option A (your sign-in), or option B.",
        "If it says ACCESS DENIED or \"Create failed\": the share or the folder doesn't let you read it. Do steps 1 and 2 again for that exact folder (Everyone: Read on both tabs).",
        "If it says the username or password wasn't accepted: check them on the PC (a Microsoft account email and its password, not the PIN).",
        "If Find on my network finds nothing but the address works: some routers keep Wi-Fi devices apart (\"AP isolation\" or \"client isolation\"): turn that off in the router, or type the address yourself.",
        "Cloud storage (Google Drive, Dropbox, OneDrive) can't be opened as a folder by Android apps: use a sync app to keep a folder on the phone in step with it, and choose that folder.",
    )),
    Topic("Finding and playing games", listOf(
        "Retro shows your systems (only ones you have games for, or all of them in Settings), Recently played, Favourites, Playlists and a search box.",
        "Open a game and press Play. Most systems play in RetroForge's built-in player: Game Boy to GBA, NES, SNES, Mega Drive, Master System, PC Engine, DS, PlayStation, N64, PSP, Virtual Boy, arcade (FB Neo, MAME 2003-Plus, CPS1-3), Saturn (experimental) and Dreamcast.",
        "Each game shows how long you've played it in the built-in player.",
        "Games from RetroBat folders show their gameplay video on the game page, and \"Read the manual\" opens the game's manual when RetroBat downloaded one.",
        "Home → 🎲 Surprise me opens a random game from your collection.",
        "Big games (discs, arcade sets) play straight from their folder or network drive, so they start at once. Slow Wi-Fi? Settings → Playing → Disc games: choose Copy first, and they're copied to the phone before they start.",
        "Settings → Playing → Graphics and resolution sets OpenGL or Vulkan and the resolution per system; the pause menu has the same.",
        "Some systems need BIOS files. Settings → Set up RetroForge finds yours and copies them where the emulators look.",
    )),
    Topic("The in-game menu", listOf(
        "Swipe in from the left or right edge (or press ☰, Back, or Home/Guide on a controller) to pause and open the menu.",
        "The first page: Resume, save and load states, fast-forward, change disc, Keyboard (computers), Take a screenshot (saved to Pictures/RetroForge), restart and quit.",
        "Picture ›: screen shape, screen filter (sharp, smooth, CRT scanlines, curved CRT, LCD grid and ghosting, Scale2x), console look, bezel (The Bezel Project's artwork round the game in landscape; download a system's bezels here or in Settings → Covers and bezels → Bezels), widescreen, Resolution (draws 3D games sharper: 2× is like 720p, up to 10× for PSP), Graphics (OpenGL, or Vulkan for GameCube, PS2 and PSP, experimental), Cardboard VR and Show FPS (a frame counter for checking speed).",
        "Sound and controls ›: volume, touch controls on or off, Remap buttons. Emulator ›: each emulator's own settings, cheats and Switch emulator.",
        "Widescreen (N64 and Dreamcast): switches the game to 16:9 and restarts it by itself.",
        "Cheats: pick from libretro's cheat database for the game, or type your own codes.",
        "Carry on where you left off: quitting saves a state and the next start continues from it (Settings to switch off).",
    )),
    Topic("Touch controls, controllers and Remap buttons", listOf(
        "On-screen buttons are drawn for each console (PlayStation pad with both sticks, N64, six-button Saturn, Mega Drive and more), in portrait and landscape.",
        "Connect a controller and the on-screen buttons hide by themselves; they come back when it disconnects.",
        "Remap buttons (in-game menu): choose a button, then press the controller button you want for it, or drag on-screen buttons where you like. Saved per system.",
        "In the app: D-pad to move, A to select, B to go back, L1/R1 to switch tabs. The red ✕ at the top of every inner page also goes back.",
        "Hold R2 to fast-forward on systems whose controllers had no R2 (Game Boy, GBA, NES, SNES, Mega Drive …).",
    )),
    Topic("Computers: keyboard, mouse and BIOS", listOf(
        "The built-in player also runs home computers and more: Commodore 64, Amiga, MSX, ZX Spectrum, Amstrad CPC, Atari ST and 800, PC-88 and PC-98, X68000, DOS, ScummVM adventures, and consoles like the 3DO, Jaguar, Neo Geo CD, Vectrex and Intellivision.",
        "Keyboard: plug in or pair a keyboard (USB or Bluetooth) and type straight into the computer. On a touchscreen, open the in-game menu and choose Keyboard: Android's keyboard comes up and what you type goes to the game. On a C64, Esc is RUN/STOP.",
        "Mouse (Amiga, Atari ST, DOS, ScummVM …): the picture works like a laptop's trackpad. Drag a finger to move the pointer, tap to click, tap with two fingers to right-click.",
        "DOS games kept as a folder (RetroBat's \"Game.pc\" folders) play as they are: the first time, RetroForge copies the folder (a progress bar shows) and starts its dosbox.bat, or the game's own program.",
        "Commodore disks load by themselves at top speed, then the game runs at normal speed. Cartridge games start straight away.",
        "BIOS: some of these need the real machine's ROMs (Kickstart for the Amiga, TOS for the Atari ST, the 3DO and Neo Geo CD BIOS …). Put the ones you own in your BIOS folder, then Settings → Help and about → Set up RetroForge → BIOS files: RetroForge recognises them by their contents, whatever they're called, and copies them where each emulator looks.",
    )),
    Topic("Other emulators (RetroArch and standalone apps)", listOf(
        "A game's page also offers RetroArch, with the right core, for people who know their way around it, and free standalone emulators: NetherSX2 (PS2), Dolphin (GameCube, Wii), Azahar (3DS), Eden (Switch), DuckStation, PPSSPP, Flycast and more.",
        "\"Get\" opens Obtainium already set up for that app, so it installs from the official release and stays updated. Settings → Emulator apps also links the Obtainium Emulation Pack.",
        "Once installed: \"Play with …\" starts the game in it, and \"Use by default\" makes Play use it for that system.",
        "PS2, GameCube, Wii, 3DS and Switch need their app's own setup the first time (PS2 BIOS in NetherSX2, keys and firmware in Eden). Games made of several files (.cue, .m3u) may also need your games folder added inside the app.",
        "RetroArch hotkeys: Start + Select quits back to RetroForge; Down + Select opens RetroArch's menu. Change them in Settings → RetroArch hotkeys. RetroArch has its own menu, not RetroForge's swipe menu.",
    )),
    Topic("Graphics drivers and texture packs", listOf(
        "Settings → GPU and drivers shows your graphics chip. On Snapdragon (Adreno) phones it lists Turnip drivers, with search, to download for emulators that have a driver manager (Eden, Dolphin, NetherSX2 …): load the file in that app.",
        "Texture packs (N64): Settings → Download N64 texture packs gets free HD packs (Super Mario 64, Mario Kart 64, Smash Bros., Majora's Mask, Ocarina of Time) and switches them on. They're big: use Wi-Fi.",
        "Your own packs: Settings → Texture packs (N64 or PSP) → Add a texture pack folder. N64 packs are named after the game's internal name (SUPER MARIO 64), PSP packs after the game's ID (ULUS10041).",
    )),
    Topic("Multi-disc games", listOf(
        "A game with an .m3u playlist shows once and plays as one game. Change disc from the in-game menu.",
        "No .m3u yet? ROM Manager → Multi-disc makes them for you.",
    )),
    Topic("Covers and artwork", listOf(
        "Artwork from your other frontend (gamelist.xml) is used as it is.",
        "Open a game and choose Change cover. Box art: free, from libretro's thumbnail library; a system's list also has \"Download box art\" for every game without artwork.",
        "SteamGridDB: thousands of community covers. Add your own free API key in Settings (Show key lets you check it).",
        "ScreenScraper: front and 3D boxes. An account (Settings, with Show password) raises your daily limit.",
        "Your own image: any picture on the device. Edit details changes a game's name, year and description.",
    )),
    Topic("Favourites, playlists and backups", listOf(
        "On a game's page: \"Add to favourites\", or \"Add to a playlist\" to tick playlists or make a new one. Open a playlist to reorder, rename or delete it; the games themselves are never touched.",
        "Coming from ES-DE: Settings → Import collections from ES-DE turns its custom collections into playlists.",
        "Settings → Back up saves and settings: one zip with in-game saves, save states, cheats, settings, favourites and playlists. On a new phone, add your game folders first, then Restore from a backup.",
        "PlayStation memory cards (Settings → Playing): pick a game's card to see its saves, copy a save to another game's card (sequels that read the first game's save) or delete one. Open a card file from another emulator (.mcr, .mcd, .mc, .gme) to bring its saves across, or save a game's card as a .mcr file. Quit the game first.",
    )),
    Topic("ROM hacks and patches", listOf(
        "ROM hacks shows hacks you already have (named after the hack, not as a duplicate) and hacks from catalogues for NES, SNES, N64, Game Boy Color and GBA.",
        "Download and patch: for hacks published on GitHub, RetroForge downloads the patch, applies it to your own ROM and puts the new game in your library. Others open their official page.",
        "ROM Patcher (Retro screen) applies IPS, BPS or UPS patches, one after another, to make a new copy next to the original. It appears in your library by itself. Your original is never changed.",
    )),
    Topic("ROM Manager", listOf(
        "The only part of RetroForge that changes your files, and only after showing you what it will do and asking for permission to change that folder.",
        "Duplicates: finds the same game in several files and checks whether they hold the same data.",
        "Compress: PlayStation, Saturn, Dreamcast and other CD games (.cue/.bin) and PS2/PSP .iso become CHD; cartridges become .zip. Each new file is read back and checked before the original is set aside. GameCube and Wii: use Dolphin's own RVZ converter.",
        "Sort: moves loose games into the right system folders. Multi-disc: makes .m3u playlists.",
        "Nothing is deleted straight away: files go to _duplicates, _originals or _deleted, and History can undo every change. Free up space is the only step that deletes for good.",
        "If another frontend uses the same folder on a PC, close it first.",
    )),
    Topic("PC: ports of games you own", listOf(
        "PC → Ports lists fan-made PC versions of games you have (Ship of Harkinian for Ocarina of Time, Ghostship for Super Mario 64, SpaghettiKart, Zelda 3 and more), matched to your library.",
        "Android tab: Download and install gets the port's official release (Android asks you to confirm the install), or Get with Obtainium. Open starts it once installed.",
        "Windows tab: Download Windows version unpacks it into Download/RetroForge/Ports, ready to add in GameNative.",
        "Prepare files copies your ROM where the port expects it. Your original stays where it is, so you can play both.",
    )),
    Topic("PC: Windows games", listOf(
        "RetroForge doesn't run Windows games itself. A free PC game app does, installed separately: DroidDeck on Snapdragon devices, or GameNative and Winlator on others. The PC section checks your graphics chip and says which suit it.",
        "GameNative: install it from PC → GameNative releases. Sign in to Steam, Epic, GOG or Amazon inside it, or tap Skip login, then + and choose a game folder you copied to the phone (allow All files access).",
        "If Play says the executable couldn't be chosen: Edit container → Executable Path → pick the game's .exe, save, and press Play again. The first start downloads Proton and takes a minute or two.",
        "DroidDeck: needs a Snapdragon chip with an Adreno 730 or newer GPU. Install it from its official releases page, sign in to Steam inside it and install a game you own.",
        "Use only games you own. RetroForge never shares your store logins or game files.",
    )),
    Topic("Media: films and 3D", listOf(
        "Media → Use RetroForge's film folders makes Movies/RetroForge on your phone, with Films and 3D Films inside. When Android shows that folder, tap Use this folder, then Allow.",
        "Copy films in: plug the phone into a computer with a USB cable and drag them into Movies/RetroForge/Films or 3D Films, or move them with a file manager. Then press Look again.",
        "Already keep films somewhere else (an SD card, a USB drive)? Choose another folder instead. RetroForge only reads it.",
        "Films show as a poster grid with tidy titles and years, 3D films first. Posters, stories and ratings come from the files saved next to each film, as Kodi, Plex and Jellyfin use them: Film-poster.jpg or Film.jpg (or poster.jpg in a film's own folder) and Film.nfo or movie.nfo. Media Companion or tinyMediaManager on a PC make these for a whole collection. Films without them show a frame from the film; you can also add your own free TMDB key for personal use (Media → Film posters and stories).",
        "3D films: their file name needs 3D, SBS, HSBS (side by side) or TAB, HOU (top and bottom), as most 3D films have. They get a 3D badge.",
        "To watch in 3D: open the film, turn the phone sideways and put it in a Cardboard-style viewer. The View button switches between normal, Cardboard cinema (a 2D film on a big screen for each eye), 3D side by side and 3D top and bottom. RetroForge picks the right 3D mode from the file name.",
        "If a film can't play here, RetroForge offers to open it in another video app.",
        "Plex, Jellyfin, Emby or a NAS: Media → Find media servers. RetroForge finds them on your home network the way smart TVs and Bigscreen do (DLNA), with no account or sign-in, and remembers them. Open a server, step into its folders, tap a poster to play. Films in a folder with 3D in its name play in 3D.",
        "Plex not found? In Plex on the PC: Settings → DLNA → tick Enable the DLNA server. Make sure the phone is on the same network (Wi-Fi or the PC's USB tethering) and that the PC's firewall lets Plex through.",
    )),
    Topic("VR", listOf(
        "VR tab: play in a Cardboard-style headset. The game is drawn once for each eye; Virtual Boy games are in real 3D.",
        "Switch VR on from the in-game menu, or make it the default for every game on the VR tab. 3D films play from the Media tab.",
        "Meta Quest: RetroForge runs as a flat Android app.",
    )),
    Topic("Dual screens (AYN Thor, AYANEO Pocket DS …)", listOf(
        "Settings → Dual screen. DS games: the top screen on the main display and the touch screen on the second.",
        "Game box on the second screen while browsing (experimental): RetroForge's artwork, or the open game's cover, name and description.",
    )),
    Topic("Android apps and launcher mode", listOf(
        "The Android tab lists every app on the device, with search, and shortcuts to Android settings, Wi-Fi, Bluetooth, display and sound.",
        "Launcher mode makes RetroForge the home screen, so a handheld or TV box starts straight into it. Switch it off there to go back to your usual home screen.",
    )),
    Topic("Looks, sounds and help", listOf(
        "Settings → Themes: colours, shapes, type and game-list layouts with previews. Import your own, or a theme's colours and system art from ES-DE or RetroBat.",
        "Interface sounds: a tick when moving, chimes on select and back (Settings to switch off; they use the media volume).",
        "Settings → Report a problem shares the app and device details (no personal data) so problems can be fixed.",
        "Links has the YouTube channels and sites behind a lot of this: Retro Game Corps, Modern Vintage Gamer, Digital Foundry, My Life in Gaming, The Gaming Historian and more.",
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
