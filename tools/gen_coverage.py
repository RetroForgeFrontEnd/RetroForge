"""docs/SYSTEM_COVERAGE.md: every system folder in the user's RetroBat roms, and how RetroForge can play it on Android."""
import json, re, sys

retrobat = [s for s in json.load(open(sys.argv[1])) if s["files"] > 0]
core_table = open(sys.argv[2], encoding="utf-8").read()
handpicked = open(sys.argv[3], encoding="utf-8").read()
standalone = json.load(open(sys.argv[4], encoding="utf-8"))
out = sys.argv[5]

ra = {}
# Every RetroBat system is in the generated table; the app also puts its hand-picked core first (RetroArchCores).
for sid, body in re.findall(r'"([^"]+)" to listOf\((.*?)\),?\n', core_table):
    names = re.findall(r'Core\("[^"]+", "([^"]+)"\)', body)
    ra.setdefault(sid, [])
    for n in names:
        if n not in ra[sid]:
            ra[sid].append(n)
sa = {k: [o["label"] for o in v] for k, v in standalone["systems"].items()}

# Launch-tested on the Android emulator (docs/TESTING.md).
TESTED = {"psp": "Pinball Dreams in PPSSPP (standalone)", "snes": "Star Fox, Super Mario World (.7z), 3 Ninjas (auto save/load)", "nes": "gameplay", "psx": "Resident Evil 2 (2 discs), Batman Forever (RetroForge CHD), Command & Conquer (disc swap)"}
NOTES = {
    "teknoparrot": "Windows-only arcade loader; no Android equivalent.",
    "Windows": "PC games: RetroForge's PC section (DroidDeck), or Winlator/GameNative outside RetroForge.",
    "steam": "PC games: RetroForge's PC section (DroidDeck).",
    "mugen": "Windows engine; no Android port.",
    "xbox": "No free, working Android emulator.",
    "ps3": "aPS3e is early and needs a high-end device.",
    "switch": "Eden needs your own console keys and firmware; RetroForge provides none.",
    "fpinball": "Future Pinball is Windows-only.",
    "vpinball": "Visual Pinball has an Android build that ES-DE launches with injected files; not supported by RetroForge yet.",
    "hikaru": "Only DEMUL (Windows) emulates Hikaru.",
    "model2": "MAME4droid's Model 2 support is limited.",
    "triforce": "Dolphin's Triforce support is limited.",
}
PORT = "PC game port: RetroBat runs a Windows build. Android ports of some exist as their own apps, but RetroForge can't launch them yet."
for k in ("openjazz", "ports", "halflife", "eduke32", "sonicretro", "rtcw", "pdark", "sonic-mania"):
    NOTES[k] = PORT
NOTES["singe"] = "Hypseus Singe (laserdisc games) has no Android build."

def status(s):
    if s["id"] in TESTED:
        return "Launch tested"
    if s["id"] in ra or s["id"] in sa:
        return "Should work (not tested)"
    return "No Android option"

rows = sorted(retrobat, key=lambda s: (status(s) == "No Android option", -s["files"]))
counts = {}
for s in retrobat:
    counts[status(s)] = counts.get(status(s), 0) + 1

with open(out, "w", encoding="utf-8") as f:
    f.write("# System coverage\n\n")
    f.write("Every system folder with games in the RetroBat collection (`G:\\Retrobat\\roms`, read-only, 2026-10-28), and how RetroForge plays it on Android.\n")
    f.write("Generated from the same tables the app uses: RetroBat's own core choices (`es_systems.cfg`) filtered to cores libretro builds for Android, and the free standalone emulators in `retro-library/src/main/resources/standalone.json` (launch commands adapted from ES-DE, MIT).\n\n")
    f.write("| Status | Systems |\n|---|---|\n")
    for k in ("Launch tested", "Should work (not tested)", "No Android option"):
        f.write(f"| {k} | {counts.get(k, 0)} |\n")
    f.write(f"| **Total** | **{len(retrobat)}** |\n\n")
    f.write("\"Should work\" means RetroForge has a launch route; it has not been tried with a real game on a device yet. Many need BIOS files (see BIOS checker) or the emulator to be installed.\n\n")
    f.write("| System | Files | Status | RetroArch cores (Android) | Free standalone emulators | Notes |\n|---|---|---|---|---|---|\n")
    for s in rows:
        st = status(s)
        note = TESTED.get(s["id"]) or NOTES.get(s["id"], "")
        f.write(f"| {s['name']} (`{s['id']}`) | {s['files']} | {st} | {', '.join(ra.get(s['id'], [])) or '—'} | {', '.join(sa.get(s['id'], [])) or '—'} | {note} |\n")
    f.write("\nFile counts include every non-media file, so multi-file games (cue/bin, game folders) count more than once.\n")
print(counts)
