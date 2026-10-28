# RetroBat and ES-DE folder names

RetroForge recognises a system folder by its RetroBat name (the system id) or by any alias. Ids come from RetroBat 8.3.1.1's `es_systems.cfg`; aliases cover ES-DE's folder names and common short names. The table lives in [systems.json](../retro-library/src/main/resources/systems.json) and is matched case-insensitively. Updated 2026-10-28.

Systems whose names match in both (most of them: `snes`, `nes`, `psx`, `ps2`, `psp`, `n64`, `nds`, `gba`, `gb`, `gbc`, `megadrive`, `mastersystem`, `gamegear`, `dreamcast`, `saturn`, `pcengine`, `neogeo`, `fbneo`, `mame`, `cps1`–`3`, `naomi`, `atomiswave`, `wii`, `wiiu`, `switch`, `3do`, `scummvm` …) need no alias.

## Names that differ

| RetroBat folder (id) | ES-DE / other names accepted |
|---|---|
| `3ds` | `n3ds` |
| `amiga500` | `amiga` |
| `amigacd32` | `cd32` |
| `archimedes` | `acornarchimedes` |
| `atari2600` | `2600` |
| `atom` | `acornatom` |
| `c20` | `vic20` |
| `cdi` | `cdimono1` |
| `colecovision` | `coleco` |
| `cplus4` | `plus4` |
| `dos` | `pc` |
| `electron` | `acornelectron` |
| `fbneo` | `finalburn` |
| `gamecube` | `gc` |
| `gamegear` | `gg` |
| `gw` | `gameandwatch` |
| `jaguar` / `jaguarcd` | `atarijaguar` / `atarijaguarcd` |
| `lynx` | `atarilynx` |
| `mame` | `arcade` |
| `mastersystem` | `sms` |
| `megacd` | `segacd`, `megacdjp` |
| `megadrive` | `genesis`, `megadrivejp` |
| `msx1` | `msx` |
| `n64` | `nintendo64` |
| `nds` | `ds` |
| `neogeocd` | `neogeocdjp` |
| `nes` | `famicom` |
| `ngp` / `ngpc` | `neogeopocket` / `neogeopocketcolor` |
| `odyssey2` | `videopac` |
| `oricatmos` | `oric` |
| `pcengine` | `tg16`, `turbografx16` |
| `pcenginecd` | `tg-cd`, `turbografxcd` |
| `ps2` | `playstation2` |
| `psx` | `ps1`, `playstation` |
| `saturn` | `saturnjp` |
| `sega32x` | `32x`, `mega32x` |
| `segastv` | `stv` |
| `sg1000` | `sg-1000` |
| `snes` | `sfc`, `snesna` |
| `wswan` / `wswanc` | `wonderswan` / `wonderswancolor` |
| `xegs` | `atarixe` |
| `zxspectrum` | `spectrum` |

## Notes

- ES-DE's `arcade` folder is a general MAME set, so it maps to `mame`. Put FinalBurn Neo sets in `fbneo` to get that emulator by default.
- `pc` maps to DOS games (ES-DE's meaning), not Windows games.
- ES-DE folders with no RetroBat equivalent (`android`, `steam`, `ports`, `emulators` …) are not scanned as retro systems.
- Media folders inside system folders (`images`, `media`, `downloaded_media`, `snap`, `wheel` …) are skipped as games ([ROM_PORTABILITY.md](ROM_PORTABILITY.md)).
