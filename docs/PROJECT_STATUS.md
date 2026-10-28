# RetroForge — Project Status

Last updated: 2026-10-27 (Milestone A complete on emulator)

This file is the single source of truth for what exists, what has been verified and what is blocked. Detailed findings live in the linked documents.

## Phase summary

| Phase | Status |
|---|---|
| 1 — Environment and feasibility audit | Done. PC approach **approved 2026-10-27: separate DroidDeck app via its launch intent.** |
| 2 — PC runtime proof of concept | Blocked on a physical device (see [pc-runtime-proof-of-concept.md](pc-runtime-proof-of-concept.md)) |
| 3 — Android foundation (Milestone A) | **Done: build verified, unit tested, emulator tested.** Physical device / TV / gamepad not tested. |
| 4 — Retro library (Milestone B) | **Done: unit tested, real-collection tested (28,274 games, read-only), emulator tested via SAF.** USB/OTG drives and very large SAF trees not yet tried on a real device |
| Links tab | Done: six verified YouTube channels |
| Emulation path check (RetroArch, outside RetroForge) | 6 ROMs from your collection launch-tested on the emulator (SNES ×3 incl. `.7z`, Mega Drive, GBA, NES with gameplay): [EMULATOR_COMPATIBILITY.md](EMULATOR_COMPATIBILITY.md) |
| ROM Patcher (IPS/BPS/UPS, zip/7z input) | **Built; 29 unit tests; emulator tested.** Patch chains, readmes and multi-patch archives work; a real 2-patch hack chain (Giant Mario → 2B) is gameplay-tested. Next: download a patch from a link. [FEATURE_PROPOSAL_ROM_TOOLS.md](FEATURE_PROPOSAL_ROM_TOOLS.md) |
| Hacks tab (catalogues) | **Built; 7 unit tests; emulator tested.** Built-in starter catalogue (5 hacks, links to romhacking.net pages); add catalogues from file or https link. [hack-catalogue-format.md](hack-catalogue-format.md). romhacking.net isn't scraped: it is read-only since Aug 2024 and its robots.txt disallows its download and listing pages |
| ROM sorting | Proposal only |
| Systems up to PS2 | Requested; test pass planned (RetroArch cores on emulator; ARM64-only emulators such as PS2 need a real phone). Obtainium + emulation pack JSON suggested as the install route, to be evaluated |

## Milestone A — what exists

- Gradle multi-module project: `app`, `core-ui`, `core-settings`, `pc` (see [ARCHITECTURE.md](ARCHITECTURE.md)).
- Branding from the RetroForge key art: navy background, neon orange/cyan two-tone wordmark, tagline, launcher icon, Android TV banner.
- Sections Home / Retro / PC / Settings; bottom bar on phones, side rail on wide screens and TV.
- Controller/keyboard navigation with visible focus rings; A/B/L1/R1 mapping ([CONTROLLER_SUPPORT.md](CONTROLLER_SUPPORT.md)).
- Settings (persisted with DataStore): theme System/Dark/Light; show/hide PC section.
- PC section, real data only: GPU detection (GL renderer, kgsl sysfs, SoC), classification against DroidDeck's published requirements, DroidDeck install detection and launch-intent check, "Open DroidDeck" when installed, and the official releases link when not.
- Retro section: clearly labelled "Not built yet" states. **No fake launch buttons anywhere.**

## Evidence

| Item | Status | Evidence |
|---|---|---|
| Debug build | Build verified | `./gradlew assembleDebug`, 2026-10-27 |
| Unit tests (10) | Unit tested, all pass | [TESTING.md](TESTING.md) |
| Lint | 0 errors | `lintDebug` |
| UI, navigation, settings persistence | Device tested (emulator, API 37) | [TESTING.md](TESTING.md) manual log |
| GPU detection on Adreno hardware | **Not tested** | needs physical device |
| DroidDeck detection when installed | **Not tested** | needs DroidDeck installed |
| Physical gamepad, Android TV | **Not tested** | |

## Environment (verified 2026-10-27)

| Item | Finding |
|---|---|
| Project | `F:\RetroForge`, Git repo initialised (branch `main`), **nothing committed yet** |
| Android Studio | Installed; bundled JBR = OpenJDK 25.0.3 (used for builds) |
| Android SDK | `C:\Users\darre\AppData\Local\Android\Sdk`: platform 37, build-tools 36, platform-tools, emulator |
| Emulator | AVD `Medium_Phone_API_37.0` (x86_64), working |
| Physical Android device | **None connected** |
| RetroBat | `G:\Retrobat` 8.3.1.1, read-only audit done ([retrobat-installation-inventory.md](retrobat-installation-inventory.md)) |
| DroidDeck source | Audited at commit `4f58230` ([droiddeck-architecture-audit.md](droiddeck-architecture-audit.md)) |
| Docker, Rust, gh | Not installed (not needed with the approved approach) |
| Disk | `G:` 95 % full (52 GB free) |

## Open items needing your input

1. **Target devices** (answered 2026-10-27): all Android device types, including dual-screen handhelds such as the AYN Thor; possibly iOS later. Still needed: **one physical device** for real testing (PC/GPU check, emulator performance, controllers).
2. **How the device reaches your ROMs:** USB drive, SD-card subset, or network share ([ROM_PORTABILITY.md](ROM_PORTABILITY.md)).
3. **Licence for RetroForge itself**, still open.
4. **Key art file:** to show the artwork in the app, save it in `F:\RetroForge\branding\` and confirm you have the right to use it (e.g. you made or commissioned it).
5. **Commit:** Milestone A is uncommitted, awaiting your OK.

## Observations about your RetroBat install (no action taken)

- `emulators/retroarch/retroarch.cfg` still references `D:\Retrobat` (8 lines); RetroBat normally rewrites these at launch.
- `roms/ps2` has 37 `.gz` files plus 37 matching `.tmp` files, which looks like an interrupted conversion.
- `G:` is 95 % full.

## Documents

Current: [ARCHITECTURE.md](ARCHITECTURE.md), [BUILD_AND_INSTALL.md](BUILD_AND_INSTALL.md), [TESTING.md](TESTING.md), [CONTROLLER_SUPPORT.md](CONTROLLER_SUPPORT.md), [THIRD_PARTY_LICENCES.md](THIRD_PARTY_LICENCES.md), [RISK_ASSESSMENT.md](RISK_ASSESSMENT.md), [ROM_PORTABILITY.md](ROM_PORTABILITY.md), [droiddeck-architecture-audit.md](droiddeck-architecture-audit.md), [pc-runtime-proof-of-concept.md](pc-runtime-proof-of-concept.md), [pc-runtime-licences.md](pc-runtime-licences.md), [retrobat-installation-inventory.md](retrobat-installation-inventory.md), [retrobat-system-emulator-matrix.md](retrobat-system-emulator-matrix.md), [third-party-dependencies-and-licences.md](third-party-dependencies-and-licences.md), [reverse-engineering-gaps.md](reverse-engineering-gaps.md).

Not yet written (each lands with its phase): USER_GUIDE, SECURITY_AND_PRIVACY (Milestone B, when storage access arrives), EMULATOR_INTEGRATION_GUIDE / EMULATOR_COMPATIBILITY (Milestone D), SHADERS_AND_GRAPHICS, BIOS_AND_FIRMWARE (Phase 11), theme-format docs (Phase 10), retroforge-pc-architecture / pc-game-compatibility (after the PoC), the remaining RetroBat analysis docs (Phases 4–5).

## Next milestone: B — read-only library scanner

Folder picker (Storage Access Framework) → background recursive scan → Room database, with platform rules seeded from your RetroBat folder names and extensions. It will skip media subfolders, handle `.m3u`, optionally import `gamelist.xml` read-only, and come with fixture-based tests. Testable on the emulator with dummy files; your real collection needs the device or a copied subset.
