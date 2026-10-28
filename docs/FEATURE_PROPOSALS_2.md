# Proposals: cheats, texture packs, frontend import, bug reports (2026-10-28)

Requested by the user; none started yet.

## Cheats (all systems)
- RetroArch already supports cheats per core (`.cht` files). libretro publishes a free cheat database (`libretro-database/cht`) per system, which RetroArch can download itself from its Online Updater.
- RetroForge can: show "Cheats available" on a game page by matching the game to the database file names, and explain how to turn cheats on in RetroArch's Quick Menu. Directly toggling cheats from RetroForge needs RetroArch support that doesn't exist from outside; to investigate.
- Standalone emulators (PPSSPP, Dolphin, PCSX2/NetherSX2, DuckStation) use their own cheat formats and folders; per-emulator work later.

## Texture packs (systems that support them)
- Supported by: N64 (Mupen64Plus-Next "HTS/HD" packs), PSP (PPSSPP), GameCube/Wii (Dolphin), PS2 (PCSX2-based, where present), SNES/NES (Mesen HD packs). Each emulator has its own folder and naming.
- RetroForge can: detect packs the user already has, show which games have one, and point the user to each emulator's folder. Packs are large and often contain copyrighted art, so RetroForge should not host or bundle them; links to the pack authors' pages, like the Hacks catalogue.

## Hacks for more systems
- The patcher already supports any system using IPS/BPS/UPS. Disc-based hacks (PS1, PS2, GameCube) mostly use xdelta: add an xdelta decoder.

## Import from other frontends (ES-DE, Batocera, RetroBat, others)
- Games, titles and artwork already come through `gamelist.xml`.
- Still to import read-only: favourites (`<favorite>`), hidden games (`<hidden>`), play counts and last played (`<playcount>`, `<lastplayed>`), and ES-DE/Batocera custom collections (`collections/custom-*.cfg`).

## Bug reporter → GitHub
- An in-app "Report a problem" screen that collects: what happened (typed by the user), app version, Android version, device model, GPU, and RetroForge's own recent log lines. Nothing personal; the user sees everything before sending.
- Sending: open a pre-filled GitHub "new issue" page in the browser (no tokens in the app, user posts it with their own account). Needs a public GitHub repository for RetroForge.
- Weekly triage: a scheduled task can read new issues with the GitHub CLI, try to reproduce them on the emulator, and propose fixes as pull requests for review.
