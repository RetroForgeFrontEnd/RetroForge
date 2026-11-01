# 0.8.2 beta test list

Nothing goes to GitHub until every row is ✅ (or the user agrees to leave it out). Device and date with each result.

0.8.2 is the first GitHub release that is a release build (shrunk by R8, 39 MB instead of 75) and the first signed with RetroForge's own key, so the rows under "Release build" matter more than usual: R8 can break code that's only found by name, and a signing mistake would stop 0.8.1 users from updating.

| Feature | What to check | Status |
|---|---|---|
| **Release build and signing** | | |
| Updates from 0.8.1 | An APK signed by tools/sign-github-release.ps1 installs over 0.8.1 (debug key) without uninstalling, and the library, history and settings stay | ✅ emulator, 1 Nov: installed over 0.8.1; Android keeps the old key as a past signature; Jump back in and Recently played still there |
| Android 8 still updates | The APK's v2 signature, which Android 8 checks, is the old key's | ✅ 1 Nov, apksigner: API 26–27 see the debug key (6927bcc6…), API 28 and later the new key with the lineage |
| Release build: main screens | Home, Retro, Hacks, PC, Media and Settings open; scan, search and a game page work | ✅ emulator, 1 Nov: no crash and no R8 errors in the log |
| Release build: built-in player | A game plays; pause menu, save state, Moves and turbo, Quit | ✅ emulator, 1 Nov: Nova the Squirrel (NES), state saved and the file written |
| Release build: downloads | Scrape all artwork, the hacks catalogue, Check for updates, a free game | ✅ emulator, 1 Nov: 1 cover added, catalogue listed, "You have the newest version", Nova downloaded into the library |
| Release build: network drives | Add a network drive (SMB), list a folder and copy a game | ⬜ needs a PC sharing a folder |
| Release build: netplay | Host a game on one device and join from another | ⬜ needs two devices |
| Release build: PC streaming | Pair with Sunshine or GeForce Experience and stream | ⬜ needs the PC set up |
| Release build: Switch, PS3, PS4 Players | Play opens each player and Quit comes back | ⬜ |
| **New since 0.8.1** | | |
| PS5 games in the library | A `ps5` folder of dumped games (eboot.bin + sce_sys/param.json) shows each under PlayStation 5 by its title, -patch folders skipped; Play says no PS5 emulator for phones and tablets exists yet | ✅ emulator, 1 Nov: a dummy folder (empty eboot.bin, made-up param.json) showed as "Forge Test Five"; without RetroArch installed it had asked for RetroArch, now it says there's no PS5 emulator yet |
| Upscaling: PS1 and N64 | PS1 (PCSX ReARMed) gets a Resolution step; N64 restarts to use a new one; each step goes up from the system's own size | 🟡 unit test passes; the upscaling session tested on the emulator, 31 Oct. Needs a PS1 or N64 game to see it |
| Free games easy to find | An empty library's Home shows "No games yet?"; the Retro tab has a Free games card; both open the list, and a game goes into the library | ✅ emulator, 1 Nov (Play build, fresh install): both cards open it; Nova downloaded into the chosen folder and was added to the library |
| Icon and splash | The launch splash and a round launcher icon show the whole RF sign | ✅ emulator, 1 Nov: whole sign with its glow on the splash and the home screen |
| Scrape all artwork on a big system | No long stall; a full NES set's libretro index is read in seconds | ✅ emulator, 1 Nov: what took over 10 minutes took 39 seconds |
| Retro tiles on a fresh install | Tiles start as game art + console and fetch the game art | ✅ emulator, 1 Nov |
| Mario Golf swings | Game Boy Color, Advance Tour and Nintendo 64: each power from full to a 5% tap lands on the impact line | 🟡 timed by the moves session on its devices; try one swing of each |
| Moves picked show it | In Moves and turbo, moves on screen are filled orange with a tick | 🟡 moves session |
| **Carried over from 0.8.1** | | |
| Pause menu music | Songs from My music: what's playing, Previous, Play/Pause and Next mid-game | 🟡 radio worked on the S22 (31 Oct); songs still to try |
| Moves: finishers | A Mortal Kombat II or UMK3 fatality, babality or friendship from the list, in a fight | 🟡 lists match in the unit test; not done in a game |
| Moves: cheat codes | Contra's Konami code gives 30 lives | 🟡 list matches in the unit test; not done in the game |
| Moves off in netplay | Moves can't be played in a netplay game, and the page says why | 🟡 code only |
