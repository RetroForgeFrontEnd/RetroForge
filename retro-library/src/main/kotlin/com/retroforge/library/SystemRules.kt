package com.retroforge.library

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SystemRule(
    val id: String,
    val name: String,
    val extensions: Set<String>,
    val aliases: List<String> = emptyList(),
)

@Serializable
private data class SystemRulesFile(val systems: List<SystemRule>)

/** Maps folder names to systems. Folder names match RetroBat/ES-DE (`snes`, `psx` …) plus a few common aliases. */
class SystemRules(val systems: List<SystemRule>) {

    private val byFolder: Map<String, SystemRule> = buildMap {
        systems.forEach { rule -> rule.aliases.forEach { put(it.lowercase(), rule) } }
        systems.forEach { rule -> put(rule.id.lowercase(), rule) }
    }

    fun forFolder(name: String): SystemRule? = byFolder[name.lowercase()]

    fun byId(id: String): SystemRule? = systems.firstOrNull { it.id == id }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String) = SystemRules(json.decodeFromString(SystemRulesFile.serializer(), text).systems)

        /** The rules bundled with RetroForge (generated from RetroBat's es_systems.cfg; see the file's `source`). */
        fun bundled(): SystemRules = parse(
            checkNotNull(SystemRules::class.java.getResourceAsStream("/systems.json")) { "systems.json missing" }
                .bufferedReader().use { it.readText() },
        )
    }
}
