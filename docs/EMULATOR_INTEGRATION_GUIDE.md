# Emulator integration guide

How RetroForge starts a game, for each route. Last updated 2026-10-28. For per-system coverage see [SYSTEM_COVERAGE.md](SYSTEM_COVERAGE.md); for hotkeys see [EMULATORS_AND_HOTKEYS.md](EMULATORS_AND_HOTKEYS.md).

## Which route Play uses

On a game's page the first card, **Play**, is:

1. the standalone emulator you chose for the system ("Use by default"), if it's installed; otherwise
2. the **built-in player**, when it has an emulator for the system and the game isn't a disc image; otherwise
3. **RetroArch** with the best core.

The other routes stay listed below Play ("Play in RetroArch with …", "Other emulators").

## 1. Built-in player

RetroForge's own libretro frontend (`retro-player` module: `player.cpp` for video/audio/input, `NativePlayer.kt` for JNI). Only `libretro.h` (MIT) comes from the libretro project.

- **Cores:** downloaded from libretro's build server (`buildbot.libretro.com/nightly/android/latest/<abi>/<core>_libretro_android.so.zip`) into RetroForge's private storage by [CoreManager](../retro-player/src/main/java/com/retroforge/player/CoreManager.kt), in the background for every system the library has, so the user never downloads one. Others are fetched on first use (Switch emulator).
- **Emulators per system:** [PlayerCores](../retro-player/src/main/java/com/retroforge/player/PlayerCores.kt): GBA (mGBA, gpSP, VBA Next), GB/GBC (Gambatte, SameBoy, Gearboy, mGBA), NES (FCEUmm, Nestopia UE, Mesen), SNES (Snes9x, Snes9x 2010), Mega Drive / Master System / Game Gear / SG-1000 (Genesis Plus GX, PicoDrive), 32X (PicoDrive), PC Engine (Beetle PCE Fast, Beetle SuperGrafx), Neo Geo Pocket, WonderSwan, Atari 2600, Lynx, DS (DeSmuME, DeSmuME 2015, melonDS). Software-rendered cores only; disc images go to the other routes.
- **Loading:** cores that take memory get the game straight from its folder through a file descriptor; cores that need a file path get a copy in the cache. `.zip` and `.7z` are unpacked unless the core reads them itself.
- **Process:** the player runs in its own process (`:player`), which ends when the game closes, so every game starts with a clean core.
- **libretro environment supported:** pixel formats, system/save directories, core options (v0 variables, changed live), geometry and AV-info changes, input bitmasks, joypad, analog, pointer (touchscreen), log and perf interfaces. Not yet: hardware rendering, disc control, rumble, VFS, cheats, achievements.

Tested: mGBA, DeSmuME (S22); Snes9x, FCEUmm, Genesis Plus GX, PicoDrive (emulator); release (R8) build.

## 2. RetroArch

Started with RetroArch's documented launch intent (`ROM`, `LIBRETRO`, `CONFIGFILE` extras) by [RetroArchLauncher](../retro/src/main/java/com/retroforge/retro/RetroArchLauncher.kt). The game must be on storage with a real file path (internal storage, SD card, USB); cloud providers can't be passed. RetroForge writes `Documents/RetroForge/retroarch-retroforge.cfg` (its hotkeys, then `#include` of RetroArch's own config) and never edits RetroArch's files. Cores are installed by RetroArch's own Online Updater, because RetroArch keeps them in its private storage.

## 3. Standalone emulators

21 free emulators with launch commands adapted from ES-DE (MIT): [standalone.json](../retro-library/src/main/resources/standalone.json), parsed by `LaunchCommand` and started by [StandaloneLauncher](../retro/src/main/java/com/retroforge/retro/StandaloneLauncher.kt) with read permission granted for the game file. "Get" opens the emulator's official page, or Obtainium.

## Adding a system to the built-in player

1. Check the core exists for Android on the build server and draws in software.
2. Add it to `PlayerCores.bySystem` (default first) and, if it needs BIOS, a rule to `bios.json`.
3. Add a touch layout in `PadLayout.forSystem` and a skin in `ConsoleSkin.forSystem`.
4. Test a game on a device, then record it in [TESTING.md](TESTING.md).
