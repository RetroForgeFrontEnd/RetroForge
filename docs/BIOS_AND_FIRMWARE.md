# BIOS, firmware and save data

RetroForge ships **no BIOS or firmware**. You use copies of your own. Last updated 2026-10-28.

## Checking and copying BIOS files

Setup → **4. BIOS files**: pick the folder where you keep your BIOS files (often a RetroBat/Batocera `bios` folder). RetroForge then:

1. Checks it against [bios.json](../retro-library/src/main/resources/bios.json) for the systems in your library (9 rules: PS1, Sega CD / Mega-CD, PC Engine CD, Dreamcast, Lynx, ColecoVision, Famicom Disk System, GBA (optional), DS). Files are matched by name and, where libretro publishes them, by MD5 checksum: **verified**, **unrecognised version** (right name, unknown checksum) or **missing**. Rules say whether a file is required or optional and whether one of several is enough (e.g. any one PS1 region).
2. Copies the files into the **built-in player's own BIOS folder** automatically (`Android/data/com.retroforge.app/files/system`), because that folder belongs to RetroForge and needs no picking.
3. Offers to copy them into **RetroArch's `system` folder**, which you pick once.

Both copies use the names the cores expect (e.g. `coleco.rom` → `colecovision.rom`), **never overwrite** a file already there, never delete, and never download anything. Code: [Bios.kt](../retro-library/src/main/kotlin/com/retroforge/library/Bios.kt), [BiosInstaller.kt](../core-library/src/main/java/com/retroforge/core/library/BiosInstaller.kt).

Standalone emulators (DuckStation, NetherSX2, Azahar …) read BIOS/firmware from folders you set inside each app; RetroForge doesn't copy there.

Tested: the check and the RetroArch copy on the emulator (PS1 game started with the user's own BIOS); the built-in player copy on the emulator with a dummy file (then deleted).

## Save data

| Route | In-game saves | Save states | Where |
|---|---|---|---|
| Built-in player | `.srm`, written on pause, on leaving the app and on quit (written to a `.part` file, then renamed, so a crash can't leave half a save) | Slot state from the pause menu; a resume state on quit when "Carry on where you left off" is on | `Android/data/com.retroforge.app/files/saves/<system>/<game>.srm` and `…/states/<system>/<game>.<emulator>.state` / `.auto.state` |
| RetroArch | RetroArch's own | RetroArch's own (auto save/load set by RetroForge when "Carry on" is on) | RetroArch's folders |
| Standalone emulators | Their own | Their own | Their own folders |

**Portability.** In-game saves (`.srm`) are shared between the built-in player's emulators for a system and usually work across them. **Save states are not portable** between emulators (each stores its own internal memory layout), so the built-in player keeps them per emulator and never loads one emulator's state into another. Switching emulator mid-game keeps the old emulator's resume state, so switching back returns to the same moment.

Saves in `Android/data` are removed if RetroForge is uninstalled. Copy them with a file manager or a computer first. A save backup/export screen is not built yet; nor is importing RetroArch saves into the built-in player.
