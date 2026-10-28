# Changelog

## 0.5.0-beta (first beta)

Tested on the Android emulator; real-device testing is part of this beta.

**Library and play**
- Finds your games in a "roms" folder from another frontend or a single system folder, on the phone, an SD card or a USB drive; reads gamelist.xml titles, descriptions and artwork.
- Plays in RetroArch with the best core per system (91 of 121 RetroBat systems have an Android core), or in 21 free standalone emulators (DuckStation, PPSSPP, Dolphin, NetherSX2, Azahar, Flycast …) with "Use by default" per system.
- Hotkeys for RetroArch games (Start + Select quits to RetroForge, Down + Select opens the menu), hold R2 to fast-forward on older systems, carry on where you left off (auto save/load).
- Multi-disc games play as one game with disc swapping.
- Favourites and custom playlists; recently played; search.

**Built-in player (new)**: RetroForge plays cartridge games itself: GBA, GB/GBC, NES, SNES, Mega Drive/Genesis, Master System, Game Gear, SG-1000, 32X, PC Engine, Neo Geo Pocket, WonderSwan, Atari 2600, Lynx and DS. The emulator cores download in the background when your library has those systems, so there is nothing to set up. Sharp pixel scaling, touch controls laid out per system (can be turned off), controller support with the same hotkeys, hold R2 to fast-forward, carry on where you left off, save/load state, screen shape (Original, 4:3, 16:9, stretch, pixel perfect), swipe in from either edge for the menu, works in portrait and landscape. Zipped and 7z games play directly. The pause menu also has fast-forward, each emulator's own settings (like RetroArch's core options) and **Switch emulator** (e.g. mGBA / gpSP / VBA Next, Snes9x / Snes9x 2010, DeSmuME / melonDS) with the choice remembered per system, also under Settings → Built-in player. DS screens go side by side in landscape. Works with only a controller or TV remote (no touch controls shown, text boxes don't trap the d-pad). Console look (on by default, off in the pause menu): each system gets a console-inspired body colour, screen surround and pad-coloured buttons, drawn in code so it fits any screen in both orientations; original designs with no logos. Disc games still use RetroArch or standalone emulators.

**Covers**: free libretro box art (one game or a whole system), SteamGridDB (your own key), ScreenScraper (needs RetroForge's developer ID), or any image on your device.

**ROM tools**: IPS/BPS/UPS patcher with patch chains and readmes; ROM hacks tab with catalogues; ROM manager (duplicates, compress discs to CHD and cartridges to .zip, multi-disc playlists, delete, undo, free up space).

**Looks**: 8 themes with their own home layouts (dashboard, Cross Bar, Hero, Blades), bundled fonts, backgrounds and effects; import theme files or colours from ES-DE/RetroBat themes.

**Also**: setup wizard with BIOS check and copy, tutorial, hotkeys page, Links tab, PC section (checks the device and works with DroidDeck for Steam games), licences, privacy, Report a problem.

**Known limitations**: only SNES, NES, PS1 and PSP launch-tested; PC games untested (need a Snapdragon device); no physical controller tested; ScreenScraper unavailable until RetroForge has its developer ID.
