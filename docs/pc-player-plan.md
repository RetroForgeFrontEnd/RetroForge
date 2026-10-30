# RetroForge PC player: plan

Last updated 2026-10-30. This replaces the "PC games only through other apps" decision in
[retroforge-pc-architecture.md](retroforge-pc-architecture.md): RetroForge now also gets its own player for Windows
games (the `pc-player` module), while GameNative and Winlator shortcuts keep working as before.
Nothing here is legal advice; licences were read from each project's repository on 2026-10-30.

## Summary

- **Approach:** don't write a Windows compatibility layer. Put together the parts GameNative and Winlator already
  use (Wine/Proton built for Android, FEX for x86 code, a small Linux userland, Winlator's X server) and write
  RetroForge's own launcher, display and controls around them.
- **Licences:** every part is GPL-3.0 compatible (MIT, BSD, zlib, LGPL-2.1+, GPL-3.0). RetroForge is GPL-3.0, so it
  can ship or download all of them, provided RetroForge's source (and the source of the binaries it hands out) stays
  available. Details below.
- **First run:** about 280 MB to download, about 1.8 GB on the device. Measured from the real files, below.
- **Android's W^X rule** (apps targeting Android 10+ can't run programs from their own storage) is handled the way
  GameNative's "modern" build does it: start Wine as `/system/bin/linker64 wine …`, and preload a small library
  (`libpcexec.so`, RetroForge's own) that does the same for every program Wine starts. RetroForge keeps targeting
  SDK 37; GameNative's main build and Winlator target SDK 28 instead.
- **Graphics:** on the S22 (Exynos 2200, Xclipse 920) the Vulkan "wrapper" driver passes Vulkan to Samsung's own
  driver, with DXVK / VKD3D-Proton turning Direct3D into Vulkan. On Snapdragon, Mesa Turnip can replace Qualcomm's
  driver (faster, better Vulkan coverage).

## Phase 1: research

### The parts, and their licences

| Part | What it does | Licence | In a GPL-3.0 app? |
|---|---|---|---|
| **Wine** | Runs Windows programs (Windows API on top of Linux/Android) | LGPL-2.1-or-later | Yes. Ship it as separate files (it is), keep its source available |
| **Proton** (Valve) | Wine plus Valve's game fixes, DXVK, VKD3D-Proton | Mixed: top level BSD-3-Clause, components their own (Wine LGPL, DXVK zlib, …) | Yes |
| **FEX-Emu** | Translates x86 / x86-64 code to ARM64. With Wine's ARM64EC build it runs inside Wine as `libarm64ecfex.dll` (64-bit) and `libwow64fex.dll` (32-bit) | MIT | Yes |
| **Box64 / Box86** | The other x86 translator; Winlator's glibc builds use it | MIT | Yes |
| **DXVK** | Direct3D 8–11 → Vulkan | zlib | Yes |
| **VKD3D-Proton** | Direct3D 12 → Vulkan | LGPL-2.1 | Yes |
| **Mesa** (Turnip, Zink, the Vulkan wrapper) | Turnip: open Vulkan driver for Adreno. Zink: OpenGL on Vulkan. "Wrapper" (leegao's `bionic-vulkan-wrapper`, a Mesa fork): a Vulkan driver that hands everything to the phone's own Vulkan driver and shows frames through the X server | MIT | Yes |
| **libadrenotools** | Loads a custom Vulkan driver (Turnip) in place of Qualcomm's | BSD-2-Clause | Yes |
| **Winlator** (brunodev85) | The original Android app that put this together | Repository says LGPL-2.1; the app's own source is no longer in it (the `app` folder is a submodule pointing elsewhere) | Yes, but there's little source left to use |
| **Winlator Cmod** (coffincolors) | Community fork with full source: X server, renderer, input, launcher | MIT (`Copyright (c) 2023 BrunoSX`) | Yes. **RetroForge's X server is Cmod's, unchanged in the main** |
| **GameNative** | Winlator-derived app with Steam/Epic/GOG/Amazon, bionic Wine, SDK-36 build | GPL-3.0 | Yes (same licence). Some of its native libraries are prebuilt only (`libwinlator_11.so`, `libredirect-bionic-wx.so`), so RetroForge doesn't copy those |
| **Termux packages** | Where most of the Linux userland's libraries come from (X11, fonts, codecs) | Each package its own (mostly MIT/BSD/LGPL/GPL) | Yes |
| **Termux:X11** | Another Android X server (a Wayland-style compositor in C) | GPL-3.0 | Yes, not used: Winlator's X server is simpler to embed and is what these Wine builds are tested against |
| **PRoot** | Fakes a Linux root filesystem without root; glibc Winlator builds use it | GPL-2.0-or-later | Not needed: bionic Wine runs directly |

Wine's display: these builds use Wine's X11 driver (`winex11.so`), so RetroForge runs an X server. Wine's own Android
driver (`wineandroid.drv`) exists but isn't what any of these builds or their game fixes use.

Obligations in practice: keep the licence texts and credits in the app (add to
[THIRD_PARTY_LICENCES.md](THIRD_PARTY_LICENCES.md)), say where each downloaded part's source is, and if RetroForge
ever hosts the runtime files itself, host or link their exact sources too.

### How GameNative and Winlator package their Linux root filesystem ("imagefs")

Both unpack a folder called `imagefs` into the app's private storage and run everything from it. Measured from
GameNative's download server on 2026-10-30 (GameNative 1.3.0 is on the S22; its APK is 574 MB because it also
bundles many drivers and DXVK versions):

| File | Download | Unpacked | What's in it |
|---|---|---|---|
| `imagefs_bionic.txz` | 175 MB | 874 MB, 10,894 files | A Termux-style `usr/` built for Android's own C library (bionic): X11/xcb, Vulkan loader, fontconfig, GStreamer, FFmpeg, OpenAL, SDL2, fonts, `libandroid-sysvshm.so` (System V shared memory through the app). No Wine in it |
| `imagefs_gamenative.txz` | 159 MB | — | The glibc variant (older Winlator style: Box64 + x86-64 Wine, needs a fake root) |
| `proton-11.0-2-arm64ec-9.wcp` | 92 MB | 832 MB, 2,246 files | Proton 11 built for bionic arm64 with ARM64EC: `bin/wine`, `bin/wineserver`, `lib/wine/aarch64-unix/*.so`, `lib/wine/{aarch64,i386}-windows/*.dll`, and `prefixPack.txz` (a ready Wine prefix, 21 MB) |
| `FEXCore-2610-gn-5.wcp` | 1.3 MB | 10 MB | `libarm64ecfex.dll`, `libwow64fex.dll` (FEX 2610 + 7 GameNative patches) |
| `wrapper-gamenative-20260925.tzst` | 0.8 MB | 3 MB | `libvulkan_wrapper.so` and its manifest, plus adrenotools hooks |
| `extras.tzst` | 82 MB | — | Optional extras (not used by RetroForge yet) |
| `container_pattern_*.tzst` | 10–40 MB | — | Starting prefixes and fonts for older Proton builds (Proton 11's comes inside its package) |

`.wcp` files are tar archives (zstd or xz) with a `profile.json` saying what goes where. Paths inside the image are
built for Winlator's package (`/data/data/com.winlator/files/imagefs/…`, `com.winlator.cmod`): mostly library search
paths, which `LD_LIBRARY_PATH` overrides, plus the Vulkan wrapper's manifest, which RetroForge rewrites. The X11
socket is found through `TMPDIR` (`$TMPDIR/.X11-unix/X0`), so no paths need patching.

RetroForge's first-run download is the first four parts: **about 280 MB to download, about 1.8 GB unpacked**
(image 874 MB + Proton 832 MB + prefix + FEX + wrapper).

### Graphics: Xclipse (the S22) and Adreno

| GPU | Path GameNative / Winlator Cmod use | Notes |
|---|---|---|
| **Samsung Xclipse 920** (Exynos 2200, SoC `s5e9925`, AMD RDNA2-based) | **Vulkan wrapper** → Samsung's own Vulkan driver. Direct3D 9–11 through DXVK, 12 through VKD3D-Proton; OpenGL through Zink on top | Turnip doesn't apply (it's Adreno only). Samsung's driver has gaps and bugs that DXVK versions and wrapper options work around per game. GameNative ran a Unity game you own to its menu on this phone this way (see [pc-game-compatibility.md](pc-game-compatibility.md)) |
| **Mali** (most other Exynos, MediaTek, Tensor) | Vulkan wrapper → the phone's driver, same as Xclipse | Mali drivers lack some features DXVK wants; more games fail |
| **Adreno 6xx–8xx** (Snapdragon) | **Mesa Turnip** loaded with libadrenotools in place of Qualcomm's driver, or the wrapper on Qualcomm's own driver | Turnip is the best-supported route: newest Vulkan, most games. GameNative ships many Turnip builds (`Turnip_Gen8_V37`, `turnip26.x`…) chosen per GPU generation |
| Older or any GPU, OpenGL games | VirGL (OpenGL calls sent to the app, drawn with GLES) | Slow, but works without good Vulkan. Not in RetroForge yet |

How a frame gets to the screen: the game draws with Vulkan (directly, or through DXVK/VKD3D); the wrapper driver
renders into an Android hardware buffer and hands it to the X server over the DRI3/Present extensions; the X server
(Winlator's, in Java) shows it as an OpenGL ES texture in RetroForge's activity. Windows not using the GPU are drawn
by the X server itself from shared memory (MIT-SHM).

### Running programs under Android's rules (W^X)

Apps that target Android 10 or later can't `exec` a file in their own storage (SELinux blocks `execute_no_trans`),
but loading code from there is still allowed (it's how RetroForge's emulator cores already load). So:

1. RetroForge starts Wine as `/system/bin/linker64 <imagefs>/opt/proton/bin/wine explorer /desktop=… D:\game.exe`.
   The system linker is a system file, so it may be run; it then loads Wine from app storage.
2. `libpcexec.so` (RetroForge's, in `pc-player/src/main/cpp/pcexec.c`) is preloaded into every Wine process. It
   wraps `execve`/`execv`/`execvp`/`posix_spawn`: a program in app storage is run through the linker the same way;
   a `#!` script runs through its interpreter. It refuses `wine-preloader` (a static program the linker can't load;
   Wine carries on without it). It answers `/proc/self/exe` with Wine's real path, because under the linker that link
   names the linker, and Wine finds its own files from it.
3. GameNative's SDK-36 build does the same with its own closed `libredirect-bionic-wx.so`, which proves the idea
   works with these Wine builds.

### Staged plan

Effort is for one developer working with AI help, in focused days, and assumes the S22 for testing.

| Stage | What | Effort | Status |
|---|---|---|---|
| 1 | Research (this document) | 1 day | Done |
| 2 | **First build:** `pc-player` module, runtime download with percentage, X server, Wine launch through the linker, full-screen activity with touch mouse, Esc/Enter/keyboard, Back to quit; PC tab "Add a Windows game folder" | 2–4 days | Built. On the S22: runtime, X server, Wine, FEX and Vulkan work; Warside opens its window but hangs loading (see below) |
| 3 | **One game working well:** fix what stage 2's test shows (Wine prefix setup, graphics path on Xclipse), sound (Winlator's ALSA server or PulseAudio from the image), per-game log in the app | 1–2 weeks | Next |
| 4 | **Controls:** gamepads into Wine (Winlator's `winhandler` / evdev shim, XInput), RetroForge's controller mapping and on-screen controls reused from the retro player, mouse modes (touchpad/relative), text input | 1–2 weeks | |
| 5 | **Graphics choices:** Turnip for Adreno (libadrenotools), DXVK / VKD3D-Proton versions per game, resolution and frame-rate options, a GPU check that says up front what's likely to work | 2–3 weeks | |
| 6 | **Games from more places:** pick the .exe, play a game folder in place where Android lets RetroForge read it (SD card, `Android/data`), install from a setup .exe, Steam/GOG/Epic stores (GameNative's GPL code for Steam sign-in and downloads could be reused) | 3–6 weeks | |
| 7 | **Own the runtime:** build the image, Wine and FEX from source in RetroForge's CI (or mirror GameNative's exact builds with sources) and download them from RetroForge's GitHub releases instead of GameNative's server; runtime updates and a size/clean-up screen | 2–4 weeks | |
| 8 | **Polish:** saves and settings per game, save sync for PC games, compatibility list, dual-screen handhelds | ongoing | |

Rough total to "most small and medium Windows games you own play well on Snapdragon, and many on Xclipse": 3–5
months, as said before starting.

## Phase 2: first build

### What was built

| Piece | Where | What it does |
|---|---|---|
| Module | `pc-player/` | Android library: Kotlin launcher + Winlator Cmod's X server (Java, MIT, package `com.winlator.cmod` kept so its native code matches) |
| Runtime download | `PcRuntime.kt` | Downloads the four parts above from GameNative's server with one percentage for the whole install ("Downloading Wine (Proton 11)… 37%", then "Unpacking…"), unpacks tar.xz / tar.zst keeping symlinks, sets up the Wine prefix and FEX's DLLs, rewrites the Vulkan wrapper's manifest. Re-downloads only what's missing; reinstalls when `VERSION` changes |
| X server | `com/winlator/cmod/xserver`, `xconnector`, `sysvshm`, `renderer`, `cpp/winlator/*.c` | Winlator Cmod's X server with MIT-SHM, DRI3, Present, Sync and BIG-REQUESTS, drawn with OpenGL ES. Its VR (XR) code was removed; its Windows-side helper (`WinHandler`) and controller manager are stand-ins for now |
| Exec redirect | `cpp/pcexec.c` → `libpcexec.so` | See "Running programs under Android's rules" |
| Session | `PcSession.kt` | Starts the shared-memory and X servers on sockets inside the image, maps the game's folder to `D:` (`C:` = the prefix, `Z:` = the image), starts Wine with RetroForge's environment (wrapper Vulkan driver, FEX for x86 code, no sound drivers yet), logs Wine's output to `files/pc/last-run.log`, stops everything with `wineserver -k` |
| Player | `PcPlayerActivity.kt` | Full screen, landscape, in its own process (`:pcplayer`, so quitting ends every Wine process). Touch is a mouse (tap clicks where you touch, drag holds the button, two-finger tap right-clicks); Esc, Enter, keyboard and Quit buttons top right; Back asks before quitting; when Wine ends, shows the last lines of its log |
| PC tab | `app/.../ui/pc/PcLibrary.kt`, `PcGamesSection.kt` | "Add a Windows game folder": pick the folder, RetroForge copies it (with a percentage) into `Android/data/com.retroforge.app/files/PC games/<name>` and picks the main .exe. The game shows as "RetroForge PC player" beside GameNative and Winlator games; the first start asks before the runtime download |

### Test result (2026-10-30)

**Warside did not reach its title screen, but it runs as far as a legitimate copy can without Steam.** It draws
its publisher splash (Lavabird logo) full screen through Vulkan on the Xclipse, its C# code runs, and it stops at
its own Steam check: `SteamClient.Init` fails with "Steam isn't running … Don't own App ID". Getting past that
needs a signed-in Steam (see "Steam games" below).

What was tested, on what:

| Step | Emulator (x86_64) | Galaxy S22 (Xclipse 920, Android 16) |
|---|---|---|
| PC tab shows the game as "RetroForge PC player"; first start asks before the download | Works | Works |
| Runtime download with a percentage, unpack, prefix set-up | Works (1.8 GB) | Works (1.7 GB; 2.3 GB after Wine's first-run set-up) |
| X server, full screen, cursor, Esc/Enter/keyboard/Quit buttons, end-of-game dialog with Wine's log | Works | Works |
| Wine started through `linker64` at target SDK 37 | Stops as expected: Wine is arm64, the emulator x86_64 (now refused up front with a message) | **Works.** `wine cmd /c echo` prints; wineserver, services, explorer start through `libpcexec.so` |
| Wine's desktop drawn by RetroForge's X server | | **Works** |
| x86-64 game code through FEX (ARM64EC) | | **Works.** Warside.exe (Godot 4.5.1 + .NET 8) starts and loads .NET's runtime |
| Godot on OpenGL (its default here: ANGLE on Direct3D 11) | | Fails with Godot's own "OpenGL 3.3 or Direct3D 11" dialog: no Direct3D layer (DXVK) in the prefix yet |
| Godot on Vulkan (RetroForge now starts Godot games with `--rendering-driver vulkan --rendering-method mobile`) | | **Works:** "Vulkan 1.3.279 – Forward Mobile – Wrapper(Samsung Xclipse 920)"; the window shows Godot's dark grey loading background |
| .NET start-up | | Aborted loading Windows' ICU (`icuuc68` missing): fixed with `DOTNET_SYSTEM_GLOBALIZATION_INVARIANT=1` |
| Loading | | First hung (one thread spinning, memory flat). Cause: .NET 7+ writes JIT code through one mapping and runs it through another, and FEX kept running stale code. **Fixed** with `DOTNET_EnableWriteXorExecute=0` (now a default) |
| Game's own frames on screen | | **Works:** the Lavabird splash, drawn by Godot through Vulkan → wrapper → X server |
| Past the splash | | **Stops at the game's Steam check** (no Steam client running). GameNative's own Steam launch of Warside fails too (status 137, tested by the main session) |
| Sound | | None yet (no sound server; Godot falls back to silence) |

Things learned that the code now handles: native libraries stay inside the APK, so `libpcexec.so` is copied out
for `LD_PRELOAD`; under the linker each program's `/proc/self/exe` must name that program (wineserver finds its
language files next to itself); Winlator's X server needs a `WinHandler` even when it does nothing.

Testing aids left in place: `files/pc/last-run.log` (Wine's output), `files/pc/extra.env` (extra `KEY=VALUE`
lines for Wine's environment, e.g. `WINEDEBUG=+seh`), and Godot's own log in the prefix
(`drive_c/users/xuser/AppData/Roaming/Godot/app_userdata/<game>/logs`). The runtime and a Warside copy are still on
the S22 (2.3 GB + 383 MB in RetroForge's storage).

### Warside reached its title screen (2026-10-30, 03:43)

On the S22, in RetroForge's own player: signed in to Steam (QR), ownership confirmed, gbe_fork in place of
steam_api64.dll with its LAN networking off, .NET 8 through FEX, Godot's Vulkan renderer on the Xclipse wrapper.
The fixes that got it there, in order: `DOTNET_EnableWriteXorExecute=0` (FEX and .NET's JIT), gbe
`disable_networking=1` (its LAN interface query hangs under Wine on Android), `DOTNET_SYSTEM_GLOBALIZATION_USENLS=1`
(Wine's ICU is incomplete; invariant mode broke the game's "en" culture).

**Sound works** (confirmed by ear on the S22, 04:25): Winlator Cmod's ALSA server played through AAudio, with three
fixes for the plugin in GameNative's image: it sends samples on the socket (not in the shared memory it's given),
it asks MIN_BUFFER_SIZE (code 8) before preparing, and AAudio stream pointers on Android 14+ carry a tag in the top
byte, so they're negative as a Java long (`streamPtr != 0`, not `> 0`).

### Steam games (2026-10-30, later)

Built and tested on the S22, GameNative's way:

| Step | Result |
|---|---|
| Sign in from the PC tab's Steam card, QR code | **Works** with the Steam app on another screen (tested by mirroring the phone's QR to the PC monitor). The connection needs a ping every 15 s or Steam drops it while you approve; the sign-in has to live outside the card (it's lost if the card scrolls away while you're in the Steam app) |
| QR on the same phone | **Not possible:** the Steam app refuses a sign-in link opened from another app ("For security purposes, please scan the Steam QR code again") and its scanner is camera-only. On one phone: password + approve in the Steam app (built, not yet tried) |
| Ownership check at launch | **Works:** Steam's app ownership ticket for Warside (2368300) = OK |
| Encrypted app ticket | Warside gets `Fail`: it doesn't use encrypted tickets. Like GameNative, RetroForge carries on without one once ownership is confirmed |
| steam_api64.dll swapped for gbe_fork's (GameNative's pinned build, hash-checked), original kept as .orig; steam_settings written | **Works** |
| Warside past its Steam check | **Yes**, no more "SteamApi_Init returned false"; it then **stays on the Lavabird splash**: Godot's main loop runs, the start-up task makes no progress, no exceptions, only local sockets (gbe's LAN listener). Not yet explained. GameNative's own Steam launch of Warside also fails on this phone (killed at ~670 MB, likely memory) |

Valve emails a "new sign-in" alert (place guessed from the IP address) the first time; Steam app → Steam Guard → ⚙ →
Authorized Devices lists RetroForge's sign-in under the phone's name.

### What's next

0. **A game to its title screen:** DXVK in the prefix, then Max Payne 2 (DRM-free, D3D9) from the user's PC. Warside:
   try gbe_fork's "regular" vs "experimental" DLLs and a Wine backtrace of its start-up task.
0. **Steam library with artwork** from the signed-in account (owned games + Steam's 600x900 covers), then downloads
   from Steam (JavaSteam's depot downloader, as GameNative).
0. **On-screen controls overlay** for PC games (the user asked for it; stage 4).
0. **Streamline the PC tab** (the user asked): it now holds PC games, Steam sign-in, PC streaming, Xbox Cloud Gaming,
   ports and free games in one long page.
0. **When GameNative can go:** once RetroForge downloads Steam games, has DXVK, Epic/GOG/Amazon and controllers in PC
   games; until then it stays as a fallback.
1. **Steam games** need a signed-in Steam (now built, see above). How GameNative does it: sign-in in the app with JavaSteam (QR code
   approved in the Steam app, or email/password + Steam Guard), library and downloads through JavaSteam's depot
   downloader; at launch it **replaces the game's `steam_api64.dll` with the Goldberg Steam emulator** (ColdClient
   loader) and gives it an encrypted app ticket from the real session. The sign-in and downloads are clean; the
   DLL swap works around the game's Steam check, which needs the project owner's decision (RetroForge's docs say it
   doesn't bypass DRM). The alternative that needs no swap is running Valve's own Windows Steam client in the
   player (heavy; its built-in browser often struggles under Wine on ARM).
2. **DXVK and VKD3D-Proton in the prefix** (GameNative's `dxvk-2.x` / `vkd3d` packages, a few MB each). Needed by
   nearly every Direct3D game: Godot's default path, Max Payne 2 (D3D9), most newer games (D3D11).
3. **A non-.NET game next** to prove frames reach the screen end to end: Max Payne 2 (32-bit D3D9, also tests FEX's
   32-bit path) once DXVK is in.
4. Sound: Winlator's ALSA server (Java) or PulseAudio from the image.
5. Then stages 4–8 above.

Test games the user suggested and owns: Max Payne 2 (on the PC, not yet copied to the phone). A
small set covering each API is in the session notes: Cave Story (DirectDraw), Max Payne (D3D8), Plants vs.
Zombies (D3D9), Terraria (D3D9/XNA), Undertale (D3D11), Hollow Knight (D3D11/Unity), Super Hexagon or FTL
(OpenGL), Warside with `--rendering-driver d3d12` (D3D12), Warside (Vulkan).
