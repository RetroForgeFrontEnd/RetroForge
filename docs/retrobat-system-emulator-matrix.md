# RetroBat System / Emulator Matrix

Extracted read-only from `G:\Retrobat\emulationstation\.emulationstation\es_systems.cfg` (RetroBat 8.3.1.1) on 2026-10-27.
Format: `system: [emulator] core core ...` — the first emulator/core listed is the RetroBat default. These are **Windows** assignments; Android equivalents are evaluated in `EMULATOR_COMPATIBILITY.md` (Phase 5).

```
flash: [arcadeflashweb] [ruffle]
j2me: [libretro] freej2me
arcade: [libretro] fbneo fbalpha2012 fbalpha2012_cps1 fbalpha2012_cps2 fbalpha2012_cps3 fbalpha2012_neogeo mame mame2003_plus mame2003 mame2003_midway mame2016 flycast [raine] [mame64]
mame: [libretro] mame mame2016 mame2014 mame2010 mame2003_plus mame2003 mame2003_midway mame2000 [mame64] [groovymame]
hbmame: [hbmame] [libretro] mame
fbneo: [libretro] fbneo fbalpha fbalpha2012 fbalpha2012_neogeo [fbneo] arcade
cave: [libretro] fbneo fbalpha fbalpha2012 mame2003_plus mame2016 mame [mame64] [demul]
laseractive: [ares] MegaLD NecLD
daphne: [hypseus] [daphne] daphne [singe2] singe2
singe: [hypseus] [singe2] singe2
actionmax: [hypseus] [singe2]
cps1: [libretro] fbneo fbalpha2012_cps1 fbalpha fbalpha2012
cps2: [libretro] fbneo fbalpha2012_cps2 fbalpha fbalpha2012
cps3: [libretro] fbneo fbalpha2012_cps3 fbalpha fbalpha2012
dice: [libretro] dice
neogeo: [libretro] neogeo
neogeocd: [libretro] neocd fbneo [raine]
model2: [m2emulator] multicpu singlecpu [libretro] mame [mame64]
model3: [supermodel] [libretro] supermodel
atomiswave: [libretro] flycast [flycast] atomiswave [demul] atomiswave
naomi: [libretro] flycast [flycast] naomi [demul] naomi
naomi2: [libretro] flycast [flycast] naomi2 [demul] naomi
hikaru: [demul] hikaru
gaelco: [demul] gaelco [libretro] mame mame2016 mame2014
chihiro: [xemu-chihiro] [chihiro] [chihiro-gun] [cxbx] [teknoparrot] chihiro
triforce: [dolphin] dolphin [libretro] dolphin [dolphin-triforce] [teknoparrot] triforce
lindbergh: [linuxloader] [teknoparrot]
sg1000: [libretro] gearsystem genesis_plus_gx [ares] SG-1000 [bizhawk] SMSHawk
multivision: [libretro] gearsystem
mastersystem: [libretro] genesis_plus_gx picodrive gearsystem fbneo [jgenesis] [blastem] [mednafen] mastersystem [mesen] [ares] MasterSystem [kega-fusion] auto mastersystem [bizhawk] SMSHawk
megadrive: [libretro] genesis_plus_gx genesis_plus_gx_wide picodrive fbneo [jgenesis] [blastem] [mednafen] megadrive [ares] MegaDrive [kega-fusion] auto megadrive genesis [bizhawk] Genplus-gx
megadrive-msu: [libretro] genesis_plus_gx genesis_plus_gx_wide genesis_plus_gx_expanded [blastem]
megacd: [libretro] genesis_plus_gx genesis_plus_gx_wide picodrive [jgenesis] [blastem] [ares] MegaCD [kega-fusion] megacd segacd
sega32x: [libretro] picodrive [jgenesis] [blastem] [ares] Mega32X [kega-fusion] sega32x [bizhawk] PicoDrive
saturn: [libretro] kronos mednafen_saturn ymir yabasanshiro [ymir] [mednafen] [yabasanshiro] [kronos] [bizhawk] [ssf]
segastv: [libretro] kronos mame mame2016 mame2014 mame2010 mame2003_plus mame2003 [mednafen] saturn [mame64]
dreamcast: [libretro] flycast [flycast] dreamcast [redream] redream [demul] dreamcast
fds: [libretro] fceumm nestopia mesen [mesen] [ares] FamicomDiskSystem
nes: [libretro] fceumm nestopia mesen [mednafen] nes [mesen] [jgenesis] [ares] Famicom [bizhawk] NesHawk quickerNES
snes: [libretro] snes9x bsnes-jg bsnes bsnes_hd_beta mednafen_snes mesen-s snes9x2005 [mednafen] snes [mesen] [snes9x] [jgenesis] [ares] SuperFamicom [bizhawk] BSNES Faust Snes9x
snes-msu1: [libretro] snes9x mesen-s bsnes bsnes_hd_beta [mesen] [snes9x]
sgb-msu1: [libretro] bsnes bsnes_hd_beta
satellaview: [libretro] snes9x mesen-s [ares] SuperFamicom
sufami: [libretro] snes9x bsnes [ares] SuperFamicom
virtualboy: [libretro] mednafen_vb [bizhawk] VirtualBoyee
n64: [libretro] parallel_n64 [mupen64] [gopher64] [simple64] [ares] Nintendo64 [bizhawk] Ares64 Mupen64Plus [project64]
recomp: [quiverlauncher]
n64dd: [mupen64] [libretro] mupen64plus_next parallel_n64 [ares] Nintendo64DD [project64]
gamecube: [dolphin] dolphin [libretro]
wii: [dolphin] dolphin [libretro]
wiiu: [cemu]
switch: [eden] [ryujinx] [citron] [sudachi] [yuzu]
pcengine: [libretro] mednafen_pce mednafen_pce_fast fbneo [mednafen] pce [mesen] [ares] PCEngine [bizhawk] HyperNyma PCEHawk TurboNyma [magicengine]
pcenginecd: [libretro] mednafen_pce mednafen_pce_fast fbneo [mednafen] [mesen] [ares] PCEngineCD [bizhawk]
supergrafx: [libretro] mednafen_supergrafx geargrafx fbneo [mednafen] pce [mesen] [ares] SuperGrafx [bizhawk] HyperNyma PCEHawk TurboNyma [magicengine]
pcfx: [libretro] mednafen_pcfx [mednafen] [bizhawk]
psx: [libretro] mednafen_psx_hw pcsx_rearmed mednafen_psx swanstation [duckstation] [mednafen] [bizhawk]
ps2: [pcsx2] pcsx2 [libretro] pcsx2 [play] [pcsx2-16]
ps3: [rpcs3]
ps4: [shadps4]
xbox: [xemu] [cxbx]
xbox360: [xenia-canary] [xenia-edge] [xenia-manager] canary mousehook netplay [xenia]
3do: [libretro] opera [phoenix]
amiga500: [libretro] puae amiberry [amiberry] [winuae] [amigaforever]
amiga1200: [libretro] puae amiberry [amiberry] [winuae] [amigaforever]
amiga4000: [libretro] puae amiberry [amiberry] [winuae] [amigaforever]
amigacd32: [libretro] puae amiberry [amiberry] [winuae] cd32 [amigaforever]
amigacdtv: [libretro] puae amiberry [amiberry] [winuae] cdtv [amigaforever]
amstradcpc: [libretro] [capriceforever]
gx4000: [libretro] cap32 [capriceforever]
apple2: [applewin] [mednafen] [bizhawk] [libretro] applewin mame [mame64]
apple2gs: [gsplus] [libretro] mame [mame64]
atari800: [libretro] atari800 [altirra]
atari2600: [libretro] stella stella2014 [stella] [bizhawk] Atari2600Hawk
atari5200: [libretro] a5200 atari800 stella2014 [altirra]
atari7800: [libretro] prosystem [bizhawk] A78
atarist: [libretro] hatari hatarib [hatari]
xegs: [libretro] mame [mame64] xegs [altirra]
jaguar: [libretro] virtualjaguar [bigpemu] [bizhawk] Jaguar [phoenix]
jaguarcd: [bigpemu] [libretro] [bizhawk]
colecovision: [libretro] gearcoleco jollycv bluemsx fbneo [openmsx] [ares] ColecoVision [bizhawk] Coleco [mesen]
c16: [libretro] vice_xplus4
c20: [libretro] vice_xvic
c64: [libretro] vice_x64 vice_x64sc frodo
cplus4: [libretro] vice_xplus4
pet: [libretro] vice_xpet
c128: [libretro] vice_x128
intellivision: [libretro] freeintv [jzintv] [bizhawk] IntelliHawk
aquarius: [libretro] mame [mame64] aquarius
channelf: [libretro] freechaf [bizhawk] ChannelFHawk
enterprise: [libretro] ep128emu_core
msx1: [libretro] bluemsx fbneo [openmsx]
msx2: [libretro] bluemsx fbneo [openmsx]
msx2+: [libretro] bluemsx fbneo [openmsx]
msxturbor: [libretro] bluemsx fbneo [openmsx]
spectravideo: [libretro] bluemsx
oricatmos: [oricutron] oricutron [libretro] mame
pc88: [libretro] quasi88
pc98: [libretro] np2kai nekop2
samcoupe: [simcoupe] simcoupe
x68000: [libretro] px68k [xm6pro]
x1: [libretro] x1
thomson: [libretro] theodore
vectrex: [libretro] vecx [bizhawk] VectrexHawk
odyssey2: [libretro] o2em mame [mame64] videopac [bizhawk] O2Hawk
p2000t: [libretro] m2000
vg5k: [libretro] mame [mame64] vg5k
zx81: [libretro] 81 fuse fbneo
zxspectrum: [libretro] fuse fbneo [zesarux] [bizhawk] ZXHawk
lynx: [libretro] mednafen_lynx handy holani [mednafen] lynx [bizhawk] Handy
gamegear: [libretro] genesis_plus_gx picodrive gearsystem fbneo [jgenesis] [blastem] [mednafen] gg [ares] GameGear [bizhawk] SMSHawk
gw: [libretro] mame [mame64]
gb: [libretro] gambatte mesen-s bsnes tgbdual sameboy DoubleCherryGB [mesen] [jgenesis] [mgba] mgba [mednafen] gb [ares] GameBoy [bizhawk] Gambatte GBHawk SameBoy
gb2players: [libretro] tgbdual sameboy DoubleCherryGB
gbc: [libretro] gambatte tgbdual sameboy DoubleCherryGB [mesen] [jgenesis] [mgba] mgba [mednafen] gbc [ares] GameBoyColor [bizhawk] Gambatte GBHawk SameBoy
gbc2players: [libretro] tgbdual sameboy DoubleCherryGB
gba: [libretro] mgba mednafen_gba gpsp [mgba] mgba [jgenesis] [mesen] [nosgba] [mednafen] gba [ares] GameBoyAdvance [bizhawk] mGBA
gba2players: [nosgba]
karaoke: [libretro] pocketcdg
pokemini: [libretro] pokemini
nds: [libretro] melondsds melonds [melonds] [desmume] [bizhawk] melonDS
3ds: [azahar] [libretro] azahar citra [lime3ds] citra-qt Encore
psp: [ppsspp] [libretro] ppsspp
ngp: [libretro] mednafen_ngp race [mednafen] ngp [ares] NeoGeoPocket [bizhawk] NeoPop
ngpc: [libretro] mednafen_ngp race [mednafen] ngp [ares] NeoGeoPocketColor [bizhawk] NeoPop
supervision: [libretro] potator
wswan: [libretro] mednafen_wswan [mednafen] wswan [ares] WonderSwan [bizhawk] Cygne
wswanc: [libretro] mednafen_wswan [mednafen] wswan [ares] WonderSwanColor [bizhawk] Cygne
scummvm: [libretro] scummvm [scummvm]
ports: [libretro]
love: [love]
lutro: [libretro] lutro
devilutionx: [devilutionx] devilutionx
easyrpg: [libretro] easyrpg
ecwolf: [libretro] ecwolf
gemrb: [gemrb] gemrb
prboom: [libretro] prboom
doom: [uzdoom] [gzdoom] [libretro] prboom
raze: [raze]
doom3: [libretro] boom3 boom3_xp [dhewm3]
quake: [libretro] tyrquake [vkquake]
quake2: [libretro] vitaquake2 vitaquake2-rogue vitaquake2-xatrix vitaquake2-zaero [vkquake2]
rtcw: [rtcw]
halflife: [xash3d]
bstone: [bstone]
cavestory: [libretro] nxengine doukutsu_rs
powerbomberman: [powerbomberman]
ikemen: [ikemen] ikemen
mugen: [mugen] mugen
openbor: [openbor] openbor openbor-specific-version
solarus: [solarus] [solarus2]
zeldaclassic: [zquestclassic] [libretro] zc255
teknoparrot: [teknoparrot]
zinc: [arcadeduck] [psxmame] [zinc] [libretro] mame [mame64]
namco2x6: namco2x6 [teknoparrot] pcsx2x6
namco3xx: [teknoparrot] namco3xx
dos: [libretro] dosbox_pure [dosbox-staging] [dosbox-pure] [dosbox]
exodos: [exodos]
exowin3x: [exodos]
exowin9x: [exodos]
amazon: [windows]
steam: [windows]
gog: [windows]
epic: [windows]
eagames: [windows]
Windows: [windows]
vpinball: [vpinball] vpinball
fpinball: [fpinball] fpinball
pinballfx: [pinballfx] steam nonsteam
pinballm: [pinballfx] steam nonsteam
pinballfx2: [pinballfx] steam nonsteam
pinballfx3: [pinballfx] steam nonsteam
zaccariapinball: [zaccariapinball] steam nonsteam
pico8: [libretro] retro8 fake08 [pico8]
tic80: [libretro] tic80 [bizhawk] TIC-80
wasm4: [libretro] wasm4
cdi: [libretro] same_cdi mame [mame64] cdi
ngage: [eka2l1]
advision: [libretro] mame [mame64] advision
tvgames: [libretro] mame [mame64] tvgames
megaduck: [libretro] sameduck mame [mame64] megaduck
arduboy: [libretro] arduous ardens
uzebox: [libretro] uzem [bizhawk] Uzem
lowresnx: [libretro] lowresnx
vircon32: [libretro] vircon32
pv1000: [libretro] mame [mame64] pv1000
bk: [libretro] bk
casloopy: [libretro] mame [mame64] casloopy
crvision: [libretro] jollycv mame [mame64] crvision
gamecom: [libretro] mame [mame64] gamecom
gamepock: [libretro] mame [mame64] gamepock
gp32: [libretro] mame [mame64] gp32
pegasus: [libretro] mame [mame64] pegasus
cassettevision: [libretro] pd777
scv: [libretro] emuscv mame [mame64] scv
fm7: [libretro] mame [mame64] fm7
apfm1000: [libretro] mame [mame64] apfm1000
bbcmicro: [libretro] mame [mame64] bbcmicro
electron: [libretro] mame [mame64] electron
adam: [libretro] mame gearcoleco [mame64] adam
atom: [libretro] mame [mame64] atom
archimedes: [libretro] mame [mame64] archimedes
arcadia: [libretro] amiarcadia mame [mame64] arcadia
gmaster: [libretro] mame [mame64] gmaster
astrocade: [libretro] mame [mame64] astrocde
ti99: [libretro] mame [mame64] mame64
tutor: [libretro] mame [mame64] tutor
coco: [libretro] mame [mame64] coco [xroar]
dragon32: [xroar]
fmtowns: [libretro] mame [tsugaru] tsugaru [mame64] fmtownsux
lcdgames: [libretro] gw [mame64] lcdgames
vsmile: [libretro] mame [mame64] vsmile
camplynx: [jynx] [libretro] mame [mame64] camplynx
vc4000: [libretro] amiarcadia mame [mame64] vc4000
supracan: [libretro] mame [mame64] supracan
gamate: [libretro] mame [mame64] gamate
sgb: [libretro] mgba mesen-s [mesen] [mgba] mgba [bizhawk] BSNES Gambatte
neogeo64: [libretro] mame mame2016 [mame64]
psvita: [vita3k]
bennugd: [libretro] bennugd
bsyndrome: [bsyndrome]
cannonball: [libretro] cannonball
cdogs: [cdogs]
cgenius: [cgenius]
corsixth: [corsixth]
openlara: [libretro] openlara
opengoal: [opengoal]
openjazz: [openjazz]
pdark: [pdark]
soh: [soh]
2ship: [2ship]
dusklight: [dusklight]
sonic3-air: [sonic3air]
sonic-mania: [sonicmania]
sonicretro: [sonicretro] [sonicretrocd]
starship: [ghostship]
starship: [starship]
dinothawr: [libretro] dinothawr
eduke32: [eduke32]
theforceengine: [theforceengine]
superbroswar: [libretro] superbroswar
reminiscence: [libretro] reminiscence
library:
kodi:
retrobat:
screenshots: [libretro] imageviewer
mess:
```
