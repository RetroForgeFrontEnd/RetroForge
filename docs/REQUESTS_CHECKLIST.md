# Requests checklist

Everything asked for (the original brief, then every later request), with its status. Last updated 2026-10-28.
✅ done and tested · 🟡 partly / built but not fully tested · ❌ not done · ⛔ blocked on hardware or a decision.
Evidence for each test is in [TESTING.md](TESTING.md); the brief's phases are in [PROJECT_STATUS.md](PROJECT_STATUS.md).

## Original brief (summary; full status in PROJECT_STATUS)

| Item | Status |
|---|---|
| Retro library: scan, browse, artwork, metadata, favourites, history, collections | ✅ |
| Launch games; return to RetroForge; play history; clear errors | ✅ |
| Emulator/core management | ✅ built-in player downloads its own cores; RetroArch and 21 standalone apps as options |
| Controllers and TV navigation | ✅ emulator (keys only); ⛔ real controller and TV box |
| Save states, BIOS, firmware | ✅ check/copy, states per emulator, save backup/restore (zip); ✅ PlayStation memory cards: list saves, copy to another game, delete, open .mcr/.mcd/.mc/.gme, save as .mcr (tested on the emulator) |
| Graphics: shapes, filters, integer scaling | ✅ |
| Themes, theme engine, import | ✅ (colours + system art); ✅ interface sounds (own synthesised sounds, switch in Settings; not heard on a device yet) |
| Clear compatibility information | 🟡 docs; per-game compatibility not shown in the app |
| Portable library (other frontends keep working) | ✅ read-only scan, own index; ✅ settings, favourites and playlists in the backup zip (tested: back up, restore, games matched by path) |
| PC subsystem via a real runtime | 🟡 GameNative boots a game on the S22; ⛔ DroidDeck (needs Snapdragon); ✅ PC games in RetroForge's library (PC tab → Your PC games): .steam/.epic/.gog/.amazon/.pcgame files start in GameNative, Winlator .desktop shortcuts in Winlator Cmod/WinNative (ES-DE's launch requests); found by themselves in a "windows"/"steam" game folder, or added by Steam search; Steam box art. Tested on the emulator up to the launch (GameNative not installed there) |
| Documentation set | ✅ core docs and the 5 RetroBat audit docs ([architecture](retrobat-architecture-analysis.md), [workflows](retrobat-workflow-mapping.md), [ES-DE folders](retrobat-esde-folder-mapping.md), [Android parity](retrobat-android-parity-matrix.md), [recommendations](android-integration-recommendations.md)) |
| Automated tests | ✅ unit tests; ❌ automated UI tests |

## Later requests

| Request | Status |
|---|---|
| ROM Manager: duplicates, compressor (CHD/zip), delete, undo | ✅ |
| Covers: SteamGridDB, ScreenScraper, The Cover Project | ✅ SteamGridDB, own images; ⛔ ScreenScraper needs RetroForge's developer ID; The Cover Project: link only (site blocks automated access) |
| Selling for £2 / funding | ✅ answered; support links built (need your URLs) |
| Standalone emulators with downloads (Obtainium, Emulation Pack) | ✅ |
| Hotkeys like Batocera/RetroArch + hotkeys page | ✅ RetroArch and built-in player |
| Cover every RetroBat system | ✅ mapped, ES-DE folder names accepted too; 🟡 tested subset (see TESTING) |
| Seamless multi-disc | ✅ RetroArch m3u; ✅ built-in player disc change |
| R2 fast-forward on older systems | ✅ |
| Auto save/load (resume) with a setting | ✅ |
| Favourites and custom playlists | ✅ |
| Rename Hacks tab to "ROM hacks" | ✅ |
| About page with the Mr Sujano joke | ✅ (moved to the top of Settings) |
| Tutorial in Settings with Russ (Retro Game Corps) nod and his guides | ✅ |
| Remove "RetroBat" from the app | ✅ no visible mentions |
| Theme engine; themes that aren't just colours; layouts (Xbox/PS styles); swipeable previews | ✅ |
| Import a theme from another frontend | ✅ colours; ✅ system artwork (canvas-es, Alekfull-NX) |
| Themes scale on rotation | ✅ |
| Key art on Home | ✅ |
| In-game menu by swiping from the edges (cheats, shaders, controllers, GPU drivers) | ✅ edge swipe, cheats, filters, emulator settings, switch emulator; ✅ Remap buttons page (controller and on-screen, per system; tested); GPU drivers: Turnip downloads for other apps |
| Feature parity with RetroBat, Batocera, ES-DE, Daijisho, NeoStation, Socket, Cocoon | ✅ [comparison](feature-parity-comparison.md); gaps: video previews, UI sounds, dual screen, RetroAchievements, playtime |
| Setup clean-up: offer to reorganise a roms folder | ✅ Set up RetroForge shows "Tidy your games folder?" when games sit outside their system folder, and opens ROM Manager → Sort (preview first, undo in History). Tested on the emulator |
| Test the PC side (TCG Card Shop Simulator) | ✅ boots in GameNative on the S22 |
| GameNative for non-Steam games, no user setup | 🟡 tested by hand; ❌ can't be fully automatic (Android needs the user to confirm installs) |
| Test on the phone; install RetroArch | ✅ |
| No core downloading for users → built-in player | ✅ |
| Overlays per system, on by default, switch off | ✅ console look |
| Aspect ratio in the in-game menu | ✅ screen shape |
| Switch emulator mid-game | ✅ (states can't move between emulators) |
| Works on TV / without touch | ✅ emulator; ⛔ real TV box |
| Touch controls smaller and at the sides in landscape | ✅ |
| DS screens side by side when rotated | ✅ |
| Bigger screen in portrait | ✅ |
| Retro tab like canvas-es with system logos, in RetroForge colours, faded logo/key art, loads by itself | ✅ |
| Console pictures centred and scaled; landscape fits | ✅ |
| Edit game details | ✅ |
| Setup without RetroArch homework; Play Store + website buttons | ✅ |
| Setup reappearing after Done | ✅ fixed (not re-tested on a fresh install) |
| RF neon icon from the logo | ✅ |
| PC ports section (like Quiver, retro decomps) | ✅ Ports in the PC tab (see below) |
| Own cores for N64, PSP, Dreamcast (GPU) | ✅ N64 and PSP play on GPU rendering; ✅ Dreamcast tested on the S22 (Ikaruga: boots on GPU rendering, memory card saves) |
| PS1 with sticks, L2/R2/L3/R3; nicer pad | ✅ |
| Volume too low | ✅ volume keys + boost (needs your ears on the phone) |
| Cheats | ✅ |
| Widescreen hacks | ✅ Widescreen switch in the pause menu for N64 (tested, restarts by itself) and Dreamcast (untested) |
| More shaders (CRT curvature, LCD ghosting, upscaler) | ✅ curved CRT, LCD with ghosting, and Smooth pixel art (Scale2x upscaler), all tested on the emulator (ghosting's blending not yet seen in a flickering game) |
| Arcade: FBNeo, MAME, CPS; Naomi | ✅ FBNeo/MAME 2003-Plus; 🟡 Naomi/Atomiswave on Flycast, untested |
| Rename a game folder | ✅ |
| Retro tab only shows systems with games; setting for all | ✅ |
| 32X/Mega Drive buttons too close to the d-pad | ✅ d-pad and buttons shrink together on narrow screens |
| Time played per game | ✅ built-in player |
| All built-in player systems tested on the emulator | ✅ 17 systems boot (plus NES, SNES, Mega Drive, PS1 earlier) |
| Standalone apps for modern systems via Obtainium / Emulation Pack | ✅ "Get" opens Obtainium set up for 11 apps (Dolphin, PPSSPP, Eden, DuckStation, Play!, NetherSX2 …); ❌ Wii U (Cemu can't be started with a game) |
| N64, PS2, Wii, Wii U, GameCube, Switch in the built-in player | ✅ N64, PS2, GameCube and Wii in the built-in player (Vulkan for Dolphin and LRPS2); ✅ Switch in RetroForge's own Switch Player (separate APK, its own repository); ❌ Wii U (Cemu can't be started with a game) |
| "Made by Darren Glancy, built with AI" credit | ✅ About page and Settings |
| GPU detector, custom GPU driver support and downloads | ✅ GPU check, per-emulator driver recommendations, Turnip list/download; ✅ the built-in player loads Turnip on Snapdragon (tested on the Tab A9+); ✅ the Switch Player downloads its recommended Turnip in one tap |
| Saturn in the built-in player | 🟡 experimental (Yabause: 1 of 2 test games boots); YabaSanshiro after GPU rendering; RetroArch and Yaba Sanshiro 2 offered |
| File browser like CX File Explorer | ✅ Retro tab → Files: game folders plus any folder you add (Android's folder picker; RetroForge has no all-files permission). Browse, sort, filter, show hidden, select (A ticks, so a controller can do it all), copy/cut/paste with progress and Stop, rename, new folder, delete (asks first), share, open with another app; a name clash becomes " (2)". Checked on the emulator: copy identical, move, rename, delete, Back goes up, adding a folder. The 'allow changes' prompt for a read-only folder is not tried yet |
| Controller button tester in Settings | ✅ Settings → Playing → Controller test: buttons light up, sticks and triggers move, the controller's name and the last key are shown; hold B 2 s to leave (checked on the emulator with adb gamepad keys; a real controller's sticks not yet tried) |
| Tester guide and email to Mr Sujano | ✅ docs/TESTER_GUIDE.md; email in Documents (Word) |
| GPU rendering in the built-in player (N64, PSP, Dreamcast, GameCube, YabaSanshiro) | ✅ OpenGL ES: N64, PSP, Dreamcast, 3DS tested; 🟡 Vulkan experimental (see Vulkan row); GameCube/PS2 still crash or stall |
| Short demo video for Mr Sujano | ✅ Documents, RetroForge demo.mp4 (raw, from the emulator) |
| Bigger portrait picture for older systems | ⛔ waiting for your choice (zoom/crop vs bigger controls) |

## Requests of 29 October (phone test feedback and ideas)

| Request | Status |
|---|---|
| Way back from every inner screen: red ✕ for touch, B on a controller | ✅ tested on the emulator |
| Test APK for Mr Sujano | ✅ Documents\RetroForge-0.5.0-beta-tester.apk (test-signed; a release key isn't set up yet) |
| Turnip drivers: search for a specific driver | ✅ search box, 30 releases per source |
| Landscape: touch buttons too small on some systems (GBA) | ✅ 2-3 button systems get bigger buttons and d-pad (GBA checked) |
| Cheats: raw code flashes for a moment the first time | ✅ list and code boxes wait for the download (not re-tested on the phone) |
| Settings: the About card's short text reads jumbled | ✅ |
| Home: the writing on the logo can't be read | ✅ name and tagline as text under the art |
| RetroArch games have no RetroForge swipe menu | ❌ answer: RetroArch is a separate app; its own menu (hotkey) is used there |
| ScreenScraper: show/hide password | ✅ (and for the SteamGridDB key) |
| Report a problem | ✅ always shown at the bottom of Settings; shares a filled-in report |
| ROM hacks: patched ROM should appear in the library by itself | ✅ save dialog opens in the original's folder; folders rescan after saving |
| ROM hacks: patched ROM showed as a duplicate of the original after rescan | ✅ the patch name now shows in the subtitle |
| ROM hacks: more consoles (only 2) | ✅ 21 hacks for NES, SNES, N64, Game Boy, Game Boy Color, GBA and Mega Drive (duplicate removed); GitHub-hosted ones (Redux series, MaternalBound Redux) download and patch directly |
| Original Xbox and Xbox 360 emulation | ❌ look into (Android options are very limited) |
| PC ports for ROMs the user owns (like Quiver), in the PC section | 🟡 Ports section in the PC tab: 28 Android-native ports, matched to your games (hacks excluded), Get via Obtainium, Open when installed, Prepare files copies your ROM to Download/RetroForge/Ports (original kept). Tested: matching, Open (Zelda 3 port starts), Prepare files. Windows ports tab (Zelda64Recomp, OpenGOAL, UnleashedRecomp) with Windows download, GameNative/Winlator button and steps; experimental, Snapdragon only |
| Links: Modern Vintage Gamer, Digital Foundry, My Life in Gaming, The Gaming Historian | ✅ |
| Scan for new games when the app starts | ✅ on by default, switch in Settings |
| Use RetroForge as the Android home screen (launcher), with Android apps and Android settings | ✅ Android apps screen (Home and Settings), settings shortcuts, launcher-mode switch; tested on the emulator (Home button opens RetroForge) |
| Media section (films, Plex-style, artwork, Plex servers, network drives) | ✅ see the Media rows below |
| Games from network drives, Google Drive, Dropbox | ✅ network drives (see above); cloud: guide explains a sync app (Android apps can't open Drive/Dropbox as folders) |
| PC / iOS / Linux / Raspberry Pi versions | 📋 looked at 30 October: see "PC, Linux, iOS and Google Play versions" in Future plans; Raspberry Pi not looked at yet |
| How to fund updates when free | ❌ question (support links exist) |
| Dual-screen option for games and the app | 🟡 DS games: top screen on the main display, touch screen on the second (automatic when a second display exists); picture split tested on a simulated display; touch on the second screen needs a real dual-screen device. App UI on two screens: not yet |
| Compare with other frontends again and suggest more | ❌ after the above |
| Remove touch controls when a controller connects | ✅ hides as soon as one connects (even mid-game), back when it disconnects; plug-in event not testable on the emulator |
| VR: Cardboard 3D in the player; Quest; 3D films | 🟡 Cardboard VR mode (picture per eye) for every system; Virtual Boy in real 3D (Beetle VB side by side); tested on the emulator. Quest: runs as a flat Android app. 3D films: with the media section |
| Dreamcast in the built-in player | ✅ Flycast on GPU rendering, tested on the S22 (Ikaruga plays and saves to its VMU) |
| Remap buttons for touch and controller | ✅ pause menu → Remap buttons: controller keys and on-screen buttons per system; tested on the emulator |

## Found in a full re-read of the conversation (29 October)

Requests that were never fully done, or only answered:

| Request | Status |
|---|---|
| Media section: films on the phone, Plex-style layout, artwork, Plex servers, network drives; 3D films for VR | ✅ Media tab: films folder, poster grid (the film's own poster and .nfo, your own TMDB key, else a frame), tidy titles, 3D badge, built-in player (ExoPlayer); network drives; Plex and other DLNA servers (see below) |
| Media player for 3D films | ✅ tested on the S22: a 1080p half side-by-side film opens in Cardboard 3D side by side by itself (picked from the file name) and plays; View button cycles Normal / Cardboard cinema / 3D side by side / 3D top and bottom; RetroForge film folders (Movies/RetroForge/Films and 3D Films) |
| Scan for loose ROMs, not only in system-named folders | ✅ loose files and other folders searched by file type (gba, nds, sfc, z64 …); zip/iso/chd outside system folders reported, not guessed; tested on the emulator |
| Direct downloads for ports (Android APK and Windows versions), and test both | ✅ Download and install (APK from the official release, Android's installer confirms; tested: download + installer prompt, Super Metroid's old APK blocked by Play Protect) and Download Windows version (unzipped into Download/RetroForge/Ports; tested with Zelda64Recomp). Windows port in GameNative: needs your phone |
| Film info from IMDb | answered: scraping IMDb breaks its terms; TMDB (free) gives the same, plus an IMDb link later |
| Android tab with every app | ✅ main tab (Android), with settings shortcuts and launcher mode |
| Download ROM hacks directly in the app and apply them | ✅ "Download and patch" for hacks hosted on GitHub (tested with ALttP Redux); romhacking.net blocks downloads, so those open the page |
| Texture packs for systems that support them | ✅ N64: one-tap downloads (GhostlyDark packs) or add a folder; ✅ PSP: add a folder; switched on automatically. Not yet tried in a game with a real pack |
| Setup: offer to reorganise a roms folder into RetroForge's structure | ✅ ROM Manager → Sort: loose games planned into their system folders (existing names kept), moved with permission, undo from History; tested move and undo |
| Themes: Aurora (Xbox 360), ReLive, PS3/PS4/PS5 and current Xbox-style layouts | 🟡 8 layouts exist; not all of these looks |
| Themes: fan-made console artwork from SteamGridDB | ❌ (canvas-es console art is used instead) |
| Import a favourite theme so it looks identical | 🟡 colours and system art only |
| Use RetroBat's per-system controller config files | ❌ (Remap buttons covers most of the need) |
| A test function for every system and feature before release | ✅ Settings → Check my setup: every system's emulator, BIOS and games, problems first (tested); scripted boot test for development |
| Dual screen for the app itself (not just DS games) | ✅ game box on the second screen while browsing (key art, or the open game's cover, name and description); tested on a simulated second display. Off by default (Settings → Dual screen) until tried on a real dual-screen device |
| Migration from other frontends (ES-DE and others) | ✅ folders, gamelist.xml, theme colours, and ES-DE custom collections → playlists (tested) |
| Remove the name DroidDeck from the app but still use it | 🟡 RetroBat removed; DroidDeck is still named because users install it themselves |
| Original Xbox / Xbox 360 | ❌ research: no libretro core; xemu Android is experimental |
| Weekly bug reports on GitHub | 🟡 Report a problem built and the GitHub repository is public now; not yet checked end to end |
| Mr Sujano nod: the subscribe part | ✅ "…don't forget to like and subscribe! …sorry, wrong YouTube channel." |
| VR section | ✅ VR tab: how it works, start every game in Cardboard VR, Virtual Boy games, 3D films, Quest note |
| Settings wording messed up | ✅ controller help moved to How to use; duplicate Carry on card removed; set-up and built-in player texts updated |
| Dual screen options in main Settings | ✅ Dual screen card: second screen detected or not; DS games on both screens on/off |
| Direct ROM hack downloads | ✅ for hacks published on GitHub (A Link to the Past, Zelda 1, Zelda II Redux): Download and patch; romhacking.net refuses automated downloads |
| Spelling across the app; ROM Manager capital M | ✅ every on-screen sentence spell-checked (no misspellings found); ROM Manager capitalised everywhere |
| Final Burn should be FB Neo | ✅ |
| System names: Master System not "Master System - Mark III", and the like | ✅ 34 names shortened (Master System, Mega Drive, NES, SNES, CPS1–3, PlayStation 2–4, Game & Watch …). Fixed: Ghostship had Starship's id (a slip in RetroBat's list); now its own |
| Download texture packs directly | ✅ Settings → Download N64 texture packs: GhostlyDark's free packs (SM64, MK64, Smash Bros., Majora's Mask, Ocarina of Time) from GitHub, unpacked where the N64 emulator looks and switched on automatically. PSP packs: add a folder (no free direct source found) |

| System list: a separate section for ROM hacks | ✅ each system has Games and ROM hacks tabs when it has hacks (NES: 41 games, 30 hacks; tested on the emulator) |
| SteamGridDB covers with the user's own key | ✅ tested on the S22: search found Super Mario 64, covers listed with their artists, chosen cover shown on the game page with credit |
| Dreamcast memory cards | ✅ tested on the S22 (Ikaruga creates its save file) |
| PS2 compression to CHD | ✅ tested on the S22 (Gran Turismo 3: 3.88 GB .iso → 3.57 GB CHD, read back and checked, original set aside) |
| Netplay for all systems | ✅ built-in player: host and join, lockstep with input delay, the host's state sent on join, checksum and automatic resync; same Wi-Fi (found by broadcast) or internet (join by address; the router is asked to open the port by UPnP). Play together on game pages, the in-game menu, the Retro tab; name and delay in Settings; tutorial. Tested S22 (host) + emulator (guest): Balloon Fight 2-player, both players' buttons on both screens, router opened the port. 🟡 Online lobby and relay server not built (need a server) |
| Network drives built into RetroForge (no second app) | ✅ own documents provider (smbj, read-only, 1 MB read-ahead); Find on my network (every interface), shared folders listed automatically, one tap adds them to games or films with a "N games found" confirmation; tested on the S22 over the PC's share: Super Mario World and a PlayStation game played from the network. Emulator can't reach SMB from apps (its virtual Wi-Fi) |
| Save syncing: pull and push saves from network drives and cloud drives automatically | 🟡 Settings → Playing → Save syncing: a network drive folder or an Android folder (cloud apps keep it in step); a game's saves come in when it starts and go out on quit, newer wins, the other kept, nothing deleted. Tested both ways with a folder on the emulator and with a PC share from the S22 (24 saves sent, a save made on the PC brought back) |

| Settings condensed | ✅ five groups (Games and library, Playing, Covers, Looks and sounds, Help and about), each on its own page |
| Ask before Back closes RetroForge | ✅ "Close RetroForge?" on Home (tested on the emulator) |
| Texture packs all in one place | ✅ one Texture packs page (downloads, N64 and PSP folders) |
| Vulkan in the built-in player | ✅ for Dolphin and LRPS2 (default for GameCube/Wii and PS2): the player gives negotiating cores a stand-in surface and answers its queries, so Dolphin builds its wrapped swapchain and hands frames over (copied and flipped into the OpenGL picture). Tested on the S22: Luigi's Mansion plays. 🟡 PPSSPP on Vulkan not yet tested; OpenGL stays its default |
| Android tab as a phone-style home screen | ✅ clock and date, search, a dock of pinned and most-used apps (the usual phone apps until there's history), icon grid, long press pins/unpins (tested on the emulator) |
| CTR-native (Crash Team Racing PC port) via GameNative | ❌ to try: decompilation (GPL-3.0), no releases yet, needs building and the user's own CTR disc data |
| Players for every system | 🟡 77 more systems in the built-in player (Amiga, C64/C128/VIC-20/Plus4/PET, DOS, MSX, ZX Spectrum, Amstrad, Atari ST/800/5200/7800, Jaguar, 3DO, Neo Geo CD, PC-FX, CD-i, Intellivision, ColecoVision, Vectrex, Odyssey², PC-88/98, X68000, Apple II, Pokémon Mini, ScummVM, Doom, Quake 1/2, Cave Story, EasyRPG, TIC-80, PICO-8 …). Tested on the emulator: C64 (VICE, disk autoloads), Atari 7800, Vectrex (pad 1-4 like the real one) and Pokémon Mini (A, B, C, Shake; plays), DOS folder games (Gods). Still none: PS3/Vita/Wii U/Xbox 360 (their libretro cores need Vulkan), Model 2/3, Naomi 2 extras, Windows-only systems |
| DOS games kept as folders (RetroBat "Game.pc" folders) | ✅ the folder is copied once into the game cache (with progress) and started from its dosbox.bat, else a .bat/.exe named like the game, else its only program. Tested on the emulator: RetroBat's Gods.pc plays (Bitmap Brothers intro) |
| Keyboards for computer systems (C64, Amiga, DOS, MSX, ZX Spectrum …) | ✅ physical keyboards type straight in; Keyboard in the in-game menu opens Android's keyboard; keys held long enough for the emulator to see them (tested: PRINT 42 in C64 BASIC; on-screen letters reach the C64) |
| Cores that ask to fast-forward (VICE warp while loading) | ✅ supported |
| Mouse for computers (Amiga, Atari ST, DOS, ScummVM …) | ✅ the picture is a trackpad: drag moves, tap clicks, two-finger tap right-clicks (tested: Amiga 1200 Workbench, pointer moved and Cancel clicked) |
| BIOS rules for the new systems | ✅ 21 more rules from libretro's System.dat (Amiga Kickstarts, 3DO, Atari ST/5200/800, CD-i, Intellivision, Neo Geo CD, PC-88/98, PC-FX, X1, X68000 …); recognised by checksum whatever the file is called (tested: TOSEC-named Kickstarts found and copied) |
| PlayStation memory card tools | ✅ Settings → Playing: a game's card and its saves, copy a save to another game's card, delete, open .mcr/.mcd/.mc/.gme, save as .mcr (tested on the emulator) |
| Plex and DLNA servers with no sign-in (like Bigscreen) | ✅ Media → Find media servers: DLNA search on every network plus Plex's DLNA port, servers remembered, folders, the server's posters, films stream in the built-in player; 3D from the folder name. Tested on the S22 with Plex over USB tethering: found by itself, 39 3D films with posters, 300: Rise of an Empire played in Cardboard 3D |
| Movie artwork without TMDB's commercial licence | ✅ no TMDB key built in (removed from the build); posters and .nfo files next to films (Kodi/Plex/Jellyfin names, as Media Companion and tinyMediaManager save them) come first, then the user's own TMDB key if they add one, then a frame; Plex/DLNA servers give their own posters. Tested on the S22 (poster, story, year, runtime, rating, IMDb link from a .nfo) |
| A way back from every page and section | ✅ red ✕ on every page but Home: inner pages go back a step, main tabs go to Home, Settings groups go back to all settings; Plex folders step back with Android's back and ‹ Back (tested on the S22) |
| 3DS in the built-in player on the phone | ✅ Citra plays on the S22 (Dr. Mario Miracle Cure, both screens, buttons) with software shaders: ANGLE on Samsung's Xclipse GPU can't compile Citra's geometry shaders. Hardware shaders come back with Vulkan rendering |
| PS2 in the built-in player | ✅ LRPS2 on Vulkan by default: tested on the S22 (Maximo: memory card screen, Capcom logo, 3D main menu, no crash). PS2 BIOS rule (73 versions, copied to pcsx2/bios); OpenGL/software renderer if picked. NetherSX2/ARMSX2 standalone still offered |
| GameCube in the built-in player | ✅ Dolphin on Vulkan by default: tested on the S22 (Luigi's Mansion: title, intro and play). On Samsung's ANGLE-based OpenGL, Dolphin crashes in the driver or draws garbage, hence Vulkan. Dolphin standalone still offered |
| Stream games from network drives and folders instead of copying | ✅ the player offers libretro's VFS and reads the game from the open file: tested on the S22, a PS1 CHD from the RetroBat share over Wi-Fi started with no copy. Earlier copies are used; cores without VFS copy; Settings → Playing → Big games: stream or copy |
| Network drives keep working when the PC's address changes | ✅ when the saved address doesn't answer, RetroForge finds the PC again on the network and saves the new address (tested: USB tethering → Wi-Fi) |
| BIOS from a RetroBat folder on a network drive | ✅ setup finds a "bios" folder beside the games on RetroForge's network drives by itself and fills in what the chosen BIOS folder lacks (tested: PS2, 64DD, ST-V and every other BIOS copied from the RetroBat share) |
| Search finds exact titles first | ✅ whole title, then title start, then word starts, before the result limit (tested: rez, ape escape, god of war) |
| Players for the systems still missing | 🟡 added: BBC Micro (b2), J2ME (SquirrelJME), Sega ST-V (MAME), 64DD (Mupen64Plus-Next) — not yet played. Still none: Apple IIGS, Astrocade, CoCo, Game.com, V.Smile (MAME's computer core, next), Daphne/Singe, Model 2/3, Hikaru, Triforce, Neo Geo 64, Jaguar CD, Xbox, and PC ports (Duke 3D, Perfect Dark, OpenJazz, OpenBOR, Sonic decomps) that need their own apps |
| Vulkan / OpenGL choice in Settings and the pause menu | ✅ per system (Settings → Playing → Graphics and resolution, and the pause menu); OpenGL default, Vulkan marked experimental; switching restarts the game with its place saved |
| Upscaling (internal resolution) | ✅ per system 1× up to each emulator's most (PSP and Dreamcast 10×, 3DS 9×, GameCube and PS2 8×, N64 4×), in Settings and the pause menu; picks the emulator's own resolution values. Tested: 3DS at 2× on the S22 |
| Game manuals | ✅ "Read the manual" on the game page opens RetroBat's manual (manuals/<game>-manual.pdf) in a built-in page viewer; tested on the S22 (Rez, from the network share) |
| Gameplay video previews | ✅ RetroBat's videos/<game>-video.mp4 plays silently on a loop on the game page; tested on the S22 (Rez) |
| Screenshots | ✅ pause menu → Take a screenshot: the picture as shown (filters included) saved to Pictures/RetroForge |
| Surprise me | ✅ Home → 🎲 Surprise me opens a random game (ROM hacks left out) |
| Pause menu condensed | ✅ main page (resume, states, fast-forward, disc, keyboard, screenshot, restart, quit) plus Picture ›, Sound and controls ›, Emulator › |
| Performance overlay (FPS) | ✅ optional: pause menu → Picture → Show FPS; frames shown per second against the game's own rate, green at full speed |


| Retro tab tiles: game art with the console | ✅ fourth tile style "game art + console" (Tiles button cycles consoles → game art → game art + console → plain); tested on the S22 |
| App wouldn't open after it was closed while a network-drive video played | ✅ fixed 2026-10-29: the network drives provider runs in its own process (":drives"), so a killed app no longer waits on its own file reads (a stuck "zombie" process that needed a phone restart). Tested: killed during video and during PS1 streaming, reopens at once |
| Free games to download (PC tab) | ✅ 21 shareware/freeware/homebrew games from RetroArch's own content server (Doom, Wolfenstein 3D, Commander Keen, homebrew for NES/SNES/Mega Drive/GB/GBA, TIC-80), saved into the user's game folder; Doom downloaded and played with touch controls on the emulator. Abandonware not offered |
| Percentages on downloads and scans | ✅ game and BIOS scans, bezels, free games, hack patches, ROM Manager scans, app updates; cover downloads are a single small file |
| App updates | ✅ checked every few hours from GitHub releases (betas included), offered on Home with a message at startup, and Settings → Help and about → Check for updates; Android's installer confirms |


## Requests of 31 October (new features, in this order)

| Request | Status |
|---|---|
| RetroAchievements | 🟡 built: rcheevos in the built-in player, sign-in in Settings → Playing, unlock pop-ups, the list in the pause menu, hardcore. CHD discs (CD, GD-ROM, DVD) and games streamed from their folder are identified too: on the emulator a PS1 and a Dreamcast CHD give the same RetroAchievements hash as their cue/bin and gdi. Sign-in and unlocks need the user's account to test |
| Save state manager in the app | ✅ tested on the emulator: pause menu → Save states › makes as many states as you like, each with a picture, to load, overwrite or delete; the game page lists every state (quick save, resume point, numbered) with its picture and date: Play from here, Rename, Delete |
| Smart backlog and playtime analytics | 📋 to do: a backlog (want to play, playing, finished), suggestions from it, and playtime charts per game, system and week |
| Peer-to-peer save and memory card sync on the local network | 📋 to do: two devices find each other on the Wi-Fi and swap newer saves and memory cards when RetroForge opens, no network drive needed |
| Custom soundtracks and a jukebox | 📋 to do: your own music in the menus (and over games if wanted), and a jukebox for game music files |
| Music Centre (a full music player in RetroForge) | 📋 to do: goes further than the jukebox above and shares its player. Scans your music folders; browse by artist, album, genre and year; plays FLAC, WAV, MP3, OGG, Opus, AAC/M4A and the game music formats; gapless playback; ReplayGain (track and album); an equaliser with presets; lyrics, with synced .lrc lyrics scrolling along; playlists to make, edit, import and export (.m3u). A polished screen of its own, controller-friendly like the rest of RetroForge |
| Smart Home Control Centre | 📋 to do: control the home from RetroForge, through Home Assistant first (its local API covers most brands, so RetroForge needn't talk to each one): lights and plugs, heating and thermostats, doorbells and cameras (live view and a pop-up over the menus when someone rings), smart speakers, and your own scenes ("game night" dims the lights). Optional, off by default, and controller-friendly |
| Game notes, strategy guides and map viewer | 📋 to do: notes per game (also in the pause menu), guides and maps from your own files, viewable mid-game |
| Interactive Game Companion | 📋 to do: grows from the game notes and guides above (built by the second chat on its branch, not yet merged). A panel while playing with walkthroughs, level guides, maps you can zoom and mark, boss strategies, collectibles, secrets and tips. Guides come from your own files, web pages you add (shown in a built-in viewer), and openly licensed wikis; RetroForge won't copy guide sites' content without permission. On dual-screen devices (AYN Thor and other two-screen handhelds) the guide sits on the second screen beside the game; on one screen it opens as a side panel or over the pause menu |
| Ghost Racing System | 📋 to do, built-in player only. A run is recorded as a save state plus every button press, so it replays exactly and stays tiny (kilobytes, not video). Personal bests kept per game and track. The ghost: for any game, the best run plays back in a small see-through picture beside or over yours, started from the same moment; for chosen racing and platform games (Mario Kart, F-Zero, Sonic, Mario), a real in-game ghost marker drawn from the game's memory (car or player position, like RetroAchievements reads memory), plus split, lap and finish times read the same way. Challenges: send a run file to a friend to beat, over the local network or as a file. Each supported game needs its memory addresses worked out once |
| Tournament Manager | 📋 to do: knockout, double knockout, league and groups-then-knockout formats, with the bracket or table drawn automatically and updated as results come in; players entered by name (and profile picture) or joined from their own device; scores entered by hand, or read from the game for supported titles (as the ghost racing times are); local tournaments passing one device or using two controllers, and online ones with friends through netplay; custom rules per tournament (game, rounds, best-of, characters or teams allowed). Results and a winners' history kept |
| Virtual Link Cable | 📋 to do: two games joined as if by a link cable, for Pokémon trades and battles, Tetris versus and the like. Game Boy and Game Boy Color first (the Gambatte and SameBoy cores have network link built in), then Game Boy Advance (mGBA's link support, and RetroForge's netplay where the core has none). Same device: two games side by side, or one on each screen of a dual-screen handheld. Two devices: over the home Wi-Fi (found automatically), or online between friends through the netplay connection. Saves stay each player's own |
| Universal High Score Database | 📋 to do: high scores for the whole library. Where scores come from: arcade games through the hiscore.dat list MAME and FinalBurn Neo already use (it says where each game keeps its score table); RetroAchievements leaderboards, which already cover thousands of console games; the game's memory for others (the same reading as ghost racing); or typed in by hand. Leaderboards per game, personal records, a score history, and a screenshot taken automatically when a score is set, as evidence. Rankings: on this device and between your devices and friends on the local network; online rankings through RetroAchievements first, since a RetroForge score server would need running and protecting from cheats |
| Game Series Journey Mode | 📋 to do: games grouped into series (Resident Evil, Zelda, Final Fantasy…) from Wikidata's open "part of the series" data and the scraper's own series and family fields, then put in order of original release (or story order where the series has one); progress through each series uses the backlog's "finished" mark (built by the second chat, not yet merged); entries you don't have are shown greyed out with their system and year; make your own journeys (any games, any order); milestones and a badge for finishing a series |
| Automatic fan-translation patching | 🟡 soft patching built (31 October): put a patch named like the game beside it ("Game.bps", ".ups" or ".ips", as RetroArch does) and the built-in player applies it to a temporary copy as the game loads, so translations and QoL patches play without the game file changing; a patch made for another version is skipped with a message. Cartridge games (also zipped or 7z), not disc games. Tested on the emulator: an IPS beside "2048 (tsone).nes" patched the copy the player loaded and left the file alone (the on-screen message wasn't caught in a screenshot). 📋 Still to do: spotting Japanese-only games and offering known fan translations (needs a catalogue of translations with their base-ROM checksums, which the Identify tab can now match) |
| Smart input profiles and per-game layout swapping | ✅ Remap buttons → "Layout for": every game on the system, this controller (by its Android id, so each pad keeps its own), or this game only; a game's layout wins, then the controller's, then the system's, and plugging in a controller brings its layout along. Tested on the emulator (system → this game, saved for the game alone); a real controller's layout not tested |
| Retro news and modding feed | 📋 to do: a feed of retro gaming news, emulator releases and modding from public RSS feeds |
| AI translation overlay | 📋 to do: translate on-screen text in a game (Japanese and others) and show it over the picture, like RetroArch's AI service; needs a translation service (on-device OCR and translation where possible) |
| Virtual light-gun: touch, gyro and second-controller aiming | 📋 to do: light-gun games (arcade, Time Crisis, Duck Hunt, Saturn shooters) aimed by touch, the phone's gyroscope or another controller's cursor, with a crosshair and calibration |
| Kiosk mode and child lock | 📋 to do: settings, deleting files, the ROM Manager and emulator settings locked behind a PIN or a switch, for family and TV setups |
| 3D disc, cartridge and box gallery | 📋 to do: on the game page, spin a 3D disc, cartridge or case and look at the back of the box |
| Smart memory card importer and converter | 📋 to do: bring in saves from other emulators and devices (PS1 .mcr/.mcd/.gme/.psv, PS2 .ps2/.max/.psu, GameCube .gci/.raw, Dreamcast VMU, N64 .eep/.mpk, .srm/.sav), work out which game they belong to, convert to the format the chosen emulator reads and put them in place |
| Speedrun and challenge mode | 📋 to do: a split timer in the player (LiveSplit-style splits, personal bests per game), RetroAchievements leaderboards and challenges shown alongside, and set challenges (no-damage, time limits) |
| Power and heat profiles per system | 📋 to do: Battery saver for light systems, High performance for PS2/GameCube/Switch/PC. Android doesn't let apps set CPU/GPU clocks (that needs root), so this uses what apps may: Android's performance hints and Game Mode, sustained performance mode, a lower frame rate and resolution where the game allows, and the picture's refresh rate |
| ROM header fixer and region patcher | 📋 to do: find and fix bad headers (strip SNES copier headers, add or repair iNES/NES 2.0 headers, fix Mega Drive and Game Boy checksums) and region-free patches where known, always on a copy with the original kept |
| Frame skip in the pause menu | ✅ done: Off, 1, 2 or 3 frames skipped between drawn ones, kept for each system; the game and its sound keep full speed |
| Virtual touchpad and motion sensor calibration | 📋 to do: use the touch screen as a mouse or touchpad (PC, DS, Wii pointer), and calibrate the gyroscope and accelerometer (centre, dead zone, sensitivity, axis flips) for motion games |
| Audio latency and resampler studio | 🟡 Sound and controls → Sound buffer (32, 48, 64, 96 or 128 ms of queued sound, kept for each system; tested on the emulator). Still to do: choosing AAudio or OpenSL ES and the resampler |
| RGB and LED lighting sync | 📋 to do: handheld stick rings and accent lights (Retroid, AYN, AYANEO where the maker exposes them) and smart lights (Philips Hue on the local network) follow the game's picture or system colours, flashing on hits |
| Automatic ROM renaming and cleaning | ✅ ROM Manager → Identify: each game's checksum (CRC32 and size) is looked up in the No-Intro (cartridge) and Redump (disc) lists from libretro-database (CC BY-SA 4.0, downloaded per system and kept a month; your own DAT-o-MATIC or Redump .dat in place of it works too). Zips and 7z read their stored checksums without unpacking; iNES, FDS, Atari 7800, Lynx and SNES copier headers are tried with and without; N64 .v64/.n64 are summed in the lists' byte order; a cue/bin is matched on track 1. Shows verified, already-named-right, not-in-the-lists (hacks, translations, homebrew, bad copies) and left-alone games, then renames the systems you tick: the file keeps its own type, a disc's tracks and cue sheet go together, gamelist.xml and .m3u playlists follow, favourites and history stay with the game, and History undoes it. Arcade sets are left out (MAME names). Tested on the emulator: RetroBatTest's 243 games gave 121 verified (97 already right, including a mislabelled "ActRaiser (Japan) (Translated En)" that is really ActRaiser 2 (Japan)), 120 not in the lists; a Lynx rename and its undo worked. 🟡 Not yet: CHD, RVZ and GD-ROM discs; the download itself (the emulator had no internet, so lists were copied in by hand); a real cue/bin rename on a device (covered by a JVM test) |
| Hardware benchmark and thermal profiler | 📋 to do: a stress test of the device for emulation (CPU, GPU, OpenGL and Vulkan), recording when it throttles and how fast it runs, then suggesting resolution and graphics backend for PS2, GameCube and PC games |
| Advanced Device Manager | 📋 to do: a control centre for the device, alongside the benchmark above (it shares the readings): CPU and GPU load and clock speeds, RAM and storage in use, battery health and charge cycles where Android reports them, temperatures where the device exposes them, network speed, the apps running and their memory, and a storage analyser that shows what fills the space (games by system, saves, covers, caches) with a way to clear caches. Made for handhelds and gaming phones; readings Android hides are shown as unavailable, not guessed |
| Personal Security Vault | 📋 to do: an encrypted space inside RetroForge for notes, documents and files, opened by PIN or fingerprint/face (Android's biometric prompt) and locked again when you leave it; private profiles that hide their games, saves and vault from other profiles. Encryption keys are kept in Android's hardware keystore, never in RetroForge's files or backups. Passwords are left to a real password manager (RetroForge opens it, doesn't store passwords itself). Shares the PIN code of the kiosk lock. Needs an outside security review before release, since a weak vault is worse than none |
| Document Scanner and Manager | 📋 to do: scan pages with the camera (edges found and straightened), turn them into PDFs, read the text with on-device OCR so documents can be searched, sort receipts, invoices and paperwork into folders, and export or share them. The OCR is the same on-device text reading as the AI translation overlay; scans can be kept in the Personal Security Vault |
| Automation Studio | 📋 to do: your own "when this, do that" rules. Triggers: battery level, home Wi-Fi joined or left, time of day, a controller connected or removed, charging, a game started or quit. Actions inside RetroForge: a lighter performance profile, connecting network shares, dark or light theme, opening a chosen app or game, a theme or layout. Some system changes (Android's own battery saver, system dark mode) can't be switched by an ordinary app, so RetroForge opens the right Android setting for those instead, or leaves them to Tasker. Rules are made with a controller-friendly picker, no code |
| Universal Clipboard Manager | 📋 to do: a history of copied text and links, pinned snippets, search, saved text templates, and sending clipboard contents between your own devices and your PC over the local network (same pairing as the peer-to-peer save sync). Limit: since Android 10 only the app in front (or the keyboard) may read the clipboard, so RetroForge can only record what was copied while it is open, or what you share to it from another app; a full background history would need a RetroForge keyboard |
| QR and Barcode Centre | 📋 to do: scan QR codes and product barcodes with the camera; make QR codes for Wi-Fi sharing, contacts, websites and file sharing (a link to a file RetroForge serves on the local network); a history of scanned codes. Read codes from a screenshot or picture too, and from what's on screen right now (Android's screen capture, with permission). Same-device QR sign-in (for example a Steam QR shown on this phone): Android won't let one app feed a fake camera picture to another without root, so instead RetroForge reads the code off the screen and opens its link in the app that handles it, or copies it; whether Steam accepts its sign-in link that way needs trying |
| World Clock and Travel Centre | 📋 to do: several world clocks, a time-zone converter, a currency converter (rates from a free public source, cached for use offline), a trip organiser with itineraries, links to flight information by flight number, weather at saved destinations, and a countdown to each trip (also on the Home screen if wanted) |
| Pass the controller: swap players 1 and 2 | 🟡 pause menu → "Pass the controller: you are player 1/2" moves this device's buttons and sticks to player 2 and back, for games that take turns (tested on the emulator with the N64 joypad test). Still to do: a hotkey, and handing player 1 to the other device in netplay |
| Quality-of-life patch collections | 📋 to do: one-tap QoL patches by game (60 fps, widescreen, faster text, slow-down removed) from public patch collections, applied on a copy or as soft patches, like translation patching |
| Voice chat in netplay | 📋 to do: push-to-talk or open voice between the players in a netplay game, over the same connection, with mute and volume in the pause menu |
| Second screen on another device | 📋 to do: send the DS or 3DS bottom screen (and later a Wii U GamePad view) to a nearby phone, tablet or second monitor on the same Wi-Fi, with touch sent back to the game |

## Future plans (30 October)

| Request | Status |
|---|---|
| RetroForge PS3 Player (our own Android build of RPCS3, open source GPL-2.0, aiming to be more stable than the existing ports) | 🔨 in progress in `F:\PS3Player` (not in 0.7.1): firmware installs, Ultimate Marvel vs. Capcom 3 boots and draws on Turnip on the Tab A8 but far too slowly, title screen not reached; RetroForge sees the player (manifest query) but doesn't offer PS3 games to it yet |
| PS4 | 📋 future. The open-source PS4 emulators (shadPS4, GPL-2.0, and others) are PC-only and early, and a PS4 game needs far more than today's phones have. Revisit once shadPS4 or a fork runs on ARM64 Android. RetroForge has no PS4 system yet |
| 3DS in full 3D on Meta Quest | 🟡 CitraVR (amwatson/CitraVR, GPL-3.0, Azahar for Quest) is a 3DS choice: RetroForge opens the game straight in CitraVR's VR view, as it does with Azahar. Its source (F:\VR3DSPlayer) shows VrActivity takes the game as content:// data, so no fork is needed. Not tried on a Quest yet |
| VR light-gun games (VR tab) | 📋 future. Play retro light-gun games (Time Crisis, House of the Dead, Virtua Cop, Duck Hunt and the like) on a Meta Quest or another Android VR headset, with the VR controllers aimed as the light guns: the controller's pointing ray becomes the gun's screen position, the trigger fires. Built-in player first (libretro cores already take a light-gun or pointer position), shown on a big virtual screen |
| PC, Linux, iOS and Google Play versions | 📋 future, looked at 30 October: Google Play first (RetroForge only, never the Switch or PS3 players; needs a real release key first), then Linux/Steam Deck and Windows with Compose Multiplatform, iOS last (needs a Mac; no JIT, so retro systems only) |

## Still to do after 0.7.1 (30 October, evening)

| Item | Status |
|---|---|
| Push the commits made after the 0.7.1 release (Switch Player links to its own page, setup wording, guide) | ⛔ waiting for the user's OK; they ship with the next update |
| Switch Player: a retail game started without keys or firmware opens Switch settings in setup mode | 🟡 built, untested (no retail game on a free device) |
| Switch Player: Install firmware from a folder of .nca files | 🟡 built, untested |
| Switch Player: a real Bluetooth controller in game and in the pause menu | 🟡 only injected key presses tested; needs the user's controller |
| Switch Player: the touch-control outline in a bright game | 🟡 untested |
| Switch Player: the Turnip download button on a Snapdragon device | 🟡 recommended driver tested on the Tab A8; the in-player button after the settings rework untested |
| Compress 3DS, Dreamcast, Wii and GameCube games (user request) | ✅ GameCube to RVZ in the ROM Manager (Dolphin's format, Zstandard level 5, junk stored as seeds; Luigi's Mansion 1.46 GB → 164 MB, checked byte for byte; Dolphin 2609a's DolphinTool reads it and its SHA-1 matches the ISO; Dolphin's own RVZ of the same game is 164.5 MB); 🟡 not yet played from RVZ on a phone; ❌ Wii to RVZ: needs Nintendo's Wii key to decrypt, which RetroForge won't include, so the ROM Manager points to Dolphin's converter; ✅ Dreamcast .gdi to CHD (laid out as chdman 0.287 does; Ikaruga matches the original CHD's checksums and `chdman verify` passes); ❌ .cdi stays as it is (chdman can't read DiscJuggler images; Flycast plays them); ✅ all CHDs now leave out rebuildable sector parity and repeat hunks once, so they're as small as `chdman -c cdzl` (were up to twice the size on GD-ROMs); ✅ 3DS to Azahar's Z3DS (.3ds/.cci → .zcci, .cxi → .zcxi, .3dsx → .z3dsx; Super Mario 3D Land 321 MB → 291 MB; Azahar 2126.2 unpacks our file to the identical ROM and its own .zcci is the same size; encrypted games are left alone with a reason, since Azahar can't use them); ❌ .cia → .zcia (CIAs are installed, not played; left alone) |
| Keys and firmware backups on the devices (/sdcard/rf-switch-backup on the S22 and the Tab A8, about 330 MB each) | ⛔ delete once the user is happy the Switch Player keeps its keys |
| GitHub two-factor authentication for RetroForgeFrontEnd | ⛔ the user enrols on github.com before 14 December 2026 |
| Big logo export (for the GitHub page and posts) | 📋 optional |
| PS3 games offered to the PS3 Player from RetroForge | ❌ after the player runs a game properly |
| Promotion: Reddit post, sharing guide | 🟡 r/EmulationOnAndroid post drafted; where-to-share.md in the release folder |
