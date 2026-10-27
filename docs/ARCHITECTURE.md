# RetroForge Architecture

## Implemented in Milestone A

| Module | Contents |
|---|---|
| `app` | `MainActivity` (controller key translation), `AppContainer` (manual DI), navigation, screens + ViewModels |
| `core-ui` | Theme (key-art palette), `Wordmark`, `FocusCard`, `SectionTab`, `StatusPill`, `ControllerKeys` |
| `core-settings` | `SettingsRepository` (DataStore) |
| `pc` | `GpuProbe`, `GpuClassifier`, `DroidDeckBridge`, `PcStatusRepository` |

Decisions taken while building:
- **Manual DI instead of Hilt.** Four dependencies don't justify annotation processing. Revisit when Room and WorkManager arrive.
- **Plain Compose focus instead of the Compose-for-TV library.** Custom focus rings work on phone and TV alike.
- No `core-model` module yet: launch interfaces arrive with Milestone C/D, when they have implementations.

## Stack (target)

Kotlin + Jetpack Compose, Gradle Kotlin DSL with a version catalog, Room for the library DB (Milestone B), DataStore for settings, kotlinx.coroutines/Flow, WorkManager for long scans. minSdk 26, compile/target SDK 37.

## Modules planned (proposal from Phase 1)

```
app                 entry point, navigation, DI wiring
core-model          GameEntry, Platform, LaunchRequest/LaunchResult, ids   (pure Kotlin)
core-ui             design system, focusable components, controller nav
core-library        Room DB, repositories, search/filter, scanner (SAF)
core-settings       DataStore settings
retro               emulator registry, launch profiles, RetroArch/standalone launchers
pc                  DroidDeck bridge, GPU capability check
testing             fixtures, fake backends (clearly named Fake*)
```

`core-metadata`, `core-input`, `theme-engine`, `runtime-management` are split out later when their code exists. `retro` and `pc` depend on `core-*`, never on each other.

## Key interfaces (core-model)

```kotlin
sealed interface GameLocation {
    data class RomFile(val rootId: String, val relativePath: String) : GameLocation
    data class SteamApp(val appId: Long) : GameLocation
}

data class LaunchRequest(val gameId: Long, val location: GameLocation, val profileId: String?)

sealed interface LaunchResult {
    data object HandedOff : LaunchResult                 // external app started; no completion signal
    data class Failed(val reason: LaunchError) : LaunchResult
}

sealed interface LaunchError {
    data class LauncherNotInstalled(val packageName: String) : LaunchError
    data class FileNotAccessible(val detail: String) : LaunchError
    data class UnsupportedHardware(val detail: String) : LaunchError
    data class MissingBios(val files: List<String>) : LaunchError
    data class IntentRejected(val detail: String) : LaunchError
}

interface GameLauncher {
    fun canHandle(location: GameLocation): Boolean
    suspend fun availability(): Availability
    suspend fun launch(request: LaunchRequest): LaunchResult
}
```

UI calls a `LaunchCoordinator` that picks the `GameLauncher`; it never sees intents or emulator names directly.

## First milestones

1. **A — Shell:** branding, Retro / PC / Settings, focus-visible controller/D-pad navigation, phone + TV layouts. PC tab shows real device capability (GPU check), not a fake launch button.
2. **B — Scanner:** SAF folder pick → recursive read-only scan in a WorkManager job → Room. Platform rules seeded from RetroBat folder names + extensions. Skips media subfolders, handles `.m3u`. Optional read-only `gamelist.xml` import. Unit tests on a fixture tree (dummy zero-byte files, no copyrighted ROMs).
3. **D — First real launch: SNES via RetroArch for Android (snes9x core).** Reasons: your collection has ~1,300 SNES files with full gamelist/media; RetroArch has a long-standing explicit-activity launch used by other frontends; no BIOS needed. The exact component/extras (`ROM`, `LIBRETRO`, `CONFIGFILE`) and whether `content://` URIs work must be **verified on a device** before claiming support. Fallback: standalone Snes9x EX+ / other emulators via `ACTION_VIEW`.
4. **E — PC PoC:** single "Launch via DroidDeck" path per [pc-runtime-proof-of-concept.md](pc-runtime-proof-of-concept.md).
