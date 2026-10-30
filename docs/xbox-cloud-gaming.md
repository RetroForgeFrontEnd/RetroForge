# Xbox Cloud Gaming (PC tab)

PC tab → **Cloud gaming → Xbox Cloud Gaming** opens `XboxCloudActivity`: Microsoft's own site, https://www.xbox.com/play,
full screen in landscape in an Android WebView, with the **Better xCloud** userscript added.

## How it works

- **Same approach as Better xCloud's own Android app.** Its author describes that app as "a web app with the Better
  xCloud userscript integrated" (better-xcloud.github.io/android), i.e. a WebView on xbox.com/play. RetroForge does the
  same with the published userscript; it doesn't copy or imitate the (not yet open-source) app.
- **Userscript:** `app/src/main/assets/xcloud/better-xcloud.user.js`, v6.7.12, the unchanged release asset from
  https://github.com/redphx/better-xcloud/releases. MIT; notice in `core-ui/src/main/assets/licences/better-xcloud-MIT.txt`
  and Settings → Licences. Its header says `@grant none`, `@run-at document-start`, so no Tampermonkey APIs are needed.
- **Injection:** `WebViewCompat.addDocumentStartJavaScript` (androidx.webkit), limited to `https://www.xbox.com`, and
  inside the script only on the paths its `@match`/`@exclude` lines name. It runs wrapped in a function, as Tampermonkey
  does. Before it, `window.BX_FLAGS = { DeviceInfo: { deviceType: 'android' } }` tells it it's on Android (that's the
  flag the script reads for Android defaults). WebViews too old for document-start scripts fall back to
  `evaluateJavascript` when the page starts, which may miss some of its patches.
- RetroForge does **not** provide Better xCloud's private `AppInterface` bridge (the Android app's native features:
  native mouse and keyboard, device volume shortcuts, shortcuts to games). Without it the script behaves as it does in
  a mobile browser.
- **Sign-in:** the user signs in on Microsoft's own pages (login.live.com etc.) inside the WebView. Cookies, including
  third-party ones for the sign-in hand-off, are kept, so they stay signed in. RetroForge never reads or types the
  password. Links to other sites open in the user's browser.
- **Controllers:** the WebView has focus, so key and motion events from controllers reach Chromium's Gamepad API.
- **Touch:** goes to the page as in a browser; xCloud shows its own touch controls in games that support them, and
  Better xCloud can turn them on for all games (its settings, Touch controller).
- **Back** asks "Leave Xbox Cloud Gaming?" (Leave / Stay, and Previous page when there is one).
- Microphone and camera requests are refused (no party chat).

## Microsoft's terms and other doubts

- Everything is streamed through Microsoft's own site with the user's own account and Game Pass plan; RetroForge
  doesn't call Xbox APIs itself, proxy the stream or handle tokens.
- No spoofing is added by RetroForge: the WebView's own user agent is used. Better xCloud has its own optional
  user-agent setting; that's the user's choice in its settings.
- Better xCloud's site says xCloud's developers have said they "don't ban people for using a third party client or
  extension", and also "Use it at your own risk". That is the author's report, not a written Microsoft policy:
  Microsoft's Services Agreement could in principle cover modified clients. **Doubtful, but the risk is to be stated,
  not hidden** — the PC tab card says it isn't made or endorsed by Microsoft.
- "Xbox" and the Xbox logos are Microsoft trademarks; RetroForge only uses the name to say what the card opens and
  shows no Xbox logo.

## Test results

**Android emulator (API 37, x86_64, WebView 145), 2026-10-30:**

- Works: PC tab shows Cloud gaming → Xbox Cloud Gaming; the card opens the screen in landscape, full screen.
  xbox.com/play loads (Microsoft's cookie banner, Cloud Gaming home with game tiles), and Better xCloud is active:
  its "BETTER XCLOUD" settings button appears next to the profile picture. xCloud's own gamepad navigation starts
  ("[GamepadNavigation] Initializing" in the console), so the page sees the Gamepad API.
- Problem: the first version ran in the main process and got "RetroForge isn't responding" while the page loaded; a
  stack sample showed the main thread recomposing RetroForge's own screens behind it after the rotation to landscape.
  The screen now runs in its own process (`:xcloud`) like the built-in player. Not re-tested on the emulator yet
  because another session was installing its own build there at the same time.
- Not tested: sign-in (needs the user's own Microsoft account, typed by them), streaming a game (needs Game Pass with
  cloud gaming; the emulator has no hardware video decode worth testing on), a real controller, touch controls.

**Galaxy S22:** not tested yet (other sessions test on it; waiting for it to be free and for the user to sign in).

## What doesn't work / not done

- Better xCloud's Android-app-only features (`AppInterface`): native mouse and keyboard, device volume shortcuts,
  game shortcuts, screenshots saved to the gallery, its own vibration. Phone vibration from the page isn't
  available in a WebView either.
- Party chat (microphone) is refused on purpose.
- The bundled script doesn't update itself; Better xCloud's "new version" button links to GitHub. Updating means
  replacing `assets/xcloud/better-xcloud.user.js` (and the version in THIRD_PARTY_LICENCES.md).
- Android 16+ ignores the fixed landscape orientation on large screens (tablets, foldables unfolded); xCloud works
  in either orientation, so this is cosmetic.

## More cloud gaming (PC tab)

Beside the Xbox card, "More cloud gaming" opens other services in their own official apps or sites; RetroForge
doesn't embed them or touch their sign-in:

| Service | Opens | When not installed |
|---|---|---|
| NVIDIA GeForce NOW | its app, `com.nvidia.geforcenow` | its Play Store page |
| Boosteroid | its app, `com.boosteroid.streaming` | its Play Store page |
| Amazon Luna | luna.amazon.com in the browser (Luna has no Play Store app for phones) | — |

Tested on the emulator: "Get GeForce NOW" opens the Play Store page, "Open Amazon Luna" opens the site in Chrome.
The button says Open instead of Get once the app is installed (checked again when RetroForge comes back to the front).
