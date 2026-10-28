package com.retroforge.player

/**
 * The libretro core RetroForge's built-in player uses per system. Only cores that draw in software (no OpenGL
 * hardware rendering yet) and load cartridge-sized games; disc systems still go to RetroArch or a standalone app.
 */
object PlayerCores {

    data class PlayerCore(val id: String, val name: String, val needsBios: Boolean = false)

    private val mgba = PlayerCore("mgba", "mGBA")
    private val gambatte = PlayerCore("gambatte", "Gambatte")
    private val fceumm = PlayerCore("fceumm", "FCEUmm")
    private val snes9x = PlayerCore("snes9x", "Snes9x")
    private val gpgx = PlayerCore("genesis_plus_gx", "Genesis Plus GX")
    private val picodrive = PlayerCore("picodrive", "PicoDrive")
    private val pce = PlayerCore("mednafen_pce_fast", "Beetle PCE Fast")
    private val ngp = PlayerCore("mednafen_ngp", "Beetle NeoPop")
    private val wswan = PlayerCore("mednafen_wswan", "Beetle Cygne")
    private val stella = PlayerCore("stella", "Stella")
    private val handy = PlayerCore("handy", "Handy", needsBios = true)
    private val desmume = PlayerCore("desmume", "DeSmuME")

    private val bySystem = mapOf(
        "gba" to mgba,
        "gb" to gambatte, "gbc" to gambatte,
        "nes" to fceumm,
        "snes" to snes9x, "sfc" to snes9x,
        "megadrive" to gpgx, "genesis" to gpgx, "mastersystem" to gpgx, "gamegear" to gpgx, "sg1000" to gpgx,
        "sega32x" to picodrive,
        "pcengine" to pce,
        "ngp" to ngp, "ngpc" to ngp,
        "wswan" to wswan, "wswanc" to wswan,
        "atari2600" to stella,
        "lynx" to handy,
        "nds" to desmume,
    )

    /** Disc images and playlists: these aren't for the built-in player yet. */
    private val discExtensions = setOf("cue", "bin", "chd", "iso", "m3u", "ccd", "img", "gdi", "cdi", "pbp", "toc")

    fun forSystem(system: String): PlayerCore? = bySystem[system]

    /** The core for this game, or null when the built-in player can't run it (unknown system or a disc). */
    fun forGame(system: String, fileName: String): PlayerCore? =
        bySystem[system]?.takeIf { fileName.substringAfterLast('.', "").lowercase() !in discExtensions }

    val all: Collection<PlayerCore> get() = bySystem.values.toSet()
}
