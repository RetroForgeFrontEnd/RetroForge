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

**Edit game details**: change a game's title, description, release date, developer, publisher, genre and players from its page. Saved in RetroForge only (game files and gamelist.xml untouched), kept through rescans and ROM manager changes, with Reset to the scanned details.

**Retro tab, canvas style**: systems first, as a big centred rounded tile with neighbours peeking in, the logo, year and game count underneath on the theme's wallpaper. Ready on first start: console pictures, logos and details from canvas-es (by Siddy212, CC0) download by themselves for the systems in your library, drawn in RetroForge's colours with the faded RetroForge logo behind. Tiles can show consoles, game art or plain name plates, and you can use your own theme folder instead (canvas-es and other ES-DE-style themes, Alekfull-NX). RetroForge ships none of this artwork.

**Saves and storage**: Settings → Built-in player → Back up saves writes one zip with in-game saves, save states and cheats; Restore puts them back (on this or another device), replacing same-named saves and keeping the rest. A Storage card shows the space emulators and copied CD/arcade games use, with Clear copied games. Each game's page shows its time played in the built-in player.

**Cardboard VR**: in the pause menu, the picture once per eye for a phone in a Cardboard-style viewer. Virtual Boy (new, with Beetle VB) shows its real 3D there, and a normal picture otherwise. Touch controls now hide the moment a controller connects and return when it disconnects.

**Dual screen** (AYN Thor, AYANEO Pocket DS and other two-screen devices): DS games put the touch screen on the second display and the top screen fills the main one. Automatic when a second display is there.

**Android apps and launcher mode**: an Android apps screen (from Home or Settings) lists your other apps with a search box and shortcuts to Android's settings (Wi-Fi, Bluetooth for controllers, display, sound). For handhelds and TV boxes, RetroForge can become the home screen: the device starts in it and the home button comes back to it. Off unless you switch it on.

**Ports** (PC tab): native Android ports of classic games (Ship of Harkinian, Ghostship, SpaghettiKart, Zelda 3, Perfect Dark, DevilutionX and more). RetroForge shows which ones your games can use, gets them through Obtainium from their official releases, opens them once installed, and copies your ROM (unzipped) to Download/RetroForge/Ports for the port to read. Your original stays where it is; RetroForge never supplies game files.

**N64 in the built-in player**: the player now runs emulators that draw with the GPU (OpenGL ES 3). Nintendo 64 plays with Mupen64Plus-Next (or ParaLLEl N64), with an N64 pad: stick, C buttons on the right stick, A, B, Z, L, R.

**Saturn (experimental)** in the built-in player with Yabause (or Beetle Saturn on fast phones): disc images (chd, cue/bin), six-button pad (X Y Z over A B C, L and R), Saturn BIOS in the BIOS check. Some games stay on a black screen; RetroArch and Yaba Sanshiro 2 are offered on the game page. YabaSanshiro, which plays far more, follows once the player draws with the GPU.

**GPU and drivers** (Settings): shows the device's GPU and whether custom drivers apply (Snapdragon/Adreno only). Lists the latest Turnip builds (Mesa's open-source Vulkan driver) and downloads the one you pick to Download/RetroForge/GPU drivers, ready to install in Eden, Dolphin, NetherSX2 and other emulators with a driver manager.

**Folders from ES-DE**: ES-DE folder names (`n3ds`, `sfc`, `famicom`, `atarilynx`, `sg-1000`, `arcade`, `gameandwatch` …) are recognised alongside RetroBat's.

**Fixed**: Mega Drive / 32X / Mega CD touch buttons touching the d-pad on narrower phones (the d-pad and buttons now shrink together to fit).

**Setup does the work**: emulators for your systems download by themselves (step 2 just shows progress). RetroArch only appears when some of your systems need it, with Google Play and website buttons. PlayStation joins the built-in player (PCSX ReARMed), with disc games read in place (cue/bin, chd, pbp; nothing copied). New neon "RF" app icon from the key art's sign. Fixed: setup reappearing on Home after Done.

**Cheats** (pause menu): the game's cheats from libretro's cheat database, switched on and off live and remembered per game, plus your own Game Genie / Action Replay / GameShark codes. Also: change disc for multi-disc games, Volume boost, Sega CD, PC Engine CD and arcade (FinalBurn Neo, MAME 2003-Plus) in the built-in player.

**Covers**: free libretro box art (one game or a whole system), SteamGridDB (your own key), ScreenScraper (needs RetroForge's developer ID), or any image on your device.

**ROM tools**: IPS/BPS/UPS patcher with patch chains and readmes; ROM hacks tab with catalogues; ROM manager (duplicates, compress discs to CHD and cartridges to .zip, multi-disc playlists, delete, undo, free up space).

**Looks**: 8 themes with their own home layouts (dashboard, Cross Bar, Hero, Blades), bundled fonts, backgrounds and effects; import theme files or colours from ES-DE/RetroBat themes.

**Also**: setup wizard with BIOS check and copy, tutorial, hotkeys page, Links tab, PC section (checks the device and works with DroidDeck for Steam games), licences, privacy, Report a problem.

**Known limitations**: only SNES, NES, PS1 and PSP launch-tested; PC games untested (need a Snapdragon device); no physical controller tested; ScreenScraper unavailable until RetroForge has its developer ID.
