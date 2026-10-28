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

## Release test plan (run in the final stage, before any release)

Everything is re-tested together on real hardware, so nothing that worked earlier is broken by later changes. Record each result here with date, device and evidence.

1. **Every system**: for each system in [SYSTEM_COVERAGE.md](SYSTEM_COVERAGE.md) marked "Should work", launch one real game with the default RetroArch core and with each listed standalone emulator; mark Launch tested / Gameplay tested / Fails (with the reason) and update the coverage table.
2. **Devices**: a mid-range and a high-end phone, a tablet, an Android TV box and a handheld with built-in controls; internal storage, SD card and USB drive.
3. **Controllers**: Bluetooth and USB pads, a TV remote, keyboard; every RetroForge screen reachable without touch; RetroArch quit/menu combos and hold-R2 fast-forward.
4. **Every app function**: setup wizard, folder add/rescan/forget, search, covers (libretro and SteamGridDB), BIOS check and copy, patcher and patch chains, ROM hacks catalogues, ROM manager (each tab, undo, free up space), multi-disc games, resume, hotkeys page, links, about, legal notice, PC section.
5. **Automated**: all unit tests, plus the opt-in real-collection runs (scan, CHD round trip against chdman, ROM manager dry run).
6. **Regression**: a short "smoke" list (add folder → play a SNES, PS1 multi-disc and PSP game → return → undo a ROM manager change) after every change in the final stage.

