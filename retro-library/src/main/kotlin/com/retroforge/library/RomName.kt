package com.retroforge.library

/**
 * What a No-Intro / GoodTools / TOSEC style file name says about a game, e.g.
 * "Legend of Zelda, The - A Link to the Past (USA) (Rev 1) [!].sfc".
 */
data class RomName(
    val title: String,
    /** Title for sorting and duplicate matching: lowercase, no leading article, punctuation collapsed. */
    val sortKey: String,
    val regions: List<String>,
    val revision: String?,
    val flags: Set<RomFlag>,
    /** Every bracketed tag, in order, for display. */
    val tags: List<String>,
    /** A patch's name in square brackets ("Game (USA) [Hard Mode].nes", as RetroForge's patcher names them): a hack. */
    val hackName: String? = null,
)

enum class RomFlag { VERIFIED, BETA, PROTO, DEMO, SAMPLE, HACK, TRANSLATION, UNLICENSED, PIRATE, BAD_DUMP, ALTERNATE, OVERDUMP, FIXED, TRAINED }

object RomNames {

    private val regionWords = setOf(
        "USA", "Europe", "Japan", "World", "Asia", "Australia", "Brazil", "Canada", "China", "France", "Germany",
        "Hong Kong", "Italy", "Korea", "Netherlands", "Spain", "Sweden", "Taiwan", "UK", "Russia", "Scandinavia",
    )
    private val goodToolsRegions = mapOf("U" to "USA", "E" to "Europe", "J" to "Japan", "W" to "World", "UE" to "USA, Europe", "JU" to "Japan, USA", "A" to "Australia")
    private val tagPattern = Regex("""[(\[]([^)\]]*)[)\]]""")
    /**
     * Bracket tags that aren't a patch's name: TOSEC and GoodTools dump flags with their details ([cr Elite], [t +2],
     * [a2], [f NTSC], [tr Fre], [m], [h TSTH] is caught as a hack above), set names ([GoodMSX]), Switch title ids and
     * versions ([0100C2700C252000], [v0]), disc and disk numbers, serials ([SLUS-01234]), years and notes like [CPM version].
     */
    private const val LANG = "en|fr|de|es|it|ja|nl|sv|pt|eng|fre|ger|spa|ita|jpn|por|dut|swe"
    private val notPatch = Regex(
        """(!.*|(cr|tr|[abcfhmoptuvx])(\d+[a-z]?)?([ +-].*)?|good\w*|[0-9a-f]{16}|v\d+(\.\d+)*.*|(disc|disk|side|cd|dvd) ?\w+.*|""" +
            """[a-z]{2,5}-?\d{3,6}.*|\d{4}.*|.*\b(version|boot|bootfile|update|dlc|base|cracked|trained|docs?|manual|disks?|drive|req\.?|aka|original|""" +
            """english|french|german|spanish|italian|translated|budget|monochrome|complete|proper|final|start|d88|dsk2rom|dcres|accel\d*)\b.*|""" +
            """rev\w*.*|\d+(\.\d+)+|""" +
            """($LANG)([ ,+-]+($LANG))*|unl|pd|public domain|alt.*|bios.*)""",
        RegexOption.IGNORE_CASE,
    )
    // "Legend of Zelda, The" and "Legend of Zelda, The - A Link to the Past".
    private val articleSuffix = Regex("""^(.+?),\s*(The|A|An)(\s+-\s+.*)?$""", RegexOption.IGNORE_CASE)

    fun parse(fileName: String): RomName {
        val base = fileName.substringBeforeLast('.', fileName).let { if (it.isEmpty()) fileName else it }
        val firstTag = base.indexOfFirst { it == '(' || it == '[' }.takeIf { it > 0 } ?: base.length
        val rawTitle = base.substring(0, firstTag).trim().replace('_', ' ').ifEmpty { base.trim() }
        val tags = tagPattern.findAll(base.substring(firstTag)).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.toList()

        val regions = mutableListOf<String>()
        var revision: String? = null
        val flags = mutableSetOf<RomFlag>()
        for (tag in tags) {
            val lower = tag.lowercase()
            when {
                tag.split(",").map { it.trim() }.all { it in regionWords } -> regions += tag.split(",").map { it.trim() }
                tag in goodToolsRegions -> regions += goodToolsRegions.getValue(tag).split(", ")
                lower.startsWith("rev ") || Regex("""v\d+(\.\d+)*""").matches(lower) || lower.startsWith("rev") && lower.length <= 6 -> revision = tag
                tag == "!" -> flags += RomFlag.VERIFIED
                lower.startsWith("beta") -> flags += RomFlag.BETA
                lower.startsWith("proto") -> flags += RomFlag.PROTO
                lower.startsWith("demo") -> flags += RomFlag.DEMO
                lower == "sample" -> flags += RomFlag.SAMPLE
                lower.startsWith("hack") || Regex("""h\d*[a-z]?( .*)?""").matches(lower) -> flags += RomFlag.HACK
                Regex("""t[+-].*""").matches(lower) -> flags += RomFlag.TRANSLATION
                lower == "unl" -> flags += RomFlag.UNLICENSED
                Regex("""p\d*""").matches(lower) -> flags += RomFlag.PIRATE
                Regex("""b\d*""").matches(lower) -> flags += RomFlag.BAD_DUMP
                Regex("""a\d*""").matches(lower) -> flags += RomFlag.ALTERNATE
                Regex("""o\d*""").matches(lower) -> flags += RomFlag.OVERDUMP
                Regex("""f\d*""").matches(lower) -> flags += RomFlag.FIXED
                Regex("""t\d*""").matches(lower) -> flags += RomFlag.TRAINED
            }
        }
        // Square-bracket text that isn't a dump or catalogue tag names a patch (RetroForge's patcher writes "[Hard Mode]"): a hack.
        val hackName = Regex("""\[([^\]]*)\]""").findAll(base.substring(firstTag)).map { it.groupValues[1].trim() }
            .firstOrNull { tag -> tag.length >= 4 && !notPatch.matches(tag) && tag.split(",").map { it.trim() }.any { it !in regionWords } }
        if (hackName != null) flags += RomFlag.HACK
        val title = articleSuffix.matchEntire(rawTitle)?.let { "${it.groupValues[2]} ${it.groupValues[1].trim()}${it.groupValues[3]}" } ?: rawTitle
        return RomName(title, sortKey(title), regions.distinct(), revision, flags, tags, hackName)
    }

    fun sortKey(title: String): String = title.lowercase()
        .replace(Regex("""^(the|a|an)\s+"""), "")
        .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
        .trim()
}
