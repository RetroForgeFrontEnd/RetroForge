# Proposal: ROM Manager (rename and sort duplicates)

Requested 2026-10-28. Status: duplicates, compression, playlists and deletion built — see [ROM_MANAGER.md](ROM_MANAGER.md). Renaming still a proposal.

This is the first RetroForge feature that would **change files in the user's collection**, so everything else in RetroForge (read-only by default) still applies, with these extra rules.

## Safety rules

1. **Preview first.** Every operation shows a full list (old name → new name, or file → destination) before anything happens.
2. **Explicit confirmation**, per batch, with the count of files affected.
3. **Undo log.** Each run writes a log in RetroForge's storage; "Undo" reverses it exactly.
4. **Never delete.** Duplicates are moved to a `_duplicates` folder inside the same system folder. The user deletes them themselves if they want to.
5. **Keep other frontends working.** RetroBat/ES-DE `gamelist.xml` files refer to ROMs and artwork by path. When a ROM is renamed or moved, its `<path>` (and media paths named after it) are updated too, after backing up `gamelist.xml` to `gamelist.xml.retroforge-backup`. Without this, RetroBat would lose that game's artwork.
6. **Write access is separate.** The library only needs read access; the ROM Manager asks for write access to a folder only when used.

## Features

- **Duplicates** (already detected by the scanner): same game in several files (e.g. `.zip` and `.7z` of Super Mario World) → keep one (rules: prefer No-Intro name, verified dump `[!]`, user's preferred region, smaller format), move the rest to `_duplicates`.
- **Rename to No-Intro names**: needs checksums. Hash each ROM (inside zip/7z too) and match against No-Intro DAT files the user provides (not bundled; DATs have their own terms). GoodTools names like `Sonic The Hedgehog (W) (REV00) [!]` become `Sonic The Hedgehog (USA, Europe)`.
- **Region/1G1R tidy**: optionally move non-preferred regions, betas and protos to a sub-folder.

## Order

1. Duplicates (no DATs needed), with preview, undo and gamelist updates.
2. Hashing + DAT import.
3. Renaming.
