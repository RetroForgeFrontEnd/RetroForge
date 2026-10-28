# RetroForge PC: architecture

Last updated 2026-10-28. Decision background: [droiddeck-architecture-audit.md](droiddeck-architecture-audit.md), [pc-runtime-proof-of-concept.md](pc-runtime-proof-of-concept.md), licences: [pc-runtime-licences.md](pc-runtime-licences.md).

## Approach (approved 2026-10-27)

RetroForge does **not** contain a PC runtime. PC games run in a separate, separately installed app, which RetroForge detects and opens. This keeps GPL-3.0 runtime code, its very large native stack (proot, FEX, Wine/Proton, Mesa Turnip) and its update cycle out of RetroForge, and keeps RetroForge's own licence open.

## The `pc` module

All PC code lives in the `pc` module, separate from the retro side:

| File | What it does |
|---|---|
| `GpuProbe.kt` | Reads the GPU (OpenGL renderer string, Adreno model from sysfs where readable, SoC model) |
| `GpuSupport.kt` | Classifies the GPU against **DroidDeck's** published requirements: Adreno 730+ / 8xx supported, 6xx experimental, others not supported by DroidDeck |
| `DroidDeckBridge.kt` | Detects DroidDeck (`com.droiddeck.launcher`), its version, whether it accepts the `LAUNCH_GAME` intent, and opens it |
| `PcStatus.kt` | Combines the above for the PC screen |

The app's PC screen ([PcScreen.kt](../app/src/main/java/com/retroforge/app/ui/pc/PcScreen.kt)) shows the device's GPU, DroidDeck's status (install from its official releases page, or open it), and, on GPUs DroidDeck doesn't support, links to the official releases of **GameNative** and **Winlator**, described as separate apps RetroForge doesn't start or test yet.

## What works and what doesn't

| Item | Status |
|---|---|
| GPU detection and classification | Tested on the S22 (Xclipse 920 correctly reported as not supported by DroidDeck) |
| DroidDeck detection / open | Built; not tested with DroidDeck installed (no Adreno device available) |
| Starting a specific PC game from RetroForge | **Not built.** DroidDeck's `LAUNCH_GAME` intent exists, but it's untested on real hardware, so there's no launch button |
| GameNative / Winlator | Links only. GameNative is GPL-3.0 and plays Steam, Epic, GOG and Amazon games you own. Its README states no GPU requirements; its release notes, seen only via mirrors, mention Mali and Exynos. Winlator's official project doesn't state Mali/Xclipse support; that's in community forks. Neither documents a launch intent for other apps |
| PC games in the unified library | Not built |

## Next steps (need hardware or your test files)

1. On an Adreno 730+ device: install DroidDeck, verify detection, then test `LAUNCH_GAME` with a game you own, and only then add Play for PC games.
2. On the S22 (Xclipse): install GameNative from its official release and test a Steam game you own, to learn what really runs before RetroForge says anything more.
3. Non-Steam games (e.g. the TCG Card Shop Simulator folder you mentioned): Winlator and GameNative's custom-game support are the candidates; test once the files are on the device.
