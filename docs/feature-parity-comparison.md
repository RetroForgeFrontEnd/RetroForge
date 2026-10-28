# Feature parity: RetroForge against other frontends

Written 2026-10-28. RetroBat was inspected read-only on this PC (`G:\Retrobat`, 8.3.1.1). The other frontends are summarised from their public descriptions and reviews; I have not installed them. Socket's and Cocoon's details come from 2025–26 coverage (retrododo, retrohandhelds.gg, Android Authority, heldgames) and may already be out of date.

✅ RetroForge has it · 🟡 partly · ❌ not yet · — not applicable on Android

| Feature | RetroBat | Batocera | ES-DE | Daijisho | NeoStation | Socket | Cocoon | RetroForge |
|---|---|---|---|---|---|---|---|---|
| Platform | Windows | Linux distro | Win/Mac/Linux/Android | Android | Android | Android | Android | Android |
| Built-in emulation (no other app) | bundles emulators | bundles emulators | ✗ launches emulators | ✗ | ✗ | ✗ | ✗ | ✅ own libretro player, cores downloaded by itself |
| Launch RetroArch / standalone apps | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ RetroArch + 21 standalone |
| Per-system / per-game emulator choice | ✅ | ✅ | ✅ | ✅ | ✅ | ? | ✅ | ✅ |
| Reads gamelist.xml / ES media in place | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ? | ✅ read-only |
| Scraper | ScreenScraper | ScreenScraper | ScreenScraper, TheGamesDB | own database | ✅ | ✅ | ✅ | 🟡 libretro thumbnails, SteamGridDB; ScreenScraper waiting on a developer ID |
| Edit game details | ✅ | ✅ | ✅ | ✅ | ✅ | ? | ✅ | ✅ |
| Favourites, history, collections | ✅ | ✅ | ✅ | ✅ | ✅ | 🟡 | ✅ | ✅ |
| Themes | ES XML themes | ES XML themes | ES-DE themes | wallpapers | ✅ | 3D cartridge/disc models | themes, Silk Pod store | ✅ own engine + colours/system art from ES themes |
| System carousel with console art | ✅ | ✅ | ✅ | 🟡 | ✅ | — | ✅ | ✅ canvas-es style |
| Video previews | ✅ | ✅ | ✅ | ✅ | ✅ | ? | ✅ | ❌ |
| Background music / UI sounds | ✅ | ✅ | ✅ | ❌ | ? | ? | ✅ | ❌ |
| Dual-screen handhelds (AYN Thor, Pocket DS) | — | — | 🟡 | 🟡 | ? | ✅ | ✅ | ❌ (DS layouts on one screen only) |
| Playtime tracking | ✅ | ✅ | ❌ | ✅ | ✅ | ? | ✅ per second | 🟡 play history, no playtime |
| Shaders, bezels | ✅ | ✅ | — | — | — | — | — | 🟡 4 filters + console look; no bezel packs |
| Cheats | ✅ | ✅ | — | — | — | — | — | ✅ built-in player |
| Save states, auto resume | ✅ | ✅ | — | — | — | — | — | ✅ built-in player |
| Hotkeys | ✅ | ✅ | — | — | — | — | — | ✅ |
| RetroAchievements | ✅ | ✅ | 🟡 shows | ✅ | ✅ | ? | ? | ❌ |
| Netplay | ✅ | ✅ | — | — | — | — | — | ❌ |
| BIOS check | ✅ | ✅ | ❌ | ❌ | 🟡 | ❌ | ? | ✅ check and copy |
| ROM tools (duplicates, compress) | ❌ | 🟡 | ❌ | ❌ | ❌ | ❌ | ❌ | ✅ |
| PC games | ✅ native | 🟡 Wine | 🟡 shortcuts | 🟡 apps | 🟡 apps | ❌ | 🟡 apps | 🟡 GameNative/Winlator hand-off |
| Android apps in the library | — | — | ✅ | ✅ | ✅ | ? | ✅ | ❌ |
| Cloud sync of settings | ❌ | ❌ | ❌ | ✅ | ✅ (paid) | ? | ? | ❌ |
| TV / controller-only use | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ (tested on the emulator) |

## What this means for RetroForge

RetroForge's lead is the built-in player. No other Android frontend here plays games without a second app, so setup is shorter. It also has ROM tools, BIOS checks, and reading an ES/RetroBat collection in place.

The gaps worth closing, most useful first:
1. **Video previews and UI sounds/music.** Every theme-heavy rival has them.
2. **Dual-screen handhelds.** Socket and Cocoon already target the AYN Thor; RetroForge's target-device list includes them.
3. **RetroAchievements** in the built-in player (rcheevos is MIT; needs the user's own login, stored only on the device).
4. **Playtime tracking** (the player knows the exact play time already).
5. **Android apps and PC ports in the library** (the planned PC ports section).
6. GPU cores (N64, PSP, Dreamcast), which every bundled-emulator rival has.

Sources: [Socket](https://retrohandhelds.gg/socket-frontend-now-available-for-free/), [Socket dual screen](https://retrododo.com/socket-frontend-finally-gets-dual-screen-handheld-support/), [Cocoon setup guide](https://heldgames.com/guides/cocoon-setup-guide), [Cocoon Shell 2.1](https://retrohandhelds.gg/cocoon-shell-beta-2-1-brings-big-upgrades-to-dual-screen-handhelds/).
