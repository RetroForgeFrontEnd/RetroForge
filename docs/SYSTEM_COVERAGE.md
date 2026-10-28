# System coverage

Every system folder with games in the RetroBat collection (`G:\Retrobat\roms`, read-only, 2026-10-28), and how RetroForge plays it on Android.
Generated from the same tables the app uses: RetroBat's own core choices (`es_systems.cfg`) filtered to cores libretro builds for Android, and the free standalone emulators in `retro-library/src/main/resources/standalone.json` (launch commands adapted from ES-DE, MIT).

| Status | Systems |
|---|---|
| Launch tested | 3 |
| Should work (not tested) | 101 |
| No Android option | 17 |
| **Total** | **121** |

"Should work" means RetroForge has a launch route; it has not been tried with a real game on a device yet. Many need BIOS files (see BIOS checker) or the emulator to be installed.

| System | Files | Status | RetroArch cores (Android) | Free standalone emulators | Notes |
|---|---|---|---|---|---|
| DOS (`dos`) | 29819 | Should work (not tested) | DOSBox Pure | — |  |
| Playstation 3 (`ps3`) | 14637 | Should work (not tested) | — | aPS3e Directory, aPS3e ISO | aPS3e is early and needs a high-end device. |
| Commodore 64 (`c64`) | 2939 | Should work (not tested) | VICE x64, VICE x64sc, Frodo | — |  |
| MAME (`mame`) | 2849 | Should work (not tested) | Mame2016, Mame2010, Mame2003 Plus, Mame2003, Mame2003 Midway, Mame2000 | MAME4droid Current, Flycast |  |
| Super NES MSU-1 (`snes-msu1`) | 2257 | Should work (not tested) | Snes9X, Bsnes, Bsnes Hd Beta | Snes9x EX+ |  |
| Final Burn (`fbneo`) | 2237 | Should work (not tested) | FinalBurn Neo, Fbalpha2012, Fbalpha2012 Neogeo | — |  |
| Amstrad CPC (`amstradcpc`) | 1933 | Should work (not tested) | Caprice32, Crocods | MAME4droid Current |  |
| Atari ST (`atarist`) | 1581 | Should work (not tested) | Hatari, Hatarib | — |  |
| Super Nintendo Entertainment System (`snes`) | 1387 | Launch tested | Snes9X, Bsnes, Bsnes Hd Beta, Mednafen Snes, Snes9X2005 | Snes9x EX+ | Star Fox, Super Mario World (.7z), 3 Ninjas (auto save/load) |
| Game Boy Advance (`gba`) | 1188 | Should work (not tested) | Mgba, Mednafen Gba, Gpsp | NooDS, SkyEmu |  |
| Atari 800 (`atari800`) | 1100 | Should work (not tested) | Atari800 | — |  |
| Megadrive - Genesis (`megadrive`) | 1090 | Should work (not tested) | Genesis Plus GX, Genesis Plus Gx Wide, Picodrive, FinalBurn Neo | — |  |
| MSX (`msx1`) | 895 | Should work (not tested) | blueMSX, FinalBurn Neo | — |  |
| Nintendo Entertainment System - Famicom (`nes`) | 841 | Launch tested | Fceumm, Nestopia, Mesen | — | gameplay |
| Laserdisc (`daphne`) | 743 | Should work (not tested) | — | MAME4droid Current |  |
| Atari 2600 (`atari2600`) | 741 | Should work (not tested) | Stella, Stella2014 | — |  |
| Game Boy (`gb`) | 730 | Should work (not tested) | Gambatte, Bsnes, Tgbdual, Sameboy | SkyEmu |  |
| Nintendo Family Computer Disk System (`fds`) | 711 | Should work (not tested) | Fceumm, Nestopia, Mesen | — |  |
| Apple II (`apple2`) | 666 | Should work (not tested) | Applewin | MAME4droid Current |  |
| Game Boy Color (`gbc`) | 649 | Should work (not tested) | Gambatte, Tgbdual, Sameboy | SkyEmu |  |
| ScummVM (`scummvm`) | 607 | Should work (not tested) | ScummVM | — |  |
| VIC-20 (`c20`) | 542 | Should work (not tested) | VICE xvic | — |  |
| Quake II (`quake2`) | 498 | Should work (not tested) | Vitaquake2 | — |  |
| Master System - Mark III (`mastersystem`) | 424 | Should work (not tested) | Genesis Plus GX, Picodrive, Gearsystem, FinalBurn Neo | — |  |
| Cavestory (`cavestory`) | 414 | Should work (not tested) | Nxengine, Doukutsu Rs | — |  |
| Nintendo 64 (`n64`) | 359 | Should work (not tested) | Parallel N64 | M64Plus FZ, Mupen64Plus AE |  |
| Game Gear (`gamegear`) | 344 | Should work (not tested) | Genesis Plus GX, Picodrive, Gearsystem, FinalBurn Neo | — |  |
| Apple IIGS (`apple2gs`) | 300 | Should work (not tested) | — | MAME4droid Current, MAME4droid Current [Software list] |  |
| PC Engine (`pcengine`) | 289 | Should work (not tested) | Mednafen Pce, Mednafen Pce Fast, FinalBurn Neo | — |  |
| ColecoVision (`colecovision`) | 287 | Should work (not tested) | Gearcoleco, Jollycv, blueMSX, FinalBurn Neo | — |  |
| Sharp X68000 (`x68000`) | 245 | Should work (not tested) | PX68k | MAME4droid Current |  |
| MSX2 (`msx2`) | 235 | Should work (not tested) | blueMSX, FinalBurn Neo | — |  |
| Nintendo DS (`nds`) | 229 | Should work (not tested) | melonDS DS, Desmume, Desmume2015, Melonds, Noods | melonDS, NooDS, SkyEmu |  |
| Amiga AGA (`amiga1200`) | 207 | Should work (not tested) | PUAE (Amiga), Amiberry | — |  |
| Mega CD (`megacd`) | 180 | Should work (not tested) | Genesis Plus GX, Genesis Plus Gx Wide, Picodrive | — |  |
| Atari 7800 (`atari7800`) | 177 | Should work (not tested) | Prosystem | MAME4droid Current |  |
| Mattel Intellivision (`intellivision`) | 174 | Should work (not tested) | Freeintv | MAME4droid Current |  |
| Atari 5200 (`atari5200`) | 157 | Should work (not tested) | A5200, Atari800, Stella2014 | — |  |
| PC Engine CD (`pcenginecd`) | 148 | Should work (not tested) | Mednafen Pce, Mednafen Pce Fast, FinalBurn Neo | — |  |
| Dreamcast (`dreamcast`) | 147 | Should work (not tested) | Flycast | Flycast, Redream |  |
| Neo Geo (`neogeo`) | 141 | Should work (not tested) | FinalBurn Neo, Geolith, Fbalpha2012 Neogeo | MAME4droid Current |  |
| PlayStation (`psx`) | 139 | Launch tested | Beetle PSX HW, Pcsx Rearmed, SwanStation | DuckStation | Resident Evil 2 (2 discs), Batman Forever (RetroForge CHD), Command & Conquer (disc swap) |
| Naomi (`naomi`) | 137 | Should work (not tested) | Flycast | Flycast |  |
| WonderSwan (`wswan`) | 113 | Should work (not tested) | Mednafen Wswan | — |  |
| TRS-80 Color Computer (`coco`) | 113 | Should work (not tested) | — | MAME4droid Current [Cartridge], MAME4droid Current [Tape] |  |
| Sharp X1 (`x1`) | 103 | Should work (not tested) | X1 | MAME4droid Current [Diskette], MAME4droid Current [Tape] |  |
| MD-MSU (`megadrive-msu`) | 100 | Should work (not tested) | Genesis Plus GX, Genesis Plus Gx Wide | — |  |
| WonderSwan Color (`wswanc`) | 93 | Should work (not tested) | Mednafen Wswan | — |  |
| Satellaview (`satellaview`) | 92 | Should work (not tested) | Snes9X | Snes9x EX+ |  |
| Odyssey² - Videopac (`odyssey2`) | 92 | Should work (not tested) | O2Em | MAME4droid Current |  |
| Lynx (`lynx`) | 84 | Should work (not tested) | Mednafen Lynx, Handy, Holani | — |  |
| Java 2 MicroEdition (`j2me`) | 83 | Should work (not tested) | — | J2ME Loader |  |
| Saturn (`saturn`) | 83 | Should work (not tested) | Mednafen Saturn, Ymir, Yabasanshiro | Yaba Sanshiro 2 |  |
| PlayStation Portable (`psp`) | 72 | Should work (not tested) | PPSSPP | PPSSPP |  |
| Uzebox (`uzebox`) | 72 | Should work (not tested) | Uzem | — |  |
| SG-1000 (`sg1000`) | 69 | Should work (not tested) | Gearsystem, Genesis Plus GX | — |  |
| Watara SuperVision (`supervision`) | 69 | Should work (not tested) | Potator | MAME4droid Current |  |
| V.Smile (`vsmile`) | 67 | Should work (not tested) | — | MAME4droid Current |  |
| Jaguar CD (`jaguarcd`) | 60 | Should work (not tested) | Virtualjaguar | — |  |
| Jaguar (`jaguar`) | 57 | Should work (not tested) | Virtualjaguar | MAME4droid Current |  |
| WASM-4 (`wasm4`) | 51 | Should work (not tested) | Wasm4 | — |  |
| 3DO (`3do`) | 50 | Should work (not tested) | Opera (3DO) | — |  |
| ECWolf (`ecwolf`) | 50 | Should work (not tested) | Ecwolf | — |  |
| Astrocade (`astrocade`) | 46 | Should work (not tested) | — | MAME4droid Current |  |
| CPS-II (`cps2`) | 44 | Should work (not tested) | FinalBurn Neo, Fbalpha2012 Cps2, Fbalpha2012 | MAME4droid Current |  |
| GameCube (`gamecube`) | 43 | Should work (not tested) | Dolphin | Dolphin |  |
| Cannonball (`cannonball`) | 43 | Should work (not tested) | Cannonball | — |  |
| Neo Geo Pocket Color (`ngpc`) | 41 | Should work (not tested) | Mednafen Ngp, Race | — |  |
| BBC Micro (`bbcmicro`) | 41 | Should work (not tested) | B2 | MAME4droid Current |  |
| Playstation 2 (`ps2`) | 39 | Should work (not tested) | Pcsx2 | NetherSX2, AetherSX2, ARMSX2, Play! |  |
| CPS-I (`cps1`) | 37 | Should work (not tested) | FinalBurn Neo, Fbalpha2012 Cps1, Fbalpha2012 | MAME4droid Current |  |
| Quake (`quake`) | 36 | Should work (not tested) | Tyrquake | — |  |
| Nintendo 3DS (`3ds`) | 35 | Should work (not tested) | — | Azahar |  |
| Model 2 (`model2`) | 34 | Should work (not tested) | — | MAME4droid Current | MAME4droid's Model 2 support is limited. |
| 32X (`sega32x`) | 33 | Should work (not tested) | Picodrive | — |  |
| LCD Games (`lcdgames`) | 33 | Should work (not tested) | Gw | MAME4droid Current Local Artwork, MAME4droid Current |  |
| Fairchild Channel F (`channelf`) | 30 | Should work (not tested) | Freechaf | MAME4droid Current |  |
| Model 3 (`model3`) | 28 | Should work (not tested) | Supermodel | MAME4droid Current |  |
| Super Cassette Vision (`scv`) | 27 | Should work (not tested) | Emuscv | MAME4droid Current |  |
| Atomiswave (`atomiswave`) | 26 | Should work (not tested) | Flycast | Flycast |  |
| Virtual Boy (`virtualboy`) | 26 | Should work (not tested) | Mednafen Vb | — |  |
| Amstrad GX4000 (`gx4000`) | 26 | Should work (not tested) | Caprice32 | MAME4droid Current |  |
| Vectrex (`vectrex`) | 26 | Should work (not tested) | Vecx | MAME4droid Current |  |
| ST-V (`segastv`) | 25 | Should work (not tested) | Mame2016, Mame2010, Mame2003 Plus, Mame2003 | MAME4droid Current |  |
| Switch (`switch`) | 25 | Should work (not tested) | — | Eden | Eden needs your own console keys and firmware; RetroForge provides none. |
| MSX turbo R (`msxturbor`) | 25 | Should work (not tested) | blueMSX, FinalBurn Neo | — |  |
| Commodore 128 (`c128`) | 24 | Should work (not tested) | VICE x128 | — |  |
| ADAM (`adam`) | 23 | Should work (not tested) | Gearcoleco | MAME4droid Current [Diskette], MAME4droid Current [Tape], MAME4droid Current [Cartridge], MAME4droid Current [Software list] |  |
| Game.com (`gamecom`) | 20 | Should work (not tested) | — | MAME4droid Current |  |
| Openbor (`openbor`) | 19 | Should work (not tested) | — | OpenBOR |  |
| MSX2+ (`msx2+`) | 17 | Should work (not tested) | blueMSX, FinalBurn Neo | — |  |
| PC-FX (`pcfx`) | 16 | Should work (not tested) | Mednafen Pcfx | — |  |
| Pokemon-Mini (`pokemini`) | 14 | Should work (not tested) | Pokemini | — |  |
| Naomi 2 (`naomi2`) | 12 | Should work (not tested) | Flycast | Flycast |  |
| Nintendo 64 Disk Drive (`n64dd`) | 10 | Should work (not tested) | Parallel N64 | M64Plus FZ, Mupen64Plus AE |  |
| Neo Geo Pocket (`ngp`) | 10 | Should work (not tested) | Mednafen Ngp, Race | — |  |
| CPS-III (`cps3`) | 9 | Should work (not tested) | FinalBurn Neo, Fbalpha2012 Cps3, Fbalpha2012 | MAME4droid Current |  |
| Wii (`wii`) | 9 | Should work (not tested) | Dolphin | Dolphin |  |
| Philips CD-i (`cdi`) | 9 | Should work (not tested) | Same Cdi | — |  |
| SuperGrafx (`supergrafx`) | 8 | Should work (not tested) | Mednafen Supergrafx, Geargrafx, FinalBurn Neo | — |  |
| Hyper Neo Geo 64 (`neogeo64`) | 8 | Should work (not tested) | Mame2016 | — |  |
| SuFami Turbo (`sufami`) | 7 | Should work (not tested) | Snes9X, Bsnes | Snes9x EX+ |  |
| Multivision (`multivision`) | 5 | Should work (not tested) | Gearsystem | — |  |
| Triforce (`triforce`) | 3 | Should work (not tested) | — | Dolphin | Dolphin's Triforce support is limited. |
| TeknoParrot (`teknoparrot`) | 43799 | No Android option | — | — | Windows-only arcade loader; no Android equivalent. |
| MUGEN (`mugen`) | 10104 | No Android option | — | — | Windows engine; no Android port. |
| OpenJazz (`openjazz`) | 588 | No Android option | — | — | PC game port: RetroBat runs a Windows build. Android ports of some exist as their own apps, but RetroForge can't launch them yet. |
| Visual Pinball (`vpinball`) | 493 | No Android option | — | — | Visual Pinball has an Android build that ES-DE launches with injected files; not supported by RetroForge yet. |
| Future Pinball (`fpinball`) | 133 | No Android option | — | — | Future Pinball is Windows-only. |
| Ports (`ports`) | 117 | No Android option | — | — | PC game port: RetroBat runs a Windows build. Android ports of some exist as their own apps, but RetroForge can't launch them yet. |
| Singe (`singe`) | 37 | No Android option | — | — | Hypseus Singe (laserdisc games) has no Android build. |
| Hikaru (`hikaru`) | 12 | No Android option | — | — | Only DEMUL (Windows) emulates Hikaru. |
| Xbox (`xbox`) | 5 | No Android option | — | — | No free, working Android emulator. |
| Half-Life (`halflife`) | 5 | No Android option | — | — | PC game port: RetroBat runs a Windows build. Android ports of some exist as their own apps, but RetroForge can't launch them yet. |
| Duke Nukem 3D (`eduke32`) | 4 | No Android option | — | — | PC game port: RetroBat runs a Windows build. Android ports of some exist as their own apps, but RetroForge can't launch them yet. |
| Sonic Retro (`sonicretro`) | 3 | No Android option | — | — | PC game port: RetroBat runs a Windows build. Android ports of some exist as their own apps, but RetroForge can't launch them yet. |
| Windows (`Windows`) | 2 | No Android option | — | — | PC games: RetroForge's PC section (DroidDeck), or Winlator/GameNative outside RetroForge. |
| RealRTCW (`rtcw`) | 1 | No Android option | — | — | PC game port: RetroBat runs a Windows build. Android ports of some exist as their own apps, but RetroForge can't launch them yet. |
| Steam (`steam`) | 1 | No Android option | — | — | PC games: RetroForge's PC section (DroidDeck). |
| Perfect Dark (`pdark`) | 1 | No Android option | — | — | PC game port: RetroBat runs a Windows build. Android ports of some exist as their own apps, but RetroForge can't launch them yet. |
| Sonic Mania (`sonic-mania`) | 1 | No Android option | — | — | PC game port: RetroBat runs a Windows build. Android ports of some exist as their own apps, but RetroForge can't launch them yet. |

File counts include every non-media file, so multi-file games (cue/bin, game folders) count more than once.
