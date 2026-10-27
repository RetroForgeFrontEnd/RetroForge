# Hack Catalogue Format (version 1)

A hack catalogue is a JSON file that lists ROM hacks for RetroForge's **Hacks** tab. Catalogues describe hacks and link to their official pages. **They never contain ROMs, BIOS files or patch data**, and RetroForge never downloads a patch or ROM because a catalogue mentions it.

RetroForge ships one built-in catalogue (`app/src/main/assets/catalogues/retroforge-starter.json`). Users can add more from a file on the device or from an `https://` link (Hacks → Catalogue sources).

## Example

```json
{
  "format": "retroforge-hack-catalogue",
  "version": 1,
  "id": "my-catalogue",
  "name": "My favourite hacks",
  "description": "Optional, shown on the sources screen.",
  "maintainer": "Your name",
  "updated": "2026-10-27",
  "hacks": [
    {
      "id": "giant-mario",
      "title": "Giant Mario Bros.",
      "system": "nes",
      "baseGame": "Super Mario Bros.",
      "summary": "Your own one- or two-sentence summary.",
      "pageUrl": "https://www.romhacking.net/hacks/6142/",
      "authors": ["N·K"],
      "version": "1.2",
      "released": "2021-12-06",
      "tags": ["graphics"],
      "baseRom": {
        "name": "Super Mario Bros. (World).nes",
        "crc32": "3337EC46",
        "sha1": "ea343f4e445a9050d4b4fbac2c77d0693b1d0922",
        "size": 40976,
        "note": "iNES header included"
      }
    },
    {
      "id": "2b-in-smb",
      "title": "2B in Super Mario Bros.",
      "system": "nes",
      "baseGame": "Super Mario Bros.",
      "summary": "Add-on for Giant Mario Bros.",
      "pageUrl": "https://example.org/2b",
      "requires": ["giant-mario"]
    }
  ]
}
```

## Fields

Catalogue:

| Field | Required | Rules |
|---|---|---|
| `format` | yes | Exactly `retroforge-hack-catalogue` |
| `version` | yes | `1`. Newer versions are refused with "update RetroForge" |
| `id` | yes | `[a-z0-9][a-z0-9._-]{0,63}`. Adding a catalogue with an existing id replaces it (that's how updates work) |
| `name` | yes | 1–100 characters |
| `description`, `maintainer`, `updated` | no | Display only |
| `hacks` | no | Up to 5,000 entries |

Hack entry:

| Field | Required | Rules |
|---|---|---|
| `id` | yes | Same pattern as the catalogue id; unique within the catalogue |
| `title`, `baseGame` | yes | Up to 200 characters |
| `system` | yes | RetroForge system id, the same as RetroBat/ES-DE folder names (`nes`, `snes`, `gba`, `megadrive` …) |
| `summary` | yes | Up to 1,000 characters. **Write your own summary.** Don't paste the hack page's description, which belongs to its author |
| `pageUrl` | yes | Must start with `https://` |
| `authors`, `tags` | no | Up to 20 items each |
| `version`, `released` | no | Free text |
| `baseRom` | no | `crc32` must be 8 hex digits and `sha1` 40, if given |
| `requires` | no | Ids of hacks in the **same** catalogue to apply first, in order. Shown as links on the hack's page |

Unknown fields are ignored, so newer catalogues still load in older apps as long as `version` is supported.

## Validation behaviour

- A broken file (bad JSON, wrong `format`, newer `version`, bad catalogue `id`, larger than 2 MB, or an HTML page) is refused as a whole, with a reason.
- A single bad entry (missing field, `http://` link, malformed checksum, duplicate id) is **skipped with a warning**. The rest of the catalogue still loads.
- If two catalogues list the same `pageUrl`, it's shown once (the first catalogue wins). Built-in comes first, then user catalogues in the order added.

## Network behaviour

- Adding or updating from a link downloads that one file, only after the user confirms.
- HTTPS only, redirects are not followed, the file is capped at 2 MB, 10-second connect and 15-second read timeouts, and the request is sent with a `RetroForge/<version>` user agent.
- "Open official page" hands the link to the device's browser. RetroForge itself does not fetch hack pages.
