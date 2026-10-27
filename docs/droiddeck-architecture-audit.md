# DroidDeck Architecture Audit

Source: https://github.com/Droid-Deck/DroidDeck, shallow clone of `main` at commit `4f58230` (2026-10-28), app `versionName 0.6.0`, `versionCode 15`.
Inspected on 2026-10-27. **Evidence status for everything here: Verified by source inspection** unless noted. Nothing was built or run.

## Summary

DroidDeck is not a library. It is a large, self-contained GPL-3.0 Android app (531 Kotlin files plus native C/C++, Rust and a Docker-based component pipeline) that boots a Linux userland under **proot**, runs the **x86-64 Steam client and games via FEX**, Windows games via **Valve's ARM64 Proton**, renders via **Mesa Turnip** (Qualcomm Adreno-only open Vulkan driver) inside **gamescope**, and routes audio through PulseAudio/PipeWire sinks. It also has store integrations (GOG, Epic, Amazon, itch.io, Battle.net, Xbox), a Flatpak "Store", a desktop, and XR-glasses support.

It **does** expose a public launch interface that another app can call — this is the key finding for RetroForge.

## Build facts

| Item | Value | Source |
|---|---|---|
| Licence | GPL-3.0 (full text in `LICENSE`) | `LICENSE`, README "Credits and licence" |
| Gradle / AGP / Kotlin | Gradle 8.10.2, AGP 8.8.0, Kotlin 2.1.0 (Compose) | `gradle-wrapper.properties`, `build.gradle` |
| compileSdk / minSdk / targetSdk | 34 / 26 / **28** | `app/build.gradle` |
| ABI | `arm64-v8a` only | `app/build.gradle` |
| NDK | 27.3.13750724 | `gradle.properties` |
| Full build needs | Docker, Java 17, Android SDK+NDK, Rust (rustup), GitHub CLI, Python 3, zstd — via `tools/build_local.sh` | README "Build" |
| Unit tests only | `./gradlew -PskipRust=true :app:testDebugUnitTest` needs no native prep | README |

`targetSdk 28` is notable: it lets the app execute binaries from its private storage (W^X restrictions on app-data executables apply to targetSdk ≥ 29). This is one reason DroidDeck **cannot be published on Google Play** in its current form and is distributed as a GitHub APK. Any app that embeds its runtime approach inherits the same constraint.

**Build status in this environment: Blocked** — no Android SDK/NDK, Docker or Rust installed.

## Hardware requirements (current, from README and code)

README (current `main`): Android 9+, **Adreno 730 or newer, or 8xx**; Adreno 6xx experimental (DX11 via DXVK 2 may run, DX12 may crash); **Mali, Xclipse, PowerVR and Adreno 710 unsupported**. ~3 GB for runtime + 1.1 GB for desktop/emulators. No root. Android 14+: user must enable *Developer options → Disable child process restrictions*.

Code (`gpu/GpuInfo.kt`) is slightly more nuanced than the README:

- Not Adreno → `UNSUPPORTED`
- Adreno 850 → `UNTESTED` (experimental drivers)
- Adreno 650 → `TESTED`
- Any Adreno 8xx → `TESTED`
- Adreno 7xx with model ≥ 725 → `TESTED`
- Adreno 710/720/722 → `UNTESTED` (test driver builds)
- Everything else → `UNTESTED`

Detection reads `/sys/class/kgsl/kgsl-3d0/gpu_model`, `gpu_chipid` and `/vendor/lib64/hw/vulkan.adreno.so`. RetroForge can replicate this check (it is a few sysfs reads; we would reimplement, not copy) to show users an honest "PC games: not available on this GPU" message.

Note the README (730+) and code (725+) disagree slightly; treat the README as the published promise.

## Architecture (high level)

| Package (`com.droiddeck.launcher.*`) | Files | Role |
|---|---|---|
| `ui` | 99 | Compose UI |
| `session` | 70 | Session service: starts proot/Linux session, Steam, gamescope |
| `glasses`, `handtracking` | 60 | XR glasses / tracking |
| `input` | 42 | Controllers, accessibility home-button service |
| `frontend` | 33 | Library + public launch entry point |
| `stores/*` | ~100 | Epic, GOG, Amazon, itch, Battle.net, Xbox, downloads |
| `gpu` | 25 | GPU detection, Turnip driver management, frame-gen |
| `runtime` | 18 | Linux runtime install/update |
| `agent` | 7 | ADB-only test bridge (requires `android.permission.DUMP`) |

Runtime components (from README credits and `tools/`): proot (GPL-2.0+), FEX (MIT), Proton/Wine (LGPL-2.1+), Mesa Turnip (MIT), gamescope (BSD-2), PulseAudio sink "DirectAudio" (LGPL-2.1+), PipeWire/WirePlumber (MIT), Flatpak (LGPL-2.1), uruntime (MIT), Steam Linux Runtime (Valve, proprietary components), and the **Steam client itself — downloaded from Valve at first launch, not bundled**. Runtime/shim/input work builds on WinNative and Bannerlator.

Permissions requested include `MANAGE_EXTERNAL_STORAGE`, `REQUEST_INSTALL_PACKAGES`, camera, microphone, location, `WRITE_SETTINGS`, an exported accessibility service, and more.

## Public interoperability surface

From `AndroidManifest.xml` and `frontend/GameLaunchIntent.kt`, `frontend/GameLaunchLink.kt`:

- `FrontEndActivity` is **exported** and accepts:
  - action `com.droiddeck.launcher.LAUNCH_GAME`, category `DEFAULT`, extras:
    - `app_id` — Steam app id (Int > 0, or decimal String up to 20 digits)
    - `game_source` — optional; if present must be `"STEAM"` (case-insensitive), otherwise the intent is ignored
  - action `app.gamenative.LAUNCH_GAME` (same extras; GameNative compatibility)
  - `ACTION_VIEW` on `droiddeck://game/<steam-app-id>`
- Package name: `com.droiddeck.launcher` (variant builds use other packages, listed in `tools/release/variants.txt`).

Limits of this surface:

- **Steam only.** GOG/Epic/Amazon/itch games cannot be launched by id through this intent.
- **No library query.** The only provider (`AgentBridgeProvider`, `content://com.droiddeck.launcher.agent`) requires `android.permission.DUMP`, which a normal app cannot hold. RetroForge therefore cannot list what is installed in DroidDeck; the user (or a Steam app-id lookup) supplies entries.
- **No result callback.** It is a fire-and-forget activity launch; RetroForge cannot learn whether the game started or exited, other than via its own lifecycle (returning to foreground).
- This is not a documented, versioned API. It exists to support Android home-screen shortcuts and GameNative compatibility, so it could change. Treat as "observed contract", re-verify each DroidDeck release.

## Integration options assessed

| Option | Feasibility | Licence impact | Maintenance | Verdict |
|---|---|---|---|---|
| Embed DroidDeck's runtime/session code in RetroForge | Technically possible but enormous: native toolchain, Docker pipeline, targetSdk 28 | RetroForge must become GPL-3.0 and ship corresponding source for all combined parts | Very high — upstream moves daily (5 commits on 2026-10-27 alone) | Not recommended now |
| Fork DroidDeck and add RetroForge's retro frontend into it | Possible | GPL-3.0 | Very high, permanent merge burden | Not recommended |
| **Separate app + launch intent** (user installs DroidDeck; RetroForge sends `LAUNCH_GAME`) | Uses existing exported entry point | Arm's-length communication between separate programs; generally not considered a combined work — **unresolved, review recommended**, but low risk since no code is copied | Low — one intent contract to re-verify | **Recommended** |
| Independent PC runtime module (own proot/FEX/Proton stack) | Months of specialised work; same targetSdk/Play constraints | Mixed (FEX MIT, Wine LGPL, proot GPL-2+) | Very high | Defer; revisit only if interop proves insufficient |

## What would still need testing on hardware

All of: DroidDeck install, runtime install, Steam sign-in, game install, launch via RetroForge's intent, graphics/audio/controls, clean return to RetroForge. See [pc-runtime-proof-of-concept.md](pc-runtime-proof-of-concept.md).
