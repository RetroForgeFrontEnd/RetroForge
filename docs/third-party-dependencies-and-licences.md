# Third-Party Dependencies and Licences (working register)

RetroForge currently bundles **nothing**. This register tracks what has been evaluated and the intended integration mode. Formal notices for shipped components will go in `THIRD_PARTY_LICENCES.md` when the first dependency is added.

| Project | Licence | Source of licence info | Intended integration | Status |
|---|---|---|---|---|
| DroidDeck | GPL-3.0 | `LICENSE` file, commit 4f58230 | Separate app, launched by intent; no code copied | Verified by source inspection |
| DroidDeck runtime components | Mixed (see [pc-runtime-licences.md](pc-runtime-licences.md)) | DroidDeck README | Not shipped by RetroForge | Partially verified |
| RetroBat | `G:\Retrobat\license.txt` (not yet read in full) | local file | Reference only; no code/assets reused | Not reviewed |
| RetroBat-installed ES themes (7) | Each theme's own licence | — | Reference only until checked | Not reviewed |
| RetroArch (Android) | GPL-3.0 | general knowledge — verify | External app via intent | Not verified |
| AndroidX / Jetpack Compose / Room / Hilt | Apache-2.0 | general knowledge — verify on add | Bundled library | Planned |
