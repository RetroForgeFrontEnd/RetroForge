# PS5 games in RetroForge: research (2026-11-01)

Research only. Nothing has been built. The question: could RetroForge play PS5 games the way it plays PS3 games
(RetroForge PS3 Player, on RPCS3) and PS4 games (RetroForge PS4 Player, on shadPS4 + FEXCore, `F:\PS4Player`)?

## Short answer

PS5 emulation went from nothing to "real games start" in summer 2026, but only on PC, and only a few small games
play from start to finish. There is one experimental Android project, and it has shown one small 2D game reaching
its menu on a top-end Snapdragon 8 Elite-class handheld. On the Galaxy Tab A9+ and the Galaxy S22, PS5 games are not
realistic today. **Recommendation: wait on a full player; do the small step now** (recognise PS5 game folders in the
library and say no player exists yet), and look again in about three months.

## The projects

| Project | Licence | What it is | Games | Android / ARM64 |
|---|---|---|---|---|
| **[KytyPS5](https://github.com/KytyPS5/KytyPS5)** | GPL-2.0-only (based on [Kyty](https://github.com/InoriRus/Kyty), MIT; that notice is kept) | C++, Clang/CMake. Runs the game's x86-64 code directly on an x86-64 CPU; Vulkan 1.3 renderer that recompiles RDNA 2 shaders to SPIR-V. Windows and Linux, macOS experimental (x86-64 under Rosetta 2). ~10k stars, source public since July 2026. | The most advanced. Its own compatibility list (dated 2026-10-28) says 209 of 379 tested games reach "in game", 58 menu, 63 logo, 49 don't boot. "In game" is not "playable": reports name a handful of genuinely playable titles (e.g. XIII remake up to 30 fps with glitches, Astro's Playroom), on desktop PCs. | None. No ARM build; it needs an x86-64 CPU. Porting would mean the same FEXCore trick the PS4 Player uses, done from scratch. |
| **[SharpEmu](https://github.com/sharpemu/sharpemu)** | GPL-2.0 | Written from scratch in C# (.NET). Runs game x86-64 code natively on x86-64; Vulkan output. Windows x64 and Linux x64 (macOS in a separate repo). ~6.5k stars, public since July 2026. Also has an experimental PS4 mode. | Lists commercial games as "tested" (Demon's Souls, Astro Bot, Gran Turismo 7 and others), mostly boot-to-menu or partial. Reports of about six small games playable at 60 fps on PC by late September. | Through **sharpdroid** (below). |
| **[sharpdroid](https://github.com/mircowuffwuff/sharpdroid)** (fork: [mircowuffwuff/sharpemu](https://github.com/mircowuffwuff/sharpemu)) | GPL-2.0(-or-later) | An Android app that runs SharpEmu's guest code through **FEXCore** (x86-64 → ARM64), with its own host layer for ELF loading, system calls, Vulkan and AAudio. arm64-v8a, Android 9+, Vulkan GPU. One developer, ~60 stars. | One game reported: *Dreaming Sarah* (small 2D indie) reached its main menu with picture and sound, on an Adreno 830 handheld (Snapdragon 8 Elite class). No speed numbers, no compatibility list. APK offered from the author's own site, not GitHub releases. | **Yes, experimental.** This is the only PS5-on-Android path found. |
| **[RPCSX](https://github.com/RPCSX/rpcsx)** | GPLv2 (some parts MIT) | From the RPCS3 team's orbit; targets PS4 and PS5. Boots the PS5 system menu and safe mode with sound and input. | Commercial games generally don't start yet. | No official Android build; one tiny community UI project. |
| shadPS5, AnyPS5 | unclear | shadPS5: status unclear, may be a name only. AnyPS5 converts PS5 programs into native PC programs rather than emulating. | Unverified. | No. |

Beware: there are fake "PS5 emulator" downloads (for example "PCSX5": no source, asks for money). Only the GitHub
projects above have real source code.

Licences: KytyPS5, SharpEmu, sharpdroid and RPCSX are all GPL-2.0, the same family as shadPS4, so a separate
GPL player app (like the PS4 Player) would be allowed, with full source published. RetroForge itself only launches
the player by intent, as with PS3 and PS4.

## How fast could it be on our devices?

The best yardstick is our own PS4 Player (`F:\PS4Player\PLAN.md`):

- **Galaxy Tab A9+ (Snapdragon 695):** Sonic Mania, a light 2D PS4 game, runs at about **40% speed**. On a
  Snapdragon 8 Elite phone, heavy PS4 games reach roughly 20–30 fps at best.
- PS5 games are written for much stronger hardware than PS4 games (an 8-core Zen 2 CPU at ~3.5 GHz and an RDNA 2
  GPU, roughly 5–6× a base PS4 on paper). The emulators are also months old, so they are less tuned than shadPS4.

Realistic guesses:

| Device | Big 3D PS5 games | Small 2D / indie PS5 games |
|---|---|---|
| Galaxy Tab A9+ (Snapdragon 695, Adreno 619) | No. | Very unlikely to be playable; it can't run a light PS4 game at full speed. |
| Galaxy S22 (Exynos 2200, Xclipse 920) | No. | Maybe boots, probably slow. The Xclipse GPU has no Turnip-style driver fallback, which already limits the PS4 Player (Adreno only). |
| Snapdragon 8 Elite-class (for comparison) | No, not yet. | The only place anyone has shown anything (one game, to the menu). |

## Options

1. **Full RetroForge PS5 Player now** (separate game-only app on sharpdroid or a FEXCore port of KytyPS5, launched
   through `standalone.json` like the PS4 Player). Effort: weeks to months; the PS4 Player took about that on a much
   more mature base. Result on the user's devices: almost certainly nothing playable. **Not recommended now.**
2. **Small step now: recognise PS5 games in the library.** PS5 game dumps are folders with an `eboot.bin` and a
   `sce_sys/param.json` (title IDs look like `PPSA01234`). The scanner already does this for PS4 folders
   (`LibraryScanner.kt`, `hasEboot`, title from `param.sfo`); PS5 would read the title from `param.json` instead
   (field names to be checked on a real dump). With no player entry for `ps5` in `standalone.json`, RetroForge shows
   the games and says no PS5 player exists yet. Effort: about a day, plus translations. **Recommended.**
3. **Wait and watch.** Re-check in about three months (around February 2027): has sharpdroid (or a KytyPS5 ARM
   port) published speed numbers, a compatibility list and GitHub releases, and does anything run on an Adreno 6xx/7xx
   or Xclipse GPU? If yes, start option 1 on that base.

## Recommendation

Option 2 now, option 3 after. Don't build a PS5 player yet: on the Tab A9+ and S22 it would not play games, and
the only Android base is one person's experiment a few weeks old.

## Sources

- [KytyPS5 repository](https://github.com/KytyPS5/KytyPS5) and [project page with compatibility counts](https://kytyps5.github.io/)
- [SharpEmu repository](https://github.com/sharpemu/sharpemu)
- [sharpdroid repository](https://github.com/mircowuffwuff/sharpdroid)
- [RPCSX repository](https://github.com/RPCSX/rpcsx)
- [Tom's Hardware: PS5 titles booting across emulators](https://www.tomshardware.com/video-games/playstation/ps5-emulation-ramps-up-in-wake-of-sonys-end-to-physical-media-ps5-titles-now-booting-across-different-emulators-with-rapid-community-development-for-both-2d-and-3d-games)
- [Tom's Hardware: 3D games playable in Kyty, 30 fps](https://www.tomshardware.com/video-games/playstation/ps5-emulation-hits-new-milestone-in-record-time-multiple-3d-games-now-playable-in-kyty-performance-and-support-steadily-improving-with-30-fps-gameplay-possible-today)
- [Notebookcheck: Astro's Playroom on KytyPS5](https://www.notebookcheck.net/KytyPS5-PS5-emulator-reportedly-adds-another-PS5-game-to-its-playable-roster.1400747.0.html)
- [FpsGen: what works and the fakes to avoid](https://fpsgen.com/guides/ps5-emulator-pc-2026)
- Our own PS4 Player plan and speed results: `F:\PS4Player\PLAN.md`

Caveats: figures for SharpEmu's playable games come from secondary sites, not the project; sharpdroid's state is from
its README only, not a test. No emulator was run for this report.
