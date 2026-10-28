package com.retroforge.library

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest

@Serializable
data class BiosFile(
    /** The name the core looks for, relative to RetroArch's system folder (may include a sub-folder: "dc/dc_boot.bin"). */
    val name: String,
    val md5: List<String>,
    val size: Long,
) {
    val baseName: String get() = name.substringAfterLast('/')
}

@Serializable
enum class BiosNeed {
    @SerialName("required") REQUIRED,
    @SerialName("recommended") RECOMMENDED,
    @SerialName("optional") OPTIONAL,
}

@Serializable
data class BiosRule(
    val systems: List<String>,
    val core: String,
    val need: BiosNeed,
    /** "any": one of the files is enough. "all": every file is needed. */
    val mode: String,
    val note: String = "",
    val files: List<BiosFile>,
)

@Serializable
private data class BiosRulesFile(val rules: List<BiosRule>)

/** A file in the user's folder that matches a BIOS the core needs. */
data class FoundBios(
    val wanted: BiosFile,
    val nodeId: String,
    /** Its name in the user's folder, which may differ from [BiosFile.name]. */
    val foundAs: String,
    /** true when its MD5 matches libretro's database; false when only the name matched. */
    val verified: Boolean,
)

enum class BiosStatus { OK, UNVERIFIED, PARTIAL, MISSING }

data class BiosReport(val rule: BiosRule, val status: BiosStatus, val found: List<FoundBios>, val missing: List<BiosFile>)

/**
 * Checks a folder of the user's own BIOS files against what each system's core needs. Read-only: it lists the
 * folder and hashes only small files whose size or name matches a known BIOS. It never downloads anything.
 */
class BiosChecker(val rules: List<BiosRule>) {

    fun rulesFor(systems: Collection<String>): List<BiosRule> = rules.filter { rule -> rule.systems.any { it in systems } }

    fun check(folder: ScanNode, systems: Collection<String>): List<BiosReport> {
        val wanted = rulesFor(systems)
        if (wanted.isEmpty()) return emptyList()
        val sizes = wanted.flatMap { r -> r.files.map { it.size } }.toSet()
        val names = wanted.flatMap { r -> r.files.map { it.baseName.lowercase() } }.toSet()

        val byMd5 = mutableMapOf<String, ScanNode>()
        val byName = mutableMapOf<String, ScanNode>()
        collect(folder, depth = 0) { node ->
            val lower = node.name.lowercase()
            if (lower in names) byName.putIfAbsent(lower, node)
            if (node.size in sizes || lower in names) {
                runCatching { md5(node) }.getOrNull()?.let { byMd5.putIfAbsent(it, node) }
            }
        }

        return wanted.map { rule ->
            val found = rule.files.mapNotNull { file ->
                file.md5.firstNotNullOfOrNull { byMd5[it] }?.let { FoundBios(file, it.id, it.name, verified = true) }
                    ?: byName[file.baseName.lowercase()]?.let { FoundBios(file, it.id, it.name, verified = false) }
            }
            val missing = rule.files.filter { f -> found.none { it.wanted == f } }
            val status = when {
                rule.mode == "any" && found.any { it.verified } -> BiosStatus.OK
                rule.mode == "any" && found.isNotEmpty() -> BiosStatus.UNVERIFIED
                found.isEmpty() -> BiosStatus.MISSING
                missing.isNotEmpty() -> BiosStatus.PARTIAL
                found.all { it.verified } -> BiosStatus.OK
                else -> BiosStatus.UNVERIFIED
            }
            BiosReport(rule, status, found, missing)
        }
    }

    // RetroBat keeps a few BIOS in sub-folders (e.g. "dc"); two levels is enough and keeps big folders quick.
    private fun collect(node: ScanNode, depth: Int, visit: (ScanNode) -> Unit) {
        val children = runCatching { node.children() }.getOrDefault(emptyList())
        for (child in children) {
            when {
                child.isDirectory -> if (depth < 2) collect(child, depth + 1, visit)
                child.size in 1..MAX_BIOS_BYTES -> visit(child)
            }
        }
    }

    private fun md5(node: ScanNode): String = node.open().use { input ->
        val digest = MessageDigest.getInstance("MD5")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val MAX_BIOS_BYTES = 64L * 1024 * 1024
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String) = BiosChecker(json.decodeFromString(BiosRulesFile.serializer(), text).rules)

        /** Rules bundled with RetroForge (checksums from libretro's System.dat; see the file's `source`). */
        fun bundled(): BiosChecker = parse(
            checkNotNull(BiosChecker::class.java.getResourceAsStream("/bios.json")) { "bios.json missing" }
                .bufferedReader().use { it.readText() },
        )
    }
}
