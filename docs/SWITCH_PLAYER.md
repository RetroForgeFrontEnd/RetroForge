# RetroForge Switch Player

A separate Android app, built from Eden's open-source code, that RetroForge hands Switch games to. It is **not** part
of the RetroForge app. It never ships, downloads or links to keys, firmware or games: the player owns those files
only after the user has copied them from their own console.

Status (2026-10-30): Eden v0.2.1 builds from source on this PC; first renamed debug APK in progress (see "Build").
Source lives outside this repo at `F:\SwitchPlayer\eden` (branch `switchplayer`, based on tag `v0.2.1`).

## Why a separate app

- **Licence.** Eden is GPL-3.0 (with GPL-2.0-or-later yuzu/Citra files). RetroForge is GPL-3.0 too, so mixing would
  be allowed, but keeping the emulator in its own APK keeps the source split obvious: anyone can rebuild the Switch
  Player from `F:\SwitchPlayer\eden` alone.
- **Legal distance.** Nintendo's 2024 case against yuzu was about the emulator being promoted alongside piracy and
  key extraction. RetroForge stays a frontend that launches "some app that handles .nsp/.xci", exactly as it does
  for Eden today. The Switch Player itself ships no keys, firmware or game, has no download links for them, and
  says so in its setup screens.
- **Size and crashes.** Eden's native library is large and the emulator process can crash on bad drivers; a
  separate process and app keep RetroForge itself small and safe.
- **Updates.** Pulling in new Eden releases becomes a rebase of a small patch set, not a merge into RetroForge.

## How Eden's Android app works (v0.2.1)

Paths below are relative to `F:\SwitchPlayer\eden\src\android\app\src\main\`.

### Structure

- Kotlin front end in `java/org/yuzu/yuzu_emu/` (Android Views + Fragments + Navigation, not Compose). Package
  namespace stays `org.yuzu.yuzu_emu`; only the applicationId differs per build (`dev.eden.eden_emulator`).
- C++ core is the whole Eden/yuzu emulator (`src/core`, `src/video_core`, dynarmic/NCE CPU, …) built by CMake as
  `libyuzu-android.so`; `jni/native.cpp` is the bridge. `NativeLibrary.kt` lists every JNI call (`run`,
  `pauseEmulation`, `stopEmulation`, `installKeys`, `installFileToNand`, `initializeGpuDriver`, …).
- Activities: `ui.main.MainActivity` (library, settings, setup), `activities.EmulationActivity` (the game, single
  `EmulationFragment`), `features.settings.ui.SettingsActivity`.
- User data lives in `getExternalFilesDir(null)` = `/sdcard/Android/data/<applicationId>/files/`
  (`utils/DirectoryInitialization.kt`): `keys/`, `nand/`, `sdmc/`, `config/`, `gpu_drivers/`, `shader/`, `load/` (mods).
  A new applicationId therefore gets its **own** keys, firmware, saves and settings; nothing is shared with Eden.

### Starting a game from another app

`AndroidManifest.xml` exports `EmulationActivity` with three filters:
`android.nfc.action.TECH_DISCOVERED` + mime `application/octet-stream`, `android.intent.action.VIEW` +
`content:` + `application/octet-stream`, and `dev.eden.eden_emulator.LAUNCH_WITH_CUSTOM_CONFIG`. An explicit intent
(component set, as RetroForge sends) skips filter matching, so only `data` matters.

`fragments/EmulationFragment.kt` `onCreate` (lines ~231-276):

1. `uri = intent.data`.
2. `FileUtil.getExtension(uri)` → `FileUtil.getFilename(uri)` queries the URI for
   `DocumentsContract.Document.COLUMN_DISPLAY_NAME` and takes the text after the last dot.
3. Only `xci`, `nsp`, `nca`, `nro` are accepted (`model/Game.kt`). Then `GameHelper.getGame(uri)`, which also needs
   `GameMetadata.getIsValid(uri)` — that reads the file, so **keys must be installed** or it returns null too.
4. If anything fails: log "No game found in arguments or intent", toast, `finish()`.

**What went wrong on the S22.** `FileUtil.getFilename` logs a copy-pasted, wrong message on failure:
`"[FileUtil]: Cannot get file size, error: " + e.message`. "error: null" is a NullPointerException: the
`resolver.query` returned no cursor (a `DocumentsProvider` returns null when it can't find the document; a missing
permission would throw SecurityException with a message instead) or the display name was null. So the name lookup
for RetroForge's URI failed before Eden even looked at the file. Checks for RetroForge's launcher: the exact URI it
sends (encoded once, pointing at an existing document), that it answers `_display_name` with a name ending in
`.nsp`/`.xci`, and `FLAG_GRANT_READ_URI_PERMISSION` (Eden later opens the file with
`FileUtil.openContentUri` → `openFileDescriptor(uri, "r")`, and the C++ side asks `FileUtil.getFileSize`, which
queries `COLUMN_SIZE`). A note with this is also in the shared side-session notes (`eden-launch-finding.md`).

### Reading game files (SAF)

The C++ file layer never sees a path; it calls back into Kotlin for `content://` paths:
`FileUtil.openContentUri` (returns a detached fd), `getFileSize` (`COLUMN_SIZE` query), `getFilename`, `exists`,
`isDirectory`, `listFiles` (tree URIs). Library folders are added as SAF tree URIs (`NativeConfig.getGameDirs()`),
scanned by `GameHelper.getGames()`. A single file passed by intent works without a library folder as long as the
read grant is on the intent.

### Settings

Settings are defined once in C++ (`src/common/settings.h`, Android extras in `jni/android_settings.h`) and stored
as INI files under `files/config/` (`config.ini`, per-game `custom/<titleId>.ini`). Kotlin mirrors them with
`features/settings/model/{Boolean,Int,Float,String}Setting.kt` enums that call `NativeConfig.get*/set*`; the screens
are built in `features/settings/ui/SettingsFragmentPresenter.kt`. Per-game settings: `menu_settings_per_game`, and
the `LAUNCH_WITH_CUSTOM_CONFIG` intent (`utils/CustomSettingsHandler.kt`) can start a game with a specific config.

### Controllers and input

`features/input/NativeInput.kt` + `jni/native_input.cpp`: Android `KeyEvent`/`MotionEvent`s are forwarded per device
(guid + port) and Eden's input engine maps them to Switch buttons. Defaults map a standard Android gamepad
automatically; per-player mapping is in Settings → Controls (`InputDialogFragment`, profiles in
`config/input/`). Touch overlay: `overlay/InputOverlay.kt`. Controller-only menus are partly there
(`utils/ControllerNavigationGlobalHook.kt`), and the in-game drawer is focusable.

### GPU drivers (libadrenotools)

`utils/GpuDriverHelper.kt` → `NativeLibrary.initializeGpuDriver(hookLibDir, customDriverDir, customDriverName,
fileRedirectDir)`. On Adreno (Snapdragon) devices `externals/libadrenotools` loads a user-supplied Vulkan driver
(e.g. Mesa Turnip) from a zip in `files/gpu_drivers/` instead of the system driver; the APK must keep native libs
uncompressed (`packaging.jniLibs.useLegacyPackaging`). `fragments/DriverFetcherFragment.kt` can list driver
releases from public GitHub repos. On the S22 (Exynos 2200 / Xclipse) custom drivers do not apply; the system
driver is used.

### Keys and firmware (user-supplied)

- Keys: `NativeLibrary.installKeys(path, ".keys")` copies the user's `prod.keys` (and `title.keys`) into
  `files/keys/`; `utils/InstallableActions.processKey`. The setup wizard (`fragments/SetupFragment.kt`) asks for them.
- Firmware: `InstallableActions.processFirmware` unzips the user's firmware zip of `.nca` files into
  `nand/system/Contents/registered/`. `NativeLibrary.isFirmwareAvailable()` / `firmwareVersion()` report it.
- Updates/DLC: `installFileToNand` installs `.nsp` updates and DLC into the emulated NAND.

### In-game menu and quitting

`res/menu/menu_in_game.xml` drawer (opened by Back, or by a controller hotkey): Pause, Overlay, Settings, Quick
settings, Per-game settings, Controls, Multiplayer, Load amiibo, Overlay controls, Lock drawer, **Exit**. Exit stops
emulation and finishes the activity, which returns to whatever app launched it (RetroForge). **Save states: none.**
Eden (like yuzu) has no save/load state in its core; games use their own saves (`nand/user/save/`). Adding real save
states would mean serialising the whole HLE kernel/GPU state — not realistic.

## Plan

### 1. Identity (done in the first build — see Build)
- applicationId `com.retroforge.switchplayer`, app name "RetroForge Switch Player", installs beside Eden.
- Keep the Kotlin namespace `org.yuzu.yuzu_emu` (renaming 600+ files buys nothing and makes Eden rebases painful).
- Custom-config action renamed to `com.retroforge.switchplayer.LAUNCH_WITH_CUSTOM_CONFIG`.
- Turn off the update checker and APK self-installer (`REQUEST_INSTALL_PACKAGES`); updates come from RetroForge.

### 2. RetroForge look
- Recolour Eden's `eden_*` palette to Forge Neon: background `#0B1020`, surface `#151C2E`, surface variant
  `#222B40`, primary orange `#FF8A2A`, secondary cyan `#3ED6FF`, text `#E6ECF7`, outline `#4A5670`
  (`res/values*/yuzu_colors.xml`). Dark theme by default.
- Wordmark: "RETRO" orange + "FORGE" cyan with glow, then "SWITCH PLAYER" — drawn as a vector/TextView on the home and
  setup screens; new launcher icon.
- Controller-first: start with focus on the first item, D-pad/A/B everywhere, no touch-only dialogs in setup.

### 3. Launch contract with RetroForge
- RetroForge sends an explicit intent: component `com.retroforge.switchplayer/org.yuzu.yuzu_emu.activities.EmulationActivity`,
  action `android.intent.action.VIEW`, data = the game's SAF document URI, type `application/octet-stream`,
  `FLAG_GRANT_READ_URI_PERMISSION`. In `standalone.json`: a new `RETROFORGE_SWITCH` emulator entry next to `EDEN`
  (`%ACTION%=android.intent.action.VIEW %DATA%=%ROMPROVIDER%`), shown only if the player is installed.
- Player side: make the filename lookup sturdy — fall back to `OpenableColumns.DISPLAY_NAME`, then to the last URI
  path segment, and accept an optional `retroforge.EXTRA_FILE_NAME` extra; log the real exception class.
- If keys are missing, show the setup screen first instead of "No game found".
- Exit always `finish()`es back to RetroForge; optional `retroforge.EXTRA_RETURN_ON_EXIT=true`.

### 4. In-game menu like RetroForge's player
- Restyle the drawer: Resume, Settings (quick), Controls, Screenshot, Quit to RetroForge. Remove Multiplayer/amiibo
  from the top level (keep under "More").
- Save/Load state: **not possible** (see above). The menu says "This game saves on its own" instead of greyed items.
- Hotkey: Select+Start (or Guide) opens the menu, same as RetroForge's built-in player.

### 5. Keys and firmware setup (done by the user)
- First-run screen explains in plain words: "Copy prod.keys and the firmware from your own Switch", with a file
  picker for each. No links, no downloads, no bundled files, no instructions for obtaining them elsewhere.
- Optional: "Import from Eden" — the user picks Eden's `Android/data/dev.eden.eden_emulator/files/keys` folder with
  the SAF picker (Android doesn't let one app read another's data otherwise).

### 6. Credits and licence
- Keep every SPDX header, `LICENSE.txt`, `LICENSES/`, and Eden's in-app Licences screen.
- About screen: "Based on Eden (eden-emu.dev) by the Eden Emulator Project, a fork of yuzu by the yuzu Emulator
  Project; Citra and dynarmic contributors. GPL-3.0." Link to the exact source of each release.
- Publish the patch set (branch `switchplayer`) wherever the APK is published — GPL requires source for distributed
  builds.

### Effort estimate

| Piece | Effort |
|---|---|
| Build Eden from source, Gradle 9 / JDK 25 fixes | done (this session) |
| Rename package/app name, recolour, first debug APK | done or nearly (this session) |
| Sturdier intent launch + RetroForge `standalone.json` entry + test on an arm64 phone | 0.5–1 day |
| Wordmark, icon, splash, controller-first home and setup | 1–2 days |
| Restyled in-game menu + hotkey + quit-to-RetroForge | 1 day |
| Keys/firmware setup screens (user-supplied only) | 0.5–1 day |
| Release signing, versioning, update channel from RetroForge | 0.5 day |
| Keeping up with Eden releases (rebase patch set) | ~1–2 h per Eden release |

Total to a polished first release: roughly **4–6 working days** of agent time, plus device testing on an arm64
phone (the S22, with the user's OK).

## Build

Source: `F:\SwitchPlayer\eden`, branch `switchplayer` (local only, not pushed), on top of Eden tag `v0.2.1`.
Build: `bash F:/SwitchPlayer/build.sh assembleMainlineRelWithDebInfo` (sets GRADLE_USER_HOME=F:\GradleHome,
JAVA_HOME = Adoptium JDK 25, NDK 28.2.13676358). APK: `src/android/app/build/outputs/apk/mainline/relWithDebInfo/`.
1778 C++ files; dependencies (Boost, OpenSSL, FFmpeg, …) are downloaded by CPM into `eden/.cache/cpm` on first build.

What had to be fixed to build on this PC:

1. **No glslangValidator** (Eden compiles its Vulkan shaders with it; normally from the Vulkan SDK). Running a
   downloaded Khronos binary was refused, so `F:\SwitchPlayer\tools\glslang-shim\glslangValidator.cmd/.py` stands in:
   it compiles with the NDK's own `glslc` and writes the same `const uint32_t NAME[] = {…}` header. Passed with
   `-PYUZU_ANDROID_ARGS=-DGLSLANGVALIDATOR=…`. Installing the Vulkan SDK would remove the need for it.
2. **Only JDK 25 is installed**; Eden's Gradle 8.13 can't run on it. Moved to Gradle 9.8.2 / AGP 9.4.1 /
   Kotlin 2.4.21 (what RetroForge uses, already cached), with `android.builtInKotlin=false`, `android.newDsl=false`,
   `buildConfig`/`resValues` turned on, `kotlinOptions` → `kotlin.compilerOptions`; dropped the ktlint and Play
   publisher plugins and the "copy artifacts" step (its path broke on Windows).
3. **pkg-config missing on Windows**: `externals/ffmpeg/CMakeLists.txt` probed PC video-acceleration libraries even
   for Android; now skipped on Android.

Commits on `switchplayer`: `6aef147` build tooling, `0e1dd82` ffmpeg, `9f5a766` + `3a03d74` RetroForge identity
(app id `com.retroforge.switchplayer`, name "RetroForge Switch Player", Forge Neon colours, dark by default,
Eden/yuzu credit on About, `FileUtil.getFilename` fallback + honest log text).
