package com.retroforge.app.player

/**
 * Why an arcade game didn't start: arcade romsets only work with the emulator version they were made for, and the
 * emulators say which files they couldn't find ("epr-23603.ic8 NOT FOUND", "missing: segabill").
 */
object ArcadeRomsets {
    private val missingLine = Regex("""not found|missing|required files""", RegexOption.IGNORE_CASE)
    /** A ROM file name: letters, digits and dashes with a dot and a short extension ("epr-23603.ic8", "u1.bin"). */
    private val fileName = Regex("""\b[A-Za-z0-9_][A-Za-z0-9_\-]*\.[A-Za-z0-9]{1,5}\b""")

    /** The files the emulator said were missing, in the order it said them (at most [limit]). */
    fun missingFiles(messages: List<String>, limit: Int = 6): List<String> =
        messages.filter { missingLine.containsMatchIn(it) }
            .flatMap { line -> fileName.findAll(line).map { it.value } }
            .filter { !it.endsWith(".zip", true) && !it.endsWith(".7z", true) }
            .distinct()
            .take(limit)

    /** What to tell the player; null when the emulator didn't name missing files. */
    fun explain(emulator: String, messages: List<String>): String? {
        val missing = missingFiles(messages).ifEmpty { return null }
        return "This arcade game's files don't match $emulator: it couldn't find ${missing.joinToString()}. Arcade romsets " +
            "only work with the emulator version they were made for (FinalBurn Neo needs FinalBurn Neo sets, MAME 2003-Plus " +
            "needs MAME 0.78 sets, and so on). Try the game with another emulator (on its page), or use a romset made for this one."
    }
}
