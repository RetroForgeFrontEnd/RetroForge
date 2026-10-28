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

Test-only (not shipped): JUnit 4.13.2 (EPL-1.0), kotlinx-coroutines-test (Apache-2.0).

Not shipped, interacted with as separate apps: DroidDeck (GPL-3.0) — see [pc-runtime-licences.md](pc-runtime-licences.md).

Artwork: icons, banner and wordmark are original to this project. The RetroForge key art shared on 2026-10-27 is not yet included in the app.

Data adapted into RetroForge: Android launch commands and package names for standalone emulators (`retro-library/src/main/resources/standalone.json`) from ES-DE Frontend (`resources/systems/android`), MIT licence, Copyright (c) 2024-2026 Northwestern Software AB, (c) 2020-2024 Leon Styhre, (c) 2014 Alec Lofquist. The MIT notice must be included with the app.

Referenced, not bundled: Obtainium (GPL-3.0; RetroForge only opens its documented `obtainium://add/` link) and the Obtainium Emulation Pack (Unlicense; linked). Standalone emulators are separate apps the user installs.

Online services used at runtime (no code bundled): libretro thumbnails (thumbnails.libretro.com), SteamGridDB API (user's own key; personal non-commercial use per its terms).

An in-app licence screen is still to be added before any public release.
