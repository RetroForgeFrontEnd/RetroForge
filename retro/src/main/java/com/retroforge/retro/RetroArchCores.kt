package com.retroforge.retro

/** A libretro core as RetroArch for Android names its file: `<id>_libretro_android.so`. */
data class Core(val id: String, val name: String)

/**
 * Which RetroArch cores to offer per system, best first. Defaults follow RetroBat's own choices where an Android
 * build of that core exists. Only the systems listed here can be launched; everything else says so plainly.
 * Status per system is recorded in docs/EMULATOR_COMPATIBILITY.md.
 */
object RetroArchCores {

    private val snes9x = Core("snes9x", "Snes9x")
    private val fceumm = Core("fceumm", "FCEUmm")
    private val nestopia = Core("nestopia", "Nestopia UE")
    private val mgba = Core("mgba", "mGBA")
    private val gambatte = Core("gambatte", "Gambatte")
    private val gpgx = Core("genesis_plus_gx", "Genesis Plus GX")
    private val picodrive = Core("picodrive", "PicoDrive")
    private val fbneo = Core("fbneo", "FinalBurn Neo")
    private val mame2003 = Core("mame2003_plus", "MAME 2003-Plus")
    private val pcsx = Core("pcsx_rearmed", "PCSX ReARMed")
    private val swanstation = Core("swanstation", "SwanStation")
    private val pce = Core("mednafen_pce_fast", "Beetle PCE Fast")

    private val bySystem: Map<String, List<Core>> = mapOf(
        "nes" to listOf(fceumm, nestopia),
        "fds" to listOf(fceumm, nestopia),
        "snes" to listOf(snes9x),
        "snes-msu1" to listOf(snes9x),
        "sfc" to listOf(snes9x),
        "gb" to listOf(gambatte, mgba),
        "gbc" to listOf(gambatte, mgba),
        "gba" to listOf(mgba),
        "megadrive" to listOf(gpgx, picodrive),
        "genesis" to listOf(gpgx, picodrive),
        "mastersystem" to listOf(gpgx, picodrive),
        "gamegear" to listOf(gpgx, picodrive),
        "sg1000" to listOf(gpgx),
        "megacd" to listOf(gpgx, picodrive),
        "segacd" to listOf(gpgx, picodrive),
        "sega32x" to listOf(picodrive),
        "psx" to listOf(swanstation, pcsx),
        "pcengine" to listOf(pce),
        "pcenginecd" to listOf(pce),
        "fbneo" to listOf(fbneo),
        "neogeo" to listOf(fbneo),
        "cps1" to listOf(fbneo),
        "cps2" to listOf(fbneo),
        "cps3" to listOf(fbneo),
        "mame" to listOf(mame2003, fbneo),
        "atari2600" to listOf(Core("stella", "Stella")),
        "lynx" to listOf(Core("handy", "Handy")),
        "ngp" to listOf(Core("mednafen_ngp", "Beetle NeoPop")),
        "ngpc" to listOf(Core("mednafen_ngp", "Beetle NeoPop")),
        "wswan" to listOf(Core("mednafen_wswan", "Beetle Cygne")),
        "wswanc" to listOf(Core("mednafen_wswan", "Beetle Cygne")),
        "n64" to listOf(Core("mupen64plus_next", "Mupen64Plus-Next"), Core("parallel_n64", "ParaLLEl N64")),
        "nds" to listOf(Core("melondsds", "melonDS DS"), Core("desmume", "DeSmuME")),
        "psp" to listOf(Core("ppsspp", "PPSSPP")),
        "dreamcast" to listOf(Core("flycast", "Flycast")),
        "dos" to listOf(Core("dosbox_pure", "DOSBox Pure")),
        "scummvm" to listOf(Core("scummvm", "ScummVM")),
        "colecovision" to listOf(Core("gearcoleco", "Gearcoleco")),
        "vectrex" to listOf(Core("vecx", "vecx")),
        "virtualboy" to listOf(Core("mednafen_vb", "Beetle VB")),
        "pokemini" to listOf(Core("pokemini", "PokeMini")),
    )

    fun coresFor(system: String): List<Core> = bySystem[system].orEmpty()

    /** Cores that need BIOS files in RetroArch's system folder before games will start. */
    val needsBios: Set<String> = setOf("swanstation", "pcsx_rearmed", "flycast", "mednafen_pce_fast")
}
