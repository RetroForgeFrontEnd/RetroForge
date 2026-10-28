# RetroForge theme format (version 1)

A theme is one small JSON file (64 KB at most). Themes are data only: nothing in them is executed, nothing is downloaded, and every value is checked before use. Parser and rules: `theme-engine/src/main/kotlin/com/retroforge/theme/ThemeSpec.kt`. The five built-in themes in `theme-engine/src/main/resources/themes/` are complete examples (CC0).

```json
{
  "format": "retroforge-theme",
  "version": 1,
  "id": "my-theme",
  "name": "My Theme",
  "author": "You",
  "description": "One or two sentences.",
  "license": "CC0-1.0",
  "dark":  { "background": "#0B1020", "surface": "#151C2E", "primary": "#FF8A2A", "secondary": "#3ED6FF", "text": "#E6ECF7" },
  "light": { "background": "#F6F4F2", "surface": "#FFFFFF", "primary": "#B34700", "secondary": "#00708F", "text": "#1B1C1F" },
  "shape": { "cornerRadius": 16 },
  "type": { "font": "sans", "headlineWeight": 700, "scale": 1.0 },
  "wordmark": { "retro": "#FF8A2A", "forge": "#3ED6FF", "glow": true },
  "layout": { "gameView": "grid", "tileAspect": "wide" }
}
```

| Field | Required | Values |
|---|---|---|
| `format`, `version` | yes | `"retroforge-theme"`, `1` (a newer version is refused with a clear message) |
| `id` | yes | 2-48 of `a-z 0-9 -`; a clash with an existing theme gets `-2`, `-3` … |
| `name` | yes | up to 40 characters |
| `author`, `description`, `license` | no | plain text (60 / 300 / 60 characters) |
| `dark`, `light` | at least one | palettes (below); a theme with only one keeps that look in both brightness modes |
| `shape.cornerRadius` | no (16) | 0-32 dp; smaller elements get proportionally smaller corners |
| `type.font` | no (`sans`) | `sans`, `serif`, `mono`, `rounded` (Android's default face) |
| `type.headlineWeight` | no (700) | 300-900 |
| `type.scale` | no (1.0) | 0.85-1.3, scales all text |
| `wordmark.retro`, `.forge`, `.glow` | no | the two "RETROFORGE" colours (default: primary and secondary) and the neon glow in dark mode. A colour too close to the background is replaced by the text colour |
| `layout.gameView` | no (`grid`) | `grid` (tiles) or `list` (rows with small art) |
| `layout.tileAspect` | no (`wide`) | `wide` (4:3 screenshots), `square`, `portrait` (3:4 box art) |

**Palette** — required: `background`, `surface` (cards), `primary` (selection, buttons, focus), `secondary`, `text`. Optional, worked out if missing: `surfaceVariant`, `onPrimary`, `onSecondary` (black or white, whichever reads better), `textMuted`, `outline`, `focus`, `good`, `caution`, `bad`. Colours are `#RRGGBB` or `#AARRGGBB`.

## Installing

Settings → Themes → Import a theme file. User themes are stored in the app's private `themes` folder; a file that no longer parses is skipped, never crashes the app.

## Planned (version 2)

Theme packages (a .zip with images), background images and gradients, bundled open-licence fonts, a system carousel with system artwork, and motion.
