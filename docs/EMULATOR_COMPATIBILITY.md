# Emulator Compatibility

Labels: Planned · Implemented · Launch tested · Gameplay tested · Partially supported · Unsupported.
"Launch tested" / "Gameplay tested" below were done **directly via RetroArch's launch intent over adb**, not from RetroForge (RetroForge cannot launch games yet — Milestone D). They prove the emulator path RetroForge will use.

## Test environment

- Android Studio emulator `Medium_Phone_API_37.0`, x86_64, Android 37, host GPU NVIDIA RTX 3050 Laptop (translated GLES)
- RetroArch **1.22.2** stable from `https://buildbot.libretro.com/stable/1.22.2/android/RetroArch.apk` (198,332,624 bytes, SHA-256 `2eca60b3…868a`, signed `O=Libretro`, package `com.retroarch`, targetSdk 28)
- Cores installed through RetroArch's Online Updater (x86_64 nightly buildbot), 2026-10-27
- ROMs copied (read-only source) from `G:\Retrobat\roms` to `/sdcard/RetroForgeTest/<system>/`

An x86_64 emulator says nothing about performance on a phone. Real-device testing is still required.

## Results (2026-10-27)

| System | ROM (your collection) | Format | Core | Result |
|---|---|---|---|---|
| SNES | Classic Kong Complete (homebrew), file `DonkeyKongClassic (Shiru).smc` | .smc | snes9x | **Launch tested**: boots to title |
| SNES | Super Mario World (USA) | .zip | snes9x | **Launch tested**: title screen |
| SNES | Super Mario World (USA) | **.7z** | snes9x | **Launch tested**: title screen (RetroArch extracted the 7z itself) |
| Mega Drive | Sonic The Hedgehog (W) (REV00) [!] | .zip | genesis_plus_gx | **Launch tested**: "Sonic Team presents" intro |
| GBA | Kirby - Nightmare in Dreamland (U) [!] | .zip | mgba | **Launch tested**: title screen |
| NES | Super Mario Bros. (World) | .zip | fceumm | **Gameplay tested**: Start → World 1-1, held Right and Mario moved (touch overlay input) |

Not yet tested: sound output, save/load (SRAM, states), return to the launching app on exit, physical controller, BIOS-dependent systems (PSX, Sega CD, etc.), opening ROMs via `content://` URIs (the test used a real file path), ROMs on a USB/OTG drive.

## Verified launch contract (RetroArch for Android)

```
am start -n com.retroarch/.browser.retroactivity.RetroActivityFuture \
  --es ROM <absolute file path> \
  --es LIBRETRO /data/user/0/com.retroarch/cores/<core>_libretro_android.so \
  --es CONFIGFILE /storage/emulated/0/Android/data/com.retroarch/files/retroarch.cfg
```

- `RetroActivityFuture` accepted an external start (COLD launch, ~0.8 s to activity).
- Cores live in RetroArch's private data dir. RetroForge can't install them, so it should guide the user to RetroArch's Core Downloader and detect missing cores.
- adb `KEYCODE_ENTER` / D-pad keyevents were **not** delivered as game input with default settings; touch overlay input was. Physical gamepad still untested.

## Planned systems

The full roadmap (all systems in the master brief) will be populated as each is evaluated. Priorities follow your collection: SNES, NES, GB/GBC/GBA, Mega Drive/MS/GG, N64, PSX, arcade (FBNeo/MAME), DOS/ScummVM.
