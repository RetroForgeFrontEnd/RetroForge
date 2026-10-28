# RetroForge — Project Status

Last updated: 2026-10-28 (built-in player, console look, TV/controller use, PC wording)

The single source of truth for what exists, what has been verified and what is blocked, by the phases of the original brief. Detail lives in the linked documents; every test run is logged in [TESTING.md](TESTING.md).

Build: debug and release (R8) build verified 2026-10-28; release build tested with the native player. All unit tests pass. Version 0.5.0-beta. Everything is committed on `main`.

## Phases

| Phase (from the brief) | Status |
|---|---|
| 1 — Environment and feasibility audit | **Done.** RetroBat audited read-only; DroidDeck audited; risks documented ([RISK_ASSESSMENT.md](RISK_ASSESSMENT.md)) |
| 2 — PC runtime proof of concept | **Blocked on hardware.** Approved approach: separate runtime app. The S22 (Xclipse) can't run DroidDeck; needs an Adreno 730+ device, or a GameNative/Winlator test on the S22 ([retroforge-pc-architecture.md](retroforge-pc-architecture.md)) |
| 3 — Android foundation | **Done; device tested** (emulator, Galaxy S22). Controller-only use tested on the emulator; no real TV box or physical controller yet |
| 4 — Retro library | **Done.** SAF folders (phone, SD card), scanner (28,274-game real collection read-only), gamelist.xml, favourites, playlists, search, filters. USB/OTG not yet tried |
| 5 — First real retro launch | **Done on device.** Built-in player: GBA and DS on the S22; SNES, NES, Mega Drive on the emulator. RetroArch and standalone launches tested on the emulator |
| 6 — Unified experience | **Partly.** Retro library unified across routes (built-in, RetroArch, standalone); PC games aren't in the library yet (Phase 8) |
| 7 — Emulator expansion | **Ongoing.** Built-in player: 21 cartridge systems, several emulators each, switchable mid-game. RetroArch: 91 of 121 RetroBat systems. 21 standalone emulators. Untested: PS2, 3DS, Wii, Switch standalone apps ([SYSTEM_COVERAGE.md](SYSTEM_COVERAGE.md)) |
| 8 — PC subsystem | **Minimal.** GPU check, DroidDeck detect/open, links to GameNative/Winlator on other GPUs. No PC game started from RetroForge yet ([pc-game-compatibility.md](pc-game-compatibility.md)) |
| 9 — Artwork and metadata | **Built.** libretro box art, SteamGridDB (your key), ScreenScraper (waits for RetroForge's developer ID), your own images. Manual metadata editing (titles, descriptions) **not built** |
| 10 — Theme engine | **Built.** Documented format, 8 themes with their own home layouts, previews, ES-DE/RetroBat colour import ([retroforge-theme-format.md](retroforge-theme-format.md)). Not yet: theme images/logos import, interface sounds |
| 11 — Controllers, graphics, firmware, saves | **Mostly built.** Controller and TV-remote navigation; player screen shapes, filters (sharp/smooth/CRT/LCD), console look; BIOS check and copy for RetroArch and the built-in player; saves and per-emulator states ([SHADERS_AND_GRAPHICS.md](SHADERS_AND_GRAPHICS.md), [BIOS_AND_FIRMWARE.md](BIOS_AND_FIRMWARE.md)). Not yet: button remapping, save backup/export, performance overlay |
| 12 — Device and performance testing | **Started.** 1 of 3 target devices (Galaxy S22). Needed: a mid-range phone, a handheld/tablet, a TV box, a real controller |
| 13 — Release readiness | **Beta prep done** ([RELEASE_PLAN.md](RELEASE_PLAN.md)): licences screen, privacy policy, bug reports, signing setup. Still needed from you: release key, support links, issues URL, privacy policy web page. Final full test pass still to run |

## Also built (asked for along the way)

ROM manager (duplicates, CHD/zip compression verified against chdman, multi-disc playlists, delete, undo) · ROM patcher and ROM hacks catalogues · hotkeys (RetroArch and built-in player), hold-R2 fast-forward, carry on where you left off · setup wizard · tutorial, About, Links · Obtainium links.

## Waiting on you

1. **Devices:** an Adreno 730+ device for DroidDeck, or OK to test GameNative on your S22; a TV box and a Bluetooth controller if you have them.
2. **PC test game:** transfer the TCG Card Shop Simulator files to the phone when ready.
3. **ScreenScraper developer ID** (requested by you).
4. **Release:** create the release key, add support links and the GitHub issues URL to `local.properties`, publish the privacy policy page.
5. **Licence for RetroForge itself** (still open; the built-in player keeps the choice open: no GPL code is linked).

## Documents

[ARCHITECTURE.md](ARCHITECTURE.md) · [BUILD_AND_INSTALL.md](BUILD_AND_INSTALL.md) · [USER_GUIDE.md](USER_GUIDE.md) · [ROM_PORTABILITY.md](ROM_PORTABILITY.md) · [EMULATOR_INTEGRATION_GUIDE.md](EMULATOR_INTEGRATION_GUIDE.md) · [EMULATOR_COMPATIBILITY.md](EMULATOR_COMPATIBILITY.md) · [EMULATORS_AND_HOTKEYS.md](EMULATORS_AND_HOTKEYS.md) · [SYSTEM_COVERAGE.md](SYSTEM_COVERAGE.md) · [CONTROLLER_SUPPORT.md](CONTROLLER_SUPPORT.md) · [SHADERS_AND_GRAPHICS.md](SHADERS_AND_GRAPHICS.md) · [BIOS_AND_FIRMWARE.md](BIOS_AND_FIRMWARE.md) · [SECURITY_AND_PRIVACY.md](SECURITY_AND_PRIVACY.md) · [PRIVACY_POLICY.md](PRIVACY_POLICY.md) · [TESTING.md](TESTING.md) · [THIRD_PARTY_LICENCES.md](THIRD_PARTY_LICENCES.md) · [ROM_MANAGER.md](ROM_MANAGER.md) · [COVER_ART.md](COVER_ART.md) · [RELEASE_PLAN.md](RELEASE_PLAN.md) · [retroforge-theme-format.md](retroforge-theme-format.md) · [retrobat-theme-compatibility.md](retrobat-theme-compatibility.md) · [droiddeck-architecture-audit.md](droiddeck-architecture-audit.md) · [retroforge-pc-architecture.md](retroforge-pc-architecture.md) · [pc-runtime-licences.md](pc-runtime-licences.md) · [pc-game-compatibility.md](pc-game-compatibility.md) · [pc-runtime-proof-of-concept.md](pc-runtime-proof-of-concept.md) · [RISK_ASSESSMENT.md](RISK_ASSESSMENT.md)
