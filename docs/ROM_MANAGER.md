# ROM Manager

Status (2026-10-28): built; unit tested (`retro-library` `manage` tests); emulator tested on a copy of real ROMs. The only part of RetroForge that changes files in a game folder, and only after the user confirms a preview.

## Safety rules (as built)

- Every change is previewed, then confirmed. Write access to a folder is asked for only then (the user picks the same folder again).
- Files are moved aside, never deleted: `_duplicates/`, `_originals/` and `_deleted/` at the top of the collection folder (RetroBat and ES-DE ignore them; RetroForge's scanner skips them).
- Every step is journalled as it happens (`files/rom-manager/*.json`), so History can undo a run exactly, even an interrupted one. Undo never overwrites a file that has reappeared.
- `gamelist.xml` and `.m3u` files are edited as text: only the affected entry or line changes. The first edit backs up `gamelist.xml` to `gamelist.xml.retroforge-backup`.
- "Free up space" is the only permanent delete: one holding folder at a time, after a confirmation giving file count and size. History entries that needed those files are marked as no longer undoable.

## Features

| Feature | What it does | Evidence |
|---|---|---|
| Duplicates | Sets the scanner found; compares game data by CRC (inside .zip/.7z too); ticks only sets with identical data; keeps the most compatible or smallest copy, or one the user picks | Real collection: 11 SNES .7z/.zip sets identical; MSU-MD .cue+.md companions (33), Quake mission packs and multi-file discs left alone; emulator: moved 2 copies + gamelist updated, undone exactly |
| Compress discs to CHD | .cue/.bin (any tracks, pregaps), Dreamcast .gdi and PS2/PSP .iso; CHD v5 with Deflate codecs; rebuildable sector sync/parity left out and repeated hunks stored once, as chdman does; read back and checked before originals move | Data and overall SHA-1 identical to chdman 0.287 for the same disc (Tactics Ogre .cue, Ikaruga .gdi, both matching the original CHDs; Namco Museum .gdi made on an Android emulator, 1.19 GB → 16 MB, matches too); `chdman verify` passes; Batman Forever boots from it in RetroArch (SwanStation). Same size as `chdman -c cdzl` (within 0.2%); chdman's default LZMA/FLAC files are smaller still (Ikaruga 26.8 MB against our 31.0 MB) |
| Compress GameCube to RVZ | Dolphin's format (Zstandard 5, 128 KiB groups, junk stored as seeds); every byte compared with the ISO before it moves | Luigi's Mansion 1.46 GB → 164 MB; DolphinTool 2609a reads it, SHA-1 matches the ISO, Dolphin's own RVZ is the same size |
| Compress 3DS to Z3DS | Azahar's format: .3ds/.cci → .zcci, .cxi → .zcxi, .3dsx → .z3dsx; encrypted games left alone | Super Mario 3D Land 321 → 291 MB; Azahar 2126.2 unpacks it to the identical ROM; Azahar's own .zcci is the same size |
| Compress cartridges to .zip | Only for systems whose RetroArch cores load .zip (allow-list) — never pinball tables, 3DS, ScummVM | Emulator test; byte-identical after undo |
| Multi-disc playlists | Makes an .m3u for "Disc 1/Disk 2 of 4/Side B" sets so they show as one game; the playlist borrows disc 1's gamelist metadata | Command & Conquer (2 CHDs): one game, disc 2 swapped in RetroArch (serial SLUS-00410 reported) |
| Delete a game | All its files (an .m3u's discs, a .cue's tracks) move to `_deleted`, gamelist entry removed; undoable | Emulator test, then permanent delete |

Real-collection dry run (read-only): 427 games / 14 GB compressible with the allow-list; 159 playlists missing (mostly Amstrad CPC and Atari 800).

## Not built

- Renaming to No-Intro names (needs DAT files the user provides).
- Wii to RVZ (needs Nintendo's Wii key; use Dolphin's converter). DiscJuggler .cdi (chdman can't read it either; Flycast plays it as it is). .cia to .zcia.
- LZMA/FLAC CHD codecs.
- Running in the background: keep RetroForge open while compressing (the screen is kept on).
