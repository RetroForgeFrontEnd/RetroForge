# Cover Art

Status (2026-10-28): built; libretro source tested on the emulator; SteamGridDB tested with simulated responses only (no API key available yet).

## Sources

| Source | Needs | Used for | Rules we follow |
|---|---|---|---|
| **Your gamelist.xml media** (RetroBat, ES-DE, Batocera scrapes) | nothing | default artwork | read-only, never modified |
| **libretro thumbnails** (`thumbnails.libretro.com`, the server RetroArch uses) | nothing | free box art; per-game picker and per-system batch | one system index per week (cached), then one download at a time |
| **SteamGridDB** (`steamgriddb.com/api/v2`) | the user's own free API key | custom community covers, per game, on request | no key ships with the app; no bulk downloads; NSFW, humour and epilepsy-warning images filtered out; artist credited |

SteamGridDB's terms (checked 2026-10-28, effective 2019-07-31) allow personal, non-commercial use and forbid automated use that loads their servers unreasonably. RetroForge therefore never batch-downloads from SteamGridDB. If RetroForge is ever sold, SteamGridDB's permission is needed first.

## Matching (libretro)

Files are matched against the real names on the server, not just guessed URLs:

1. Exact name (No-Intro sets match directly).
2. Otherwise the same title (", The" handled, punctuation ignored), ranked by region (a "World" cart accepts USA, Europe or Japan box art), revision, and preferring original releases over Beta/Demo/Collection/Virtual Console editions.

Real-collection result (read-only, `G:\Retrobat\roms`, 2026-10-28):

| System | Games | Exact | Fuzzy | No match | Without art today → coverable |
|---|---|---|---|---|---|
| GBA | 1,186 | 725 | 347 | 114 | 972 → **873** |
| SNES | 1,386 | 984 | 258 | 144 | 81 → 2 (rest are hacks) |
| Mega Drive | 1,089 | 476 | 357 | 256 | 1 → 1 |
| NES | 839 | 732 | 55 | 52 | 31 → 3 |
| PlayStation | 114 | 97 | 9 | 8 | 5 → 3 |

Misses are mostly homebrew, hacks and translations, which libretro doesn't carry: that's what SteamGridDB is for.

## Storage

Chosen covers are saved in RetroForge's private `files/covers/` (named by a hash of folder + path) and recorded in the `covers` table. ROM folders are never written. "Use the original artwork" deletes RetroForge's copy and returns to the gamelist art.

## Tested

- Unit: matching (exact, GoodTools `(W)`, region preference, no cross-title matches), URL encoding, index parsing, SteamGridDB request/response handling and bad keys, image sniffing (8 tests).
- Emulator: Resident Evil 2 (no art) → "Resident Evil 2 (USA)" ranked first → saved and shown; SNES batch: "4 covers added, 79 not found" with live progress.
- Found and fixed: a download that trickles data could hang forever (read timeout only fires on silence); every download now has a 60-second deadline.
- Not tested: SteamGridDB against the live service (needs the user's key).
