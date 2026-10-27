# Security and Privacy

Status: covers what exists today (Milestone A, ROM Patcher, Hacks tab). Updated as features land.

## Network access

RetroForge declares `android.permission.INTERNET` for **one** purpose: downloading a hack catalogue when the user adds or updates one from a link. Nothing else connects to the network.

- No analytics, telemetry, crash reporting or advertising SDKs.
- No accounts and no sign-in.
- Catalogue downloads: HTTPS only, user-confirmed, no redirects, 2 MB cap ([hack-catalogue-format.md](hack-catalogue-format.md#network-behaviour)).
- Opening a hack's official page, or DroidDeck's releases page, hands the link to the browser; the browser's own privacy settings apply.

## Files and storage

- ROMs and patches are opened read-only through Android's file picker (Storage Access Framework). RetroForge does not request broad storage permission.
- Patched ROMs are written only to a new file the user creates through the system save dialog.
- App-private data: settings (DataStore) and added catalogues (`files/catalogues/`). App backup and device transfer are disabled because this data refers to paths on one device.

## Untrusted input

| Input | Handling |
|---|---|
| Patch files (IPS/BPS/UPS) | Parsed with bounds checks; output capped at 256 MB; malformed patches are reported, never crash |
| Zip/7z archives | Read in memory with per-entry size caps; nothing extracted to disk; nothing executed |
| Readmes | Decoded as text (UTF-8, else Latin-1), control characters removed, 64 KB cap, displayed as plain text only |
| Catalogue JSON | Size-capped, validated field by field; links must be `https://`; ids restricted to safe characters before use as file names; text shown as plain text only |

## Permissions

| Permission | Why |
|---|---|
| `INTERNET` | Catalogue download (above) |
| `<queries>` for `com.droiddeck.launcher` | Detect whether DroidDeck is installed (PC section) |
