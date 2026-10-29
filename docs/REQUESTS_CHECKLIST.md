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
| PC subsystem via a real runtime | 🟡 GameNative boots a game on the S22; ⛔ DroidDeck (needs Snapdragon); ❌ PC games in RetroForge's library |
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
| N64, PS2, Wii, Wii U, GameCube, Switch in the built-in player | 🟡 libretro builds exist for all but Switch; needs GPU rendering in the player (in progress); standalone apps stay offered |
| "Made by Darren Glancy, built with AI" credit | ✅ About page and Settings |
| GPU detector, custom GPU driver support and downloads | ✅ GPU check + Turnip list/download for apps with a driver manager (tested on the emulator); ❌ our player loading them (needs Vulkan GPU rendering first) |
| Saturn in the built-in player | 🟡 experimental (Yabause: 1 of 2 test games boots); YabaSanshiro after GPU rendering; RetroArch and Yaba Sanshiro 2 offered |
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
| PC / iOS / Linux / Raspberry Pi versions | ❌ question |
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
| Weekly bug reports on GitHub | 🟡 Report a problem built; needs the GitHub repository |
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
| Netplay for all systems | ❌ to do: libretro netplay lives in the frontend (RetroArch), so RetroForge needs its own (rollback, same game file and emulator on both sides); RetroArch launches can use RetroArch's own netplay |
| Network drives built into RetroForge (no second app) | ✅ own documents provider (smbj, read-only, 1 MB read-ahead); Find on my network (every interface), shared folders listed automatically, one tap adds them to games or films with a "N games found" confirmation; tested on the S22 over the PC's share: Super Mario World and a PlayStation game played from the network. Emulator can't reach SMB from apps (its virtual Wi-Fi) |
| Save syncing: pull and push saves from network drives and cloud drives automatically | ❌ to do (needs write access to shares; cloud through a provider the user picks) |

| Settings condensed | ✅ five groups (Games and library, Playing, Covers, Looks and sounds, Help and about), each on its own page |
| Ask before Back closes RetroForge | ✅ "Close RetroForge?" on Home (tested on the emulator) |
| Texture packs all in one place | ✅ one Texture packs page (downloads, N64 and PSP folders) |
| Vulkan in the built-in player | 🟡 experimental: the player offers libretro's Vulkan interface (context negotiation v2; cores make their own device) and shows Vulkan frames through its OpenGL path. Tested on the S22: Dolphin starts on Vulkan (Xclipse 920) but stalls waiting for frame buffers; needs more work. OpenGL stays the default; Settings and the pause menu offer Vulkan (experimental) for Dolphin, LRPS2 and PPSSPP |
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
| PS2 in the built-in player | 🟡 PS2 BIOS rule (73 versions, copied to pcsx2/bios); LRPS2 first (Play! crashes): it boots the disc and runs game code (Maximo), then crashes with a memory fault; needs more work, likely with Vulkan. NetherSX2/ARMSX2 standalone offered meanwhile |
| GameCube in the built-in player | 🟡 Dolphin loads and boots the disc (WarioWare), then stops itself; fastmem off didn't help. Needs more work, likely with Vulkan. Dolphin standalone offered meanwhile |
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

