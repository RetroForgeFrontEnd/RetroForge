# Streaming games from your own PC

The PC tab's **Stream from your PC** section plays games running on the user's own computer on the phone, the way
Moonlight does: the PC runs **Sunshine** (free, open source, Windows/Linux/macOS) or NVIDIA GameStream (older GeForce
Experience), and the phone shows the video, plays the sound and sends back controller, touch, mouse and keyboard input.

## The options looked at

| Option | What it means | Verdict |
|---|---|---|
| (a) Hand off to the separately installed Moonlight app | Moonlight's manifest has one exported activity besides its launcher: `com.limelight.ShortcutTrampoline`, which takes string extras `UUID` and `Name` (the PC) and optionally `AppId`, `AppName`, `HDR` (the game). It only works for a PC Moonlight already knows and has paired, by its uniqueid. No deep links or `ACTION_VIEW` filters. | Works, but the user installs a second app, pairs in Moonlight, and RetroForge can't list the PC's games itself without pairing a second time. Moonlight's own UI takes over. |
| (b) Build Moonlight's streaming code into RetroForge | Moonlight for Android (`com.limelight`) and its C core moonlight-common-c are GPL-3.0, the same licence as RetroForge, so they can be included as source. | **Chosen.** One app, one pairing, RetroForge's own UI for finding, pairing and choosing; Moonlight's mature stream screen (decoder, audio, input) unchanged. |
| (c) Both | (b), plus a "open in Moonlight" button when Moonlight is installed. | Not needed now; (a)'s extras are noted above if wanted later. |

## What's in the build

A new library module, `pc-stream`:

- `src/main/java/com/limelight/**`: Moonlight for Android's Java code at commit `b48494cb96bf` (moonlight-android
  `master`, 2026-09-12), package names unchanged (the native library calls back into them by name).
- `src/main/jni/moonlight-core/`: Moonlight's JNI glue plus moonlight-common-c at `874ac9548f1b` (with its enet and
  nanors submodules), built with ndk-build for arm64-v8a, armeabi-v7a and x86_64 into `libmoonlight-core.so`
  (about 5 MB per ABI). OpenSSL's libcrypto (4.0.2) and libopus come as Moonlight's prebuilt static libraries
  (x86 32-bit dropped; ~40 MB of `.a` files in the source tree).
- `src/main/res/**`: Moonlight's resources (stream screen layouts, settings, strings and translations).
- `src/main/java/com/retroforge/stream/PcStreaming.kt`: RetroForge's layer over it: saved PCs, mDNS discovery, adding
  by address, pairing, app list, quit, and the intents for the stream and its settings.
- App UI: `app/src/main/java/com/retroforge/app/ui/pc/PcStreamSection.kt`, shown on the PC tab under "PC games".

Changes to Moonlight's code (each marked with a `RetroForge:` comment, as the GPL asks for modified files):

- `Game.java`: no longer reports the PC and game as launcher shortcuts (RetroForge has none).
- `preferences/StreamSettings.java`: no longer restarts Moonlight's PC list after a language change on Android 12
  and older.
- `binding/video/MediaCodecHelper.java`: skips the Android emulator's `c2.goldfish` decoders, which reject every
  streamed frame (MediaCodec error 14), so emulators use the software decoder. No real device has them.
- `utils/ServerHelper.java`: cut down to the connection-test server address the stream screen uses.
- `org/cgutman/shieldcontrollerextensions/*`: stand-ins for Moonlight's NVIDIA SHIELD controller library (only
  published on JitPack); they recognise no controllers, so standard Android battery/rumble paths are used.
- Removed: Moonlight's PC list, app list, add-PC screen, shortcut trampoline, poster provider, grid adapters, TV
  channel and shortcut helpers, computer database and discovery/computer-manager services.
- Manifest: only the stream screen (`Game`), its settings and help pages and the USB controller driver service.

## How each piece works

- **Finding the PC**: `PcStreaming.discover()` listens for `_nvstream._tcp` mDNS announcements for 4 seconds
  (NsdManager on Android 14+, jmDNS with a multicast lock before that, as Moonlight does). Or the user types the
  address (IP or name, optional `:port`; default HTTP port 47989). RetroForge asks `/serverinfo` and saves the PC by its
  uniqueid (SharedPreferences `pc_stream_hosts`).
- **Pairing (PIN)**: RetroForge makes a 4-digit PIN and shows it; the user types it into Sunshine's web page
  (`https://localhost:47990`, PIN tab) or GeForce Experience's prompt. Moonlight's `PairingManager` does the
  challenge exchange with this phone's client certificate (generated once by `AndroidCryptoProvider`, stored in app
  files); the PC's certificate is saved with the PC and pinned for every HTTPS call after that.
- **App list**: `/applist` over HTTPS with the client certificate; each app is a chip. "Quit the running game" calls
  `/cancel` when the PC says a game is running.
- **Streaming**: starts Moonlight's `Game` activity with the PC, app and certificate as extras. It launches the app
  (`/launch` or `/resume`), then moonlight-common-c does RTSP, ENet control, RTP video/audio with FEC.
  - Video: MediaCodec hardware decoding of H.264, HEVC or AV1 (Moonlight picks by what the phone decodes well),
    up to the resolution/frame rate/bitrate set in **Stream settings**; HDR where both ends support it.
  - Audio: Opus decoded by libopus, stereo or 5.1/7.1, played through AudioTrack.
  - Controllers: Android gamepads (up to 16, with rumble, motion and touchpad where supported), Xbox controllers
    through Moonlight's own USB driver, and an on-screen controller option for touch.
  - Touch: touchscreen as a trackpad or direct mouse; mouse and keyboard pass through.
- **Back/quit path**: back (or Start+Select+L1+R1 on a controller) ends the stream and returns to the PC tab; the
  game keeps running on the PC so it can be resumed ("Resume" on the chip), or closed with "Quit the running game".

## Tested

**With a real Sunshine PC (2026-10-30).** Sunshine 2026.914 installed from LizardByte's winget package on the user's
Windows PC (Intel GPU, QuickSync H.264/HEVC encoding), streamed to the RetroForge_Bezels_Test emulator over the LAN
(PC at 192.168.0.33):

- Added by address, shown as the PC's name ("Darren"), "Not paired".
- Pair: RetroForge showed a PIN, the user typed it in Sunshine's PIN page, the card changed to "Paired." / "Ready".
- "Show games" listed Sunshine's apps (Desktop, Steam Big Picture).
- Desktop streamed full screen in landscape: 1280×720, ~60 FPS received and rendered, 0% frames dropped, 3 ms
  network latency, ~12.6 ms host processing; audio track opened (stereo Opus).
- Touch: a drag on the stream moved the PC's mouse pointer (trackpad mode).
- Back ended the stream and returned to the PC tab; the chip then read "Resume Desktop" and resumed the session.
- "Quit the running game" closed the session on the PC ("The game on the PC was closed.").
- The emulator's own decoders failed on both HEVC and H.264 (MediaCodec error 14); after the `c2.goldfish` change
  above, the default codec setting picks the software H.264 decoder and streams.

**On the Galaxy S22 (Exynos), same PC, run by the PC-player session:** "Look for PCs" found the PC by mDNS over real
Wi-Fi and added it; pairing with a new PIN gave "Ready"; Desktop streamed full screen within about a second at
1280×720×60 using the hardware HEVC decoder (`c2.exynos.hevc.decoder`) with no decoder errors; audio track opened and
Android reported active playback (phone was on silent, so not heard); back returned to the PC tab.

**Against a fake host** (before Sunshine was installed): unreachable address shows "Nothing answered at …"; a
refused pairing is reported; a failed connection shows Moonlight's "Connection Error" and OK/back return to the PC
tab; Stream settings opens.

Not tested yet: 1080p and above, touch and controllers (Bluetooth/USB) on a phone, sound actually heard,
GeForce Experience hosts.

## What's next

1. More S22 testing (claim it first): 1080p60, sound by ear, touch, a Bluetooth/USB controller.
2. Box art: `PcStreaming.boxArt()` already fetches each app's image from the PC; show covers instead of chips.
3. Wake-on-LAN for sleeping PCs (Moonlight's `nvstream/wol` code is included; needs the MAC from serverinfo saved).
4. Streaming over the internet (Sunshine's external address / IPv6), and a "network test" button.
5. Put streamed PC apps into the library and Home's "Jump back in", like other PC games.
6. Strip debug symbols from the prebuilt `.a` files or build OpenSSL from source to cut the repo size; drop the
   unused Moonlight layouts/strings left in `res`.
7. Optional (c): if Moonlight is installed, offer "open in Moonlight" through `ShortcutTrampoline`.

## Licences

| Component | Licence |
|---|---|
| Moonlight for Android (`com.limelight` code and resources) | GPL-3.0 (`pc-stream/LICENSE`) |
| moonlight-common-c | GPL-3.0 |
| ENet (moonlight-stream fork) | MIT |
| nanors (Reed-Solomon) | MIT |
| OpenSSL 4.0.2 libcrypto | Apache-2.0 |
| libopus | BSD-3-Clause |
| Bouncy Castle (bcprov/bcpkix 1.86) | MIT |
| OkHttp 5.5.1 | Apache-2.0 |
| jmDNS 3.6.4 | Apache-2.0 |
| JCodec 0.2.5 | BSD-2-Clause |

All are compatible with RetroForge's GPL-3.0. Sunshine runs on the user's PC and isn't shipped.
