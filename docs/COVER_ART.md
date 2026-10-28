# Cover Art

Status (2026-10-28): libretro, SteamGridDB (live-tested with the user's key) and your own images built and tested; ScreenScraper built and unit-tested, waiting for RetroForge's developer ID.

## Sources

| Source | Needs | Used for | Rules we follow |
|---|---|---|---|
| **Your gamelist.xml media** (RetroBat, ES-DE, Batocera scrapes) | nothing | default artwork | read-only, never modified |
| **libretro thumbnails** (`thumbnails.libretro.com`, the server RetroArch uses) | nothing | free box art; per-game picker and per-system batch | one system index per week (cached), then one download at a time |
| **SteamGridDB** (`steamgriddb.com/api/v2`) | the user's own free API key | custom community covers, per game, on request | no key ships with the app; no bulk downloads; NSFW, humour and epilepsy-warning images filtered out; artist credited |
| **ScreenScraper** (`api.screenscraper.fr/api2`) | RetroForge's developer ID (issued by ScreenScraper on request; in `local.properties`, not in git); optionally the user's own account | front/3D boxes per game, on request: file name, size and MD5 (files up to 64 MB) | only free apps may use the API without ScreenScraper's permission; one lookup per request; quota errors shown; credentials are URL parameters as the API requires, never logged, and removed from the stored image address. System ids from ES-DE (MIT) |
| **Your own image** | nothing | any picture on the device, via Android's photo picker | checked to be PNG/JPEG/WebP/GIF, 15 MB cap |
| **The Cover Project** | a browser | opened in the browser only | the site blocks automated access (Cloudflare check on every page, even robots.txt), so RetroForge doesn't download from it; save a scan and use "Choose an image" |

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
- Emulator (2026-10-28): "Choose an image" saved a picked photo as the cover and the game page shows "Your own image"; "Open The Cover Project" opens the browser; ScreenScraper shows "not available in this build" without a developer ID and sends nothing.
- Unit: ScreenScraper request building, XML parsing (DOCTYPE refused), cover ordering by type and region, credential stripping, errors (7 tests).
- Not tested: ScreenScraper against the live service (needs the developer ID).
