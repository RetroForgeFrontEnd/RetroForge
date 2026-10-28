# PC game compatibility

Last updated 2026-10-28. No PC game has been started *from RetroForge* yet; RetroForge links the PC apps. This table holds only what has actually been tested, in the PC app itself. Architecture: [retroforge-pc-architecture.md](retroforge-pc-architecture.md).

## Devices

| Device | GPU | DroidDeck | Other apps | Tested |
|---|---|---|---|---|
| Samsung Galaxy S22 (SM-S901B, Exynos 2200) | Xclipse 920 | Not supported (needs Adreno) | **GameNative 1.3.0: a Unity game reached its main menu** (below) | GPU detection; GameNative |
| Android emulator (x86_64) | SwiftShader | n/a | n/a | n/a |

## Games

| Game | Source | Runtime | Device | Result |
|---|---|---|---|---|
| TCG Card Shop Simulator (Unity, v1.00) | Your copy, folder copied over USB without its 8.5 GB BepInEx mods (4.6 GB) | GameNative 1.3.0, custom game (Skip login), bionic container, Proton 10.0 arm64ec, 1280x720 | S22 | **Boots to the main menu** (2026-10-28). Executable had to be chosen by hand (Edit container → Executable Path). First start downloaded Proton and set up components (~2 min). Sound, gameplay and controls not yet checked. GameNative's community data said "Runs" (5 sessions on this GPU family) |

Only games you own, installed by the runtime's supported methods. RetroForge doesn't bypass DRM, store sign-in or purchase checks, and doesn't distribute game files.
