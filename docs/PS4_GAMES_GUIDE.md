# Playing your PS4 games in RetroForge

RetroForge's PS4 player is built on shadPS4. It plays games the same way shadPS4 does: from games you have dumped
from your own PS4. Nothing in this guide downloads a game, firmware or a key for you, and RetroForge never ships any
of them.

> **Status:** the PS4 player is in early testing, and RetroForge's PS4 screens (step 4) are still being built.
> Steps 1–3 work today and the dumps you make now are exactly what it will use.

## The short version

| What you have | Does it work? |
|---|---|
| A game folder dumped from your PS4 (`CUSAxxxxx` with `eboot.bin` and `sce_sys`) | **Yes.** It's the main way, exactly as on shadPS4. No keys needed. |
| A homebrew or backup `.pkg` file | Only if you already have the PKG keys (see the end). Dumping the game instead is easier. |
| A `.pkg` bought from the PlayStation Store | **No.** Store packages are locked to the console. Install the game on your PS4, then dump it. |

**Unpacked (dumped) games work just like they do on shadPS4.** If a game runs in shadPS4 on a PC, the same dumped
folder is what RetroForge wants, and how well it plays depends on shadPS4's compatibility for that game and on
your phone or tablet.

## What you need

- A PS4 you own, jailbroken, with a way to run homebrew payloads.
- The games you want to play, installed on that PS4 (disc or digital, including any updates and DLC you want).
- A USB drive formatted as exFAT, or a network share your Android device can reach.
- RetroForge and the RetroForge PS4 Player installed on your Android device.

## Step 1: Dump your game

1. Install the game on your PS4 as normal, with its latest update if you want it.
2. On your jailbroken PS4, run a **game dumper** payload with your homebrew loader. Pick the game, and choose
   to dump the **base game** and the **update merged** into one folder if your dumper offers it.
3. Dump to the USB drive. When it's done you have a folder named after the game's ID, for example `CUSA12345`.

Check the folder before you copy it. It should look like this:

```
CUSA12345/
  eboot.bin          the game's program, decrypted
  sce_sys/
    param.sfo        the game's name and ID
    icon0.png        its icon
  ...                the game's other files
```

If there's no `eboot.bin`, or the dumper said it couldn't decrypt, the dump didn't work. Run it again.

## Step 2: Dump the system files (once)

shadPS4 needs some of the PS4's own system modules and fonts. These come from your console too, and you only do
this once.

1. Use a **system file dumper** payload to copy the PS4's system folder to your USB drive.
2. Keep the whole dump (a folder or a `.zip`). The PS4 player finds the modules (`.sprx` files) and the fonts
   in it by itself.

## Step 3: Copy to your Android device

1. Copy each game folder (`CUSAxxxxx`) into one folder on your device, for example `PS4 Games`.
2. Copy the system dump anywhere on the device. You can delete it after it's imported.

Use the device's own storage or a fast microSD card. PS4 games are big, so leave room for them.

## Step 4: Set it up in RetroForge

1. **Import the system files.** Give RetroForge the system dump folder or `.zip`. It puts the files where shadPS4
   looks for them and tells you if any modules are still missing.
2. **Add your games folder.** RetroForge finds every `CUSAxxxxx` folder with an `eboot.bin`, reads each game's
   name and icon, and shows it in your library.
3. Tap a game to play.

Optional: if you want trophies to unlock, you can import your console's **trophy key** (shadPS4 reads it from its
`keys.json`). Games play fine without it.

## If a game doesn't start

- **"Missing modules"**: the system dump didn't include them all. Dump the system files again and import the new
  dump.
- **Black screen or crash at start**: check shadPS4's compatibility list for that game. If it doesn't run in
  shadPS4 yet, it won't run here either.
- **Game starts on PC shadPS4 but not on Android**: some games need more memory or a faster GPU than the device
  has. Android ports of shadPS4 are new, so fewer games run than on a PC.
- **The game isn't in the library**: make sure the folder has `eboot.bin` and `sce_sys/param.sfo` directly inside it,
  not one folder deeper.

## About `.pkg` files and keys

Unpacking a homebrew or backup `.pkg` needs two PKG keys. They're **not** something you dump from your PS4 like a
game, and RetroForge doesn't include them, link to them or tell you where to find them. If you already have them,
you can import them once and the player unpacks such packages in the background. If you don't, dump the game from
your console instead (step 1): the result is the same game folder, and it needs no keys.

## The legal bit

Only use games and system files from a console and games you own. RetroForge and the PS4 player include no PS4
firmware, system files, keys or games.
