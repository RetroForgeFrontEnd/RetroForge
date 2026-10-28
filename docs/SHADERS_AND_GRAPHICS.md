# Shaders and graphics

What RetroForge controls about how games look, per playing route. Last updated 2026-10-28.

## Built-in player (RetroForge's own libretro frontend)

Drawn with OpenGL ES 2 on the player's own surface (`retro-player/src/main/cpp/player.cpp`). All settings are per system, in the pause menu, and remembered.

| Setting | Choices | How it works | Tested |
|---|---|---|---|
| Screen shape | Original (the core's aspect ratio), 4:3, 16:9, Stretch to fill, Pixel perfect (whole-number scale of square pixels) | `Viewport.fit` works out the picture rectangle in Kotlin; the native side draws into it, and touchscreen input maps through the same rectangle | Unit tested (`ViewportTest`); Original checked on S22 and emulator |
| Screen filter | Sharp pixels (default), Smooth, CRT scanlines, LCD grid | One fragment shader. Sharp = "sharp bilinear": whole-pixel scaling with a one-pixel blend at the edges, crisp at any size without the uneven pixels of nearest-neighbour. Smooth = plain bilinear. CRT darkens the gap between the console's scan lines. LCD draws a faint grid between pixels | CRT checked on the emulator by eye; LCD and Smooth built, not yet checked by eye |
| Console look | On (default) / Off | A console-inspired body, a screen surround and pad-coloured buttons drawn in Compose round the picture ([ConsoleSkins.kt](../app/src/main/java/com/retroforge/app/player/ConsoleSkins.kt)). Original designs, no logos | SNES and NES checked on the emulator |
| DS screen layout | Follows the orientation | DeSmuME's `desmume_screens_layout` set to top/bottom (portrait) or left/right (landscape), changed live on rotation | S22 |

**Frame pacing.** The audio clock drives timing: after each frame the player waits while more than about 64 ms of sound is queued, so speed and sound stay exact on any display refresh rate. On Android 11+ the surface also asks for the core's frame rate (`Surface.setFrameRate`, fixed-source), so 120 Hz phones can switch to a matching mode. Fast-forward (hold R2 on systems without one, or the menu switch) runs unpaced and silent.

**Not built yet:** custom shader files (the RetroArch/slang format isn't supported), per-game (rather than per-system) settings, hardware-rendered cores (OpenGL/Vulkan cores such as N64, PSP and Dreamcast stay with RetroArch or standalone emulators), and a performance overlay (fps, frame time).

**Performance presets.** There is no single "low/high" switch yet. Lower-powered devices can pick a faster emulator per system under Switch emulator or Settings → Built-in player: gpSP for GBA, Snes9x 2010 for SNES, DeSmuME 2015 for DS.

## RetroArch

RetroForge doesn't change RetroArch's video settings. Shaders, integer scaling, aspect ratio and overlays are set in RetroArch's own menu (Down + Select by default from a RetroForge-launched game), and RetroArch keeps them per core or per game itself. See [EMULATORS_AND_HOTKEYS.md](EMULATORS_AND_HOTKEYS.md).

## Standalone emulators

Each has its own graphics settings (renderer, resolution scale, shaders). RetroForge only starts them; it promises nothing about their graphics.
