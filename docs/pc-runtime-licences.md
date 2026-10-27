# PC Runtime Licences

Status: from DroidDeck's `LICENSE` file and README credits (commit `4f58230`, 2026-10-28). Individual component licence files were **not** each opened; versions are whatever DroidDeck pins. **Re-verify each upstream licence before redistributing anything.** Nothing here is legal advice.

## Under the recommended design (separate DroidDeck app)

RetroForge ships **none** of these. It sends an Android intent to an app the user installed. Obligations below apply to DroidDeck, not to RetroForge.
Open question (unresolved, recommend review if RetroForge is ever distributed commercially): confirm that intent-based launching of a GPL-3.0 app does not make RetroForge a derivative work. Prevailing FSF guidance treats communication between separate programs via exec/IPC at arm's length as not combining them.

## Component table

| Component | Licence (as stated by DroidDeck) | Notes |
|---|---|---|
| DroidDeck app | GPL-3.0 | Embedding any of its code → RetroForge must be GPL-3.0 and ship source |
| WinNative / Bannerlator (runtime, shim, input base) | Not stated in README | **Unverified** |
| FEX-Emu | MIT | x86-64 → ARM64 translation |
| Proton / Wine (Valve ARM64 build) | LGPL-2.1-or-later | |
| Mesa Turnip | MIT | Adreno-only Vulkan driver |
| PRoot | GPL-2.0-or-later | |
| gamescope | BSD-2-Clause | |
| PipeWire, WirePlumber | MIT | |
| DirectAudio PulseAudio sink | LGPL-2.1-or-later | |
| Flatpak | LGPL-2.1 | |
| uruntime, sharun | MIT | |
| Legendary (Epic), gogdl (GOG) adaptations | Not stated in README; believed GPL-3.0 for both — **unverified** | Store engines |
| Steam client, Steam Linux Runtime | Proprietary (Valve) | Downloaded from Valve at first launch; never redistribute |
| Lossless Scaling shaders | Proprietary | Not shipped; user supplies own copy |
| VITURE SDK | Proprietary | Not included |
