package com.retroforge.library

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * A native Android port of a game: a decompilation, a recompilation or a PC source port. It needs the user's own copy
 * of the game; RetroForge only points to the project's official releases.
 */
@Serializable
data class GamePort(
    val id: String,
    val name: String,
    /** "decomp", "recomp" or "pc". */
    val kind: String,
    /** The project's GitHub page; releases are at [repo]/releases. */
    val repo: String,
    /** Library systems whose games this port can use. Empty: the files can't come from a retro library (PC, Xbox 360). */
    val systems: List<String> = emptyList(),
    /** Regular expression matched against a game's title in lower case. */
    val titleMatch: String? = null,
    /** What the user has to provide, in plain words. */
    val needs: String,
    /** "verified" or "experimental". */
    val status: String,
    /** The app's package name, when known, to open it once installed. */
    val `package`: String? = null,
    /** "android": a native Android app. "windows": a Windows build, run through GameNative or Winlator. */
    val platform: String = "android",
) {
    val isWindows: Boolean get() = platform == "windows"

    private val pattern by lazy { titleMatch?.let { Regex(it) } }

    /** True when the user's game ([system], [title]) is one this port is built from. */
    fun matches(system: String, title: String): Boolean = system in systems && pattern?.containsMatchIn(title.lowercase()) == true

    val kindLabel: String get() = when (kind) {
        "decomp" -> "Decompilation"
        "recomp" -> "Recompilation"
        else -> "PC port"
    }
}

@Serializable
private data class PortsFile(val source: String, val ports: List<GamePort>)

object GamePorts {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): List<GamePort> = json.decodeFromString(PortsFile.serializer(), text).ports

    fun bundled(): List<GamePort> =
        parse(GamePorts::class.java.getResourceAsStream("/ports.json")!!.use { it.readBytes().decodeToString() })
}
