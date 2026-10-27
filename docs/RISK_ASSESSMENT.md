# Risk Assessment (Phase 1)

| # | Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|---|
| 1 | Target device GPU is not Adreno 650/725+/8xx → no local PC gaming | Unknown (device not identified) | High for PC goal | Detect GPU, show honest "not available"; retro side unaffected |
| 2 | DroidDeck's `LAUNCH_GAME` intent changes or is removed (undocumented contract, very active repo) | Medium | Medium | Isolate in `pc` module; version-check DroidDeck; fallback "Open DroidDeck" |
| 3 | External emulators can't open SAF `content://` URIs; need real file paths | High for some emulators | High | Test per emulator; support `MANAGE_EXTERNAL_STORAGE` as opt-in for sideload builds; document per emulator |
| 4 | USB drive filesystem (NTFS?) not mountable on device | Medium | High | Check filesystem; offer SD/subset or exFAT guidance (never reformat for the user) |
| 5 | `.7z` ROMs (≈60 % of SNES) unsupported by some Android cores | Medium | Medium | Test in Milestone D; report per core; never auto-convert |
| 6 | GPL obligations if any GPL emulator/DroidDeck code is embedded | Certain if embedded | High | Launch external apps instead; choose RetroForge licence early |
| 7 | Metadata provider terms (ScreenScraper etc.) | Medium | Medium | Start offline with your existing gamelist.xml; add providers with their terms later |
| 8 | Theme import from ES themes (XML + licences vary) | High complexity | Low-Medium | Native format first; importer scoped later |
| 9 | Large libraries (tens of thousands of files) over SAF are slow | High | Medium | Background job, incremental rescan by mtime, progress + cancel |
| 10 | No device for testing → claims can't be verified | Current | High | Android Studio emulator for UI/scanner; physical device required for emulators/PC |
| 11 | `G:` 95 % full; PS2 folder has apparent incomplete `.gz/.tmp` pairs | Present | Low for RetroForge | Informed you; untouched |
