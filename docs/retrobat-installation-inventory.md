# RetroBat Installation Inventory

Inspected **read-only** on 2026-10-27 at `G:\Retrobat`. No files were created, modified, moved or deleted there.

## Version

`system/version.info` and `emulationstation/version.info`: **8.3.1.1-stable-win64**. Frontend is RetroBat's EmulationStation fork (Batocera-derived — tools such as `batocera-store.exe`, `batocera-systems.exe` ship with it).

## Drive

`G:` — 932 GB, 880 GB used, **52 GB free**. RetroBat and ROMs share this drive. Filesystem type not yet checked (matters for Android: NTFS on USB is vendor-dependent; exFAT is broadly supported).

## Top-level layout

| Folder | Content | Android relevance |
|---|---|---|
| `roms/` | **306** system folders | The collection. Reuse in place (read-only). |
| `bios/` | 310 entries (files + subfolders e.g. `Databases`, `Enterprise`, `HdPacks`) — flat, RetroArch-style naming | Reusable per emulator; Android emulators expect their own BIOS dirs |
| `saves/` | Per-system subfolders (≈250) | Format depends on emulator; see portability doc |
| `emulators/` | 138 Windows emulator folders (RetroArch with 160 cores, Dolphin, PCSX2, DuckStation, PPSSPP, melonDS, Azahar, Cemu, Eden/Ryujinx/Citron, xemu, Vita3K, …) | **Not reusable** — Windows binaries |
| `emulationstation/` | `emulationstation.exe`, `emulatorLauncher.exe` (C#, launches per-system), configs | Reference only |
| `emulationstation/.emulationstation/` | `es_settings.cfg`, `es_systems.cfg` (246 systems), `es_input.cfg`, `themes/`, `collections/` (empty), `video/`, `music/` | `es_systems.cfg` = source for platform/extension rules |
| `system/` | `configgen`, `shaders`, `decorations`, `padtokey`, `templates`, `tools`, `scripts` | Shaders/bezels: reference; licence-check before reuse |
| `decorations/`, `cheats/`, `screenshots/`, `records/`, `library/`, `user/` | Supporting data | |

## Frontend settings (es_settings.cfg)

`ThemeSet = Alekfull-NX`, `GamelistViewStyle = grid`, `Language = en_GB`.
Installed themes: Alekfull-NX, ES-THEME-ARCADEPLANET, art-book-next-es, atlas-es, canvas-es, es-theme-HEXA, es-theme-carbon.

## System definition example (snes)

```
<path>~\..\roms\snes</path>
<extension>.smc .fig .sfc .gd3 .gd7 .dx2 .bsx .swc .rom .wad .zip .7z .decomp</extension>
<command>"%HOME%\emulatorLauncher.exe" -gameinfo %GAMEINFOXML% %CONTROLLERSCONFIG% -system %SYSTEM% -emulator %EMULATOR% -core %CORE% -rom %ROM%</command>
emulators: libretro → snes9x, bsnes-jg, …
```

All observed system paths are relative (`~\..\roms\<system>`) — good for portability: RetroForge can map `<root>/<system>` directly. Full matrix: [retrobat-system-emulator-matrix.md](retrobat-system-emulator-matrix.md).

## Metadata and media

- **125** `gamelist.xml` files; 131 systems have `images/` or `media/` folders.
- Gamelists are ScreenScraper-sourced, with relative media paths per game: `image → ./mixart/`, `video → ./snap/`, `marquee → ./wheel/`, `thumbnail → ./images/…-thumb.png`, `manual → ./manuals/…pdf`, plus `desc`, `rating`, `releasedate` (`YYYYMMDDT000000`), `developer`, `publisher`, `genre`, `players`, `cheevosHash`.
- Media lives **inside** the ROM folders (`roms/snes/mixart`, `snap`, `videos`, `wheel`, `images`, `manuals`). The scanner must skip these subfolders as games.
- `gamelist.xml.old` backups exist alongside.

→ RetroForge can import gamelist.xml read-only as its initial metadata source, offline, with no scraping. Strong win.

## File formats sampled

| System | Files at top level |
|---|---|
| snes | 794 `.7z`, 511 `.zip`, 1 `.smc`; subfolders `SMW HACKS`, `SNES HACKS` |
| psx | 118 `.chd`, 8 `.m3u` (multi-disc), 2 `.PBP` |
| ps2 | 37 `.gz` + 37 `.tmp` — **looks like an interrupted conversion; please check** |

`.7z` matters: RetroArch on Android handles `.zip` reliably; `.7z` support depends on the core/build — to be tested in Milestone D.

Large folders (files incl. media, depth ≤ 2, capped at 2000 — counts are indicative only): snes, nes, mame, fbneo, dos, c64, atarist, apple2 (≥2000 each), amstradcpc, n64, gba, dreamcast, nds, gbc, megadrive, psx, psp, ps2, gamecube, 3ds, saturn …

## Controllers (es_input.cfg)

Keyboard mapping and a **Nintendo Switch Pro Controller** (SDL GUID-based). SDL key/button ids are not Android `KeyEvent` codes; mappings will be recreated, not imported.

## PC-game folders in RetroBat

`roms/steam` (`Steam.steam` shortcut file), `roms/windows` (contains `RGSX Retrobat.bat`, a custom script), `roms/gog`, `roms/epic`. These point at Windows installs and **cannot** be launched on Android. Steam app ids from `.steam` files could seed RetroForge PC entries later.

## Windows-specific assumptions to translate

- `%HOME%`, `~\..\` and backslash paths
- `emulatorLauncher.exe` per-launch config generation (writes each emulator's config just before launch)
- `retroarch.cfg` still contains 8 `D:\Retrobat` paths (install moved to G:) — RetroBat rewrites them at launch
- `.bat` scripts, `.exe` emulators, Windows controllers (XInput) — none usable on Android
