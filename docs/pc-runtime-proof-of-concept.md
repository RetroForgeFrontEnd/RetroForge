# PC Runtime Proof of Concept

Status: **Planned — no hardware available yet.** Nothing in this document has been run on a device.

Evidence statuses used: Verified by source inspection · Build verified · Launch verified · Gameplay verified · Partially verified · Not tested · Blocked.

## Stage results so far

| # | Stage | Status | Notes |
|---|---|---|---|
| 1 | Identify target device (CPU, GPU, Android, RAM, Vulkan, storage) | **Blocked** | No device attached (`adb devices` empty, 2026-10-27). Need device model or the commands below. |
| 2 | Verify current DroidDeck requirements | Verified by source inspection | Android 9+; Adreno 730+/8xx supported; 650 tested in code; 6xx experimental; Mali/Xclipse/PowerVR/710 unsupported. ~4.1 GB storage. Android 14+: "Disable child process restrictions". See [droiddeck-architecture-audit.md](droiddeck-architecture-audit.md). |
| 3 | Identify reusable components and licences | Verified by source inspection | DroidDeck is GPL-3.0; runtime components mixed (see [pc-runtime-licences.md](pc-runtime-licences.md)). Public `LAUNCH_GAME` intent available for interop. |
| 4 | Build or install the runtime here | Blocked | Building DroidDeck needs Docker, NDK, Rust — none installed. Not needed for the recommended approach: install DroidDeck's release APK on the device instead. |
| 5 | Minimal genuine execution test | Not tested | Needs device. |
| 6 | Install / launch / graphics / audio / controls / shutdown with a legal test game | Not tested | Needs device + a Steam game you own. |
| 7 | Record results | This document | |

## Recommendation

**Integrate through the external-app approach: RetroForge PC launches games inside a separately installed DroidDeck using DroidDeck's exported `com.droiddeck.launcher.LAUNCH_GAME` intent.**

Why:

- **Technical:** DroidDeck's runtime is a moving native stack (proot + FEX + Proton + Turnip + gamescope) with a Docker build. Reproducing it would dwarf the rest of RetroForge. The intent already exists and is small.
- **Licensing:** No DroidDeck code is copied, so RetroForge's own licence stays your choice. (Whether a launcher calling another app counts as a combined work under GPL-3.0 is generally considered no for arm's-length exec/intent communication, but this is recorded as *unresolved — review recommended* rather than asserted.)
- **Maintenance:** One contract to re-check per DroidDeck release, versus tracking daily upstream runtime changes.
- **Hardware honesty:** RetroForge can read the same sysfs GPU model DroidDeck reads and hide/disable the PC section on unsupported GPUs, with the reason shown.
- **Store policy:** RetroForge can stay targetSdk-current (Play-eligible if you ever want that). DroidDeck's targetSdk 28 problem stays in DroidDeck.

Trade-offs you accept with this approach:

- Steam games only (the intent accepts `game_source=STEAM` only). GOG/Epic/Amazon games inside DroidDeck can't be launched directly from RetroForge — the fallback is "Open DroidDeck".
- RetroForge cannot list DroidDeck's installed games; you add PC games by Steam app id (or a search against Steam's public app list, to be evaluated against Steam's terms).
- No launch result callback; RetroForge only knows it handed off.
- The user must install DroidDeck separately from its GitHub releases.

If the device turns out **not** to have a supported Adreno GPU, the recommendation becomes **Defer PC gaming** and show the PC section as "Not available on this device — requires Adreno 650/725+/8xx".

## The smallest useful PoC

Goal: prove one owned Steam game can be started from a *non-DroidDeck* app and that control returns.

### Step 1: identify the device

With USB debugging enabled and the device plugged into this PC:

```bash
adb shell getprop ro.product.model
```
```bash
adb shell getprop ro.build.version.release
```
```bash
adb shell getprop ro.soc.model
```
```bash
adb shell cat /sys/class/kgsl/kgsl-3d0/gpu_model
```
```bash
adb shell cat /proc/meminfo
```
```bash
adb shell df -h /data
```
```bash
adb shell pm has-feature android.hardware.vulkan.version
```

(Or just tell me the exact device model.)

### Step 2: DroidDeck alone (you, on the device)

1. Install the latest APK from https://github.com/Droid-Deck/DroidDeck/releases (only that page — the README warns about fake download sites).
2. Install the Linux runtime, press Play, sign in to Steam.
3. On Android 14+: Developer options → *Disable child process restrictions*.
4. Install one small game you own that is known to work under Proton (pick something light, e.g. a 2D indie title).
5. Launch it from DroidDeck. Note: does it render, is there sound, does the controller work, does it quit cleanly?

Result → stage 5/6 "Launch verified" / "Gameplay verified" for DroidDeck itself.

### Step 3: launch from outside DroidDeck (I can drive this over adb)

```bash
adb shell am start -a com.droiddeck.launcher.LAUNCH_GAME --ei app_id <STEAM_APP_ID> --es game_source STEAM
```

Then the deep-link form:

```bash
adb shell am start -a android.intent.action.VIEW -d droiddeck://game/<STEAM_APP_ID>
```

Pass criteria: the game starts without manual steps inside DroidDeck; after quitting the game, pressing Home/Back can return to the launching app. Record what actually happens.

### Step 4: same call from a RetroForge debug build

Once the app shell exists, a single "Launch via DroidDeck" path with the same intent, plus package-installed and GPU checks. Only after this passes would `retroforge-pc` move beyond this one path.

## Hardware test log

_No entries yet._
