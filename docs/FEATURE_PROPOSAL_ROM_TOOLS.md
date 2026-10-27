# Proposal: ROM Sorting Tool and ROM Hacks Section

Requested 2026-10-27. Status: **ROM Patcher (local files) built and emulator-tested**, see [TESTING.md](TESTING.md). Sorting and online sources not started.

Lessons from the first real patch ("2B in Super Mario Bros", IPS): it is an add-on that must be applied *after* another patch (Giant Mario v1.2), and only its readme says so. So:
- **Patch chains**: done. Patches apply top to bottom, with move up and remove.
- **Readme display**: done. A preview shows, and selecting it expands the full text.
- **Archives with several patches**: done (choose one), as Giant Mario v1.2.zip ships three.
- All of these matter even more for in-app downloads (next).

## 1. ROM sorting

### The catch
Your RetroBat gamelists reference files by relative path (`./Super Mario World (USA).zip`, `./mixart/...`). **Physically moving or renaming ROMs would break RetroBat's metadata and artwork links**, and ES-DE's too. So:

### Proposed design
- **Virtual sorting first (default, zero risk).** RetroForge parses No-Intro/GoodTools name tags: region `(USA)`, `(Europe)`, revision `(Rev 1)`, `[!]` verified, `(Beta)`, `(Proto)`, `(Hack)`, translations `[T+Eng]`. Library views can then:
  - group by region, hide betas/protos, show "best version only" (1G1R-style),
  - flag duplicates (same game in .zip and .7z, e.g. your Super Mario World),
  - build smart collections ("All hacks", "Unverified dumps").
  Nothing on disk changes.
- **Physical reorganisation (opt-in, later).** A planner that shows a full dry-run (from → to), warns about gamelist.xml / media links it would break, and offers to rewrite those paths too. It needs an explicit confirmation, writes an operation log, and supports one-click undo. It never deletes; duplicates go to a `_duplicates` folder only if you choose.
- Optional hash checking (CRC32/SHA-1 against No-Intro DAT files you supply) to identify misnamed ROMs.

## 2. ROM hacks section

### What you already have
- `roms/snes/SMW HACKS`: 49 **pre-patched** `.smc` hacks
- `roms/snes/SNES HACKS`: 30 `.zip` + 1 `.7z`, pre-patched

These can appear as a **Hacks** section as soon as the scanner exists: no patching needed.

### Patching (new)
- Formats: **IPS, BPS, UPS** first (they cover most SNES/NES/GB/GBA/MD hacks), xdelta later for disc games.
- **Original ROM is never modified.** Two modes:
  1. *Build a patched copy* in RetroForge's own storage. BPS/UPS carry the source checksum, so RetroForge refuses a wrong base ROM (e.g. headered vs unheadered SMW) with a clear message. IPS has no checksum, so RetroForge warns instead.
  2. *Soft-patching:* RetroArch can apply a patch at load time without writing a file. How to pass that through the Android intent is **not yet verified**.
- Patching code is plain Kotlin, unit-testable with synthetic data (no copyrighted ROMs in tests).

### "Patches from the net": constraints to check before building
- RetroForge will **only download patch files, never ROMs**. You supply the base ROM from your collection.
- Sources need checking for terms of use and whether automated access is allowed. Romhacking.net stopped taking submissions in 2024 (its current state and any archive/API need verifying). SMW Central hosts SMW hacks as BPS files (terms and API not yet checked). No scraping without permission.
- A safe first version: open a patch you downloaded yourself (file picker), or paste a direct link to a `.ips/.bps/.ups` (download shown and confirmed first, size-limited, never executed).
- Patch metadata (title, author, base-ROM requirement, readme) stored alongside the patched copy.

## Suggested order
1. Milestone B scanner, including name-tag parsing and a Hacks section for your existing hack folders
2. Virtual sorting views + duplicate detection
3. IPS/BPS/UPS patcher (local patch files)
4. Online patch sources, after their terms are checked
5. Physical reorganisation planner with undo
