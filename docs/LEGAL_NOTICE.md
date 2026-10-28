# Legal Notice (games, BIOS and firmware)

RetroForge **does not include, sell, host or download** any games (ROMs or disc images), BIOS or firmware files, and must never do so in any release, catalogue or update.

- Users need their own copies, obtained legally (for example, made from cartridges, discs or consoles they own). Laws differ by country; users are responsible for the files they use.
- RetroForge only reads folders the user chooses. It never copies BIOS or games to other apps by itself.
- Emulators (RetroArch and others) are separate apps from their own developers, installed by the user.
- Nintendo, Sony, Sega and other names are trademarks of their owners; RetroForge is not affiliated with or endorsed by them.

## Where users see this

- First launch: a full-screen notice that must be acknowledged before the app can be used (`app/.../ui/LegalNotice.kt`, `LEGAL_NOTICE_VERSION`; bump the version to show a changed notice again).
- Always: Settings → "Games, BIOS and the law".
- Store listing and README at release (to do).

## Rules for contributors

- No ROMs, BIOS, firmware or keys in the repository, test fixtures, catalogues or app assets. Tests use synthetic data; real files stay in the git-ignored `patch-test/` folder.
- Hack catalogues may link to patch pages only (`docs/hack-catalogue-format.md`).
- Features that help users place their own BIOS (e.g. a BIOS checker) may read the user's folders and report what's missing, but must not download BIOS from anywhere.

Not legal advice. Get proper legal review before a public release.
