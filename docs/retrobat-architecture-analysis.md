# RetroBat architecture analysis

Inspected read-only at `G:\Retrobat` (8.3.1.1-stable-win64), 2026-10-27/28. Nothing there was changed. Inventory: [retrobat-installation-inventory.md](retrobat-installation-inventory.md).

## Parts

| Part | Where | What it does |
|---|---|---|
| Frontend | `emulationstation/emulationstation.exe` | RetroBat's fork of Batocera's EmulationStation: system carousel, game lists, scraper, themes, settings menus |
| System definitions | `.emulationstation/es_systems.cfg` | 246 systems: folder path (`~\..\roms\<system>`), extensions, launch command, list of emulators/cores (first = default) |
| Feature definitions | `.emulationstation/es_features.cfg` | Per emulator/core/system options the menus offer (gamepad type, multitap, ratio, shaders …) — 350 emulator/core entries |
| Save-state rules | `.emulationstation/es_savestates.cfg` | Where each emulator keeps states and auto-states, e.g. libretro: `saves/<system>/libretro.<core>/<rom>.state<slot>`, `.state.auto` |
| Launcher | `emulationstation/emulatorLauncher.exe` (C#) | Called for every launch with `-system -emulator -core -rom -gameinfo`; writes the emulator's config (controls, video, paths) from templates and settings, then starts it and waits |
| Input | `es_input.cfg`, `system/padtokey`, `es_padtokey.cfg` | Controller mappings; pad-to-keyboard for PC games |
| Emulators | `emulators/` (138 folders) | Windows builds: RetroArch with 160 cores, plus standalones |
| Data | `roms/`, `bios/`, `saves/`, `screenshots/`, `decorations/`, `system/shaders` | Collection, firmware (flat), saves per system, bezels, shaders |
| Store / updates | `batocera-store.exe`, `es-update.exe` | Downloads content and updates RetroBat itself |

## How a launch works

1. ES reads `es_systems.cfg` and scans `roms/<system>` for listed extensions; media and metadata come from `gamelist.xml` in each folder.
2. The user picks a game. ES resolves the emulator and core (per-game, then per-system setting, then the system's first entry).
3. ES runs the system's `<command>`, i.e. `emulatorLauncher.exe` with the game info XML and controller config.
4. emulatorLauncher generates that emulator's config, adds bezels/shaders/ratio from settings, sets save paths per `es_savestates.cfg`, starts the emulator and waits for it to exit.
5. ES comes back, updating play count and last played in `gamelist.xml`.

## What carries over to Android, and what doesn't

| Carries over | Doesn't |
|---|---|
| Folder names, extensions, gamelist.xml, media layout | All of `emulators/` (Windows binaries) |
| BIOS file names (RetroArch-style) | emulatorLauncher's config generation (Windows paths, Windows emulators) |
| RetroArch `.srm` battery saves for the same core | Most save states (core/version specific) |
| The idea of per-system defaults and per-game overrides | ES XML themes as layouts (they target ES's renderer) |
| Hotkey conventions | Store/updater |

RetroForge mirrors the useful structure: system rules generated from `es_systems.cfg` ([systems.json](../retro-library/src/main/resources/systems.json)), gamelist.xml read in place, per-system and per-game emulator choice, BIOS check by RetroArch names. It replaces emulatorLauncher with Android launchers (RetroArch intent, standalone intents, and the built-in player).
