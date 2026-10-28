# RetroBat → Android parity matrix

For each kind of system in RetroBat, how it plays on Android through RetroForge. Updated 2026-10-28. Emulator-by-system detail: [EMULATOR_COMPATIBILITY.md](EMULATOR_COMPATIBILITY.md) and [SYSTEM_COVERAGE.md](SYSTEM_COVERAGE.md); test evidence: [TESTING.md](TESTING.md).

| Group | RetroBat (Windows) | RetroForge built-in player | Through other apps | Tested |
|---|---|---|---|---|
| Nintendo 8/16-bit, handhelds (NES, SNES, GB/GBC/GBA, VB-less) | RetroArch cores | ✅ fceumm/nestopia/mesen, snes9x, gambatte/sameboy/mgba, mgba/gpsp | RetroArch, standalone | ✅ emulator; GBA on S22 |
| Nintendo DS | melonDS / DeSmuME | ✅ desmume, melonds (touch, side-by-side) | melonDS app, DraStic | ✅ S22 |
| N64, GameCube/Wii, 3DS, Switch, Wii U | standalones | ❌ (N64 needs GPU core work) | RetroArch, Dolphin, Azahar, Eden-type apps, Cemu | 🟡 RetroArch N64 path only |
| Sega 8/16-bit, 32X, Mega CD | genesis_plus_gx, picodrive | ✅ | RetroArch | ✅ emulator |
| Saturn, Dreamcast, Naomi/Atomiswave | Beetle Saturn/Kronos, Flycast | ❌ (GPU core work) | RetroArch, Yaba Sanshiro, Flycast app, Redream | 🟡 |
| NEC PC Engine / CD / SuperGrafx | beetle pce | ✅ pce_fast, supergrafx | RetroArch | ✅ emulator |
| SNK Neo Geo, NGP | fbneo / neogeo, mednafen ngp | ✅ fbneo, ngp | RetroArch | ✅ emulator |
| Arcade (FBNeo, MAME, CPS1–3) | many MAME versions | ✅ fbneo, mame2003_plus | RetroArch, MAME4droid | ✅ emulator |
| Sony PS1 | DuckStation / SwanStation | ✅ pcsx_rearmed, swanstation (sticks, multi-disc) | DuckStation, RetroArch | ✅ emulator |
| PS2, PSP, PS Vita, PS3 | PCSX2, PPSSPP, Vita3K, RPCS3 | ❌ PSP planned | NetherSX2/AetherSX2-type, PPSSPP, Vita3K | 🟡 PPSSPP launch |
| Atari 2600/7800/Lynx, WonderSwan | stella, prosystem, handy, beetle wswan | ✅ stella, handy, wswan | RetroArch | ✅ emulator |
| Computers (Amiga, C64, MSX, DOS, ZX …) | many | ❌ | RetroArch cores, standalone apps | ❌ |
| Ports (Doom, Quake, SoH …) | native ports | ❌ | some RetroArch cores, own Android ports | ❌ (PC ports section idea) |
| Windows games | native | — | GameNative, Winlator, DroidDeck | ✅ GameNative on S22 |

What Android can't match: Windows-only emulators, systems needing desktop-class GPUs (PS3, Xbox 360, Switch on weak phones), lightguns, and some arcade setups (Model 2/3, Triforce). These are shown as "needs another app" or unsupported in the app, never as playable.
