package com.retroforge.player

import java.io.File
import java.util.Properties

/**
 * The libretro cores RetroForge's built-in player offers per system, the default first. Only cores that draw in
 * software (no OpenGL hardware rendering yet) and load cartridge-sized games; disc systems still go to RetroArch
 * or a standalone app.
 */
object PlayerCores {

    data class PlayerCore(val id: String, val name: String, val needsBios: Boolean = false)

    private val mgba = PlayerCore("mgba", "mGBA")
    private val gpsp = PlayerCore("gpsp", "gpSP")
    private val vbaNext = PlayerCore("vba_next", "VBA Next")
    private val gambatte = PlayerCore("gambatte", "Gambatte")
    private val sameboy = PlayerCore("sameboy", "SameBoy")
    private val gearboy = PlayerCore("gearboy", "Gearboy")
    private val fceumm = PlayerCore("fceumm", "FCEUmm")
    private val nestopia = PlayerCore("nestopia", "Nestopia UE")
    private val mesen = PlayerCore("mesen", "Mesen")
    private val snes9x = PlayerCore("snes9x", "Snes9x")
    private val snes9x2010 = PlayerCore("snes9x2010", "Snes9x 2010 (faster)")
    private val gpgx = PlayerCore("genesis_plus_gx", "Genesis Plus GX")
    private val picodrive = PlayerCore("picodrive", "PicoDrive")
    private val pce = PlayerCore("mednafen_pce_fast", "Beetle PCE Fast")
    private val supergrafx = PlayerCore("mednafen_supergrafx", "Beetle SuperGrafx")
    private val ngp = PlayerCore("mednafen_ngp", "Beetle NeoPop")
    private val wswan = PlayerCore("mednafen_wswan", "Beetle Cygne")
    private val stella = PlayerCore("stella", "Stella")
    private val handy = PlayerCore("handy", "Handy", needsBios = true)
    private val desmume = PlayerCore("desmume", "DeSmuME")
    private val desmume2015 = PlayerCore("desmume2015", "DeSmuME 2015 (faster)")
    private val melonds = PlayerCore("melonds", "melonDS", needsBios = true)
    // Has its own BIOS replacement; uses the real one when it's in the BIOS folder.
    private val pcsx = PlayerCore("pcsx_rearmed", "PCSX ReARMed")
    // CD add-ons need the add-on's BIOS (Sega CD / Mega-CD, PC Engine CD System Card).
    private val gpgxCd = PlayerCore("genesis_plus_gx", "Genesis Plus GX", needsBios = true)
    private val picodriveCd = PlayerCore("picodrive", "PicoDrive", needsBios = true)
    private val pceCd = PlayerCore("mednafen_pce_fast", "Beetle PCE Fast", needsBios = true)
    private val fbneo = PlayerCore("fbneo", "FinalBurn Neo")
    private val mame2003 = PlayerCore("mame2003_plus", "MAME 2003-Plus")
    private val swanstation = PlayerCore("swanstation", "SwanStation", needsBios = true)
    // Yabause has its own BIOS replacement and runs on phones, but not every game boots; Beetle Saturn is accurate but
    // needs a fast phone and the BIOS. YabaSanshiro (better) draws with the GPU, so it waits for hardware rendering.
    private val yabause = PlayerCore("yabause", "Yabause (experimental)")
    private val beetleSaturn = PlayerCore("mednafen_saturn", "Beetle Saturn (accurate, needs a fast phone)", needsBios = true)

    // Draw with the GPU (OpenGL ES).
    private val mupen = PlayerCore("mupen64plus_next_gles3", "Mupen64Plus-Next")
    private val parallelN64 = PlayerCore("parallel_n64", "ParaLLEl N64")
    private val ppsspp = PlayerCore("ppsspp", "PPSSPP")
    private val beetleVb = PlayerCore("mednafen_vb", "Beetle VB")
    private val flycast = PlayerCore("flycast", "Flycast")
    private val flycastArcade = PlayerCore("flycast", "Flycast", needsBios = true)
    // Newer systems on OpenGL ES (Vulkan later): they run, but the standalone apps are faster on most phones today.
    private val dolphin = PlayerCore("dolphin", "Dolphin (experimental)")
    private val citra = PlayerCore("citra", "Citra (experimental)")
    private val play = PlayerCore("play", "Play! (experimental)")
    private val lrps2 = PlayerCore("pcsx2", "LRPS2 (experimental, needs the PS2 BIOS)", needsBios = true)

    // Computers, more consoles and game engines (one core each, from the libretro buildbot like the rest).
    private val opera = PlayerCore("opera", "Opera", needsBios = true)
    private val puae = PlayerCore("puae", "PUAE", needsBios = true)
    private val cap32 = PlayerCore("cap32", "Caprice32")
    private val crocods = PlayerCore("crocods", "CrocoDS")
    private val applewin = PlayerCore("applewin", "AppleWin")
    private val arduous = PlayerCore("arduous", "Arduous")
    private val a5200 = PlayerCore("a5200", "a5200", needsBios = true)
    private val atari800 = PlayerCore("atari800", "Atari800", needsBios = true)
    private val prosystem = PlayerCore("prosystem", "ProSystem")
    private val hatari = PlayerCore("hatari", "Hatari", needsBios = true)
    private val bk = PlayerCore("bk", "BK")
    private val viceX64 = PlayerCore("vice_x64sc", "VICE x64sc")
    private val viceX128 = PlayerCore("vice_x128", "VICE x128")
    private val vicePlus4 = PlayerCore("vice_xplus4", "VICE xplus4")
    private val viceVic = PlayerCore("vice_xvic", "VICE xvic")
    private val vicePet = PlayerCore("vice_xpet", "VICE xpet")
    private val nxengine = PlayerCore("nxengine", "NXEngine")
    private val sameCdi = PlayerCore("same_cdi", "SAME CDi", needsBios = true)
    private val freechaf = PlayerCore("freechaf", "FreeChaF", needsBios = true)
    private val gearcoleco = PlayerCore("gearcoleco", "Gearcoleco", needsBios = true)
    private val dosboxPure = PlayerCore("dosbox_pure", "DOSBox Pure")
    private val prboom = PlayerCore("prboom", "PrBoom")
    private val easyrpg = PlayerCore("easyrpg", "EasyRPG")
    private val ecwolf = PlayerCore("ecwolf", "ECWolf")
    private val gw = PlayerCore("gw", "Game & Watch")
    private val tgbdual = PlayerCore("tgbdual", "TGB Dual")
    private val freeintv = PlayerCore("freeintv", "FreeIntv", needsBios = true)
    private val virtualjaguar = PlayerCore("virtualjaguar", "Virtual Jaguar")
    private val pocketcdg = PlayerCore("pocketcdg", "PocketCDG")
    private val lowresnx = PlayerCore("lowresnx", "LowRes NX")
    private val lutro = PlayerCore("lutro", "Lutro")
    private val minivmac = PlayerCore("minivmac", "Mini vMac", needsBios = true)
    private val sameduck = PlayerCore("sameduck", "SameDuck")
    private val bluemsx = PlayerCore("bluemsx", "blueMSX", needsBios = true)
    private val fmsx = PlayerCore("fmsx", "fMSX")
    private val neocd = PlayerCore("neocd", "NeoCD", needsBios = true)
    private val o2em = PlayerCore("o2em", "O2EM", needsBios = true)
    private val openlara = PlayerCore("openlara", "OpenLara")
    private val quasi88 = PlayerCore("quasi88", "QUASI88", needsBios = true)
    private val np2kai = PlayerCore("np2kai", "Neko Project II Kai", needsBios = true)
    private val pcfx = PlayerCore("mednafen_pcfx", "Beetle PC-FX", needsBios = true)
    private val retro8 = PlayerCore("retro8", "Retro8")
    private val pokemini = PlayerCore("pokemini", "PokeMini")
    private val tyrquake = PlayerCore("tyrquake", "TyrQuake")
    private val vitaquake2 = PlayerCore("vitaquake2", "vitaQuake 2")
    private val scummvm = PlayerCore("scummvm", "ScummVM")
    private val emuscv = PlayerCore("emuscv", "EmuSCV", needsBios = true)
    private val potator = PlayerCore("potator", "Potator")
    private val theodore = PlayerCore("theodore", "Theodore")
    private val tic80 = PlayerCore("tic80", "TIC-80")
    private val uzem = PlayerCore("uzem", "Uzem")
    private val vecx = PlayerCore("vecx", "vecx")
    private val vircon32 = PlayerCore("vircon32", "Vircon32", needsBios = true)
    private val wasm4 = PlayerCore("wasm4", "WASM-4")
    private val x1 = PlayerCore("x1", "X Millennium", needsBios = true)
    private val px68k = PlayerCore("px68k", "PX68k", needsBios = true)
    private val fuse = PlayerCore("fuse", "Fuse")
    private val zx81 = PlayerCore("81", "EightyOne")
    private val supergrafxCore = PlayerCore("mednafen_supergrafx", "Beetle SuperGrafx")
    private val mrboom = PlayerCore("mrboom", "Mr.Boom")

    private val sega = listOf(gpgx, picodrive)
    private val gameBoy = listOf(gambatte, sameboy, gearboy, mgba)

    private val bySystem: Map<String, List<PlayerCore>> = mapOf(
        "gba" to listOf(mgba, gpsp, vbaNext),
        "gb" to gameBoy, "gbc" to gameBoy,
        "nes" to listOf(fceumm, nestopia, mesen),
        "snes" to listOf(snes9x, snes9x2010), "sfc" to listOf(snes9x, snes9x2010),
        "megadrive" to sega, "genesis" to sega, "mastersystem" to sega, "gamegear" to sega, "sg1000" to sega,
        "sega32x" to listOf(picodrive),
        "pcengine" to listOf(pce, supergrafx),
        "ngp" to listOf(ngp), "ngpc" to listOf(ngp),
        "wswan" to listOf(wswan), "wswanc" to listOf(wswan),
        "atari2600" to listOf(stella),
        "lynx" to listOf(handy),
        "nds" to listOf(desmume, desmume2015, melonds),
        "psx" to listOf(pcsx, swanstation),
        "megacd" to listOf(gpgxCd, picodriveCd), "segacd" to listOf(gpgxCd, picodriveCd),
        "pcenginecd" to listOf(pceCd),
        "saturn" to listOf(yabause, beetleSaturn),
        "n64" to listOf(mupen, parallelN64),
        "psp" to listOf(ppsspp),
        "virtualboy" to listOf(beetleVb),
        "dreamcast" to listOf(flycast),
        "gamecube" to listOf(dolphin), "gc" to listOf(dolphin), "wii" to listOf(dolphin),
        "3ds" to listOf(citra), "n3ds" to listOf(citra),
        "ps2" to listOf(play, lrps2),
        "naomi" to listOf(flycastArcade), "naomi2" to listOf(flycastArcade), "atomiswave" to listOf(flycastArcade),
        "fbneo" to listOf(fbneo), "neogeo" to listOf(fbneo), "cps1" to listOf(fbneo), "cps2" to listOf(fbneo), "cps3" to listOf(fbneo),
        "mame" to listOf(mame2003, fbneo),
        "3do" to listOf(opera),
        "amiga500" to listOf(puae),
        "amiga1200" to listOf(puae),
        "amiga4000" to listOf(puae),
        "amigacd32" to listOf(puae),
        "amigacdtv" to listOf(puae),
        "amstradcpc" to listOf(cap32, crocods),
        "gx4000" to listOf(cap32),
        "apple2" to listOf(applewin),
        "arduboy" to listOf(arduous),
        "atari5200" to listOf(a5200, atari800),
        "atari7800" to listOf(prosystem),
        "atari800" to listOf(atari800),
        "xegs" to listOf(atari800),
        "atarist" to listOf(hatari),
        "bk" to listOf(bk),
        "c64" to listOf(viceX64),
        "c128" to listOf(viceX128),
        "cplus4" to listOf(vicePlus4),
        "c16" to listOf(vicePlus4),
        "c20" to listOf(viceVic),
        "pet" to listOf(vicePet),
        "cavestory" to listOf(nxengine),
        "cdi" to listOf(sameCdi),
        "channelf" to listOf(freechaf),
        "colecovision" to listOf(gearcoleco),
        "adam" to listOf(gearcoleco),
        "dos" to listOf(dosboxPure),
        "doom" to listOf(prboom),
        "easyrpg" to listOf(easyrpg),
        "ecwolf" to listOf(ecwolf),
        "gw" to listOf(gw),
        "lcdgames" to listOf(gw),
        "gb2players" to listOf(tgbdual),
        "gbc2players" to listOf(tgbdual),
        "intellivision" to listOf(freeintv),
        "jaguar" to listOf(virtualjaguar),
        "karaoke" to listOf(pocketcdg),
        "lowresnx" to listOf(lowresnx),
        "lutro" to listOf(lutro),
        "megaduck" to listOf(sameduck),
        "msx1" to listOf(bluemsx, fmsx),
        "msx2" to listOf(bluemsx, fmsx),
        "msx2+" to listOf(bluemsx),
        "msxturbor" to listOf(bluemsx),
        "spectravideo" to listOf(bluemsx),
        "neogeocd" to listOf(neocd),
        "odyssey2" to listOf(o2em),
        "openlara" to listOf(openlara),
        "pc88" to listOf(quasi88),
        "pc98" to listOf(np2kai),
        "pcfx" to listOf(pcfx),
        "pico8" to listOf(retro8),
        "pokemini" to listOf(pokemini),
        "quake" to listOf(tyrquake),
        "quake2" to listOf(vitaquake2),
        "scummvm" to listOf(scummvm),
        "scv" to listOf(emuscv),
        "supergrafx" to listOf(supergrafxCore),
        "supervision" to listOf(potator),
        "thomson" to listOf(theodore),
        "tic80" to listOf(tic80),
        "uzebox" to listOf(uzem),
        "vectrex" to listOf(vecx),
        "vircon32" to listOf(vircon32),
        "wasm4" to listOf(wasm4),
        "x1" to listOf(x1),
        "x68000" to listOf(px68k),
        "zxspectrum" to listOf(fuse),
        "zx81" to listOf(zx81),
        "mrboom" to listOf(mrboom),
        "satellaview" to listOf(snes9x),
        "sufami" to listOf(snes9x),
        "snes-msu1" to listOf(snes9x),
        "sgb" to listOf(mgba),
        "megadrive-msu" to listOf(gpgx),
        "fds" to listOf(fceumm),
    )

    /** Arcade systems: games are romset .zips that may need the board's BIOS set from the same folder. */
    val arcadeSystems = setOf("fbneo", "neogeo", "cps1", "cps2", "cps3", "mame")

    /** Files to bring along with a game when they're next to it (Neo Geo BIOS for arcade sets). */
    fun companions(system: String): List<String> = if (system in arcadeSystems) listOf("neogeo.zip") else emptyList()

    /** Disc systems the built-in player runs; their disc images (cue/bin, chd, pbp …) are allowed. */
    private val discSystems = setOf("psx", "megacd", "segacd", "pcenginecd", "saturn", "psp", "dreamcast", "gamecube", "gc", "wii", "ps2", "3do", "amigacd32", "amigacdtv", "cdi", "neogeocd", "pcfx", "dos")

    /**
     * Systems where the built-in player is experimental: a standalone emulator the user has installed (Dolphin,
     * Azahar, NetherSX2) is used for Play instead, and the built-in player is offered beside it.
     */
    val experimentalSystems = setOf("gamecube", "gc", "wii", "3ds", "n3ds", "ps2")

    /** Disc images and playlists: only for the [discSystems]. */
    private val discExtensions = setOf("cue", "bin", "chd", "iso", "m3u", "ccd", "img", "gdi", "cdi", "pbp", "toc", "rvz", "gcz", "wbfs", "wia", "gcm", "cso")

    /** The built-in player's emulators for [system], the default first. */
    fun choices(system: String): List<PlayerCore> = bySystem[system].orEmpty()

    /** The default core: the one downloaded in the background when the library has this system. */
    fun forSystem(system: String): PlayerCore? = choices(system).firstOrNull()

    /** The core for this game ([preferred] when it's one of the system's), or null when the built-in player can't run it. */
    fun forGame(system: String, fileName: String, preferred: String? = null): PlayerCore? {
        if (system !in discSystems && fileName.substringAfterLast('.', "").lowercase() in discExtensions) return null
        val list = choices(system)
        return list.firstOrNull { it.id == preferred } ?: list.firstOrNull()
    }

    /**
     * The controller a core should be told about, when not a plain joypad: PCSX ReARMed's DualShock
     * (RETRO_DEVICE_SUBCLASS(RETRO_DEVICE_ANALOG, 1)), so analog games get both sticks.
     */
    fun portDevice(coreId: String): Int? = when (coreId) {
        "pcsx_rearmed" -> (1 + 1 shl 8) or 5
        else -> null
    }

    /** Core options set by default: SwanStation's analog controller. */
    fun defaultOptions(coreId: String): Map<String, String> = when (coreId) {
        "swanstation" -> mapOf("swanstation_Controller1_Type" to "AnalogController")
        // Both eyes side by side: real 3D in Cardboard VR, the left eye alone otherwise.
        "mednafen_vb" -> mapOf("vb_3dmode" to "side-by-side", "vb_sidebyside_separation" to "0")
        // A memory card (VMU) in the first controller, or games can't save; one card per game so they never fill up.
        "flycast" -> mapOf("reicast_device_port1_slot1" to "VMU", "reicast_per_content_vmus" to "VMU A1")
        else -> emptyMap()
    }

    fun byId(id: String): PlayerCore? = bySystem.values.flatten().firstOrNull { it.id == id }

    val all: Collection<PlayerCore> get() = bySystem.values.flatten().toSet()

    /**
     * Core options that follow the screen's orientation: the DS's two screens stacked in portrait and side by
     * side in landscape, so they always use the most of the screen.
     */
    /** A core's widescreen hack: (option, widescreen value, original value), for the pause menu's Widescreen switch. */
    fun widescreenOption(coreId: String): Triple<String, String, String>? = when (coreId) {
        "mupen64plus_next_gles3" -> Triple("mupen64plus-aspect", "16:9 adjusted", "4:3")
        "flycast" -> Triple("reicast_widescreen_hack", "enabled", "disabled")
        else -> null
    }

    /** Cores that draw both eyes side by side (see the defaults above). */
    fun sideBySide(coreId: String): Boolean = coreId == "mednafen_vb"

    fun orientationOptions(coreId: String, landscape: Boolean): Map<String, String> = when (coreId) {
        "desmume", "desmume2015" -> mapOf("desmume_screens_layout" to if (landscape) "left/right" else "top/bottom")
        else -> emptyMap()
    }
}

/**
 * Which built-in emulator each system uses, when not the default. A small file both the library (main process)
 * and the player (its own process) read and write, so a switch made mid-game sticks.
 */
class CoreChoices(private val file: File) {

    @Synchronized
    fun get(system: String): String? = load().getProperty(system)

    @Synchronized
    fun all(): Map<String, String> = load().let { p -> p.stringPropertyNames().associateWith { p.getProperty(it) } }

    /** [coreId] null goes back to the default. */
    @Synchronized
    fun set(system: String, coreId: String?) {
        val p = load()
        if (coreId == null) p.remove(system) else p.setProperty(system, coreId)
        file.parentFile?.mkdirs()
        val part = File(file.path + ".part")
        part.outputStream().use { p.store(it, "RetroForge built-in player: emulator per system") }
        if (!part.renameTo(file)) {
            file.delete()
            part.renameTo(file)
        }
    }

    private fun load() = Properties().apply { if (file.isFile) file.inputStream().use(::load) }
}
