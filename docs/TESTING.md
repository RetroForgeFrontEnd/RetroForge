# Testing

Evidence levels: Implemented · Build verified · Unit tested · Integration tested · Device tested · Gameplay verified · Not tested · Known limitation.
"Device tested" here means the Android Studio emulator unless a physical device is named.

## Automated tests

Run: `./gradlew testDebugUnitTest` (JVM, no device needed, no ROMs needed).

| Suite | Module | Tests | Covers |
|---|---|---|---|
| `GpuClassifierTest` | pc | 6 | Adreno model parsing (GL renderer and kgsl strings); supported / experimental / unsupported / unknown classification per DroidDeck's published requirements |
| `ControllerKeysTest` | core-ui | 2 | Gamepad A/B/L1/R1 → frontend commands; D-pad left to Compose focus |
| `SettingsRepositoryTest` | core-settings | 2 | DataStore defaults and persistence |
| `RomPatcherTest` | retro-patch | 17 | IPS (records, RLE, grow, truncate, missing EOF); UPS (grow/shrink targets, wrong ROM, damaged patch); BPS (all four commands incl. overlapping target copy, negative offsets, metadata, out-of-range copy); SNES copier-header retry; patch chains (order, failing-step index, output naming); originals never modified |
| `ArchiveInputTest` | retro-patch | 12 | ROM/patch selection inside .zip and .7z, choosing one of several patches, readme extraction (UTF-8/Latin-1, size cap), corrupt archives |
| `CatalogueTest` | retro-hacks | 7 | Catalogue parsing (unknown fields, wrong format/version, HTML pages, unsafe ids), per-entry rejection (http:// and javascript: links, bad checksums, duplicates, missing requires), built-in catalogue validity, store add/update/remove/order, de-duplication by page |
| `LibraryScannerTest` | retro-library | 12 | RetroBat-style collection: system folders, media folders skipped, gamelist.xml metadata + artwork, hack folders, .m3u/.cue hiding discs/tracks, zip/7z duplicates, single-system and parent-of-roms layouts, unrecognised folder, denied folder, read-only (ROMs never opened), cancellation, DOCTYPE/XXE refused, game-named folders (`X.pc`) |
| `RomNamesTest` | retro-library | 4 | No-Intro and GoodTools tags, ", The" titles, flags |
| `RealCollectionTest` | retro-library | 1 (skipped unless `RETROFORGE_REAL_ROMS` is set) | Read-only scan of a real collection, printing statistics |
| `BiosCheckerTest` | retro-library | 6 | BIOS verified by MD5 under any file name, right-name-wrong-contents flagged, all/any rules, sub-folders, only library systems checked, bundled rules valid |
| `RealBiosTest` | retro-library | 1 (skipped unless `RETROFORGE_REAL_BIOS` is set) | Read-only check of a real BIOS folder |
| `ArtworkTest` | retro-artwork | 8 | libretro matching (exact, GoodTools, region, no cross-title), URL encoding, index parsing, SteamGridDB requests and bad keys, image sniffing |
| `RealMatchTest` | retro-artwork | 1 (opt-in) | Matches a real collection against the live libretro indexes |
| `RealPatchTest` | retro-patch | 1 (skipped unless `patch-test/` has `.base` files) | Applies real patches or patch chains from the git-ignored `patch-test/` folder and prints SHA-1 for cross-checking |

Last run: 2026-10-28, **63 passed, 0 failed** (64 incl. opt-in) (+1 real-patch chain test that ran locally). `lintDebug`: 0 errors, 2 accepted warnings (TV banner vector is 320×180 by Android TV requirement; `allowBackup=false` already covers pre-Android-12).

No instrumented (on-device) automated UI tests exist yet. Planned for Milestone B alongside the scanner.

## Manual test log

### 2026-10-27 — Milestone A on emulator (Medium_Phone_API_37.0, x86_64, Android 37)

Input was sent with `adb shell input keyevent` (simulated gamepad/D-pad key codes, not a physical controller).

| Check | Result |
|---|---|
| Install + cold launch | Pass, no crash entries in `logcat -b crash` |
| Phone layout (bottom section bar) | Pass |
| Wide layout (landscape, 280 dpi → side rail; content capped at 960dp) | Pass, after fixing the width cap |
| R1 / L1 switches sections, wraps around | Pass |
| D-pad moves focus; orange focus ring visible on cards and tabs | Pass |
| Gamepad A activates focused card | Pass (toggled "Show PC section") |
| Gamepad B goes back | Pass (Settings → Home) |
| "Show PC section" off hides PC tab and Home card; persists across app restart | Pass |
| PC screen on emulator GPU | Pass: "PC games not available", renderer string shown, DroidDeck "Not installed" |
| Initial focus without touch | Pass: the selected section tab gets focus after key input |

### 2026-10-27: ROM Patcher on emulator

| Check | Result |
|---|---|
| Pick ROM from a `.7z` via Android's file picker | Pass: extracted `Super Mario World (USA).sfc`, CRC32 B19ED489 |
| BPS made for a different ROM | Pass: "Wrong ROM for this patch" shown, Create disabled |
| IPS (hand-made, renames header title) → save via Create Document | Pass: new file saved with suggested name; output SHA-1 identical to an independent Python IPS implementation; patched ROM boots in RetroArch/snes9x |
| Original ROM untouched | Pass: `.7z` SHA-1 identical on device and on `G:` |
| Real-world IPS ("2B in Super Mario Bros", from the user) applied alone | Output identical to the Python reference (53,008 bytes). Not meaningful on its own: its readme requires Giant Mario v1.2 first |

### 2026-10-27: Patch chain (real hacks) on emulator

| Check | Result |
|---|---|
| Patch archive with 3 patches (Giant Mario v1.2.zip) | Pass: chooser lists all three; "(Level unchanged)" picked with D-pad + A |
| Readme from 2B zip | Pass: preview shown, full text expands on select (monospace) |
| Chain SMB (World) → Giant Mario v1.2 (Level unchanged) → 2B, saved via Create Document | Pass: 65,552 bytes, SHA-1 76a4ac56… identical on device, desktop engine and independent Python reference |
| Gameplay | **Pass**: boots in RetroArch/FCEUmm; HUD reads "TWO B"; Start (touch overlay) begins World 1-1, timer counts, the giant 2B sprite moves right under input |
| Controller focus | Pass: focus jumps to the first archive option when the chooser opens, then to "Add another patch on top" after choosing |

### 2026-10-27: Hacks tab on emulator

| Check | Result |
|---|---|
| Built-in catalogue shows NES and SNES carousels with filter chips | Pass |
| D-pad flicks through a carousel; A opens a hack | Pass |
| Detail page: summary, authors, version, tags, source | Pass |
| Add catalogue from a file (1 bad http:// entry inside) | Pass: added, "1 entry was skipped: …page link must be an https:// address" |
| Duplicate page across catalogues shown once | Pass |
| `requires` link resolves even when the target was merged as a duplicate | Pass after a fix (it first showed a disabled raw id) |
| Add from link: confirmation required, then fetch | Pass: example.com returned HTML, now reported as "That's a web page, not a catalogue file" (message improved after this test) |
| Open official page | Hand-off to Chrome works; Chrome stopped at its own first-run screen on this fresh emulator |

### 2026-10-28: Library scanner, real collection (desktop, read-only)

`RETROFORGE_REAL_ROMS=G:\Retrobat
oms ./gradlew :retro-library:test --tests '*RealCollectionTest*'`

| Result | Value |
|---|---|
| Games / systems | **28,274 games in 107 systems**, 9.6 s |
| With gamelist metadata / artwork | 26,809 / 26,779 |
| Hacks | 248 (SMW HACKS 49, SNES HACKS 31, N64 HACKS 64, NES HACKS 30, GBA HACKS 16, DS 7, PS1 3, PSP 2, DC 1, plus tagged files) |
| Multi-disc playlists | 394 `.m3u` games, discs hidden |
| Possible duplicates | 98 (e.g. `Sentinel, The.zip` / `Sentinel.zip`; MSU-MD `.md` + `.cue` pairs) |
| Problems | 1: six MUGEN folders nested > 6 levels |
| Unrecognised folders | 92 (ports, Windows stores and systems not in es_systems.cfg) |

Found and fixed on the real data: folders named like games (`Skate (EUR).ps3`, `XCOM.pc`) were entered and their inner files listed as games; they are now one game each.

### 2026-10-28: Library on emulator (SAF)

Test data: 371 files copied from `G:` (SNES 64 + gamelist + mixart + SMW/SNES HACKS, NES 40 + gamelist + mixart + NES HACKS, Mega Drive 25 + media/images, PS1 Resident Evil 2 two-disc `.m3u`, a decoy folder).

| Check | Result |
|---|---|
| Pick the folder *above* `roms` via the system folder picker | Pass: `roms` found, 240 games in 4 systems |
| Read access kept after app restart | Pass |
| `.m3u` whose name says (USA) but lists (Europe) discs | Pass: one game, discs hidden |
| Artwork from gamelist `mixart` via SAF | Pass |
| Titles from gamelist (e.g. "Super Baseball 2020" for `2020 Super Baseball`) | Pass; sorted by shown title after a fix |
| Filter, duplicate badge, "Other copies" link on detail page | Pass |
| Hacks tab "Your hack ROMs": NES 30, SNES 80 | Pass |
| Unrecognised folder reported; Rescan with live progress | Pass |

### 2026-10-28: Links tab

All six YouTube handles resolved to the named channels on youtube.com (checked 2026-10-28). Opening one launched the YouTube app on the emulator.

### 2026-10-28: Setup wizard and SD card (emulator)

| Check | Result |
|---|---|
| Real BIOS folder `G:\Retrobatios` (310 files, read-only) | PS1, Mega CD, PC Engine CD, FDS, Lynx, ColecoVision (found as `coleco.rom`), Dreamcast, GBA all verified; NDS firmware.bin not a recognised dump; PS1 scph1001 and Mega CD bios_CD_U present but different versions from libretro's |
| Wizard opens once on first run; Settings → Set up RetroForge reopens it | Pass (it first reopened on every launch until "Done"; changed to once) |
| Steps 1–3: 240 games, RetroArch 1.22.2 detected, 4 cores listed with game counts | Pass |
| Step 4: BIOS folder check, then copy into RetroArch/system (picker starts there) | Pass: only verified files copied, existing files left alone, copies byte-identical (MD5) |
| **SD card**: add `/storage/0000-0000/Retrobat` (parent of `roms`) | Pass: 3 games found next to the 240 internal ones |
| **SD card**: BIOS folder on the SD card checked and copied | Pass |
| **SD card**: Resident Evil 2 (2-disc .m3u) played from the SD card via Play | **Launch tested**: RetroArch received `/storage/0000-0000/...`, intro plays |
| Same game in two folders | Search results now show which folder each copy is in |

Not tested: physical gamepad, Android TV device or TV emulator image, tablet, light theme visuals, "System" theme switching, opening the DroidDeck releases page (browser), DroidDeck-installed state, the real Adreno GPU path.

### 2026-10-28: ROM manager, emulators and hotkeys (emulator)

- ROM manager on a copy of real ROMs (`/sdcard/ManagerTest`): duplicates moved and gamelist updated; discs to CHD and a cartridge to .zip; History undo restored byte-identical files; delete to `_deleted` then permanent delete; Command & Conquer playlist made and disc 2 swapped in RetroArch. Details: [ROM_MANAGER.md](ROM_MANAGER.md).
- RetroArch hotkey file: combos, "Confirm Quit" off and "Fast-Forward (Hold): Button 105" shown in RetroArch's own menus; `.state.auto` written on close and auto-loaded on the next launch (3 Ninjas Kick Back, Snes9x).
- **Standalone emulator: PPSSPP 1.20.4** (official APK from ppsspp.org, signed by Henrik Rydgård; x86_64 build included). Pinball Dreams (.cso, from the RetroBat collection) launched from RetroForge's "Play with PPSSPP" via a content:// URI with per-file read access: reached the game's table select. "Use by default" made the main Play button launch PPSSPP; play history recorded.
- Not tested: a physical controller (combos, R2), other standalone emulators (most are ARM64-only).

### 2026-10-28: Favourites and playlists (emulator, software rendering)

- Upgraded the installed app from database v3 to v4: library (251 games), recently played and covers kept.
- Favourited a game (★ shown, Favourites row on the Retro screen); made a playlist from a game's page, added a second game, opened it, moved a game down, renamed it, deleted it after the confirmation; the games stayed in the library.
- Fixed the same day: when the ROM manager compresses a game or tidies a duplicate, its favourite, playlist entries, play history and chosen cover move with it (journalled; compression undo moves them back). Emulator: Batman Forever favourited, compressed to .chd (still a favourite), undone (still a favourite). A deleted game's entries stay hidden and return if the delete is undone.
- The emulator crashed with host GPU rendering after a long session; `-gpu swiftshader_indirect` was stable.

### 2026-10-28: Themes (emulator)

- Themes screen: live previews of all 5 built-in themes; Phosphor, Arcade Sunset and Paper applied app-wide (colours, corners, monospaced type).
- Rotation: portrait stacks preview above details; landscape puts them side by side. Found and fixed: the side rail didn't scroll, so Links and Settings were unreachable on a phone turned sideways.
- Found and fixed: Paper's dark wordmark vanished in dark mode (wordmark colours now fall back to the text colour when too close to the background).
- Imported canvas-es colours (copied XML only): the report listed each colour's source, the licence (CC0) and what wasn't imported. Found and fixed: white cards with white text, and a near-invisible selected tab; the importer now checks text-on-card and accent-on-background readability.

### 2026-10-28: First real device: Samsung Galaxy S22 (SM-S901B)

Android 16 (API 36), Exynos 2200 (s5e9925), Xclipse 920 GPU (AMD RDNA2), 7 GB RAM, arm64. Debug build installed over USB.

- Legal notice, setup wizard, folder picker: added the phone's `Roms` folder (GBA and NDS folders): **27 games in 2 systems** found.
- Forge Neon theme, Orbitron headings, systems carousel render correctly at 1080×2340.
- **PC section verified on real hardware**: detected "ANGLE (Samsung Xclipse 920) on Vulkan 1.3.279", chip s5e9925, and reported "PC games not available" (DroidDeck needs Adreno). Changed: no longer suggests installing DroidDeck on devices that can't run it.
- Found and fixed: the "ROM hacks" tab label was cut off at this phone's font/display size (labels now shrink further).
- Not yet tested on this phone: launching games (no RetroArch or emulators installed), controllers.

### 2026-10-28: Built-in player (RetroForge's own libretro frontend)

S22 (arm64) and the Android emulator (x86_64, software GPU). Debug build.

- **Cores need no user action**: on opening RetroForge, the cores for the library's systems downloaded by themselves in seconds (S22: mGBA and DeSmuME for GBA/NDS; emulator: Snes9x, FCEUmm, Genesis Plus GX). No RetroArch involved.
- Play is now the built-in player; RetroArch and standalone emulators stay as "Play in RetroArch…" / other emulators.
- S22: Advance Wars (USA) (Rev 1).gba loaded in mGBA (240×160, 59.73 fps, 65536 Hz), save data synced. First attempt drew black (Compose painted over the game surface); fixed, then the phone was unplugged before a screenshot.
- Emulator: 3 Ninjas Kick Back (USA).7z in Snes9x: **.7z unpacked**, game ran at full width with sharp pixel scaling; touch Start moved to the next screen; **rotating to landscape kept the game running** and used the full screen height; in-game menu (☰) opened with Resume / Save state / Load state / Screen shape / Touch controls / Restart / Quit; **Quit then Play resumed at the same screen** (auto state 823 KB written to Android/data/…/states/snes).
- Fixed during testing: portrait picture was stuck at the top with a large gap; now centred above the controls.
- **S22 retest**: Advance Wars (GBA, mGBA) now shows full width and sharp; sound stream running with 0 underruns (AAudio, not muted). Advance Wars - Dark Conflict (DS, DeSmuME, 256×384 at 59.83 fps): both screens shown, and **tapping "Touch to start" on the lower screen worked** (DS touchscreen through RetroForge's pointer input).
- DS black bars at the sides in portrait (user report): added compact touch controls, used automatically when the full-width picture wouldn't leave room for the normal ones. DS now fills the S22's width. Checked on simulated screens with `wm size`/`wm density` (reset afterwards): small phone 720×1280 @320, tablet 1600×2560 @320, foldable 1768×2208 @420: picture fitted and centred, controls never overlap; side bars only where the screen is wider than the DS's shape.
- Fixed on the S22: the DS's tall picture ran under the L/R buttons in portrait; the picture now always fits the space above the controls (all of the screen when touch controls are off). Touch still lands correctly after the change.
- Not yet tested: whether the sound is right by ear (stream is healthy), physical controller and hotkeys in the player, edge swipes, screen shapes other than Original, Save/Load state buttons, cores other than mGBA, DeSmuME and Snes9x, a release (R8) build of the player.

### 2026-10-28: Player rotation, TV/controller use, pause menu (S22 and emulator)

- DS (S22): screens stacked in portrait, **side by side in landscape**, switched live on rotation; touchscreen works in both (right-hand screen in landscape). Landscape controls moved to the side columns, sized from screen height and fainter after user feedback (too big).
- Phone settings changed during the test and restored: auto-rotate (turned back on), `wm size`/`wm density` (reset).
- Controller only (emulator, adb key events, no touches): Home → Retro → search → game page → Play → Start → Guide opens the pause menu → menu navigation → Quit. Found and fixed: moving onto a text box popped up the keyboard and trapped focus (now only A opens it; up/down leave); the box swallowed up/down; menu focus too faint (bold orange outline now).
- Pause menu: Genesis Plus GX's own settings listed (System Region changed auto → ntsc-u). **Switch emulator** Genesis Plus GX → PicoDrive → back: game restarts in the new emulator and the choice is remembered on the game page and in Settings → Built-in player. Found and fixed: the first switch died (new player started in the old player's process, which then ended itself).
- Not yet tested: switching on the S22, melonDS (needs BIOS), Settings per-system emulator chips by hand, a real TV box and a real controller.

### 2026-10-28: Console look (emulator)

- SNES (Snes9x): light grey body, dark screen surround, red/yellow/green/blue face buttons; portrait and landscape (picture full height in its surround, controls on the body at the sides).
- NES (FCEUmm): grey top, dark lower panel, red B/A, black d-pad.
- Pause menu "Console look: On/Off" switches live (plain see-through controls when off) and is kept per system.
- Not yet checked by eye: GBA, GB/GBC, Mega Drive, Master System, Game Gear, PC Engine and DS skins (colours defined, same drawing code). Not testable from adb: starting a second game while one is open in the background (the player now saves and switches to the new game).

### 2026-10-28: Edit game details (emulator)

- Database upgraded 4 → 5 by installing over the old version: library intact (251 games in 6 systems).
- Edited an NES hack's title ("TMNT Ultimate Hack") and genre: shown at once on the game page and in search.
- Rescanned all three folders: the edit was put back by the scan.
- "Reset to the scanned details" showed the scanned title and restored it.
- Not tested: an edit following a file through ROM manager compress/undo (uses the same relink as favourites, which is tested).

### 2026-10-28: canvas-style Retro tab with theme artwork (emulator)

- Without artwork: centred rounded name plates with neighbours, name, game count, hints.
- Chose a copy of canvas-es's artwork folders (from the user's own install; never shipped): index built (art tiles, SVG logos, consoles, metadata, wallpaper). Commodore 64 showed its art tile, SVG logo, "1982", "2 GAMES" on the theme's dark wallpaper.
- "Tiles: consoles" (default): Mega Drive pad picture on its blue system colour with the SEGA MEGA DRIVE logo and 1990. The old index rebuilt itself to pick up console pictures.
- Swipe and d-pad left/right browse; tapping the middle tile opened Mega Drive (25 games).
- Not yet: on the S22 / a real controller; Alekfull-NX folder (layout rules written, not tried on device).
- Automatic artwork (no folder chosen): canvas-es console pictures, logos and details downloaded by themselves for the library's systems only (c64, megadrive, nes, psp, psx …) into private storage; tiles drawn on RetroForge's theme colours (Forge Neon orange-to-cyan) over the faded RetroForge key art.
- Centring and scale (user report: console pictures off-centre): transparent borders are trimmed when loading, so every console and logo is centred at the same scale. Landscape (user report: cut off): tiles left, logo/year/count right, all on one screen. Checked portrait (Commodore 64) and landscape (Mega Drive with C64 and NES neighbours).

### 2026-10-28: PlayStation in the built-in player (emulator)

- PCSX ReARMed downloaded by itself (PlayStation in the library). Batman Forever (.cue + .bin tracks) reached PRESS START with the core's HLE BIOS; DualShock on port 1.
- Linking to the open files failed (EACCES: Android re-checks folder access when a path is reopened), so disc games are copied once into RetroForge's cache with progress shown, reused next time, keeping the two most recent games.
- PS1 pad: L2/L1/R1/R2, split d-pad, △○✕□ drawn as shapes, two floating thumbsticks (tap = L3/R3), Select/Start/menu; DualShock-style dark console look with shaded buttons. Portrait surround slimmed to 4 dp so the picture is wider.
- Not yet: analog input confirmed in a game, Resident Evil 2 (.m3u/.chd) and multi-disc swapping (disc control not implemented), on the S22.

### 2026-10-28: Pre-phone-test checks (emulator)

- Multi-disc: Resident Evil 2 (.m3u, two .chd, copied once with progress shown) — pause menu "Disc 1 of 2 · change to disc 2" swapped; the core read disc 2 (SLES-10972).
- Release (R8) build, signed with the debug key for testing: Retro tab with downloaded canvas-es art and SVG logo, a PS1 game, pause menu (disc, volume) — no crashes.
- Unit tests: all modules pass. New: cue sheet track names, theme metadata. `SettingsRepositoryTest.persistsChanges` now skips on Windows hosts (DataStore's rename-over-existing-file is refused by Windows; fine on Android/Linux).
- Added without game tests: Sega CD, PC Engine CD (need their BIOS), arcade via FinalBurn Neo / MAME 2003-Plus (neogeo.zip copied alongside when present; clone parent sets not handled yet).

## Release test plan (run in the final stage, before any release)

Everything is re-tested together on real hardware, so nothing that worked earlier is broken by later changes. Record each result here with date, device and evidence.

1. **Every system**: for each system in [SYSTEM_COVERAGE.md](SYSTEM_COVERAGE.md) marked "Should work", launch one real game with the default RetroArch core and with each listed standalone emulator; mark Launch tested / Gameplay tested / Fails (with the reason) and update the coverage table.
2. **Devices**: a mid-range and a high-end phone, a tablet, an Android TV box and a handheld with built-in controls; internal storage, SD card and USB drive.
3. **Controllers**: Bluetooth and USB pads, a TV remote, keyboard; every RetroForge screen reachable without touch; RetroArch quit/menu combos and hold-R2 fast-forward.
4. **Every app function**: setup wizard, folder add/rescan/forget, search, covers (libretro and SteamGridDB), BIOS check and copy, patcher and patch chains, ROM hacks catalogues, ROM manager (each tab, undo, free up space), multi-disc games, resume, hotkeys page, links, about, legal notice, PC section.
5. **Automated**: all unit tests, plus the opt-in real-collection runs (scan, CHD round trip against chdman, ROM manager dry run).
6. **Regression**: a short "smoke" list (add folder → play a SNES, PS1 multi-disc and PSP game → return → undo a ROM manager change) after every change in the final stage.

