# RetroBat / ES-DE theme compatibility

Checked 2026-10-28 against the 7 themes in `G:\Retrobat\emulationstation\.emulationstation\themes` (read-only).

## What those themes are

EmulationStation themes (RetroBat, ES-DE, Batocera, Recalbox) are folders of XML files plus artwork: `theme.xml` with `<view>`s (system, gamelist …) placing elements at fractional screen positions, `<variables>` (colours, sizes, image names), per-system folders of logos and backgrounds, fonts and sounds. Each theme organises these differently (art-book-next-es and canvas-es use aspect-ratio and colour-scheme subsets; HEXA has one folder per system; carbon has layouts and subsets). They're written for EmulationStation's renderer, not for Android layouts, so they aren't interchangeable with RetroForge's screens.

## Licences found

| Theme | Licence (from its LICENSE/README) |
|---|---|
| canvas-es | CC0 1.0 (public domain) |
| atlas-es, art-book-next-es, es-theme-carbon, Alekfull-NX | CC BY-NC-SA (non-commercial, share-alike) |
| ES-THEME-ARCADEPLANET, es-theme-HEXA | not stated |

RetroForge ships none of these. A user may import colours from a theme they have; the licence is shown, and non-commercial themes can't be bundled in a paid app.

## What RetroForge imports

Settings → Themes → "Colours from an ES-DE / RetroBat theme": the user picks the theme folder; RetroForge reads up to 200 XML files (3 folders deep, 512 KB each, DOCTYPE refused), collects colour variables and maps them by name to background, cards, text, muted text and accents. It refuses combinations that can't be read (text on cards, accents on the background) and works out readable ones instead, then shows a report: what came from which variable, what was chosen for readability, what wasn't imported, and the licence. The folder is never written to.

Not imported: layouts and positions, system carousels, artwork, logos, fonts and sounds.

Result for the 7 themes: 5 give a usable palette (Alekfull-NX, art-book-next-es, canvas-es, ARCADEPLANET, carbon); atlas-es and HEXA define no colour variables RetroForge can read. Evidence: `ThemeTest.realThemes` (opt-in) and the emulator test in [TESTING.md](TESTING.md).

## Possible later

System logos from themes the user owns (per-system image lookup differs per theme), once the theme engine supports images.
