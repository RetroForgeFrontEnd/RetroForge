package com.retroforge.app.player

/**
 * Saves from other emulators and devices, brought into the built-in player: what a file is, which game it probably
 * belongs to, and the built-in player's own save (`saves/<system>/<game>.srm`) made from it.
 *
 * - PlayStation: whole cards (.mcr, .mcd, .mc, .srm, .vm1, DexDrive .gme) and single saves (.mcs) are copied onto
 *   the game's card, keeping the saves already on it.
 * - Nintendo 64: Mupen64Plus-Next keeps a game's EEPROM, Controller Paks, SRAM and FlashRAM in one .srm; .eep, .mpk,
 *   .sra and .fla files from Project64, standalone Mupen64Plus and save dumpers go into their part of it.
 * - Cartridges with battery saves (NES, SNES, Game Boy, GBA, Mega Drive and the rest): .sav, .srm, .sram and .eep
 *   files are the cartridge's memory as it is, which is what the cores read.
 */
object SaveImport {
    enum class Kind { PS1_CARD, PS1_SINGLE, N64_EEPROM, N64_PAK, N64_SRAM, N64_FLASH, N64_SRM, BATTERY }

    /** What a file is: [systems] it can belong to and, for PlayStation cards, the titles of the saves on it. */
    data class Detected(val kind: Kind, val systems: Set<String>, val titles: List<String>)

    /** Systems whose built-in player saves are the cartridge's battery memory as it is. */
    val BATTERY_SYSTEMS = setOf(
        "nes", "fds", "snes", "satellaview", "sufami", "gb", "gbc", "gba", "megadrive", "genesis", "sega32x",
        "mastersystem", "gamegear", "sg1000", "pcengine", "atari7800", "lynx", "ngp", "ngpc", "wonderswan",
        "wonderswancolor", "virtualboy", "pokemini", "colecovision", "msx",
    )

    // Mupen64Plus-Next's .srm: EEPROM, four Controller Paks, SRAM, FlashRAM, one after the other.
    private const val N64_EEPROM = 0
    private const val N64_PAKS = 0x800
    private const val N64_SRAM = N64_PAKS + 4 * 0x8000
    private const val N64_FLASH = N64_SRAM + 0x8000
    const val N64_SRM_SIZE = N64_FLASH + 0x20000

    fun detect(fileName: String, bytes: ByteArray): Detected? {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        val card = runCatching { MemoryCard.read(bytes) }.getOrNull()?.takeIf { looksLikeCard(it.data) }
        if (card != null) return Detected(Kind.PS1_CARD, setOf("psx"), card.saves.map { it.title })
        if (ext == "mcs" || ext == "ps1") {
            val single = runCatching { MemoryCard.fromSingleSave(bytes) }.getOrNull()
            if (single != null) return Detected(Kind.PS1_SINGLE, setOf("psx"), single.saves.map { it.title })
        }
        val n64 = setOf("n64")
        return when {
            bytes.size == N64_SRM_SIZE -> Detected(Kind.N64_SRM, n64, emptyList())
            ext == "eep" && (bytes.size == 0x200 || bytes.size == 0x800) -> Detected(Kind.N64_EEPROM, n64, emptyList())
            (ext == "mpk" || ext == "mpk1" || ext == "pak") && bytes.size == 0x8000 -> Detected(Kind.N64_PAK, n64, emptyList())
            ext == "sra" && bytes.size == 0x8000 -> Detected(Kind.N64_SRAM, n64, emptyList())
            ext == "fla" && bytes.size == 0x20000 -> Detected(Kind.N64_FLASH, n64, emptyList())
            ext in setOf("sav", "srm", "sram", "eep", "bkp") && bytes.size in 64..(1 shl 20) -> Detected(Kind.BATTERY, BATTERY_SYSTEMS, emptyList())
            else -> null
        }
    }

    /** A PlayStation card image with the "MC" mark, so a 128 KB cartridge save isn't taken for one. */
    private fun looksLikeCard(data: ByteArray) = data[0] == 'M'.code.toByte() && data[1] == 'C'.code.toByte()

    /**
     * The new save for the game, from [bytes] of [detected] and the game's [existing] save (null if none).
     * [swapBytes] turns N64 SRAM/FlashRAM saves round in 4-byte words, for files from emulators that keep them the
     * other way. Throws when a PlayStation card hasn't room for the saves.
     */
    fun convert(detected: Detected, bytes: ByteArray, existing: ByteArray?, swapBytes: Boolean = false): ByteArray = when (detected.kind) {
        Kind.PS1_CARD, Kind.PS1_SINGLE -> {
            val from = if (detected.kind == Kind.PS1_CARD) MemoryCard.read(bytes) else MemoryCard.fromSingleSave(bytes)
            val target = existing?.takeIf { it.size == MemoryCard.SIZE }?.let { MemoryCard(it.copyOf()) } ?: MemoryCard.blank()
            from.saves.forEach { save ->
                check(target.copy(from, save)) { "full:${target.freeBlocks}:${save.blocks.size}" }
            }
            target.data
        }
        Kind.N64_SRM -> bytes
        Kind.N64_EEPROM, Kind.N64_PAK, Kind.N64_SRAM, Kind.N64_FLASH -> {
            val srm = existing?.takeIf { it.size == N64_SRM_SIZE }?.copyOf() ?: ByteArray(N64_SRM_SIZE)
            val part = if (swapBytes && (detected.kind == Kind.N64_SRAM || detected.kind == Kind.N64_FLASH)) swapWords(bytes) else bytes
            val at = when (detected.kind) {
                Kind.N64_EEPROM -> N64_EEPROM
                Kind.N64_PAK -> N64_PAKS
                Kind.N64_SRAM -> N64_SRAM
                else -> N64_FLASH
            }
            System.arraycopy(part, 0, srm, at, part.size)
            srm
        }
        Kind.BATTERY -> bytes
    }

    fun swapWords(bytes: ByteArray): ByteArray = bytes.copyOf().also { b ->
        for (i in 0 until b.size - 3 step 4) {
            val (a, c) = b[i] to b[i + 1]
            b[i] = b[i + 3]; b[i + 1] = b[i + 2]; b[i + 2] = c; b[i + 3] = a
        }
    }

    /** "Final Fantasy VII (USA) (Disc 1)" → "final fantasy vii": names compared without tags, case or punctuation. */
    fun plain(name: String): String = name.substringBeforeLast('.', name).let { if (it.length < name.length - 5) name else it }
        .replace(Regex("""\([^)]*\)|\[[^]]*]"""), " ")
        .lowercase().replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()

    /** How well [game] (a title or file name) matches any of [names], 0 to 100. */
    fun score(game: String, names: List<String>): Int {
        val g = plain(game)
        if (g.isEmpty()) return 0
        val gw = g.split(' ').toSet()
        return names.map(::plain).filter { it.isNotEmpty() }.maxOfOrNull { n ->
            when {
                n == g -> 100
                n.startsWith(g) || g.startsWith(n) -> 80
                else -> {
                    val nw = n.split(' ').toSet()
                    (60 * (gw intersect nw).size / (gw union nw).size.coerceAtLeast(1))
                }
            }
        } ?: 0
    }
}
