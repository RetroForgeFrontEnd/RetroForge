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

Not set up yet: there is no signing key. A release build (`assembleRelease`) enables R8 minification and resource shrinking but has not been tested.
