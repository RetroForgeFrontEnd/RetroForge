# PC player: Play together (LAN and internet multiplayer for Windows games)

Last updated 2026-10-30. Branch `claude/sad-franklin-d79194` (based on `claude/pc-player`). Not pushed.

## What it does

The PC player's pause menu has **Play together**:

| Choice | What happens |
|---|---|
| **Same Wi-Fi** | Nothing to set up for most games: their LAN mode now sees the Wi-Fi network (see "Windows' adapter list" below). Steam games restart with gbe_fork's lobbies on |
| **Host over the internet** | Starts a virtual LAN on port 55460 (TCP and UDP), asks the router to open it (UPnP, as the retro player's netplay does) and shows the address friends type. The game sees an adapter at 10.77.0.1 |
| **Join over the internet** | Connects to the host's address; the game sees an adapter at 10.77.0.2–9 |
| **Stop playing together** | Closes the virtual LAN and the router port; the game keeps running |

## Windows' adapter list under Wine on Android (`pcnet.c`, in `libpcexec.so`)

Android doesn't let apps make the netlink `RTM_GETLINK` query, so bionic's `if_nameindex()` returns NULL. Wine's
nsiproxy (`update_if_table`) walks that NULL without checking, so its service dies, and everything built on it
(`GetAdaptersAddresses`, `GetAdaptersInfo`, `gethostbyname(own name)`) hangs or fails. That's why gbe_fork's
`SteamAPI_Init` hung with LAN networking on. `libpcexec.so` (preloaded into every Wine process) now answers:

- `if_nameindex()` from `getifaddrs()` (which works) when the real one fails;
- `/proc/net/route` (unreadable for apps) with routes made from `getifaddrs()`, so Wine's route table and
  `gethostbyname(own name)` work;
- `SIOCGIFHWADDR` (refused for apps) with a made-up, stable, per-device MAC (`PCNET_MACSEED` = ANDROID_ID), so Wi-Fi
  shows as Ethernet/Wi-Fi rather than "other".

## The virtual LAN (no VPN)

`PcLan.kt` (in the player's process) plus socket hooks in `pcnet.c`, switched on while `files/pc/pcnet.cfg` exists:

- Windows sees one extra adapter, `rf0`, with this device's 10.77.0.x address; the real adapters are hidden so games
  advertise the virtual address.
- UDP to 10.77.0.x, 10.77.0.255 or 255.255.255.255 is sent to the relay on 127.0.0.1 with a 12-byte header; packets
  from other players arrive from the relay with a header naming the real sender, which the hook puts back.
- TCP `connect()` to 10.77.0.x: the hook tells the relay where it was going, then connects to the relay, which opens
  a stream to the other device; there the relay connects to the game. `accept`/`getpeername`/`getsockname` are
  rewritten so each side sees the other's 10.77.0.x address.
- Between devices: each player keeps one TCP connection to the host (control, TCP streams, UDP until UDP works) and
  sends UDP keep-alives; game UDP then goes by UDP. The host forwards between players (up to 8 joiners).
- Licences: all RetroForge's own code; no library added.

Not covered: multicast discovery (239.x.x.x), IPv6, games that need the players' real addresses. Players behind
mobile data or networks without UPnP can't host (joining works); the host's router can be set up by hand.

## Tests

| Test | Result |
|---|---|
| `PcLanTest` (JVM): host and guest on one machine, games speaking the relay protocol: UDP broadcast + reply with the right sender, TCP through the relay both ways, `accept` sees 10.77.0.2:port, close propagates | Pass |
| `libpcexec.so` builds for arm64 (NDK 28) | Pass |
| `lantest.exe` (tools/lantest) natively on the PC | Lists adapters, broadcasts and listens |
| On the S22: adapter list, broadcasts with the PC, virtual LAN | Not yet: the phone was busy (another session's download) |

## Games

| Game | Multiplayer | In RetroForge |
|---|---|---|
| **Warside** (Steam 2368300) | Online lobbies on Lavabird's server (`coordinator.game.playwarside.com`), signed in with a real Steam ticket; matches over Steam networking. Its `LanListenPort` / `CoordinatorAddress` are developer command-line options, not a LAN mode | **Won't work** with gbe_fork (no real Steam ticket), on Wi-Fi or over the internet. Needs the real Steam client |
| Games with a plain LAN mode (UDP broadcast discovery + TCP/UDP game traffic) | | Expected to work; to be tested with one the user owns |
| Steam games whose multiplayer is Steam lobbies + Steam P2P (no developer server) | | Expected to work between RetroForge devices with gbe_fork's lobbies on; each player must use their own Steam account (gbe tells players apart by Steam ID) |

`tools/lantest/lantest.c` builds with `zig cc -target x86_64-windows-gnu -O2 lantest.c -o lantest.exe -lws2_32 -liphlpapi`
and does what a game's LAN mode does: lists adapters, broadcasts on UDP 47777, connects back over TCP 47778.
