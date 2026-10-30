# PC player: Play together (LAN and internet multiplayer for Windows games)

Last updated 2026-10-30. Branch `claude/sad-franklin-d79194` (based on `claude/pc-player`, merged up to cbb0fc9). Not pushed.

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
| S22, Wine, **old** `libpcexec.so`: `tools/lantest` | **Hangs** in `GetAdaptersAddresses` (killed after 150 s): the gbe_fork hang |
| S22, Wine, new `libpcexec.so`: adapter list | `wlan0 192.168.0.14/24` (Ethernet), `rmnet*`, `lo`; `GetAdaptersInfo` and `gethostbyname(own name)` work; route table made from `getifaddrs` |
| S22 (Wine) ↔ PC (native), same Wi-Fi: `tools/lantest` on both | **Works both ways:** each hears the other's UDP broadcast on 47777, connects to the other over TCP 47778 and gets its reply |
| S22 (Wine, virtual LAN, joined) ↔ PC (`PcLanCli host … answer`, a stand-in game on 10.77.0.1), over Wi-Fi to the PC's address | **Works:** Windows on the phone sees only `rf0 10.77.0.2/24` (and loopback), `gethostbyname` gives 10.77.0.2; its broadcasts reach the PC from 10.77.0.2, the PC's reply arrives from 10.77.0.1, TCP to 10.77.0.1:47778 goes through the relay with both sides seeing virtual addresses |
| The Play together menu in the installed app; a real internet connection (two networks, UPnP); a phone accepting TCP from the other side through the virtual LAN; two RetroForge phones | Not yet tested |

Known quirk: Wine reports some adapters (`rf0`, `epdg5`) with type 0 in `GetAdaptersAddresses`, although the
`SIOCGIFHWADDR` answer is Ethernet; LAN games rarely look at it.

How to repeat the device tests (debug build, as the app's user, nothing installed):
`run-as com.retroforge.app app_process -cp /data/local/tmp/<app-debug.apk> /system/bin com.retroforge.pcplayer.net.PcLanCli join <host> <config>`
on the phone; on the PC `java -cp <pc-player debug classes>;<kotlin-stdlib>;<kotlinx-coroutines-core-jvm> com.retroforge.pcplayer.net.PcLanCli host <config> answer`;
then Wine with `PCNET_FILE=<config>` running `lantest.exe` from a C: path (a Unix path crashes Wine's start-up).

## Games

| Game | Multiplayer | In RetroForge |
|---|---|---|
| **Warside** (Steam 2368300) | Online lobbies on Lavabird's server (`coordinator.game.playwarside.com`), signed in with a real Steam ticket; matches over Steam networking. Its `LanListenPort` / `CoordinatorAddress` are developer command-line options, not a LAN mode | **Won't work** with gbe_fork (no real Steam ticket), on Wi-Fi or over the internet. Needs the real Steam client |
| Games with a plain LAN mode (UDP broadcast discovery + TCP/UDP game traffic) | | Expected to work; to be tested with one the user owns |
| Steam games whose multiplayer is Steam lobbies + Steam P2P (no developer server) | | Expected to work between RetroForge devices with gbe_fork's lobbies on; each player must use their own Steam account (gbe tells players apart by Steam ID) |

`tools/lantest/lantest.c` builds with `zig cc -target x86_64-windows-gnu -O2 lantest.c -o lantest.exe -lws2_32 -liphlpapi`
and does what a game's LAN mode does: lists adapters, broadcasts on UDP 47777, connects back over TCP 47778.
