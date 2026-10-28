"""RetroBat's libretro cores per system (es_systems.cfg order), kept only where libretro builds the core for Android.
Writes retro/src/main/java/com/retroforge/retro/RetroArchCoreTable.kt and prints coverage."""
import json, sys

retrobat = json.load(open(sys.argv[1]))
android = set(open(sys.argv[2]).read().split())
out = sys.argv[3]

# RetroBat core names that differ from the Android core file names.
RENAME = {"mesen-s": "mesen-s", "bsnes-jg": "bsnes_jg", "fbalpha": None, "mame": "mame"}

rows = {}
for s in retrobat:
    if s["files"] == 0:
        continue
    cores = []
    for e in s["emulators"]:
        if e["emulator"] != "libretro":
            continue
        for c in e["cores"]:
            c = RENAME.get(c, c)
            if c and c in android and c not in cores:
                cores.append(c)
    if cores:
        rows[s["id"]] = cores

def pretty(core):
    special = {"genesis_plus_gx": "Genesis Plus GX", "mupen64plus_next": "Mupen64Plus-Next", "fbneo": "FinalBurn Neo", "ppsspp": "PPSSPP",
               "dosbox_pure": "DOSBox Pure", "scummvm": "ScummVM", "puae": "PUAE (Amiga)", "vice_x64": "VICE x64", "vice_x64sc": "VICE x64sc",
               "vice_xvic": "VICE xvic", "vice_x128": "VICE x128", "cap32": "Caprice32", "hatari": "Hatari", "bluemsx": "blueMSX", "px68k": "PX68k",
               "opera": "Opera (3DO)", "flycast": "Flycast", "melondsds": "melonDS DS", "mednafen_psx_hw": "Beetle PSX HW", "swanstation": "SwanStation"}
    return special.get(core, core.replace("_", " ").title())

with open(out, "w", encoding="utf-8") as f:
    f.write("package com.retroforge.retro\n\n")
    f.write("// Generated from RetroBat's es_systems.cfg (its libretro cores per system, in its order), keeping only cores\n")
    f.write("// libretro builds for Android (buildbot.libretro.com/nightly/android/latest/arm64-v8a). Hand-picked choices in\n")
    f.write("// RetroArchCores come first; this fills in every other system. Regenerate rather than editing by hand.\n")
    f.write("internal val retroBatCores: Map<String, List<Core>> = mapOf(\n")
    for sid in sorted(rows):
        cores = ", ".join(f'Core("{c}", "{pretty(c)}")' for c in rows[sid])
        f.write(f'    "{sid}" to listOf({cores}),\n')
    f.write(")\n")

with_files = [s for s in retrobat if s["files"] > 0]
print(len(rows), "of", len(with_files), "systems have an Android RetroArch core")
print("NONE:", sorted(s["id"] for s in with_files if s["id"] not in rows))
