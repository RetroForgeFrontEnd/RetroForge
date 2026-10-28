# Build and Install

## Requirements (as verified on the development PC, 2026-10-27)

- Android Studio (bundled JBR, OpenJDK 25.0.3) — used as `JAVA_HOME` for command-line builds
- Android SDK at `C:\Users\darre\AppData\Local\Android\Sdk` with platform 37 and build-tools 36 (AGP downloads missing platforms automatically once licences are accepted)
- Gradle is supplied by the wrapper (9.8.2); no global install needed

Versions: AGP 9.4.1, Kotlin 2.4.21, Compose BOM 2026.09.00, compileSdk 37, targetSdk 37, minSdk 26.

`local.properties` (not committed) must contain the SDK path with forward slashes:

```
sdk.dir=C\:/Users/darre/AppData/Local/Android/Sdk
```

## Build from a terminal (Git Bash)

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
```
```bash
./gradlew testDebugUnitTest assembleDebug lintDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

Or open `F:\RetroForge` in Android Studio and run the **app** configuration.

## Install on a device or emulator

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

RetroForge appears as **RetroForge** in the phone launcher and in the Android TV launcher row (the manifest declares `LEANBACK_LAUNCHER` and does not require a touchscreen).

## Release builds

`assembleRelease` shrinks and obfuscates the code with R8 (APK about 4 MB). Tested 2026-10-28: the release build, signed with the debug key for testing, ran every main screen on the emulator without errors.

### Your release key (once, keep it safe)

Create the key yourself so only you hold it; losing it means existing installs can't be updated. In a terminal, in `F:\RetroForge`:

```
keytool -genkeypair -v -keystore retroforge-release.jks -alias retroforge -keyalg RSA -keysize 4096 -validity 10000
```

It asks for a password and your name. Then create `keystore.properties` next to it:

```
storeFile=retroforge-release.jks
storePassword=YOUR_PASSWORD
keyAlias=retroforge
keyPassword=YOUR_PASSWORD
```

Both files are in `.gitignore`. Back them up somewhere safe (not GitHub). `gradlew assembleRelease` then signs automatically; without them the APK is unsigned.

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
5. Install: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
