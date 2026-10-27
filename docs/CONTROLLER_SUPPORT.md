# Controller Support

Scope: RetroForge's own menus. Emulators and DroidDeck keep their own controller configuration; RetroForge does not push mappings into them.

## Default mapping (Milestone A)

| Input | Action |
|---|---|
| D-pad / arrow keys / left stick (as D-pad) | Move focus (Compose focus traversal) |
| A, Enter, D-pad centre | Select |
| B, Back, Escape | Back |
| L1 / R1 | Previous / next section (wraps) |

Implementation: `ControllerKeys` (core-ui) maps key codes to commands; `MainActivity.onKeyDown/onKeyUp` handles only buttons the UI did not consume, translating A to D-pad centre itself instead of relying on each vendor's key-character-map fallbacks.

Focus: every interactive and readable card is focusable and draws a 3dp orange ring when focused. On devices without touch, the current section tab receives focus at start.

## Evidence

Simulated key events on the emulator: see [TESTING.md](TESTING.md). **No physical controller has been tested yet.**

## Planned

- Button remapping UI (Phase 11)
- Detecting controller type for on-screen button glyphs (Xbox / PlayStation / Nintendo layouts swap A/B positions)
- Testing with Bluetooth and USB gamepads and an Android TV remote
