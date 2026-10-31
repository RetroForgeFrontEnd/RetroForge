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

- [ ] **PS3 and PS4 buttons.** They only open the separate RetroForge PS3/PS4 Player apps, which aren't on Play.
      Probably fine, but the first Play release could hide them too.
- [ ] **PC → Ports** (Ship of Harkinian, Zelda 3 and others): fan ports that need the user's own game. RetroForge
      only links to them. A reviewer could object; hide them in Play if it's rejected.
- [ ] **Setup and guides that mention keys or firmware** (PS3 firmware, PS4 dumps). Check the Play build's text
      doesn't explain how to get them.
- [ ] **Search and Recently played** can still show Switch games already scanned in the Play build. Hide them there
      too if a reviewer notices.

## Google's own requirements

- [ ] Google Play developer account (one-off US$25), with identity checks.
- [ ] New personal accounts: a **closed test with at least 12 testers for 14 days** before the app can go public.
      Start this early.
- [ ] **Privacy policy** at a public URL (Settings → Privacy in the app already says what's kept; turn it into a page).
- [ ] **Data safety form:** no data collected or shared; logins (Steam, RetroAchievements, ScreenScraper,
      network drives) stay on the device; anything sent goes only to the service the user signs in to.
- [ ] **Foreground service types:** Play asks why `FOREGROUND_SERVICE_SPECIAL_USE`, `DATA_SYNC` and
      `MEDIA_PLAYBACK` are used (music playback, save sync, Steam downloads).
- [ ] **Target SDK:** already 37.
- [ ] **Play App Signing:** upload with a separate upload key; keep it with the GitHub release key, off GitHub.
- [ ] **Store listing:** no Nintendo, Sony or other trademarks or logos in the icon, name, screenshots or text
      beyond saying which systems it plays; screenshots with homebrew games only (Super Tilt Bro and others).
- [ ] **Content rating questionnaire** and **target audience** (not aimed at children).
- [ ] **Version numbers:** Play needs a higher `versionCode` for every upload.

## Testing the Play build

- [x] Builds, and the unit tests pass for both versions (31 Oct 2026).
- [x] The Play APK has no `REQUEST_INSTALL_PACKAGES` and installs beside the GitHub one (emulator, 31 Oct 2026).
- [ ] Settings has no Updates card and no Support card; Retro shows no Switch; texture packs have no download list.
- [ ] A release `.aab` (`bundlePlayRelease`) built and checked with Play's pre-launch report.
