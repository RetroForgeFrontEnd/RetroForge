# RetroForge beta: tester guide

Thanks for trying it. This takes about 20 minutes. Bring your own games and BIOS; RetroForge comes with none.

## Install

1. Install the APK (allow installs from your browser or file manager when Android asks).
2. Open RetroForge and follow setup: pick your roms folder (a RetroBat/ES-DE style `roms` folder works as it is), tick the systems you play, and let it fetch emulators in the background.
3. Nothing is written into your roms folder. RetroForge keeps its own index.

## What to try

**Built-in player** (no RetroArch needed)
- Play something from GB/GBA, SNES, Mega Drive, PS1, DS and an arcade game (FBNeo or Neo Geo).
- Swipe in from the left or right edge for the menu: save/load state, screen shape, filters, emulator settings, cheats, change disc, switch emulator.
- Rotate the phone. Try it with a controller too.

**Snapdragon / PC side** (the bit we can't test ourselves)
- PC tab: does the GPU check show your Adreno correctly? Does DroidDeck start a game?
- GameNative / Winlator hand-off for a non-Steam game.
- Settings → GPU and drivers: does it see your GPU, and does a Turnip build download and install in Eden or Dolphin?

**Everything else**
- Retro tab looks and loads right in both orientations.
- Standalone emulators: the "Get" button (with Obtainium installed it should open Obtainium set up for the app).
- ROM Manager, covers, playlists, if you fancy it.

## Known issues

- Saturn in the built-in player is experimental (some games stay black). N64, PSP, Dreamcast, GameCube/Wii and PS2 don't run in the built-in player yet (they need GPU support, being worked on). They launch in RetroArch or standalone apps.
- No button remapping in the built-in player yet.
- Only tested on a Galaxy S22 (Exynos) and the Android emulator. Snapdragon devices, handhelds and TV boxes are new ground.
- Real TV boxes and dual-screen handhelds haven't been tried.

## Sending feedback

What device and Android version, what you did, what happened, and a screenshot or screen recording if you can. If something crashes, the time it happened helps.
