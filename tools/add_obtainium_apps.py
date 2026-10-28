"""Adds Obtainium app setups from the Obtainium Emulation Pack (github.com/RJNY/Obtainium-Emulation-Pack, Unlicense)
to retro-library/src/main/resources/standalone.json, matched by package name. Run after gen_standalone.py:

    python -I tools/add_obtainium_apps.py <obtainium-emulation-pack-vX.json> retro-library/src/main/resources/standalone.json

The setup lets RetroForge open Obtainium ready to install an emulator whose releases aren't plain GitHub releases
(Dolphin, PPSSPP, Eden, Play! …): obtainium://app/<setup>.
"""
import json, sys

pack_path, catalogue_path = sys.argv[1:3]
pack = {a["id"]: a for a in json.load(open(pack_path, encoding="utf-8"))["apps"]}
catalogue = json.load(open(catalogue_path, encoding="utf-8"))
KEEP = ("id", "url", "author", "name", "preferredApkIndex", "additionalSettings", "overrideSource")
added = []
for emulator in catalogue["emulators"]:
    app = next((pack[p["package"]] for p in emulator["packages"] if p["package"] in pack), None)
    emulator.pop("obtainiumApp", None)
    if app is None:
        continue
    emulator["obtainiumApp"] = json.dumps({k: app[k] for k in KEEP if k in app}, ensure_ascii=False, separators=(",", ":"))
    added.append(emulator["id"])
catalogue["obtainiumSource"] = "Obtainium app setups from the Obtainium Emulation Pack (github.com/RJNY/Obtainium-Emulation-Pack), Unlicense."
json.dump(catalogue, open(catalogue_path, "w", encoding="utf-8"), indent=1, ensure_ascii=False)
print(len(added), "emulators with an Obtainium setup:", ", ".join(added))
