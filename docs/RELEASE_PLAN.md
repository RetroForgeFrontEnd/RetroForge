# Release plan

Written 2026-10-28. Not legal or tax advice: get both before taking money.

## First beta (0.5.0-beta): what it needs

| Must have | Status |
|---|---|
| Works on at least 3 real devices (a mid-range phone, a high-end phone, a handheld or tablet) | Not started: only the Android emulator so far |
| The smoke test in [TESTING.md](TESTING.md#release-test-plan) passes on each | Not started |
| Release build signed with a release key kept off GitHub (and backed up: losing it means users can't update) | Not started |
| In-app licences screen (MIT notice for ES-DE data, Apache/0BSD libraries, theme and artwork credits) | Not started |
| Privacy policy page (what goes to libretro, SteamGridDB, ScreenScraper, nothing else; no tracking) | Draft content exists in [SECURITY_AND_PRIVACY.md](SECURITY_AND_PRIVACY.md) |
| A way for testers to report bugs (GitHub Issues template; in-app "Report a problem" link with app/device version) | Not started |
| Version name/number, changelog | Not started |
| Features that need permission switched off or clearly marked (ScreenScraper without a developer ID already says "not available") | Done |

Nice to have for the beta: the in-game edge-swipe menu, theme engine v2, the feature comparison against other frontends.

## How to release

1. **Closed beta on GitHub Releases** (signed APK). Free, immediate, and Obtainium can track it, so testers get updates automatically. Add RetroForge to the Obtainium Emulation Pack later.
2. **Google Play** when the beta is stable. New personal developer accounts must run a closed test (currently 12 testers for 14 days; check Google's current rule) before production. Play's emulator policy is fine for apps that ship no games or BIOS; avoid store text that mentions piracy, specific commercial games or console makers' logos.
3. **F-Droid** only if the source is published under an open licence (F-Droid builds from source and rejects non-free dependencies).

## Funding without charging for the app

Charging £2 up front would make RetroForge a commercial app: ScreenScraper's API is only for entirely free apps unless they agree otherwise, SteamGridDB's terms are personal and non-commercial, and libretro's box art is the publishers' artwork. Options that keep the app free:

| Option | How it works | Fits the services' terms? |
|---|---|---|
| Donations (Ko-fi, GitHub Sponsors, Patreon, Buy Me a Coffee) | Free app; a "Support development" link in About | Yes (the app stays free) |
| Patreon with supporter perks that aren't features | Name in credits, vote on the roadmap, behind-the-scenes posts, Discord role | Generally yes |
| Patreon early access (like Socket) | Supporters get new builds first; everyone gets them later | Grey area: ask ScreenScraper and SteamGridDB first |
| Paid app or paid features | £2 upfront or unlocks | Only with ScreenScraper's and SteamGridDB's written permission, or with those features removed from the paid version |

Recommendation: release free, with a donation link and a Patreon for perks that aren't features. Ask ScreenScraper and SteamGridDB about early access before trying it.
