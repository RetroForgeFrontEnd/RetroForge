"""Builds retro-library/src/main/resources/standalone.json from ES-DE's Android es_systems.xml / es_find_rules.xml (MIT)."""
import json, sys
import xml.etree.ElementTree as ET

systems_xml, rules_xml, out = sys.argv[1:4]

# Free emulators only (no purchase needed to play), with their official download page and, where releases are on
# GitHub, the URL Obtainium tracks.
FREE = {
    "DUCKSTATION": ("DuckStation", "https://play.google.com/store/apps/details?id=com.github.stenzek.duckstation", None),
    "PPSSPP": ("PPSSPP", "https://www.ppsspp.org/download/", None),
    "DOLPHIN": ("Dolphin", "https://dolphin-emu.org/download/", None),
    "AZAHAR": ("Azahar", "https://github.com/azahar-emu/azahar/releases", "https://github.com/azahar-emu/azahar"),
    "M64PLUS-FZ": ("M64Plus FZ", "https://play.google.com/store/apps/details?id=org.mupen64plusae.v3.fzurita", None),
    "MUPEN64PLUS-AE": ("Mupen64Plus AE", "https://github.com/mupen64plus-ae/mupen64plus-ae/releases", "https://github.com/mupen64plus-ae/mupen64plus-ae"),
    "MELONDS": ("melonDS", "https://github.com/rafaelvcaetano/melonDS-android/releases", "https://github.com/rafaelvcaetano/melonDS-android"),
    "NOODS": ("NooDS", "https://github.com/Hydr8gon/NooDS/releases", "https://github.com/Hydr8gon/NooDS"),
    "SKYEMU": ("SkyEmu", "https://github.com/skylersaleh/SkyEmu/releases", "https://github.com/skylersaleh/SkyEmu"),
    "FLYCAST": ("Flycast", "https://github.com/flyinghead/flycast/releases", "https://github.com/flyinghead/flycast"),
    "REDREAM": ("Redream", "https://redream.io/download", None),
    "YABASANSHIRO-2": ("Yaba Sanshiro 2", "https://play.google.com/store/apps/details?id=org.devmiyax.yabasanshioro2", None),
    "SNES9X-EXPLUS": ("Snes9x EX+", "https://github.com/Rakashazi/emu-ex-plus-alpha/releases", "https://github.com/Rakashazi/emu-ex-plus-alpha"),
    "NETHERSX2": ("NetherSX2", "https://github.com/Trixarian/NetherSX2-patch", None),
    "ARMSX2": ("ARMSX2", "https://github.com/ARMSX2/ARMSX2/releases", "https://github.com/ARMSX2/ARMSX2"),
    "PLAY!": ("Play!", "https://purei.org/downloads.php", None),
    "EDEN": ("Eden", "https://eden-emu.dev", None),
    "MAME4DROID-CURRENT": ("MAME4droid", "https://github.com/seleuco/MAME4droid-current/releases", "https://github.com/seleuco/MAME4droid-current"),
    "SCUMMVM": ("ScummVM", "https://www.scummvm.org/downloads/", None),
    "OPENBOR": ("OpenBOR", "https://github.com/DCurrent/openbor/releases", "https://github.com/DCurrent/openbor"),
    "J2ME-LOADER": ("J2ME Loader", "https://github.com/nikita36078/J2ME-Loader/releases", "https://github.com/nikita36078/J2ME-Loader"),
    "APS3E": ("aPS3e", "https://github.com/aenu1/aps3e/releases", "https://github.com/aenu1/aps3e"),
}
# Package names ES-DE also lists that we don't want to start (a renamed build of Eden posing as another app).
SKIP_PACKAGES = {"com.miHoYo.Yuanshen"}
# Tokens we can't fill (ES-DE writes injected files or uses its own file provider).
UNSUPPORTED = ("%INJECT%", "%FILEINJECT%", "%ANDROIDAPP%", "%EXTRAARRAY_")
# RetroBat system id -> ES-DE system id, where they differ.
ALIAS = {
    "gamecube": "gc", "megacd": "segacd", "megadrive": "genesis", "megadrive-msu": "genesis", "snes-msu1": "snes",
    "wswan": "wonderswan", "wswanc": "wonderswancolor", "c20": "vic20", "msx2+": "msx2", "segastv": "stv",
    "astrocade": "astrocde", "neogeo64": "hyperneogeo64", "sg1000": "sg-1000", "lynx": "atarilynx", "3ds": "n3ds",
    "jaguar": "atarijaguar", "jaguarcd": "atarijaguarcd", "Windows": "windows",
}

rules = ET.parse(rules_xml).getroot()
emulators = {}
for e in rules.findall("emulator"):
    key = e.get("name")
    if key not in FREE:
        continue
    name, page, obtainium = FREE[key]
    packages = []
    for entry in e.findall("./rule/entry"):
        pkg, _, activity = entry.text.partition("/")
        if pkg in SKIP_PACKAGES:
            continue
        packages.append({"package": pkg, "activity": (pkg + activity) if activity.startswith(".") else activity})
    emulators[key] = {"id": key, "name": name, "page": page, "obtainium": obtainium, "packages": packages}

esde = {s.findtext("name"): s for s in ET.parse(systems_xml).getroot().findall("system")}
retrobat_ids = json.load(open(sys.argv[4]))
systems = {}
for rb in retrobat_ids:
    s = esde.get(ALIAS.get(rb, rb)) or esde.get(rb)
    if s is None:
        continue
    options = []
    for c in s.findall("command"):
        text = c.text or ""
        if "%EMULATOR_RETROARCH%" in text or any(u in text for u in UNSUPPORTED):
            continue
        key = text.split()[0].strip("%")[len("EMULATOR_"):] if text.startswith("%EMULATOR_") else None
        if key not in emulators:
            continue
        options.append({"emulator": key, "label": (c.get("label") or "").replace(" (Standalone)", ""), "command": text.split(None, 1)[1] if " " in text else ""})
    if options:
        systems[rb] = options

used = {o["emulator"] for opts in systems.values() for o in opts}
json.dump({
    "source": "Launch commands and package names adapted from ES-DE Frontend for Android (resources/systems/android), "
              "MIT licence, Copyright (c) 2024-2026 Northwestern Software AB, (c) 2020-2024 Leon Styhre.",
    "emulators": [emulators[k] for k in sorted(used)],
    "systems": systems,
}, open(out, "w", encoding="utf-8"), indent=1, ensure_ascii=False)
print(len(used), "emulators,", len(systems), "systems")
