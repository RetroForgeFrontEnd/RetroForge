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

| Press Start 2P, Orbitron, Rajdhani, Exo 2 fonts (google/fonts) | 2026-10 | SIL Open Font License 1.1 (texts in core-ui assets/licences) |

Test-only (not shipped): JUnit 4.13.2 (EPL-1.0), kotlinx-coroutines-test (Apache-2.0).

Not shipped, interacted with as separate apps: DroidDeck (GPL-3.0) — see [pc-runtime-licences.md](pc-runtime-licences.md).

Artwork: icons, TV banner and wordmark are original to this project. The RetroForge key art (app/src/main/res/drawable-nodpi/key_art.jpg, the Forge Neon home banner) was made by the project owner with Google Gemini; it depicts recognisable console hardware designs, so consider a version with generic consoles before a store release (see RELEASE_PLAN.md).

Data adapted into RetroForge: Android launch commands and package names for standalone emulators (`retro-library/src/main/resources/standalone.json`) from ES-DE Frontend (`resources/systems/android`), MIT licence, Copyright (c) 2024-2026 Northwestern Software AB, (c) 2020-2024 Leon Styhre, (c) 2014 Alec Lofquist. The MIT notice must be included with the app.

ScreenScraper system ids (`retro-artwork/src/main/resources/screenscraper_systems.json`) are also adapted from ES-DE (MIT, same notice).

Built-in player: `retro-player/src/main/cpp/libretro.h` is the libretro API header, MIT licence, Copyright (C) 2010-2024 The RetroArch team (notice in Settings → Licences). The player itself (player.cpp, PlayerActivity) is RetroForge's own code; no LibretroDroid or RetroArch code is used, so RetroForge's own licence stays open.

Emulator cores are **not bundled**: RetroForge downloads them at run time from libretro's build server (buildbot.libretro.com/nightly/android/latest/<abi>/) into its private storage. Each core keeps its own licence: mGBA (MPL-2.0), Gambatte, FCEUmm, DeSmuME, Handy, Stella, Beetle cores and PicoDrive's parts (GPL-2.0 or similar), Snes9x and Genesis Plus GX (non-commercial). Non-commercial cores are another reason RetroForge must stay free: a paid RetroForge would need them removed or replaced.

Referenced, not bundled: Obtainium (GPL-3.0; RetroForge only opens its documented `obtainium://add/` link) and the Obtainium Emulation Pack (Unlicense; linked). Standalone emulators are separate apps the user installs.

Online services used at runtime (no code bundled): libretro thumbnails (thumbnails.libretro.com), SteamGridDB API (user's own key; personal non-commercial use per its terms).

An in-app licence screen is still to be added before any public release.
