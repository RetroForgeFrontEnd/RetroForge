# Testing

Evidence levels: Implemented · Build verified · Unit tested · Integration tested · Device tested · Gameplay verified · Not tested · Known limitation.
"Device tested" here means the Android Studio emulator unless a physical device is named.

## Automated tests

Run: `./gradlew testDebugUnitTest` (JVM, no device needed, no ROMs needed).

| Suite | Module | Tests | Covers |
|---|---|---|---|
| `GpuClassifierTest` | pc | 6 | Adreno model parsing (GL renderer and kgsl strings); supported / experimental / unsupported / unknown classification per DroidDeck's published requirements |
| `ControllerKeysTest` | core-ui | 2 | Gamepad A/B/L1/R1 → frontend commands; D-pad left to Compose focus |
| `SettingsRepositoryTest` | core-settings | 2 | DataStore defaults and persistence |
| `RomPatcherTest` | retro-patch | 14 | IPS (records, RLE, grow, truncate, missing EOF); UPS (grow/shrink targets, wrong ROM, damaged patch); BPS (all four commands incl. overlapping target copy, negative offsets, metadata, out-of-range copy); SNES copier-header retry; originals never modified |
| `ArchiveInputTest` | retro-patch | 7 | ROM/patch selection inside .zip and .7z, ambiguity, corrupt archives, size cap |
| `RealPatchTest` | retro-patch | 1 (skipped unless `patch-test/` has files) | Applies real patches from the git-ignored `patch-test/` folder and prints SHA-1 for cross-checking |

Last run: 2026-10-27, **31 passed, 0 failed** (+1 real-patch test that ran locally). `lintDebug`: 0 errors, 2 accepted warnings (TV banner vector is 320×180 by Android TV requirement; `allowBackup=false` already covers pre-Android-12).

No instrumented (on-device) automated UI tests exist yet. Planned for Milestone B alongside the scanner.

## Manual test log

### 2026-10-27 — Milestone A on emulator (Medium_Phone_API_37.0, x86_64, Android 37)

Input was sent with `adb shell input keyevent` (simulated gamepad/D-pad key codes, not a physical controller).

| Check | Result |
|---|---|
| Install + cold launch | Pass, no crash entries in `logcat -b crash` |
| Phone layout (bottom section bar) | Pass |
| Wide layout (landscape, 280 dpi → side rail; content capped at 960dp) | Pass, after fixing the width cap |
| R1 / L1 switches sections, wraps around | Pass |
| D-pad moves focus; orange focus ring visible on cards and tabs | Pass |
| Gamepad A activates focused card | Pass (toggled "Show PC section") |
| Gamepad B goes back | Pass (Settings → Home) |
| "Show PC section" off hides PC tab and Home card; persists across app restart | Pass |
| PC screen on emulator GPU | Pass: "PC games not available", renderer string shown, DroidDeck "Not installed" |
| Initial focus without touch | Pass: the selected section tab gets focus after key input |

### 2026-10-27: ROM Patcher on emulator

| Check | Result |
|---|---|
| Pick ROM from a `.7z` via Android's file picker | Pass: extracted `Super Mario World (USA).sfc`, CRC32 B19ED489 |
| BPS made for a different ROM | Pass: "Wrong ROM for this patch" shown, Create disabled |
| IPS (hand-made, renames header title) → save via Create Document | Pass: new file saved with suggested name; output SHA-1 identical to an independent Python IPS implementation; patched ROM boots in RetroArch/snes9x |
| Original ROM untouched | Pass: `.7z` SHA-1 identical on device and on `G:` |
| Real-world IPS ("2B in Super Mario Bros", from the user) | Engine output identical to the Python reference (53,008 bytes, SHA-1 c0a2f254…). **Not gameplay-tested**: its readme requires the "Giant Mario v1.2" patch to be applied first |

Not tested: physical gamepad, Android TV device or TV emulator image, tablet, light theme visuals, "System" theme switching, opening the DroidDeck releases page (browser), DroidDeck-installed state, the real Adreno GPU path.
