# Google Play version

RetroForge builds two versions from one codebase (Gradle product flavours, `app/build.gradle.kts`):

| | full (GitHub) | play (Google Play) |
|---|---|---|
| Package | `com.retroforge.app` | `com.retroforge.app.play` |
| Build | `assembleFullRelease` (APK) | `bundlePlayRelease` (.aab for Play) |
| Switch | ✅ RetroForge Switch Player, Eden, Switch setup step | ❌ hidden: no Switch system, emulators or settings |
| Updates itself from GitHub | ✅ Settings → Updates | ❌ Play updates it |
| Installs other apps (ports' Download and install) | ✅ | ❌ no `REQUEST_INSTALL_PACKAGES`; Get (Obtainium or the releases page) still works |
| Donation links (Buy Me a Coffee and others) | ✅ | ❌ |
| One-tap N64 fan texture pack downloads | ✅ | ❌ (adding your own pack folder still works) |
| Everything else | ✅ | ✅ |

The two use different package names because a Play install and a GitHub install are signed with different keys: one
can't update over the other, so they install side by side.

`Store.kt` holds what the Play build leaves out (`Store.play`, `Store.hiddenSystems`, `Store.hiddenEmulators`).
`app/src/play/AndroidManifest.xml` drops the install permission.

## Why these

- **Switch:** after Nintendo's lawsuit against Yuzu (2024), Switch emulators were taken off Play. The clearest risk.
- **Updating outside Play** is against Play's Device and Network Abuse policy.
- **`REQUEST_INSTALL_PACKAGES`** is only allowed for apps whose core job is installing apps.
- **Donations** outside Play billing are a grey area in Play's Payments policy; left out to be safe.
- **Texture packs** of Nintendo games made by fans: not ours to hand out from a store app.

## Still to decide

- [x] **PS3 and PS4: kept** (the user's choice, 1 Nov 2026). RetroForge only opens the separate PS3/PS4 Player apps
      (from GitHub, not Play) and doesn't explain how to get firmware or keys. If Play objects, add `ps3`, `ps4`,
      `RETROFORGE_PS3`, `RETROFORGE_PS4` and `APS3E` to `Store.kt`.
- [x] **PC → Ports: left out** (fan rebuilds of Nintendo games). The rest of the PC tab stays: Steam, Windows games,
      GameNative, Winlator, the PC player, streaming from your PC, cloud gaming, free games.
- [x] **Keys and firmware text:** the Switch setup step and the tutorial line about Switch keys and firmware are
      left out of the Play build, as are the Ports topic and the texture pack download line.
- [x] **Search, Recently played and Favourites** leave out Switch games in the Play build.

## Google's own requirements

- [ ] Google Play developer account (one-off US$25), with identity checks: created as "Yourself", ID sent
      (1 Nov 2026); phone number to verify once the ID is approved.
- [ ] New personal accounts: a **closed test with at least 12 testers for 14 days** before the app can go public.
      Start this early.
- [x] **Privacy policy** at a public URL: https://retroforgefrontend.github.io/RetroForge/privacy/ (GitHub Pages,
      the `gh-pages` branch, 1 Nov 2026). Paste it into Play Console → App content → Privacy policy. When
      [PRIVACY_POLICY.md](PRIVACY_POLICY.md) changes, copy it to `privacy.md` on `gh-pages` too.
- [ ] **Data safety form:** answers below.
- [ ] **Foreground service types:** Play asks why `FOREGROUND_SERVICE_SPECIAL_USE`, `DATA_SYNC` and
      `MEDIA_PLAYBACK` are used (music playback, save sync, Steam downloads).
- [ ] **Target SDK:** already 37.
- [x] **Play App Signing:** upload key made (F:\RetroForge-keys, off GitHub); the play version signs only with it,
      the full version only with the GitHub key.
- [ ] **Store listing:** no Nintendo, Sony or other trademarks or logos in the icon, name, screenshots or text
      beyond saying which systems it plays; screenshots with homebrew games only (Super Tilt Bro and others).
- [ ] **Content rating questionnaire** and **target audience** (not aimed at children).
- [ ] **Version numbers:** Play needs a higher `versionCode` for every upload.

## Testing the Play build

- [x] Builds, and the unit tests pass for both versions (31 Oct 2026).
- [x] The Play APK has no `REQUEST_INSTALL_PACKAGES` and installs beside the GitHub one (emulator, 31 Oct 2026).
- [x] Settings has no Updates card and no Support card; setup skips the Switch step; the PC tab has no Ports and its
      subtitle doesn't mention them (emulator, 31 Oct 2026).
- [x] A release `.aab` (`bundlePlayRelease`) builds (37.8 MB).
- [x] Signed with the upload key (`keystore-play.properties`, not in git) (1 Nov 2026).
- [ ] Checked with Play's pre-launch report.

## Data safety answers (Play Console → App content → Data safety)

- Does your app collect or share any of the required user data types? **No.** Everything stays on the device;
  logins go only to the service they're for (Steam, RetroAchievements, ScreenScraper, RPCN), which Google counts as
  the user's own action, not collection by the app.
- Is all user data encrypted in transit? **Yes** (HTTPS). Network drives and netplay on the user's own network are
  the exceptions: say so if asked.
- Do you provide a way for users to request that their data be deleted? **Not needed** (no data is collected);
  uninstalling deletes everything.
- Ads: **No.** Target audience: **18+** (or 13+), not children.

## Store listing draft

- **App name:** RetroForge (30 characters max).
- **Short description (80):** Your own retro and modern games, films and music in one front end.
- **Full description:**

  RetroForge brings your game collection together in one place: consoles, arcade, computers and PC, with box art,
  playlists and a built-in player for most retro systems.

  • Play your own games from your phone, an SD card or a network drive on your PC.
  • A built-in player for retro systems, with save states, cheats, bezels, filters, RetroAchievements and netplay
    with friends.
  • Opens games in RetroArch and other emulator apps you install yourself.
  • Box art from libretro and ScreenScraper, scraped for the whole library at once.
  • Your Steam and Windows games, streaming from your own PC, and cloud gaming.
  • Films (including 3D) and music, with an equaliser, and internet radio.
  • Controller-friendly, works as a home screen on handhelds and TV boxes, seven languages.

  RetroForge doesn't include any games, BIOS or firmware. Use copies of games you own.

- **Screenshots:** homebrew and free games only (Super Tilt Bro, the free games list), no console makers' logos.
- **Category:** Tools or Entertainment (emulator front ends usually pick Tools).
