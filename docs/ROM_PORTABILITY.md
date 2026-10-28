# ROM Portability

Goal: one organised collection used by RetroBat (Windows), ES-DE and RetroForge (Android) without renaming or duplicating files.

## Rules RetroForge follows

1. **Read-only by default.** Scanning never writes into ROM folders. No `gamelist.xml`, no media, no `.nomedia` files are written there.
2. RetroForge data (library DB, metadata edits, artwork cache, play history) lives in app storage, keyed by *root id + relative path*, so it survives the drive being mounted at a different path.
3. Multiple game roots; each root has a **folder→system alias map** (default: RetroBat/ES-DE folder names).
4. Media/metadata subfolders (`images`, `mixart`, `snap`, `videos`, `wheel`, `manuals`, `media`, `downloaded_media`) are excluded from game detection.
5. `.m3u` playlists take precedence over the disc files they reference (no duplicate entries for each `.chd`).
6. Import/copy/export are explicit, previewed and confirmed. Never silent.
7. Portability ≠ compatibility: a ROM that works in RetroBat may need a different core, BIOS name/location, or save conversion on Android.

## Folder-name mapping (initial)

RetroBat and ES-DE share most short names because both inherit from the EmulationStation lineage. Known differences to handle via aliases (to be confirmed against ES-DE's current `es_systems.xml` in Phase 4):

| RetroBat folder | ES-DE folder | RetroForge system id |
|---|---|---|
| `snes` | `snes` | `snes` |
| `megadrive` | `megadrive` (also `genesis`) | `megadrive` |
| `psx` | `psx` | `psx` |
| `fbneo` | `fbneo` | `fbneo` |
| `amiga500` / `amiga1200` | `amiga` / `amiga1200` *(verify)* | `amiga` (+ model hint) |
| `msx1` | `msx` *(verify)* | `msx1` |
| `zxspectrum` | `zxspectrum` | `zxspectrum` |

The full alias table will be generated from your `es_systems.cfg` (246 systems) plus ES-DE's definitions, and stored as an editable config, not hard-coded.

## Getting the collection onto Android

Your ROMs are on a USB drive (`G:`). Options (you choose):

- **Plug the drive into the device** via USB-C/OTG. Works if the device mounts the filesystem (exFAT almost always; NTFS depends on vendor). Access is granted via Android's folder picker (SAF). Permissions can drop when the drive is unplugged.
- **SD card / internal storage with a subset** — fastest and most reliable; a copy is made by you, never by RetroForge silently.
- **Network share (SMB)** — possible later; external emulators generally cannot open SMB paths, so this needs care.

External emulators (RetroArch etc.) need to read the ROM file themselves. With SAF-only access, RetroForge may have to pass a `content://` URI the emulator can't open. Which emulators accept content URIs vs. file paths is a key Milestone D test.

## Saves

RetroBat stores saves in `G:\Retrobat\saves\<system>`. RetroArch `.srm` battery saves are generally portable between RetroArch on Windows and Android **for the same core**. Save states are core- and version-specific: never promised portable. Any save import will back up first.

## Implemented (Milestone B, 2026-10-28)

- System rules: `retro-library/src/main/resources/systems.json`, 221 systems generated from RetroBat 8.3.1.1 `es_systems.cfg` (ids, names, extensions only; Windows-only systems and extensions removed; common ES-DE aliases such as `genesis` added).
- Accepted folders: a collection root (`roms`), its parent (e.g. the RetroBat folder), or one system folder.
- Skipped as media: `images`, `media`, `mixart`, `snap`, `videos`, `wheel`, `manuals`, `downloaded_media` and similar.
- `gamelist.xml` is read for titles, descriptions, dates, developer/publisher, genre, players, rating and artwork paths. It is never written.
- `.m3u`, `.cue` and `.gdi` hide the files they list; folders named like games (`X.pc`, `X.ps3`) count as one game.
- RetroForge's index lives in its own database; "Forget folder" deletes only that index and the access grant.
