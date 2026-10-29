# RetroBat workflows mapped to RetroForge

How each thing a RetroBat user does is done in RetroForge on Android. Status as of 2026-10-28.

| RetroBat workflow | RetroForge | Status |
|---|---|---|
| First run: pick a roms folder (default `RetroBat\roms`) | Setup → choose the folder (the RetroBat folder, its `roms`, or one system folder) | ✅ |
| Emulators come bundled | Built-in player downloads its cores in the background at setup; RetroArch/standalone apps are options | ✅ |
| Drop BIOS files in `bios\` | Setup → BIOS check finds them in your folder and copies them where the player and RetroArch look | ✅ |
| Scrape with ScreenScraper | Reads existing `gamelist.xml` media; covers from libretro thumbnails / SteamGridDB | 🟡 ScreenScraper waits for a developer ID |
| Browse system carousel → game list | Retro tab (canvas-es style systems) → system page → game page | ✅ |
| Pick emulator per system / per game (Advanced settings) | Game page → Play with …; system default in Settings | ✅ |
| In-game: hotkey + Start to quit, save/load states | Built-in player: swipe from the edge or the menu button; hotkeys; RetroArch keeps its own | ✅ |
| Change disc (multi-disc m3u) | Built-in player menu → Change disc; RetroArch via m3u | ✅ |
| Bezels / decorations, shaders | Console look (on by default), Sharp/Smooth/CRT/LCD filters | 🟡 Bezel Project bezels in landscape (download per system); no shader presets |
| Cheats | Built-in player → Cheats (libretro database + own codes) | ✅ |
| Favourites, collections | ✅ favourites, history, own collections | ✅ |
| Edit metadata | Game page → Edit details (kept in RetroForge, gamelist.xml untouched) | ✅ |
| Themes | Settings → Themes; colours and system art from an ES theme you have | ✅ |
| Save states per emulator in `saves\<system>\libretro.<core>` | Per game per core in RetroForge storage | ✅ (not shared with RetroBat) |
| Update RetroBat / store | App updates through its store; cores update when the player downloads them again | 🟡 |
| Netplay, RetroAchievements | — | ❌ |
| PC games (Windows) | PC tab: GameNative / Winlator hand-off; DroidDeck on Snapdragon | 🟡 |

The one real difference is that RetroForge never writes into the collection. On RetroBat, ES writes `gamelist.xml`; RetroForge keeps play counts and edits in its own database, so the same drive keeps working in RetroBat.
