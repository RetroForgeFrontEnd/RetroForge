# 0.8.0 beta test list

Nothing goes to GitHub until every row is ✅ (or the user agrees to leave it out). Device and date with each result.

| Feature | What to check | Status |
|---|---|---|
| Update over 0.7.1 | Installs over 0.7.1 without uninstalling; games, saves and settings kept | ✅ S22, 31 Oct |
| RetroAchievements | Sign in (the user types the password), an unlock pop-up, the list in the pause menu, hardcore; a CHD disc game | 🟡 S22, 31 Oct: signed in; Metroid Fusion (25) and Emerald (197) load with every achievement live after the memory-map fix; list in the pause menu with badge pictures; the game page shows "0 of 25 earned · 0 of 353 points" for Metroid after one play. Still to see: an unlock pop-up, and hardcore (RetroAchievements shows "Unknown Emulator" until they approve RetroForge) |
| Save states | Make, load, overwrite, delete in the pause menu; Play from here, Rename, Delete on the game page | ✅ S22, 31 Oct: made, loaded (went back), renamed, played from, deleted. Fixed: the page didn't show the resume point just written on quitting |
| Pass the controller | Player 1's controller plays as player 2 | 📋 needs a controller |
| Sound buffer | Crackle-free sound at each setting | 🟡 S22: every setting cycles and the sound keeps playing (checked at 128 ms); listening for crackle needs the user |
| Frame skip | Each setting runs | ✅ S22, 31 Oct: Off, 1, 2, 3; played at 2 |
| Input profiles | Layout for the system, one controller, one game; plugging in a controller brings its layout | 🟡 emulator; a real controller untested |
| Controller test | Buttons, sticks, triggers show; hold B leaves | 📋 needs a controller |
| Link cable (GB/GBC) | A Pokémon trade between two devices on the Wi-Fi | 📋 needs two devices |
| ROM Manager: identify and rename | No-Intro and Redump matches on RetroBatTest; rename and undo | 🟡 emulator |
| ROM Manager: header fixer | Fix and undo; originals kept | 🟡 emulator |
| ROM Manager: RVZ, Z3DS, GDI to CHD | Compressed games play in Dolphin, Azahar, the built-in player | 🟡 checked with the emulators' own tools |
| ROM hacks labelled | "(patched)" files show as hacks; "This is a ROM hack" / "Not a ROM hack" on a game page; Identify's Short names drop (Rev 1) | 🟡 S22, 31 Oct: patched FireRed moved to the GBA hacks tab after the update; Advance Wars Alpha marked, unmarked and marked again (left marked). Short names: unit tests pass; needs the No-Intro lists on a device |
| Soft patching | A patch beside a game plays patched; the game page shows it | 🟡 emulator |
| Backlog | Set a status on a game page; it shows in Backlog with a suggestion | ✅ S22, 31 Oct: Playing set on Advance Wars, listed with a "Carry on" suggestion, taken out again |
| Playtime | Charts per game, system, week | ✅ S22, 31 Oct (real playtime shown) |
| Notes and guides | Write notes on the page and mid-game; a guide picture and a PDF open | ✅ S22 and emulator, 31 Oct: notes both ways; a guide picture scrolls and a two-page PDF opens (the Close manual button covered the first line; fixed in 25fe940) |
| Files | Copy, cut, paste, rename, new folder, delete, share, open with | ✅ S22, 31 Oct, in a test folder made and deleted in Roms; Share and Open with show Android's chooser |
| Save importer | A save from another emulator plays in the built-in player | 🟡 emulator, 31 Oct: PS1 .mcs, N64 .eep and GB .sav imports written correctly; playing one in a real game that keeps saves still to do |
| Save swap | Two devices pair with the code and swap a newer save | 📋 needs two devices |
| Tournaments | All four formats to a champion | ✅ emulator, 31 Oct; S22 screen opens |
| Kiosk mode | PIN set; Settings, ROM Manager, delete ask for it; wrong tries wait | ✅ S22, 31 Oct: PIN set, Settings locked after leaving, 5 wrong tries wait 30 s, turned off. Fixed: a page open when the PIN was set stayed open after leaving (fix checked on the S22) |
| Smart collections | Retro → Smart collections: backlog, recent, most played, never played, genre, decade, developer, players; each opens with its games | ✅ emulator-5556 (made-up gamelist details) and S22, 31 Oct: real play history gives Played in the last 30 days (6), Played most (4), Never played (22); no genre/decade ones on the S22 as its games have no gamelist details |
| Gaming passport | Retro → Gaming passport: total time, systems, finished and playing, milestones, bars, year by year | ✅ emulator-5556 and S22, 31 Oct: 32 min, 7 games, 4 systems, milestones reached and next shown |
| Retro news | Feeds load; filters; a story opens | ✅ emulator, 31 Oct: stories load; News, Modding, Emulators and All filters right; a story opens in the browser |
| CitraVR on Quest | A 3DS game opens in CitraVR | 📋 needs a Quest |
| Colours | Cyan and orange in the pause menu, tabs orange to cyan | ✅ emulator, 31 Oct: pause menu cyan ✕, orange focus ring, D-pad moves; tabs orange to cyan |
| Pause menu | Condensed menu; Back once from each sub-menu; Title Case headings | 🔲 |
| HD texture packs | Add a pack from a game page (N64, PSP, Dreamcast, GameCube/Wii); it's used in play | 🔲 |
| Free up memory | Settings: shows space used and clears what it says | 🔲 |
| PS3 games | A PS3 game opens in the RetroForge PS3 Player; DLC added from the game page | 🟡 S22, 31 Oct (PS3 chat): Ultimate Marvel vs. Capcom 3 boots and logs in to RPCN |
| PS3 Online (RPCN) | Settings → Online and friends: sign in, the status shows, sign out | 🟡 S22, 31 Oct (PS3 chat): signed in as the user, game logs in; a match needs a second player |
| PS4 games | Dumped game folders found (title from param.sfo); open in the PS4 Player; DLC folders added | 🔲 |
| Switch Play Online | Host a room; another device on the Wi-Fi finds and joins it; up to 4 | 🟡 tablet, 31 Oct, Switch player 0.8.0: hosting 3D World works (game copied from the PC, room on port 24872, answers "who's hosting?" with the game, pause menu Online room shows SM-X210). Joining from the S22 still to do |
| Retro netplay, 4 players | Host and join on the Wi-Fi; Android 17 local network permission asked | 📋 needs two devices |
| Settings layout | Bold section headings; Playing split into Saves, Online and Controls | ✅ emulator, 31 Oct |
| Player icons | PS3, PS4 and Switch players show RetroForge's neon icons | 🟡 built; on devices after each player's next install |
| Music | Phone songs and a chosen folder; albums with tag art; Play all, Shuffle, See more; Scan again | ✅ emulator, 31 Oct: 3 phone songs play; See more and Scan again shown |
| Music from a network drive | Find on my network, pick the share, songs play | ✅ emulator, 31 Oct: PC share Retrobat, 12 songs from library\Music play |
| Now Playing | Visuals move; round Previous / Play / Next; Shuffle, Repeat, EQ; playing in the background | ✅ emulator, 31 Oct: visuals, EQ presets and bands, Next changes song |
| Radio | Stations near you, search, favourites, See more | ✅ emulator, 31 Oct: list loads with logos, a station plays, See more adds 12 |
| Film player | Game-menu style controls; seek, subtitles, EQ; 3D view modes | 🟡 emulator, 31 Oct: controls in game-menu colours, big Play, EQ presets and bands (a small screen-recorded clip; the emulator cannot decode a full-size one). A real film on the S22 still to do |
| Network drives kept apart | Games, Films and Music cards each list only their own drives | 🟡 emulator: Music card shows only its drive |
| Music during a game | Your music keeps playing in the built-in player; game volume from the pause menu | ✅ emulator, 31 Oct: music and Sonic's sound play together |
