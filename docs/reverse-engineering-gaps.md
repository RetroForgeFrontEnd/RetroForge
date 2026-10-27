# Reverse-Engineering Gaps

Things we know exist but have not confirmed in detail. Each must be closed with evidence before relying on it.

| Gap | Why it matters | How to close |
|---|---|---|
| RetroArch Android launch intent: exact activity, extras, content-URI support, return behaviour | Milestone D depends on it | Read RetroArch Android source; test on device |
| `emulatorLauncher.exe` per-system config generation (what it writes for each emulator) | Explains RetroBat behaviour we may want to mirror (e.g., per-system shaders) | Read RetroBat's open-source emulatorLauncher repo rather than decompiling |
| ES-DE current folder names and `es_systems.xml` on Android | Alias table | Read ES-DE source |
| DroidDeck behaviour after an intent launch (return to caller? session reuse?) | PC UX | Device test (PoC step 3) |
| DroidDeck variant package names (`tools/release/variants.txt`) | Detect non-default installs | Read file |
| WinNative / Bannerlator licences | Licence register completeness | Inspect upstream repos |
| `G:` filesystem type | Android mountability | `fsutil fsinfo volumeinfo G:` (read-only) |
| `.steam` shortcut file format in `roms/steam` | Seeding PC entries with Steam app ids | Read the file |
| RetroBat `license.txt` and theme licences | Reuse permissions | Read files |
