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
        "fbneo" to listOf(fbneo), "neogeo" to listOf(fbneo), "cps1" to listOf(fbneo), "cps2" to listOf(fbneo), "cps3" to listOf(fbneo),
        "mame" to listOf(mame2003, fbneo),
    )

    /** Arcade systems: games are romset .zips that may need the board's BIOS set from the same folder. */
    val arcadeSystems = setOf("fbneo", "neogeo", "cps1", "cps2", "cps3", "mame")

    /** Files to bring along with a game when they're next to it (Neo Geo BIOS for arcade sets). */
    fun companions(system: String): List<String> = if (system in arcadeSystems) listOf("neogeo.zip") else emptyList()

    /** Disc systems the built-in player runs; their disc images (cue/bin, chd, pbp …) are allowed. */
    private val discSystems = setOf("psx", "megacd", "segacd", "pcenginecd", "saturn")

    /** Disc images and playlists: only for the [discSystems]. */
    private val discExtensions = setOf("cue", "bin", "chd", "iso", "m3u", "ccd", "img", "gdi", "cdi", "pbp", "toc")

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
        else -> emptyMap()
    }

    fun byId(id: String): PlayerCore? = bySystem.values.flatten().firstOrNull { it.id == id }

    val all: Collection<PlayerCore> get() = bySystem.values.flatten().toSet()

    /**
     * Core options that follow the screen's orientation: the DS's two screens stacked in portrait and side by
     * side in landscape, so they always use the most of the screen.
     */
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
