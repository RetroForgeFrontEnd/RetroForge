package com.retroforge.library

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class RawPackage(val `package`: String, val activity: String)

@Serializable
private data class RawEmulator(val id: String, val name: String, val page: String, val obtainium: String? = null, val obtainiumApp: String? = null, val packages: List<RawPackage>)

@Serializable
data class StandaloneOption(val emulator: String, val label: String, val command: String)

@Serializable
private data class RawFile(val source: String, val emulators: List<RawEmulator>, val systems: Map<String, List<StandaloneOption>>)

/** A free standalone emulator app: its package names (any one may be installed) and where to get it. */
data class StandaloneEmulator(
    val id: String,
    val name: String,
    /** Official download page. */
    val page: String,
    /** Repository URL Obtainium can track for updates, when releases are published there. */
    val obtainium: String?,
    /** Obtainium's full setup for this app (from the Obtainium Emulation Pack), for releases that aren't plain GitHub ones. */
    val obtainiumApp: String? = null,
    /** (package, activity) pairs, preferred first. */
    val components: List<Pair<String, String>>,
)

/**
 * Free standalone emulators per system, with the launch commands ES-DE uses on Android (MIT licence; see
 * `standalone.json`'s `source`). RetroForge never downloads or installs these itself; it links to official pages.
 */
class StandaloneEmulators(val emulators: Map<String, StandaloneEmulator>, private val systems: Map<String, List<StandaloneOption>>, val source: String) {

    fun optionsFor(system: String): List<Pair<StandaloneOption, StandaloneEmulator>> =
        systems[system].orEmpty().mapNotNull { o -> emulators[o.emulator]?.let { o to it } }

    val systemIds: Set<String> get() = systems.keys

    /** The same list without [ids] (emulators a build doesn't offer). */
    fun without(ids: Set<String>): StandaloneEmulators =
        if (ids.isEmpty()) this else StandaloneEmulators(emulators - ids, systems.mapValues { (_, o) -> o.filter { it.emulator !in ids } }, source)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): StandaloneEmulators {
            val raw = json.decodeFromString(RawFile.serializer(), text)
            val emulators = raw.emulators.associate { e ->
                e.id to StandaloneEmulator(e.id, e.name, e.page, e.obtainium, e.obtainiumApp, e.packages.map { it.`package` to it.activity })
            }
            // Two labels for one app and command (ES-DE lists AetherSX2 and NetherSX2 that way) would be one button twice.
            val systems = raw.systems.mapValues { (_, opts) -> opts.distinctBy { it.emulator to it.command } }
            return StandaloneEmulators(emulators, systems, raw.source)
        }

        fun bundled(): StandaloneEmulators = parse(
            checkNotNull(StandaloneEmulators::class.java.getResourceAsStream("/standalone.json")) { "standalone.json missing" }
                .bufferedReader().use { it.readText() },
        )
    }
}

/** What to fill a launch command with. Paths are null when the game isn't on local storage. */
data class LaunchVars(
    /** content:// URI of the game file (the emulator is granted read access to it). */
    val romUri: String,
    val romPath: String?,
    /** Folder holding the game, as a path. */
    val gameDirPath: String?,
    /** The collection folder (e.g. ".../roms"), as a path. */
    val romsPath: String?,
)

sealed interface IntentExtra {
    val name: String
    data class Text(override val name: String, val value: String) : IntentExtra
    data class Bool(override val name: String, val value: Boolean) : IntentExtra
}

/** An Android intent described as data, so commands can be parsed and tested off-device. */
data class LaunchSpec(
    val action: String?,
    val category: String?,
    val data: String?,
    val extras: List<IntentExtra>,
    val clearTask: Boolean,
    val clearTop: Boolean,
    /** content:// URIs the emulator must be allowed to read. */
    val grants: Set<String>,
)

class UnsupportedCommandException(message: String) : Exception(message)

/** Reads ES-DE style Android launch commands: "%ACTION%=… %DATA%=%ROMSAF% %EXTRA_bootPath%=%ROMSAF% …". */
object LaunchCommand {
    private val token = Regex("""%([A-Za-z_][\w.!-]*)%(?:=("[^"]*"|\S+))?""")
    private val variable = Regex("""%([A-Z]+)%""")

    fun parse(command: String, vars: LaunchVars): LaunchSpec {
        var action: String? = null
        var category: String? = null
        var data: String? = null
        val extras = mutableListOf<IntentExtra>()
        var clearTask = false
        var clearTop = false
        val grants = mutableSetOf<String>()
        val pathVars = mutableListOf<String>()

        fun fill(raw: String): String = variable.replace(raw.removeSurrounding("\"")) { m ->
            when (val name = m.groupValues[1]) {
                "ROMSAF", "ROMPROVIDER" -> vars.romUri.also { grants += it }
                "ROM", "ROMRAW" -> vars.romPath ?: run { pathVars += name; "" }
                "GAMEDIRRAW" -> vars.gameDirPath ?: run { pathVars += name; "" }
                "ROMPATHRAW", "ROMPATH" -> vars.romsPath ?: run { pathVars += name; "" }
                "BASENAME" -> (vars.romPath ?: vars.romUri).substringAfterLast('/').substringAfterLast("%2F").substringBeforeLast('.')
                else -> throw UnsupportedCommandException("unsupported value %$name%")
            }
        }

        for (m in token.findAll(command)) {
            val key = m.groupValues[1]
            val value = m.groupValues[2].takeIf { it.isNotEmpty() }
            when {
                key == "ACTION" -> action = value?.let(::fill)
                key == "CATEGORY" -> category = value?.let(::fill)
                key == "DATA" -> data = value?.let(::fill)
                key == "ACTIVITY_CLEAR_TASK" -> clearTask = true
                key == "ACTIVITY_CLEAR_TOP" -> clearTop = true
                key.startsWith("EXTRABOOL_") -> extras += IntentExtra.Bool(key.removePrefix("EXTRABOOL_"), value.toBoolean())
                key.startsWith("EXTRA_") -> extras += IntentExtra.Text(key.removePrefix("EXTRA_"), fill(value ?: ""))
                else -> throw UnsupportedCommandException("unsupported option %$key%")
            }
        }
        if (pathVars.isNotEmpty()) throw UnsupportedCommandException("needs a file path, but this game isn't on local storage")
        return LaunchSpec(action, category, data, extras, clearTask, clearTop, grants)
    }
}
