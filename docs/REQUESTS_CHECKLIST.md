# Requests checklist

Everything asked for (the original brief, then every later request), with its status. Last updated 2026-10-28.
✅ done and tested · 🟡 partly / built but not fully tested · ❌ not done · ⛔ blocked on hardware or a decision.
Evidence for each test is in [TESTING.md](TESTING.md); the brief's phases are in [PROJECT_STATUS.md](PROJECT_STATUS.md).

## Original brief (summary; full status in PROJECT_STATUS)

| Item | Status |
|---|---|
| Retro library: scan, browse, artwork, metadata, favourites, history, collections | ✅ |
| Launch games; return to RetroForge; play history; clear errors | ✅ |
| Emulator/core management | ✅ built-in player downloads its own cores; RetroArch and 21 standalone apps as options |
| Controllers and TV navigation | ✅ emulator (keys only); ⛔ real controller and TV box |
| Save states, BIOS, firmware | ✅ check/copy, states per emulator, save backup/restore (zip); ❌ memory-card tools |
| Graphics: shapes, filters, integer scaling | ✅ |
| Themes, theme engine, import | ✅ (colours + system art); ❌ interface sounds |
| Clear compatibility information | 🟡 docs; per-game compatibility not shown in the app |
| Portable library (other frontends keep working) | ✅ read-only scan, own index; ❌ export/import of RetroForge's setup |
| PC subsystem via a real runtime | 🟡 GameNative boots a game on the S22; ⛔ DroidDeck (needs Snapdragon); ❌ PC games in RetroForge's library |
| Documentation set | ✅ core docs and the 5 RetroBat audit docs ([architecture](retrobat-architecture-analysis.md), [workflows](retrobat-workflow-mapping.md), [ES-DE folders](retrobat-esde-folder-mapping.md), [Android parity](retrobat-android-parity-matrix.md), [recommendations](android-integration-recommendations.md)) |
| Automated tests | ✅ unit tests; ❌ automated UI tests |

## Later requests

| Request | Status |
|---|---|
| ROM manager: duplicates, compressor (CHD/zip), delete, undo | ✅ |
| Covers: SteamGridDB, ScreenScraper, The Cover Project | ✅ SteamGridDB, own images; ⛔ ScreenScraper needs RetroForge's developer ID; The Cover Project: link only (site blocks automated access) |
| Selling for £2 / funding | ✅ answered; support links built (need your URLs) |
| Standalone emulators with downloads (Obtainium, Emulation Pack) | ✅ |
| Hotkeys like Batocera/RetroArch + hotkeys page | ✅ RetroArch and built-in player |
| Cover every RetroBat system | ✅ mapped, ES-DE folder names accepted too; 🟡 tested subset (see TESTING) |
| Seamless multi-disc | ✅ RetroArch m3u; ✅ built-in player disc change |
| R2 fast-forward on older systems | ✅ |
| Auto save/load (resume) with a setting | ✅ |
| Favourites and custom playlists | ✅ |
| Rename Hacks tab to "ROM hacks" | ✅ |
| About page with the Mr Sujano joke | ✅ (moved to the top of Settings) |
| Tutorial in Settings with Russ (Retro Game Corps) nod and his guides | ✅ |
| Remove "RetroBat" from the app | ✅ no visible mentions |
| Theme engine; themes that aren't just colours; layouts (Xbox/PS styles); swipeable previews | ✅ |
| Import a theme from another frontend | ✅ colours; ✅ system artwork (canvas-es, Alekfull-NX) |
| Themes scale on rotation | ✅ |
| Key art on Home | ✅ |
| In-game menu by swiping from the edges (cheats, shaders, controllers, GPU drivers) | ✅ edge swipe, cheats, filters, emulator settings, switch emulator; ❌ controller remapping; GPU drivers n/a (no GPU cores yet) |
| Feature parity with RetroBat, Batocera, ES-DE, Daijisho, NeoStation, Socket, Cocoon | ✅ [comparison](feature-parity-comparison.md); gaps: video previews, UI sounds, dual screen, RetroAchievements, playtime |
| Setup clean-up: offer to reorganise a roms folder | ❌ |
| Test the PC side (TCG Card Shop Simulator) | ✅ boots in GameNative on the S22 |
| GameNative for non-Steam games, no user setup | 🟡 tested by hand; ❌ can't be fully automatic (Android needs the user to confirm installs) |
| Test on the phone; install RetroArch | ✅ |
| No core downloading for users → built-in player | ✅ |
| Overlays per system, on by default, switch off | ✅ console look |
| Aspect ratio in the in-game menu | ✅ screen shape |
| Switch emulator mid-game | ✅ (states can't move between emulators) |
| Works on TV / without touch | ✅ emulator; ⛔ real TV box |
| Touch controls smaller and at the sides in landscape | ✅ |
| DS screens side by side when rotated | ✅ |
| Bigger screen in portrait | ✅ |
| Retro tab like canvas-es with system logos, in RetroForge colours, faded logo/key art, loads by itself | ✅ |
| Console pictures centred and scaled; landscape fits | ✅ |
| Edit game details | ✅ |
| Setup without RetroArch homework; Play Store + website buttons | ✅ |
| Setup reappearing after Done | ✅ fixed (not re-tested on a fresh install) |
| RF neon icon from the logo | ✅ |
| PC ports section (like Quiver, retro decomps) | ❌ not started |
| Own cores for N64, PSP, Dreamcast (GPU) | ❌ next big item |
| PS1 with sticks, L2/R2/L3/R3; nicer pad | ✅ |
| Volume too low | ✅ volume keys + boost (needs your ears on the phone) |
| Cheats | ✅ |
| Widescreen hacks | ❌ (needs GPU cores mostly) |
| More shaders (CRT curvature, LCD ghosting, upscaler) | ❌ |
| Arcade: FBNeo, MAME, CPS; Naomi | ✅ FBNeo/MAME 2003-Plus; ❌ Naomi (Flycast, GPU) |
| Rename a game folder | ✅ |
| Retro tab only shows systems with games; setting for all | ✅ |
| 32X/Mega Drive buttons too close to the d-pad | ✅ d-pad and buttons shrink together on narrow screens |
| Time played per game | ✅ built-in player |
| All built-in player systems tested on the emulator | ✅ 17 systems boot (plus NES, SNES, Mega Drive, PS1 earlier) |
| Standalone apps for modern systems via Obtainium / Emulation Pack | ✅ "Get" opens Obtainium set up for 11 apps (Dolphin, PPSSPP, Eden, DuckStation, Play!, NetherSX2 …); ❌ Wii U (Cemu can't be started with a game) |
| N64, PS2, Wii, Wii U, GameCube, Switch in the built-in player | 🟡 libretro builds exist for all but Switch; needs GPU rendering in the player (in progress); standalone apps stay offered |
| "Made by Darren Glancy, built with AI" credit | ✅ About page and Settings |
