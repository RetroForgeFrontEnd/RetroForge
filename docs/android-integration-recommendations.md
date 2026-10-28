# Android integration recommendations

What we learnt bringing a RetroBat-style setup to Android, and what RetroForge should keep doing. Written 2026-10-28 after the emulator and S22 tests.

## Keep

1. **Built-in player first, other apps second.** Launching other apps works, but each one needs installing, permissions, BIOS in its own folder and its own controls. Our own libretro player removes all of that for the systems it covers. Keep RetroArch and standalone apps as choices, not requirements.
2. **Never write into the collection.** SAF access to a USB or SD drive can disappear; writing `gamelist.xml` or media there would also break the drive for RetroBat. Keep RetroForge's data in its own storage, keyed by folder + relative path.
3. **Copy only when a core needs a real file.** Cores that read whole files from memory get a file descriptor; `need_fullpath` cores (CD systems, arcade) get a one-off copy in app storage, pruned to the last two games. Symlinks through `/proc/self/fd` are refused on Android's FUSE storage.
4. **Player in its own process.** A crashing core can't take the library down, and quitting a game frees all its memory.
5. **One place for BIOS.** Find BIOS by RetroArch file names in the user's folder and copy it to where the player and RetroArch look, with a clear list of what's missing.

## Change or add

1. **Google Play:** downloading cores (`.so`) from outside Play is against Play policy. A Play build has to ship cores inside the app or as Play asset/feature delivery; the current downloader stays for the direct-download build ([RELEASE_PLAN.md](RELEASE_PLAN.md)).
2. **GPU cores** (N64, PSP, Dreamcast/Naomi, Saturn) need hardware rendering support in the player (`RETRO_ENVIRONMENT_SET_HW_RENDER`, an FBO and context reset). This is the largest piece of work left for wide coverage.
3. **Dual-screen handhelds** (AYN Thor, Pocket DS): use Android's `Presentation` API for the second screen. DS games are the obvious first use.
4. **Controllers:** add remapping per system and save it per controller name. Real TV boxes and Bluetooth pads still need hands-on testing.
5. **Intents to other apps:** keep the per-app tested launch recipes. Many apps can't open `content://` URIs, so the file path or a shared folder must be checked app by app.
6. **Storage size:** core downloads are small, but the CD copies are not. Show storage used in Settings and let the user clear it.
7. **Automated UI tests:** the scripted adb boot tests should become instrumented tests so every release is checked per system.
