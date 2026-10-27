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

Last run: 2026-10-27 — **10 passed, 0 failed, 0 skipped**. `lintDebug`: 0 errors, 2 accepted warnings (TV banner vector is 320×180 by Android TV requirement; `allowBackup=false` already covers pre-Android-12).

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

Not tested: physical gamepad, Android TV device or TV emulator image, tablet, light theme visuals, "System" theme switching, opening the DroidDeck releases page (browser), DroidDeck-installed state, the real Adreno GPU path.
