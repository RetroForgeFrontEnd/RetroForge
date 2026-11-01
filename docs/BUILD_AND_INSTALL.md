# Build and Install

## Requirements (as verified on the development PC, 2026-10-27)

- Android Studio (bundled JBR, OpenJDK 25.0.3) — used as `JAVA_HOME` for command-line builds
- Android SDK at `%LOCALAPPDATA%\Android\Sdk` with platform 37 and build-tools 36 (AGP downloads missing platforms automatically once licences are accepted)
- Gradle is supplied by the wrapper (9.8.2); no global install needed

Versions: AGP 9.4.1, Kotlin 2.4.21, Compose BOM 2026.09.00, compileSdk 37, targetSdk 37, minSdk 26.

`local.properties` (not committed) must contain the SDK path with forward slashes:

```
sdk.dir=C\:/Users/<you>/AppData/Local/Android/Sdk
```

## Build from a terminal (Git Bash)

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
```
```bash
./gradlew testDebugUnitTest assembleDebug lintDebug
```

There are two builds of the app (see [PLAY_STORE](PLAY_STORE.md)). `assembleDebug` makes both:

- `app/build/outputs/apk/full/debug/app-full-debug.apk`: the GitHub version, with everything (`com.retroforge.app`).
- `app/build/outputs/apk/play/debug/app-play-debug.apk`: the Google Play version (`com.retroforge.app.play`).

For just one, use `assembleFullDebug` or `assemblePlayDebug`.

Or open `F:\RetroForge` in Android Studio and run the **app** configuration.

## Install on a device or emulator

```bash
adb install -r app/build/outputs/apk/full/debug/app-full-debug.apk
```

RetroForge appears as **RetroForge** in the phone launcher and in the Android TV launcher row (the manifest declares `LEANBACK_LAUNCHER` and does not require a touchscreen).

## Release builds

`assembleFullRelease` (GitHub) and `bundlePlayRelease` (the .aab Google Play wants) shrink and obfuscate the code with R8 (APK about 4 MB). Tested 2026-10-28: the release build, signed with the debug key for testing, ran every main screen on the emulator without errors.

### The GitHub key and its rotation

0.8.0 and 0.8.1 went out signed with this PC's debug key. From the next release, the GitHub APK is signed with RetroForge's own key, `F:\RetroForge-keys\retroforge-github-release.jks`, together with a lineage (`retroforge-github-lineage.bin`) saying it replaces the debug key (kept as `retroforge-github-debug.keystore`). Android 9 and later accept the new key as an ordinary update. Older Android still checks the debug key's v2 signature, so it updates too. Every release needs all three files, so back them up together, away from this PC.

1. `gradlew :app:assembleFullRelease` makes an unsigned APK (there is no `keystore.properties` for the full version).
2. `powershell -ExecutionPolicy Bypass -File tools\sign-github-release.ps1` asks for the key's password in the terminal and writes `app\build\outputs\apk\full\release\RetroForge-<version>.apk`.

Tested 2026-11-01 on the emulator: an APK signed this way installed over the debug-signed 0.8.1 with no uninstall, and the library and history were kept.

The Play version has its own upload key, `retroforge-upload.jks`, described in `keystore-play.properties` (in `.gitignore`). `gradlew :app:bundlePlayRelease` signs the .aab with it.

### Optional settings in local.properties

```
screenscraper.devid=...          # RetroForge's ScreenScraper developer ID
screenscraper.devpassword=...
bugreport.url=https://github.com/YOU/RetroForge/issues   # enables Settings -> Report a problem
support.buymeacoffee=https://...  # any of these show Support RetroForge buttons
support.kofi=https://...
support.patreon=https://...
support.github=https://...
```

## Testing on your phone

1. Settings → About phone → tap "Build number" 7 times (Developer options).
2. Settings → Developer options → turn on USB debugging.
3. Connect the phone by USB, unlock it, and tap **Allow** on "Allow USB debugging?" (tick "Always allow from this computer").
4. In a terminal: `adb devices` should list the phone as `device` (not `unauthorized`).
5. Install: `adb install -r app/build/outputs/apk/full/debug/app-full-debug.apk`

## Film artwork (no built-in TMDB key)

RetroForge doesn't ship a TMDB key: TMDB's free API is for non-commercial use, and commercial use needs a licence from TMDB. The Media tab reads the poster and .nfo saved next to each film (Kodi/Plex/Jellyfin names, as Media Companion and tinyMediaManager write them) and the posters Plex and other DLNA servers send. Users may add their own TMDB key for personal use; the line "This product uses the TMDB API but is not endorsed or certified by TMDB" is shown next to it.
