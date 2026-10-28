"""Builds retro-artwork/src/main/resources/screenscraper_systems.json: RetroBat system id -> ScreenScraper system id.

Uses ES-DE's PlatformId.h/.cpp (enum order and platform names) and ScreenScraper.cpp (platform -> ScreenScraper id),
MIT licence. Usage: gen_screenscraper.py PlatformId.h PlatformId.cpp ScreenScraper.cpp rb_ids.json out.json
"""
import json, re, sys

header, names_cpp, scraper_cpp, rb_ids_path, out = sys.argv[1:6]

enum_body = re.search(r"enum PlatformId : unsigned int \{(.*?)\};", open(header, encoding="utf-8").read(), re.S).group(1)
enum = [m.group(1) for m in re.finditer(r"^\s*([A-Z0-9_]+)\s*(?:=\s*\d+)?\s*,", enum_body, re.M)]
names_body = re.search(r"platformNames \{(.*?)\};", open(names_cpp, encoding="utf-8").read(), re.S).group(1)
names = re.findall(r'"([^"]+)"', names_body)
# The names list ends with "invalid", standing for PLATFORM_COUNT (which has no trailing comma in the enum).
if names[-1] == "invalid":
    names = names[:-1]
if len(enum) != len(names):
    sys.exit(f"enum ({len(enum)}) and names ({len(names)}) differ; check the parsing")
platform_name = dict(zip(enum, names))

ss = {platform_name[k]: int(v) for k, v in re.findall(r"\{([A-Z0-9_]+), (\d+)\}", open(scraper_cpp, encoding="utf-8").read()) if k in platform_name}

# RetroBat ids that differ from ES-DE platform names (same table as gen_standalone.py, plus platform-level names).
ALIAS = {
    "gamecube": "gc", "megacd": "segacd", "megadrive": "genesis", "megadrive-msu": "genesis", "snes-msu1": "snes",
    "wswan": "wonderswan", "wswanc": "wonderswancolor", "c20": "vic20", "msx1": "msx", "msx2+": "msx2", "segastv": "stv",
    "astrocade": "astrocde", "sg1000": "sg-1000", "lynx": "atarilynx", "3ds": "n3ds", "jaguar": "atarijaguar",
    "jaguarcd": "atarijaguarcd", "amiga1200": "amiga", "fbneo": "arcade", "mame": "arcade", "cps1": "arcade",
    "cps2": "arcade", "cps3": "arcade", "naomi2": "naomi", "neogeo64": "arcade", "model2": "arcade", "model3": "arcade",
    "daphne": "daphne", "satellaview": "satellaview", "sufami": "sufami", "msxturbor": "msxturbor",
}
result = {}
for rb in json.load(open(rb_ids_path)):
    sid = ss.get(ALIAS.get(rb, rb)) or ss.get(rb)
    if sid:
        result[rb] = sid
json.dump({
    "source": "System ids from ES-DE Frontend's ScreenScraper scraper (es-app/src/scrapers/ScreenScraper.cpp), MIT licence.",
    "systems": dict(sorted(result.items())),
}, open(out, "w", encoding="utf-8"), indent=1)
print(len(result), "systems mapped;", "missing:", sorted(set(json.load(open(rb_ids_path))) - set(result)))
