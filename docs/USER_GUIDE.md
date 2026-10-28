# RetroForge user guide

Generated from the in-app tutorial (Settings → How to use RetroForge) by `tools/gen_user_guide.py`; edit `TutorialScreen.kt`, then regenerate.

## Getting started

- Settings → Set up RetroForge walks you through it: pick your games folder, install RetroArch, get the cores your games need and check your BIOS files.
- Your games folder can be a "roms" folder from another frontend (with snes, nes, psx … inside) or a single system folder, on the phone, an SD card or a USB drive.
- RetroForge comes with no games and no BIOS. Use your own.

## Finding and playing games

- Retro shows your systems, Recently played, Favourites, Playlists and a search box.
- Open a game and press Play. It starts in RetroArch with the best core for that system; other cores are listed underneath.
- If RetroArch says a core is missing: in RetroArch open Online Updater → Core Downloader and get the one named on the game's page.
- Some systems need BIOS files. Settings → Set up RetroForge checks yours and copies them where RetroArch looks.

## Other emulators

- A game's page lists free standalone emulators for its system (DuckStation, PPSSPP, Dolphin, NetherSX2 …).
- "Get" opens Obtainium (if installed) or the emulator's official page. RetroForge never installs apps itself.
- Once installed: "Play with …" starts the game in it, and "Use by default" makes the main Play button use it for that system.
- Games made of several files (.cue, .m3u) usually also need your games folder added inside the emulator.

## Hotkeys, fast-forward and resume

- In RetroArch games: Start + Select quits back to RetroForge; Down + Select opens RetroArch's menu (save states, Disc Control, shaders). Change them in Settings → RetroArch hotkeys.
- Hold R2 to fast-forward on systems whose controllers had no R2 (Game Boy, GBA, NES, SNES, Mega Drive …): handy for slow dialogue.
- Carry on where you left off: quitting saves a state, and the next launch continues from it. Switch it off in Settings.
- Settings → Hotkeys lists every hotkey. Standalone emulators use their own menus.

## Multi-disc games

- A game with an .m3u playlist shows once and plays as one game.
- To change disc in RetroArch: open the menu, Disc Control → Eject, choose the next disc in Current Disc Index, then Insert.
- No .m3u yet? ROM manager → Multi-disc makes them for you.

## Covers

- Open a game and choose Change cover.
- Box art: free, from libretro's thumbnail library. A system's list also has "Download box art" for every game without artwork.
- SteamGridDB: thousands of community covers. Add your own free API key in Settings.
- ScreenScraper: front and 3D boxes, looked up for one game when you ask. An account (Settings) raises your daily limit.
- Your own image: any picture on the device, for example a scan saved from The Cover Project.

## Favourites and playlists

- On a game's page: "Add to favourites", or "Add to a playlist" to tick existing playlists or make a new one.
- Both show on the Retro screen. Open a playlist to reorder, remove, rename or delete it; the games themselves are never touched.

## ROM hacks and patches

- ROM hacks shows hacks you already have and hacks from catalogues, with links to their official pages.
- ROM Patcher (on the Retro screen) applies IPS, BPS or UPS patches, one after another, to make a new patched copy. Your original ROM is never changed.

## ROM manager

- The only part of RetroForge that changes your files, and only after showing you exactly what it will do.
- Duplicates: finds the same game in several files and checks whether they hold the same data.
- Compress: discs to CHD, cartridges to .zip. Each new file is checked before the originals are set aside.
- Multi-disc: makes .m3u playlists. Delete a game: from its page.
- Nothing is deleted straight away: files go to _duplicates, _originals or _deleted, and History can undo every change. Free up space is the only step that deletes for good.
- If another frontend uses the same folder on a PC, close it first.

## SD cards and controllers

- Games and BIOS can live on an SD card or USB drive: add that folder like any other.
- Everything works with a controller: D-pad to move, A to select, B to go back, L1/R1 to switch sections.

## PC games

- The PC section checks whether this device can run PC games and works with a separately installed PC game app. Only some high-end devices can.

## Learn more

- Retro Game Corps: [Android Emulation Starter Guide](https://retrogamecorps.com/2022/03/13/android-emulation-starter-guide/), [Dual-Screen Android Handheld Guide](https://retrogamecorps.com/2025/10/27/dual-screen-android-handheld-guide/), [all guides](https://retrogamecorps.com/guides/).
