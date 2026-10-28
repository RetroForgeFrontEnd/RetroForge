"""Writes docs/USER_GUIDE.md from the in-app tutorial (TutorialScreen.kt), so the two never disagree."""
import re

src = open("app/src/main/java/com/retroforge/app/ui/settings/TutorialScreen.kt", encoding="utf-8").read()
string = r'"((?:[^"\\]|\\.)*)"'
out = ["# RetroForge user guide", "",
       "Generated from the in-app tutorial (Settings → How to use RetroForge) by `tools/gen_user_guide.py`; edit `TutorialScreen.kt`, then regenerate.", ""]
for title, body in re.findall(r'Topic\(' + string + r', listOf\((.*?)\)\),', src, re.S):
    out += [f"## {title}", ""]
    out += [f"- {s.encode().decode('unicode_escape').encode('latin-1').decode('utf-8')}" for s in re.findall(string, body)]
    out.append("")
out += ["## Learn more", "",
        "- Retro Game Corps: [Android Emulation Starter Guide](https://retrogamecorps.com/2022/03/13/android-emulation-starter-guide/), "
        "[Dual-Screen Android Handheld Guide](https://retrogamecorps.com/2025/10/27/dual-screen-android-handheld-guide/), "
        "[all guides](https://retrogamecorps.com/guides/).", ""]
open("docs/USER_GUIDE.md", "w", encoding="utf-8").write("\n".join(out))
print(sum(1 for line in out if line.startswith("## ")), "sections")
