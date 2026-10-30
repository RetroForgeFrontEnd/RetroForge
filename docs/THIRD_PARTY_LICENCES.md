# Third-Party Licences (components shipped in the RetroForge APK)

Licences below are the standard licences of these projects; versions are pinned in `gradle/libs.versions.toml`. Confirm against each artifact's POM before a public release.

| Component | Version | Licence |
|---|---|---|
| AndroidX Core KTX | 1.19.1 | Apache-2.0 |
| AndroidX Activity Compose | 1.13.0 | Apache-2.0 |
| AndroidX Navigation Compose | 2.10.2 | Apache-2.0 |
| AndroidX Lifecycle (runtime/viewmodel compose) | 2.11.0 | Apache-2.0 |
| AndroidX DataStore Preferences | 1.3.0 | Apache-2.0 |
| Jetpack Compose (BOM) | 2026.09.00 | Apache-2.0 |
| Kotlin standard library | 2.4.21 | Apache-2.0 |
| kotlinx.coroutines | 1.11.0 | Apache-2.0 |
| Apache Commons Compress (7z reading) | 1.28.0 | Apache-2.0 |
| kotlinx.serialization (JSON) | 1.12.0 | Apache-2.0 |
| XZ for Java (LZMA for 7z) | 1.12 | 0BSD / public domain (verify the exact statement in the artifact before release) |
| zstd-jni (zstd for the PC runtime's .tzst/.wcp files) | 1.5.7-5 | BSD-2-Clause (bundles zstd, BSD) |
| AndroidX Collection, Annotation | 1.5.0, 1.9.1 | Apache-2.0 |
| Winlator Cmod X server, renderer and natives (`pc-player/src/main/java/com/winlator/cmod`, `pc-player/src/main/cpp/winlator`), from github.com/coffincolors/winlator branch `cmod_bionic` @ 49bfb90 | 2025-08 | MIT, Copyright (c) 2023 BrunoSX. The MIT notice must be included with the app |
| Moonlight for Android (PC streaming, `pc-stream` module, source included) | b48494c (2026-09-12) | GPL-3.0 |
| moonlight-common-c, with ENet and nanors (PC streaming core) | 874ac95 | GPL-3.0; ENet MIT; nanors MIT |
| OpenSSL libcrypto, libopus (Moonlight's prebuilt static libraries) | 4.0.2; Moonlight's build | Apache-2.0; BSD-3-Clause |
| Bouncy Castle bcprov/bcpkix, OkHttp, jmDNS, JCodec (PC streaming) | 1.86; 5.5.1; 3.6.4; 0.2.5 | MIT; Apache-2.0; Apache-2.0; BSD-2-Clause |

| AndroidX WebKit | 1.14.0 | Apache-2.0 |
| Better xCloud userscript (redphx, app/src/main/assets/xcloud) | 6.7.12 | MIT (text in core-ui assets/licences/better-xcloud-MIT.txt) |
| Press Start 2P, Orbitron, Rajdhani, Exo 2 fonts (google/fonts) | 2026-10 | SIL Open Font License 1.1 (texts in core-ui assets/licences) |

Test-only (not shipped): JUnit 4.13.2 (EPL-1.0), kotlinx-coroutines-test (Apache-2.0).

Downloaded on first use of the PC player, not in the APK (from GameNative's server, see [pc-player-plan.md](pc-player-plan.md)): Proton 11 / Wine for Android arm64ec (LGPL-2.1-or-later, Proton components their own licences), FEX-Emu DLLs (MIT), a Termux-based Linux userland for Android (each package its own licence), Mesa's Vulkan wrapper driver (MIT), libadrenotools (BSD-2-Clause). Their source must be pointed to from the app before a public release.
PC streaming details: [pc-streaming.md](pc-streaming.md).

Not shipped, interacted with as separate apps: DroidDeck (GPL-3.0) — see [pc-runtime-licences.md](pc-runtime-licences.md).

Artwork: icons, TV banner and wordmark are original to this project. The RetroForge key art (app/src/main/res/drawable-nodpi/key_art.jpg, the Forge Neon home banner) was made by the project owner with Google Gemini; it depicts recognisable console hardware designs, so consider a version with generic consoles before a store release (see RELEASE_PLAN.md).

Data adapted into RetroForge: Android launch commands and package names for standalone emulators (`retro-library/src/main/resources/standalone.json`) from ES-DE Frontend (`resources/systems/android`), MIT licence, Copyright (c) 2024-2026 Northwestern Software AB, (c) 2020-2024 Leon Styhre, (c) 2014 Alec Lofquist. The MIT notice must be included with the app.

ScreenScraper system ids (`retro-artwork/src/main/resources/screenscraper_systems.json`) are also adapted from ES-DE (MIT, same notice).

Built-in player: `retro-player/src/main/cpp/libretro.h` is the libretro API header, MIT licence, Copyright (C) 2010-2024 The RetroArch team (notice in Settings → Licences). The player itself (player.cpp, PlayerActivity) is RetroForge's own code; no LibretroDroid or RetroArch code is used, so RetroForge's own licence stays open.

Emulator cores are **not bundled**: RetroForge downloads them at run time from libretro's build server (buildbot.libretro.com/nightly/android/latest/<abi>/) into its private storage. Each core keeps its own licence: mGBA (MPL-2.0), Gambatte, FCEUmm, DeSmuME, Handy, Stella, Beetle cores and PicoDrive's parts (GPL-2.0 or similar), Snes9x and Genesis Plus GX (non-commercial). Non-commercial cores are another reason RetroForge must stay free: a paid RetroForge would need them removed or replaced.

Retro tab artwork: canvas-es by Siddy212 (CC0-1.0, github.com/Siddy212/canvas-es) is **not bundled**; console pictures, logos and system details (and game art if that tile style is chosen) are downloaded from its repository for the systems in the user's library. The artwork depicts trademarks and characters of their owners; RetroForge doesn't host it.

Cheats: libretro-database's cht files (CC BY-SA 4.0, github.com/libretro/libretro-database) are **not bundled**; one game's file is downloaded when its Cheats page is opened, and credited in Settings → Licences.

Bezels: The Bezel Project's overlay PNGs (github.com/thebezelproject/bezelproject-<System>) are **not bundled**. Their repositories carry no licence file; RetroForge downloads them only when the user asks (Settings → Covers and bezels → Bezels, or Picture in the game's menu), as The Bezel Project's own installer does, into private storage for the user's own use, and credits The Bezel Project in Settings → Licences, the About screen and the README. The artwork depicts trademarks and characters of their owners; RetroForge doesn't host it.

Bundled: AndroidX Media3 (ExoPlayer and its UI), Apache 2.0, for the Media tab's film player.

Referenced, not bundled: TMDB (film posters, only with the user's own API key; "This product uses the TMDB API but is not endorsed or certified by TMDB"). Turnip GPU drivers (Mesa, MIT), downloaded only when the user picks one in Settings → GPU and drivers, from the GitHub releases of K11MCH1/AdrenoToolsDrivers and MrPurple666/purple-turnip; Qualcomm's proprietary driver packages on those pages are not listed. Obtainium (GPL-3.0; RetroForge only opens its documented `obtainium://add/` and `obtainium://app/` links) and the Obtainium Emulation Pack (Unlicense): its app setups for 11 emulators are copied into `standalone.json` by `tools/add_obtainium_apps.py`, and the pack itself is linked. Standalone emulators are separate apps the user installs.

Online services used at runtime (no code bundled): libretro thumbnails (thumbnails.libretro.com), SteamGridDB API (user's own key; personal non-commercial use per its terms).

An in-app licence screen is still to be added before any public release.

Arcade game names (`retro-library/src/main/resources/arcade_names.tsv.gz`): short name, title, year and maker from libretro-database's FinalBurn Neo and MAME lists (github.com/libretro/libretro-database), CC BY-SA 4.0.
